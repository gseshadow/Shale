#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ROOT=$(cd -- "$SCRIPT_DIR/../.." && pwd)

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "This script must be run on macOS." >&2
  exit 1
fi

VERSION=$(python3 - <<'PY'
from pathlib import Path
import re
text = Path('pom.xml').read_text(encoding='utf-8')
match = re.search(r'<version>([^<]+)</version>', text)
if not match:
    raise SystemExit('Failed to resolve version from pom.xml')
print(match.group(1))
PY
)

cd "$ROOT"

DIST_MAC="$ROOT/dist-macos"
MAC_ZIP_NAME="ShaleApp-$VERSION-mac.zip"
MAC_ZIP_PATH="$DIST_MAC/$MAC_ZIP_NAME"
META_PATH="$DIST_MAC/shale-mac-release.json"

echo "===================================="
echo "Building Shale macOS release $VERSION"
echo "===================================="

echo "Step 1: Building app image"
echo "Runtime image preference: MAC_RUNTIME_IMAGE=${MAC_RUNTIME_IMAGE:-<unset>} JAVA_HOME=${JAVA_HOME:-<unset>}"
./build/scripts/build-shale-macos.sh app-image

echo "Step 2: Creating Mac updater zip"
rm -f "$MAC_ZIP_PATH"
ditto -c -k --sequesterRsrc --keepParent \
  "$DIST_MAC/Shale.app" \
  "$MAC_ZIP_PATH"

echo "Step 3: Computing SHA256"
MAC_SHA256=$(shasum -a 256 "$MAC_ZIP_PATH" | awk '{print tolower($1)}')

echo "Step 4: Writing metadata"
cat > "$META_PATH" <<EOF
{
  "version": "$VERSION",
  "macZipName": "$MAC_ZIP_NAME",
  "macSha256": "$MAC_SHA256"
}
EOF

echo
echo "===================================="
echo "Mac release ready"
echo "ZIP:  $MAC_ZIP_PATH"
echo "JSON: $META_PATH"
echo "SHA:  $MAC_SHA256"
echo "===================================="
