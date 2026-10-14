# Player Roles Picker

Current version: **0.4** (manifest `0.4.0`). ID: `wsc.player-roles-picker`.

Functional native mod example for WSCLauncher, using **Native Mod ABI v1**. Player Roles arrows open a table in the game's own Cocos, with position, portrait, stars, rating and age. Shared logic maintains the original assignment and saving callback.

| Architecture | Static build and profile | Runtime of this version |
|--------------|--------------------------|-------------------------|
| ARM64 (`arm64-v8a`) | ✓ | ✓ Manually tested |
| ARM32 (`armeabi-v7a`) | ✓ | ✓ Manually tested |

Target profile for both ABIs: **World Soccer Champs 2026.12, versionCode 468**, package `com.monkeyibrow.worldsoccerchamps`. Version 0.4 was compiled, verified against the real libraries of both architectures and **manually tested at runtime on ARM64 and ARM32**.

Each architecture has its own [binary profile](profiles/README.md), offsets, layouts and signatures in `profiles/arm64.h` and `profiles/arm32.h`. Selection and rendering code remains shared. These bindings belong to the mod; they do not depend on gameplay APIs like `wsc.players` or `wsc.stats`. The launcher ABI remains v1, and this mod uses the Cocos UI directly, without calling `show_choice`.

Complete SHA256 hashes required for the original game library:

```text
ARM64: 6fd30552b2b5419b143b06d12c1aa397d6f24e118d59304ac3db8f8a041c98e4
ARM32: a219f831a937e59af2891be9f0d50b6b157bdda1d624220833af3b37db3ab531
```

Before hooks, the launcher checks the game hash for the selected ABI. The mod also verifies its 23 functions, layout evidences and seven vtable slots. A divergence prevents hook installation; a failure during installation undoes already installed hooks. Thumb RVAs preserve the mode bit for calls and ShadowHook, removing it only to read signatures.

## Installing the Ready Package

1. Install your full WSC copy and a WSCLauncher compatible with its architecture. On an ARM64 device with ARM32 game, explicitly use the launcher's ARM32 APK.
2. Download [wsc-player-roles-picker-v0.4.zip](wsc-player-roles-picker-v0.4.zip) and verify the SHA256 below.
3. Save the match. To replace the previous version, remove **only Player Roles** from the Mods tab; the importer does not overwrite existing IDs. Do not delete launcher data.
4. In **Mods → Add mod**, select the ZIP without extracting.
5. Enable your switch. The launcher automatically selects the library for the process ABI.
6. Start/restart the game **via the launcher**. Opening the original game icon does not load the mod.

The Captain, Fouls and five penalty takers arrows open the list. Choosing the current player or CANCEL preserves data. BACK first closes the selector; with it closed, preserves the original flow. Penalty takers cannot repeat players in other penalty positions. Use BACK and the game's normal saving to persist changes.

The ZIP contains **exactly**:

```text
mod.json
lib/arm64-v8a/libplayer_roles.so
lib/armeabi-v7a/libplayer_roles.so
```

It contains no APK, assets, saves nor proprietary World Soccer Champs libraries. No game binary is needed for compilation.

ZIP SHA256:

```text
4d9040a68c8884d71d515aeee88ab143bd24357206892eef43882d228955afb0
```

Payload SHA256:

```text
ARM64: fd3f1ebd22e0f5fad77ad6481e94245edb1b78c9cd77689939818ed8ca9cf885
ARM32: 4dac264d5974ca495504a91810527f6033549eaabf06991714a9f22b41934e49
```

## Compile and Test

In the root directory, on Linux x86_64 with Android NDK **27.2.12479018**:

```sh
python3 scripts/build-native.py --mod-only --ndk "$ANDROID_NDK_HOME"
```

The script uses C++17, static libc++, Android API 24 and targets `aarch64-linux-android24` / `armv7a-linux-androideabi24`. Compiles with `-Wall -Wextra -Werror`, applies `exports.map`, verifies that only `wsc_mod_init` is public, removes unnecessary symbols and generates the manifest with real hashes and a dual-ABI ZIP. Intermediates go to `build/<abi>/`; the final ZIP is in this folder. Without `--mod-only`, it also compiles the host for both ABIs. Update the hashes in this README after recompiling a different package.

The launcher's minimal interface remains in [wsc_mod_api.h](../../native/include/wsc_mod_api.h).

Host-side C++ tests, executed from the root:

```sh
mkdir -p examples/player-roles/build
g++ -std=c++17 -Wall -Wextra -Werror -I native/include \
  examples/player-roles/callback_test.cpp -o examples/player-roles/build/callback-test
examples/player-roles/build/callback-test
g++ -std=c++17 -Wall -Wextra -Werror \
  examples/player-roles/selection_policy_test.cpp -o examples/player-roles/build/policy-test
examples/player-roles/build/policy-test
g++ -std=c++17 -Wall -Wextra -Werror -I native/include \
  examples/player-roles/profile_test.cpp -o examples/player-roles/build/profile-test
examples/player-roles/build/profile-test
./gradlew :app:testDebugUnitTest
```

C++ tests cover the seven functions, eligibility, cancellation, current selection, revalidation, BACK, init protection and rollback. JVM tests import the real ZIP, verify ELF/ABI, payloads, claims and activation. Fixture tests cover per-process selection, independent hashes, modified files and single-ABI mods. They do not load the game nor execute Android hooks.

With authorized local copies of the original game libraries **outside the repository**:

```sh
python3 scripts/verify-player-roles-profile.py /path/to/arm64/libWorldSoccerChamps.so
python3 scripts/verify-player-roles-profile.py /path/to/arm32/libWorldSoccerChamps.so
```

The script detects ELF64/AArch64 or ELF32/ARM and verifies the corresponding profile, including vtable relocations. It does not extract nor package the game. Another version requires new analysis, not hash swapping.

## Limitations and Diagnosis

Only the two profiles above are supported. Version 0.4 was manually tested on ARM64 and ARM32 devices. Future WSC changes still require new validation of profiles, hooks, offsets and signatures before being considered compatible. Programmatic scene change while selection is open remains unsupported; guest termination releases the session.

On failure, export diagnostics and look for `NATIVE MOD` / `ROLE_PICKER`. See [TROUBLESHOOTING](../../docs/TROUBLESHOOTING.md) and [MODDING](../../docs/MODDING.md). This example follows the [project license](../../LICENSE), with notices in [THIRD_PARTY_NOTICES](../../THIRD_PARTY_NOTICES.md).