#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ROOT=$(cd -- "$SCRIPT_DIR/../.." && pwd)

usage() {
  echo "Usage: $0 <branch> <version>" >&2
  echo "Example: $0 codex/latest 1.0.11" >&2
  exit 1
}

if [[ $# -lt 2 ]]; then
  usage
fi

BRANCH="$1"
VERSION="$2"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "This script must be run on macOS." >&2
  exit 1
fi

cd "$ROOT"

export PATH="/Users/admin/apache-maven/bin:/Library/Java/JavaVirtualMachines/liberica-jdk-21.jdk/Contents/Home/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"

if ! command -v mvn >/dev/null 2>&1; then
  echo "mvn not found in PATH: $PATH" >&2
  exit 1
fi

echo "===================================="
echo "Preparing Shale macOS release"
echo "Branch:  $BRANCH"
echo "Version: $VERSION"
echo "Root:    $ROOT"
echo "===================================="
echo

echo "Step 1: Git fetch / checkout / pull"
git fetch origin
git checkout "$BRANCH"
git pull origin "$BRANCH"

echo
echo "Step 2: Update root pom version"
python3 - "$ROOT" "$VERSION" <<'PY'
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
version = sys.argv[2]

root_pom = root / "pom.xml"
text = root_pom.read_text(encoding="utf-8")
text_new, count = re.subn(r'(<artifactId>shale-parent</artifactId>\s*<version>)([^<]+)(</version>)',
                          r'\g<1>' + version + r'\g<3>',
                          text, count=1, flags=re.DOTALL)
if count != 1:
    raise SystemExit("Failed to update root pom version")
root_pom.write_text(text_new, encoding="utf-8")
PY

echo
echo "Step 3: Update child parent versions"
python3 - "$ROOT" "$VERSION" <<'PY'
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
version = sys.argv[2]

child_poms = [
    root / "shale-core" / "pom.xml",
    root / "shale-data" / "pom.xml",
    root / "shale-ui" / "pom.xml",
    root / "shale-desktop" / "pom.xml",
    root / "shale-updater" / "pom.xml",
]

pattern = re.compile(
    r'(<parent>\s*<groupId>com\.shale</groupId>\s*<artifactId>shale-parent</artifactId>\s*<version>)([^<]+)(</version>)',
    re.DOTALL
)

for pom in child_poms:
    text = pom.read_text(encoding="utf-8")
    text_new, count = pattern.subn(r'\g<1>' + version + r'\g<3>', text, count=1)
    if count != 1:
        raise SystemExit(f"Failed to update parent version in {pom}")
    pom.write_text(text_new, encoding="utf-8")
PY

echo
echo "Step 4: Verify versions"
grep -n "<version>$VERSION</version>" "$ROOT/pom.xml" >/dev/null
for pom in "$ROOT"/shale-core/pom.xml "$ROOT"/shale-data/pom.xml "$ROOT"/shale-ui/pom.xml "$ROOT"/shale-desktop/pom.xml "$ROOT"/shale-updater/pom.xml; do
  grep -n "<version>$VERSION</version>" "$pom" >/dev/null || {
    echo "Version verification failed for $pom" >&2
    exit 1
  }
done

echo
echo "Step 5: Clean old mac outputs"
rm -rf "$ROOT/dist-macos"
mkdir -p "$ROOT/dist-macos"

echo
echo "Step 6: Build macOS app image"

export JAVAFX_JMODS_DIR="$ROOT/build/assets/javafx-jmods-21.0.10"

"$ROOT/build/scripts/build-shale-macos.sh" app-image

DIST_MAC="$ROOT/dist-macos"
MAC_ZIP_NAME="ShaleApp-$VERSION-mac.zip"
MAC_ZIP_PATH="$DIST_MAC/$MAC_ZIP_NAME"
META_PATH="$DIST_MAC/shale-mac-release.json"

echo
echo "Step 7: Create Mac updater ZIP"
ditto -c -k --sequesterRsrc --keepParent \
  "$DIST_MAC/Shale.app" \
  "$MAC_ZIP_PATH"

echo
echo "Step 8: Compute SHA256"
MAC_SHA256=$(shasum -a 256 "$MAC_ZIP_PATH" | awk '{print tolower($1)}')

echo
echo "Step 9: Write metadata JSON"
cat > "$META_PATH" <<EOF
{
  "version": "$VERSION",
  "macZipName": "$MAC_ZIP_NAME",
  "macSha256": "$MAC_SHA256"
}
EOF

echo
echo "Step 10: Final verification"
if [[ ! -f "$MAC_ZIP_PATH" ]]; then
  echo "Expected Mac ZIP not found: $MAC_ZIP_PATH" >&2
  exit 1
fi

if [[ ! -f "$META_PATH" ]]; then
  echo "Expected metadata JSON not found: $META_PATH" >&2
  exit 1
fi

echo
echo "===================================="
echo "Mac release prepared successfully"
echo "ZIP:  $MAC_ZIP_PATH"
echo "JSON: $META_PATH"
echo "SHA:  $MAC_SHA256"
echo "===================================="