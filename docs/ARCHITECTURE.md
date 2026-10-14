# Architecture

**WSCLauncher 1.x** is the Android application (`dev.wsc.launcher`). **Native Mod ABI v1** is the interface between the launcher and native modules, versioned independently. A future version of the application may continue using ABI v1. It is not a gameplay API for World Soccer Champs.

## Game Hosting

The main screen detects `com.monkeyibrow.worldsoccerchamps` installed by the user. `GuestRuntime` uses the context of that package to obtain classes, resources, base APK, splits, and libraries compatible with the process ABI. The original files remain intact and are not redistributed.

`GuestStubActivity` starts in its own task, in the process `dev.wsc.launcher:guest`. Java Pine hooks instantiate the original Activity and adapt the context/lifecycle. The original Application and its protection are initialized normally. Library resolution points to the installed files or `apk!/lib/...` entries.

`GuestContext` combines game code/resources with launcher storage. Some Google clients receive `SdkContext`, which identifies the launcher's real package and UID. This does not virtualize all Android services.

The ads SDK also creates clients in Dynamite modules from Google Play Services, outside the `BaseGmsClient` hierarchy loaded by the APK. For WSC 468, `SdkContextHooks` adapts the context before MobileAds initialization, the configuration manager, and the broker that creates local/remote clients. Activity and View parameters used by overlays retain their original objects. The internal points are specific to the SDK version and appear as `SDK ADS CONTEXT` in diagnostics; service security verification continues using the launcher's real identity.

When the installed manifest declares an active `FirebaseInitProvider`, `GuestProviders` executes its original `attachInfo`/`onCreate` on the main thread, before `Application.onCreate` and Cocos. This order corresponds to the normal initialization of providers by Android and avoids the dispute between Firebase initialization and instrumented requests initiated by SDKBOX. The provider uses the original classes, resources, and metadata with guest storage; it is not registered as a launcher provider. Other providers are not automatically started.

The guest has the same UID and permissions as the launcher. Its sandbox is separated from the original game's sandbox but shared with the launcher's main process. Private saves from the original game are not imported. Mods loaded in the guest have no individual sandbox.

## Native Mods

`ModStore` imports ZIPs, validates manifest format 2, CRC, limits, ELF/ABI and SHA256 of payloads, and installs libraries as read-only. Selection persists on disk, without preference caching between processes. Activation verifies game version and claim conflicts; startup also verifies ABI and the full hash of the original game library.

The bootstrap records in `mods-runtime.txt` the fingerprint of the effectively prepared selection. `GuestSession` compares this fingerprint with the current selection and restarts only the guest when needed at the next launcher startup.

After `Cocos2dxActivity.onLoadNativeLibraries`, `NativeModBridge` loads **`libwscmodhost.so`** and the selected `.so` modules. The host uses `dlopen(RTLD_NOW | RTLD_LOCAL)`, resolves the mandatory export **`wsc_mod_init`** and passes `WscHostV1` from [wsc_mod_api.h](../native/include/wsc_mod_api.h). Init must return zero; on failure, the mod must undo its hooks.

The ABI preserves `api_version`, `struct_size`, original game library base, logging, hook/unhook and the `show_choice` helper. The host provides ShadowHook in UNIQUE mode. The helper copies UTF-8 strings, shows an Android dialog, and delivers the response on the GL thread when available. Player Roles 0.4 uses its own Cocos UI and does not call this helper.

There is no **hot unload**: even a module whose initialization failed remains mapped until the guest exits, avoiding dangling pointers. Activating, deactivating, or removing mods requires restarting the guest. Hooks, offsets, signatures, structures and WSC-specific behavior belong to each mod's code, such as [Player Roles](../examples/player-roles/README.md).

## Diagnostics and Limits

`GameDataArchive` exports the private directory obtained from `Application.getDataDir()`, in two modes, with a file list and sizes collected before opening the destination. The exporter uses fixed buffers, calculates SHA256 during copying, and verifies size/mtime and links before/after reading. The tree is stored in `data/`, with format 2 metadata in `wsc-export.json`. The lightweight mode keeps complete slots, auxiliaries, configurations and tables, without traversing resource folders discarded by the filter.

`LauncherViewModel` maintains the task and percentage during rotation; results published on the main thread update the bar without reconstructing the page on each increment. The export type is also encoded in the selector's request code to survive Activity recreation. After the user chooses a destination, `GuestSession.stopForExport` ends only the `:guest` process with this installation's package and UID and waits for its exit. Test APKs with another applicationId export only their own sandbox.

The main process remains available after a guest crash. The diagnostic file includes lifecycle, installed/loaded selection, rotating logcat and termination records/traces available on Android. `onResume` proves only one bootstrap step; functional success requires confirming menu and match on the device.

The launcher supports Android 7+, ARM32 and ARM64 with universal APK and specific APKs. The process does not switch ABI; the UI detects incompatibility with the game installation before startup. Player Roles 0.4 has ARM64 and ARM32 profiles for WSC 2026.12 (468), compiled, statically verified and manually tested at runtime on both architectures. There is no guarantee of compatibility with different versions, Play services or all game protection mechanisms.