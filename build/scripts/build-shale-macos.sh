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

echo "setting runtime environment"
DEFAULT_MAC_JAVA_HOME="/Library/Java/JavaVirtualMachines/liberica-jdk-21.jdk/Contents/Home"
export JAVA_HOME="${JAVA_HOME:-}"
export MAC_RUNTIME_IMAGE="${MAC_RUNTIME_IMAGE:-}"


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

resolve_base_jdk_runtime() {
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

  if [[ -z "$resolved" && -n "$DEFAULT_MAC_JAVA_HOME" ]]; then
    candidate="$DEFAULT_MAC_JAVA_HOME"
    checked+=("$candidate")
    if [[ -d "$candidate/Contents/Home/bin" ]]; then
      resolved="$candidate"
    elif [[ -d "$candidate/bin" ]]; then
      resolved="$candidate"
    fi
  fi

  if [[ -z "$resolved" ]]; then
    echo "No valid macOS JDK runtime found." >&2
    echo "Provide MAC_RUNTIME_IMAGE (preferred), JAVA_HOME, or install a JDK at $DEFAULT_MAC_JAVA_HOME." >&2
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

resolve_javafx_jmods_dir() {
  local configured_jmods="${JAVAFX_JMODS_DIR:-}"
  local default_jmods="$ROOT/build/assets/javafx-jmods-macos"
  local legacy_jmods="$ROOT/build/assets/javafx-jmods-21.0.10"
  local checked=()

  if [[ -n "$configured_jmods" ]]; then
    checked+=("$configured_jmods")
    if [[ -d "$configured_jmods" ]]; then
      echo "$configured_jmods"
      return
    fi
  fi

  checked+=("$default_jmods")
  if [[ -d "$default_jmods" ]]; then
    echo "$default_jmods"
    return
  fi

  checked+=("$legacy_jmods")
  if [[ -d "$legacy_jmods" ]]; then
    echo "$legacy_jmods"
    return
  fi

  echo "JavaFX jmods directory not found." >&2
  echo "Checked candidates (in precedence order):" >&2
  printf '  - %s\n' "${checked[@]}" >&2
  echo "Set JAVAFX_JMODS_DIR or ensure one of the default paths exists." >&2
  exit 1
}

build_custom_runtime_image() {
  local base_jdk_runtime=$1
  local javafx_jmods_dir=$2
  local runtime_output="$ROOT/build/tmp/macos-runtime-image"
  local jlink_bin="$base_jdk_runtime/bin/jlink"
  local jdk_jmods_dir="$base_jdk_runtime/jmods"
  local module_path="$jdk_jmods_dir:$javafx_jmods_dir"
  local modules="javafx.controls,javafx.fxml,java.sql,java.naming,java.net.http,jdk.crypto.ec"

  if [[ ! -x "$jlink_bin" ]]; then
    echo "Expected jlink executable at $jlink_bin" >&2
    exit 1
  fi

  if [[ ! -d "$jdk_jmods_dir" ]]; then
    echo "Expected JDK jmods directory at $jdk_jmods_dir" >&2
    exit 1
  fi

  rm -rf "$runtime_output"
  mkdir -p "$(dirname "$runtime_output")"

  "$jlink_bin" \
    --module-path "$module_path" \
    --add-modules "$modules" \
    --output "$runtime_output"

  echo "$runtime_output"
}

verify_generated_runtime_image() {
  local runtime_image=$1
  local runtime_java="$runtime_image/bin/java"

  if [[ ! -x "$runtime_java" ]]; then
    echo "Generated runtime image is missing java binary: $runtime_java" >&2
    echo "Generated runtime has bin/java: no"
    exit 1
  fi

  echo "Generated runtime has bin/java: yes ($runtime_java)"
}

verify_generated_runtime_process_spawning() {
  local base_jdk_runtime=$1
  local runtime_image=$2
  local smoke_dir="$ROOT/build/tmp/macos-runtime-smoke"
  local smoke_src="$smoke_dir/SpawnSmokeTest.java"
  local javac_bin="$base_jdk_runtime/bin/javac"
  local runtime_java="$runtime_image/bin/java"

  if [[ ! -x "$javac_bin" ]]; then
    echo "Expected javac executable at $javac_bin for runtime spawn verification." >&2
    exit 1
  fi

  rm -rf "$smoke_dir"
  mkdir -p "$smoke_dir"

  cat > "$smoke_src" <<'EOF'
public final class SpawnSmokeTest {
  public static void main(String[] args) throws Exception {
    Process process = new ProcessBuilder("/usr/bin/true").start();
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new IllegalStateException("spawn check failed with exit code " + exitCode);
    }
  }
}
EOF

  "$javac_bin" -d "$smoke_dir" "$smoke_src"
  "$runtime_java" -cp "$smoke_dir" SpawnSmokeTest
  echo "Generated runtime process spawn check: passed"
}

DESKTOP_TARGET="$ROOT/shale-desktop/target"
DIST_DIR="$ROOT/dist-macos"
mkdir -p "$DIST_DIR"

rm -rf "$DIST_DIR/Shale" "$DIST_DIR/Shale.app"
rm -f "$DIST_DIR"/Shale*.dmg

BASE_JDK_RUNTIME=$(resolve_base_jdk_runtime)
JAVAFX_JMODS_PATH=$(resolve_javafx_jmods_dir)
RUNTIME_IMAGE=$(build_custom_runtime_image "$BASE_JDK_RUNTIME" "$JAVAFX_JMODS_PATH")

echo "Base JDK runtime used: $BASE_JDK_RUNTIME"
echo "JavaFX jmods path used: $JAVAFX_JMODS_PATH"
echo "Generated runtime image path: $RUNTIME_IMAGE"
verify_generated_runtime_image "$RUNTIME_IMAGE"
verify_generated_runtime_process_spawning "$BASE_JDK_RUNTIME" "$RUNTIME_IMAGE"

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
