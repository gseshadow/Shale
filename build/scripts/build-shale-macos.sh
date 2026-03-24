#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ROOT=$(cd -- "$SCRIPT_DIR/../.." && pwd)
TYPE=${1:-both}

usage() {
  echo "Usage: $0 [app-image|dmg|both]" >&2
  exit 1
}

if [[ "$TYPE" != "app-image" && "$TYPE" != "dmg" && "$TYPE" != "both" ]]; then
  usage
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

resolve_runtime_image() {
  local candidate
  local resolved=""
  local checked=()

  if [[ -n "${MAC_RUNTIME_IMAGE:-}" ]]; then
    candidate="$MAC_RUNTIME_IMAGE"
    checked+=("$candidate")
    if [[ -d "$candidate/Contents/Home/bin" ]]; then
      resolved="$candidate"
    elif [[ -d "$candidate/bin" ]]; then
      resolved="$candidate"
    fi
  fi

  if [[ -z "$resolved" && -n "${JAVA_HOME:-}" ]]; then
    candidate="$JAVA_HOME"
    checked+=("$candidate")
    if [[ -d "$candidate/Contents/Home/bin" ]]; then
      resolved="$candidate"
    elif [[ -d "$candidate/bin" ]]; then
      resolved="$candidate"
    fi
  fi

  if [[ -z "$resolved" ]]; then
    echo "No valid macOS runtime image found." >&2
    echo "Provide MAC_RUNTIME_IMAGE (preferred) or JAVA_HOME." >&2
    if [[ ${#checked[@]} -gt 0 ]]; then
      echo "Checked candidates:" >&2
      printf '  - %s\n' "${checked[@]}" >&2
    fi
    exit 1
  fi

  if [[ -d "$resolved/Contents/Home/bin" ]]; then
    echo "$resolved/Contents/Home"
  else
    echo "$resolved"
  fi
}

DESKTOP_TARGET="$ROOT/shale-desktop/target"
DIST_DIR="$ROOT/dist-macos"
mkdir -p "$DIST_DIR"

rm -rf "$DIST_DIR/Shale" "$DIST_DIR/Shale.app"
rm -f "$DIST_DIR"/Shale*.dmg

RUNTIME_IMAGE=$(resolve_runtime_image)
RUNTIME_SOURCE=${MAC_RUNTIME_IMAGE:-${JAVA_HOME:-}}

if [[ ! -x "$RUNTIME_IMAGE/bin/java" ]]; then
  echo "Invalid runtime image: expected executable java binary at $RUNTIME_IMAGE/bin/java" >&2
  echo "Resolved from: $RUNTIME_SOURCE" >&2
  exit 1
fi

echo "Using macOS runtime image for packaging: $RUNTIME_IMAGE"
echo "Runtime image source candidate: $RUNTIME_SOURCE"

verify_runtime_image() {
  local app_path=$1
  local java_binary="$app_path/Contents/runtime/Contents/Home/bin/java"

  if [[ ! -x "$java_binary" ]]; then
    echo "Expected bundled Java launcher missing or not executable: $java_binary" >&2
    exit 1
  fi
}

build_package() {
  local package_type=$1

  jpackage \
    --type "$package_type" \
    --name Shale \
    --input "$DESKTOP_TARGET" \
    --dest "$DIST_DIR" \
    --icon "$ROOT/build/assets/Shale.icns" \
    --main-jar "shale-desktop-$VERSION.jar" \
    --main-class com.shale.desktop.MainApp \
    --runtime-image "$RUNTIME_IMAGE" \
    --app-version "$VERSION" \
    --vendor "Get Downing" \
    --description "Shale Desktop"

  if [[ "$package_type" == "app-image" ]]; then
    verify_runtime_image "$DIST_DIR/Shale.app"
  fi
}

mvn -f "$ROOT/pom.xml" -pl shale-desktop -am clean package

case "$TYPE" in
  app-image|dmg)
    build_package "$TYPE"
    ;;
  both)
    build_package app-image
    build_package dmg
    ;;
esac

echo "macOS package output created in $DIST_DIR"
