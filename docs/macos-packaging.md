# macOS packaging

This is the first-pass macOS packaging path for Shale. It is intended to produce a launchable `.app` image or `.dmg` on **macOS** without signing or notarization.

## Prerequisites

- macOS.
- JDK 21 with `jpackage` on your `PATH`.
- Maven.
- A macOS JavaFX jmods directory.
  - Set `JAVAFX_JMODS_DIR` to that directory, or
  - place the macOS jmods at `build/assets/javafx-jmods-macos`.

## Build commands

From the repository root:

```bash
export JAVAFX_JMODS_DIR=/absolute/path/to/javafx-jmods
./build/scripts/build-shale-macos.sh app-image
```

To build a DMG instead:

```bash
export JAVAFX_JMODS_DIR=/absolute/path/to/javafx-jmods
./build/scripts/build-shale-macos.sh dmg
```

## Output

Artifacts are written to `dist-macos/`:

- `dist-macos/Shale.app` when using `app-image`
- `dist-macos/Shale.dmg` when using `dmg`

## Runtime image details

`jpackage` runs `jlink` automatically when `--runtime-image` is not supplied. Its default `jlink` options include `--strip-native-commands`, which removes `Contents/runtime/Contents/Home/bin/java` from the packaged app runtime.

Shale now overrides the `jlink` options in `build/scripts/build-shale-macos.sh` so the runtime keeps native launchers while still stripping debug symbols, man pages, and header files. The build script also fails fast if the packaged app image is missing:

```text
Contents/runtime/Contents/Home/bin/java
```

## Launch test on macOS

After building an app image:

```bash
open dist-macos/Shale.app
```

Or launch the app binary directly:

```bash
dist-macos/Shale.app/Contents/MacOS/Shale
```

## Current updater behavior on macOS

- Shale uses `~/Library/Application Support/Shale` for writable startup and updater logs.
- Login and normal app startup do **not** require updater support on macOS.
- In-app updater launch remains temporarily bypassed on macOS while the desktop launcher stays Windows-only.
- The updater plumbing now expects a macOS **ZIP** payload that contains `Shale.app`, stages that bundle, replaces the installed app bundle, and relaunches it with `open` once the macOS launcher path is enabled.
- DMG is still for manual install/distribution only; it is not used as the updater payload.

## Machine identity data

Phase 4A reserves `/Library/Application Support/Shale/machine-id` for the workstation-wide random UUID.
This is deliberately separate from both the replaceable `Shale.app` bundle and the per-user log directory
above. The current unsigned first-pass DMG does not install a privileged helper or provision that directory;
an administrator must pre-create the Shale directory with application-specific read/write permissions when
ordinary users cannot create it. A permission failure is reported by the desktop identity provider as
unavailable and never causes fallback to a per-user or ephemeral identity. Bundle replacement, ordinary
uninstall, and reinstall retain this external file.
