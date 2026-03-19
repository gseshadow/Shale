#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ROOT=$(cd -- "$SCRIPT_DIR/../.." && pwd)
TYPE=${1:-app-image}

if [[ "$TYPE" != "app-image" && "$TYPE" != "dmg" ]]; then
  echo "Usage: $0 [app-image|dmg]" >&2
  exit 1
fi

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

JAVAFX_JMODS_DIR=${JAVAFX_JMODS_DIR:-$ROOT/build/assets/javafx-jmods-macos}
if [[ ! -d "$JAVAFX_JMODS_DIR" ]]; then
  echo "Missing JavaFX jmods directory: $JAVAFX_JMODS_DIR" >&2
  echo "Set JAVAFX_JMODS_DIR to a macOS JavaFX jmods folder before running this script." >&2
  exit 1
fi

DESKTOP_TARGET="$ROOT/shale-desktop/target"
DIST_DIR="$ROOT/dist-macos"
mkdir -p "$DIST_DIR"

rm -rf "$DIST_DIR/Shale" "$DIST_DIR/Shale.dmg"

mvn -f "$ROOT/pom.xml" -pl shale-desktop -am clean package

jpackage \
  --type "$TYPE" \
  --name Shale \
  --input "$DESKTOP_TARGET" \
  --dest "$DIST_DIR" \
  --main-jar "shale-desktop-$VERSION.jar" \
  --main-class com.shale.desktop.MainApp \
  --module-path "$JAVAFX_JMODS_DIR" \
  --add-modules javafx.controls,javafx.fxml,java.sql,java.naming,java.net.http,jdk.crypto.ec \
  --app-version "$VERSION" \
  --vendor "Get Downing" \
  --description "Shale Desktop"

echo "macOS package created in $DIST_DIR"
