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
- In-app updater launch remains Windows-only for now, so macOS update prompts are bypassed until macOS updater/install replacement work is added.
