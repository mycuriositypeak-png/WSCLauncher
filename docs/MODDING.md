# Native Mods

WSCLauncher 1.x accepts exclusively **executable native module ZIPs**, with **manifest format 2**, `type: native` and **Native Mod ABI v1** (`api: 1`). The old `plist-patch` format was removed and its manifests are rejected.

Native Mod ABI v1 is the minimal interface between the launcher and native modules. It is not a World Soccer Champs gameplay API. The ABI version is independent of the launcher version. The [header](../native/include/wsc_mod_api.h) continues with the `WscHostV1` layout used by the [Player Roles Picker 0.4](../examples/player-roles/README.md).

## Package and Manifest

The ZIP contains `mod.json` in the root and only the declared libraries, without directory entries. Example structure:

```text
mod.json
lib/arm64-v8a/libplayer_roles.so
lib/armeabi-v7a/libplayer_roles.so
```

Example manifest (replace the hashes with real SHA256, in lowercase hexadecimal of 64 characters):

```json
{
  "format": 2,
  "type": "native",
  "api": 1,
  "id": "wsc.example",
  "name": "Example",
  "version": "1.0",
  "description": "External native module",
  "game": {
    "package": "com.monkeyibrow.worldsoccerchamps",
    "versionCode": 468
  },
  "libraries": {
    "arm64-v8a": {
      "path": "lib/arm64-v8a/libexample.so",
      "sha256": "<SHA256 of the mod library>",
      "gameLibrarySha256": "<SHA256 of the original game library>"
    }
  },
  "claims": ["Example.integrationPoint"]
}
```

`id` accepts 2–64 lowercase letters, numbers, dot, hyphen and underscore, starting with a letter/number. Name has up to 80 characters, version up to 32 and description up to 400. Package and positive `versionCode` are required.

The manifest example above is single-ABI; add an `armeabi-v7a` entry in `libraries` for a dual-ABI package, with its own path and hashes. Existing mods do not need to provide both.

`libraries` declares one or both ABIs `arm64-v8a` / `armeabi-v7a`. Paths must follow `lib/<abi>/lib[a-z0-9_]+.so`. The library must be a little-endian shared ELF of the correct architecture. The payload SHA256 is verified on import and at startup; the full game library SHA256 is verified before loading. Changing only a hash does not make hooks/offsets compatible with another game. The launcher selects only the library of the process ABI; the absence of this ABI is incompatibility. The game hash is also independent per ABI.

A mod with internal bindings must analyze and validate each binary separately. ARM32 can use Thumb, other offsets, object layouts, vtables and calling conventions. Recompiling the ARM64 profile for ARM32 does not constitute a port. Player Roles 0.4 shares the logic but maintains separate profiles in `examples/player-roles/profiles/`. Native Mod ABI remains v1.

`claims` requires 1–32 unique integration point names, with up to 100 characters (`A–Z`, `a–z`, numbers, dot, underscore, hyphen). Two active mods cannot declare the same claim. Declare the hooks used by the mod; claims do not detect hooks omitted by the author.

Limits: ZIP 16 MiB, manifest UTF-8 64 KiB, each library 4 MiB and up to 128 installed mods. The importer verifies CRC, duplicate entries, missing libraries and undeclared files. Installing does not activate and does not overwrite existing IDs; remove the previous version to update. Do not include APKs, assets or proprietary game libraries.

## Initialization and ABI

Export with public visibility and C linkage:

```cpp
#include "wsc_mod_api.h"
extern "C" int wsc_mod_init(const WscHostV1* host) {
    if (!host || host->api_version != 1 || host->struct_size < sizeof(WscHostV1)) return 1;
    host->log(4, "Example initiated");
    return 0;
}
```

The game already had its library loaded when init is called, on the Android main thread. Do not assume an existing scene during init. The ABI provides the game library base, logging, hook/unhook and generic `show_choice` dialog with GL thread response (`-1` cancels). Preserve header field order/size; game-specific callbacks and functions must respect the thread and profile ABI. Return zero on success and undo installed hooks before returning an error.

There is no hot unloading nor dependencies between mods. Libraries remain loaded for the guest lifetime; changes require restart via the launcher. Use [scripts/build-native.py](../scripts/build-native.py) and the [Player Roles](../examples/player-roles/README.md) tests as reference. Each mod implements its own gameplay and UI bindings.

## Changing Texts at Runtime

Removing `plist-patch` does not prevent mods from changing texts. A native mod can intercept localization functions, replace strings returned by the game, intercept UI element creation or alter Cocos labels/nodes at runtime. This is implemented by the mod's code and validated for the game profile; there is no longer a generic declarative resource substitution format.