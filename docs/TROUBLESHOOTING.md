# Troubleshooting

## Game Not Detected

Install World Soccer Champs (`com.monkeyibrow.worldsoccerchamps`) with all splits/asset packs and confirm it opens normally. The launcher reads the user's installation; it does not provide the game. The installation must be accessible in the same Android profile.

## Incompatible ABI

The launcher and game libraries must have the same ABI. Install `WSCLauncher-<version>-arm64.apk` for ARM64 or `WSCLauncher-<version>-arm32.apk` for ARM32; device ARM64 support does not prove the installation's ABI. Check `GAME ARCHITECTURES`, `PROCESS ABI` and `NATIVE DIRECTORY` in diagnostics. Player Roles 0.4 includes both ABIs; other mods may provide only one. The universal APK contains both launcher libraries but normally runs on ARM64 on an ARM64 device. For WSC installed only on ARM32, use the ARM32 launcher APK. The Home shows the incompatibility and keeps Play disabled.

## Version or Hash Incompatible

A mod declares exactly the game's `versionCode` and the SHA256 of its original library per ABI. Player Roles supports WSC 2026.12 (468) and the hash indicated in its [README](../examples/player-roles/README.md). Disable incompatible mods or obtain an update made for your profile. Do not swap hashes/offsets just to bypass validation; the mod also verifies signatures before installing hooks.

Incorrect payload hash indicates a corrupted package or altered installed library. Check the SHA256 of the distributed ZIP and import again a reliable copy, first removing the already installed ID.

## Old Format or Mod Already Installed

Only `native` / format 2 / Native Mod ABI v1 is accepted. Remove declarative old mods using the previous launcher version **before updating**: persisted old manifests are also rejected by the 1.x line and may prevent listing. Do not clear app data as a first attempt, as this also deletes the launcher sandbox saves.

To update Player Roles, save the game, remove only the installed mod and import its ZIP 0.4. The importer does not overwrite an existing ID. Source code and ZIP are in [examples/player-roles](../examples/player-roles/README.md).

## Claim Conflict

Two mods cannot occupy the same declared integration point. Disable the conflicting mod before activating another. Rejected activation preserves the previous selection; see the names involved in the message.

## Changes Not Appearing

Save the match before returning to the launcher. In the **Mods** tab, enable/disable the mod; then, in the **Home** tab, tap **Play** or **Restart with mods**. Selection only changes on a new guest, without hot unload. The launcher restarts the guest when the selection fingerprint changes. If a failure left the same guest open without selection change, force stop the launcher and open again. Opening the game via the original icon does not load launcher mods.

## Permissions and Services

If the game gets stuck on opening in a release build, check if diagnostics record `PROVIDERS: Firebase completed` before `APPLICATION: attach completed` and Cocos. The launcher initializes the original `FirebaseInitProvider` before these steps. Initializing Firebase only after an instrumented SDKBOX HTTP request can block `FirebaseApp` and the `SessionManager` initializer, before any mod UI hook. The line `bootstrap=context-before-create-v2+firebase-provider-v1` identifies the corrected flow. If the problem continues, export a new diagnosis; do not delete data or saves to test.

If the game closes seconds after opening when the network allows ads, look for `FATAL EXCEPTION: GassClient`, `GassDGClient` or `LooperProvider` and `SecurityException: Unknown calling package name 'com.monkeyibrow.worldsoccerchamps'`. In the reported case, the game worked with private DNS blocking ads and closed about 20 seconds after opening with that DNS disabled. This exception indicates that the ads SDK presented the game's package to an Android service using the launcher's UID. The APK's `BaseGmsClient` hook does not cover clients loaded in another module by Google Play Services. The launcher adapts the context during MobileAds initialization and client creators, before sending this context to the local or remote module. The user confirmed that the test build stopped showing the crash when opening the game with DNS disabled. The `SDK ADS CONTEXT` lines register the installed points; internal names are specific to the WSC 468 ads SDK and missing classes are recorded. This adjustment does not treat all network or ad failures.

To validate a fix for this crash, compare the same device and installation with private DNS active and inactive, first with mods disabled and then with the usual selection. Wait beyond the time when the failure occurred, open a match and export the diagnosis of any new closure. Also check ad functioning when the network allows. The absence of `Unknown calling package` in JVM tests does not substitute real execution of the Google module on the device.

The guest uses the launcher's permissions and UID. The manifest retains `VIBRATE`, necessary to avoid `SecurityException` on game vibration; it is a normal permission, without runtime request. Observed Google clients receive launcher context, but services/providers/jobs are not fully virtualized. If `Unknown calling package` appears, scheduling failure or another permission denied, export the diagnosis; do not assume that original installation permissions apply to the guest.

## Exporting Game Data

In **Settings → Game Data → Export Game Data**, choose **Complete** or **Light**, tap **Choose Destination** and save the ZIP via the Android file picker. Export is done in the background, with bar and percentage, and continues during screen rotation. The initial file listing step uses an indeterminate bar; the percentage accompanies processed bytes and entries. 100% is only emitted after closing the ZIP and its destination.

Save the match before exporting. After the destination is chosen, the launcher ends only its own guest process and waits for its exit before reading the files. This avoids copying saves while the game writes to them. A failure or file alteration during copying is presented as an error; export does not modify saves.

- **Complete:** includes all regular files and accessible folders within this installation's private directory, normally `/data/user/0/dev.wsc.launcher/`, including downloaded resources, mods, logs and caches. Symbolic links and special files are omitted, without following external destinations.
- **Light:** preserves all files in `files/savegame_slot_*` slots, inclusive `data.bin.old` and the `datapack.db` of each slot; configurations and data files in the `files` root (`.db` and its auxiliaries, `.bin`, `.csv` and other config formats); `shared_prefs`, `databases`, `monkey_ranks` data and DataStore outside Firebase paths. Omits logos, adboards, resource ZIP packages, mods, logs, caches and crash reports. The filter was verified against the WSC 468 example; a future version that changes save organization may require updating. For a full tree copy, choose Complete.

The ZIP contains the original tree inside **`data/`** and a **`wsc-export.json`** in the root, format 2, with origin package, mode, quantity, relative paths, sizes and SHA256 of files. The prefix avoids conflict with an existing metadata file in the exported folder. There is no automatic import/restoration in this option.

Each installation exports its own sandbox. A test build with another applicationId exports that build's data, and not the private data of the original launcher or the original game application. The launcher accesses its own folder without root and uses the Android file picker to write the ZIP, without requesting general storage access.

## Exporting Diagnostic

After reproducing, reopen the launcher and access **Settings → Diagnosis → Export Diagnosis**. The file contains metadata, bootstrap/lifecycle log, guest logcat with rotation, installed/loaded mod selection and termination records/traces when available (Android 11+ for `ApplicationExitInfo`). Look for `NATIVE MOD`, `WSCMod` and `ROLE_PICKER` for the example. Logs may contain session paths and data; review before sharing.

Built-in capture is limited to the application's permitted access. To also capture messages available via ADB, connect an authorized device to a computer and run from the root:

```sh
scripts/capture-test.sh /tmp/wsc-test
```

The script forces launcher stop, opens its screen and waits for you to reproduce the problem and press Enter. Save before executing. Requires ADB in PATH. `onResume` in logs does not prove correct rendering; visually confirm the menu, a match and mod behavior.