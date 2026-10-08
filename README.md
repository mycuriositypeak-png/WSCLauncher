<p align="center">
  <strong>English</strong> · <a href="README.pt-BR.md">Português (Brasil)</a> · <a href="README.es.md">Español</a>
</p>

<p align="center">
  <img src="artwork/wsc-launcher-icon.jpg" width="180" alt="WSCLauncher icon">
</p>

<h1 align="center">WSCLauncher</h1>

<p align="center">
  An Android launcher for running <strong>World Soccer Champs</strong> with native mods.
</p>

<p align="center">
  <strong>Version 1.0.2</strong> · Android 7+ · ARM64 / ARM32
</p>

## ✨ About

**WSCLauncher** runs your installed copy of World Soccer Champs in its own process and allows external native mods to be loaded.

This project **does not include the original game APK, assets, or native libraries**. You must have your own copy of the game installed.

> WSCLauncher is an unofficial community project and is not affiliated with, endorsed by, or sponsored by the developers or publishers of World Soccer Champs.

## 📦 Compatibility

- ✅ Android 7+ (API 24)
- ✅ ARM64 (`arm64-v8a`)
- ✅ ARM32 (`armeabi-v7a`)
- ✅ Universal APK with both architectures
- ✅ Dedicated ARM64 and ARM32 APKs
- ✅ Native Mod ABI v1

> On ARM64 devices with an ARM32 installation of WSC, use the ARM32 WSCLauncher APK. A 64-bit process cannot load 32-bit native libraries.

## 🧩 Mods

Mods are native ZIP packages and may support one or both architectures.

The project includes **[Player Roles Picker 0.4](examples/player-roles/README.md)** as a functional example, tested on **ARM64 and ARM32**.

It adds a player picker for:

- Captain
- Free kicks
- Penalties

Each mod is responsible for declaring which game versions and architectures it supports. The launcher validates compatibility before loading the module.

## 🚀 How to use

1. Install World Soccer Champs.
2. Install the WSCLauncher APK that matches the game's architecture.
3. Open **Mods → Add mod** and select the ZIP.
4. Enable the mod and launch the game from the **Home** tab.

Official builds will be distributed through **GitHub Releases**.

## 🎨 Interface

WSCLauncher has three main sections:

- 🏠 **Home** — game status, compatibility, and Play button
- 🧩 **Mods** — import, enable, disable, inspect, and remove mods
- ⚙️ **Settings** — language, theme, logs, diagnostics, and licenses

The interface uses Material 3, light/dark themes, and dynamic colors when available.

## 🛠️ Build

Main requirements:

- JDK 17+
- Android SDK 35
- Android NDK 27.2.12479018

```sh
python3 scripts/build-native.py --ndk "$ANDROID_NDK_HOME"
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

The build generates:

```text
WSCLauncher-<version>-universal.apk
WSCLauncher-<version>-arm64.apk
WSCLauncher-<version>-arm32.apk
```

## 📚 Documentation

- 📐 [Architecture](docs/ARCHITECTURE.md)
- 🧩 [Modding](docs/MODDING.md)
- 🩺 [Troubleshooting](docs/TROUBLESHOOTING.md)
- ⚽ [Player Roles Picker](examples/player-roles/README.md)

## ⚠️ Project status

WSCLauncher is experimental and distributed through sideloading. Game hosting depends on the version, architecture, and behavior of the installed WSC build.

Native mods run inside the launcher process. Only install mods from sources you trust.

## 📄 License

Distributed under **GPL-3.0**.

Third-party dependencies, attribution, and notices are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
