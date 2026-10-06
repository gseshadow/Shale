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

The cross-platform `release-all.bat` flow pushes its exact preflighted Git `HEAD` when needed, fetches the configured
remote again, verifies that its upstream ref contains that exact commit, and only then passes the SHA to a narrow
SSH bootstrap. The bootstrap fetches the configured Mac remote, verifies the full requested commit, extracts
`prepare-shale-mac-release.sh` from that commit into a temporary file, verifies the extracted Git blob, and executes
it with the repository root supplied explicitly. It never invokes the checkout's potentially stale release script.
Before workspace synchronization, the requested script fetches the remote, resolves the requested commit, extracts
`mac_release_workspace.py` into a separate temporary file, and verifies its Git blob against that commit. It runs
this verified helper to clean only allowlisted POMs and check out that exact commit before applying the requested
release version. A stale checkout may lack the helper entirely; no pre-sync helper is loaded from that checkout.
The same verified temporary helper handles exit cleanup and is removed on exit. Mac and Windows artifacts use the
same origin-available source revision. Direct legacy two-argument Mac preparation remains supported but emits a
source revision mismatch warning because it falls back to `origin/<branch>`.

The dedicated Mac checkout is reusable. Before restoring POMs or switching revisions, preparation inspects all tracked,
staged, and untracked changes. It restores only the root POM and the six module POMs whose versions are temporarily
rewritten by the release build; any other change fails closed and is reported without being discarded. After the
fetch, the requested commit must exist, is checked out detached, and is compared with `HEAD` before version injection.
An exit trap restores the same seven POMs after packaging on both success and ordinary build failure. The flow does
not use `git clean`, blanket stashing, or an unvalidated hard reset. Build artifacts remain in `dist-macos/` as before.

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
- Phase 12 passes optional `--attemptId` and `--attemptDir` arguments to the existing updater. Older invocations
  remain valid. The updater atomically records only bounded, non-secret outcome codes; the next Shale startup is
  the only confirmation of completion. These per-user files live under
  `~/Library/Application Support/Shale/update-attempts` and survive bundle replacement/reboot.

## Machine identity data

Phase 4A reserves `/Library/Application Support/Shale/machine-id` for the workstation-wide random UUID.
This is deliberately separate from both the replaceable `Shale.app` bundle and the per-user log directory
above. The current unsigned first-pass DMG does not install a privileged helper or provision that directory;
an administrator must pre-create the Shale directory with application-specific read/write permissions when
ordinary users cannot create it. A permission failure is reported by the desktop identity provider as
unavailable and never causes fallback to a per-user or ephemeral identity. Bundle replacement, ordinary
uninstall, and reinstall retain this external file.

Phase 13A stores the independent, non-secret automatic-update permission at
`/Library/Application Support/Shale/automatic-update-preference.properties`. It has the same external-directory
persistence and provisioning limitations as machine identity; the package adds no Keychain entry or privileged
helper. Missing, unreadable, or corrupt state fails safe. Installed macOS permissions and shared-user behavior
remain a platform verification requirement.
