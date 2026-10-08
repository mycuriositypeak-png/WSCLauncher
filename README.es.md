<p align="center">
  <a href="README.md">English</a> · <a href="README.pt-BR.md">Português (Brasil)</a> · <strong>Español</strong>
</p>

<p align="center">
  <img src="artwork/wsc-launcher-icon.jpg" width="180" alt="Icono de WSCLauncher">
</p>

<h1 align="center">WSCLauncher</h1>

<p align="center">
  Un launcher para Android que permite ejecutar <strong>World Soccer Champs</strong> con mods nativos.
</p>

<p align="center">
  <strong>Versión 1.0.1</strong> · Android 7+ · ARM64 / ARM32
</p>

## ✨ Acerca de

**WSCLauncher** ejecuta tu copia instalada de World Soccer Champs en su propio proceso y permite cargar mods nativos externos.

El proyecto **no incluye el APK, los assets ni las bibliotecas nativas originales del juego**. Debes tener tu propia copia instalada.

## 📦 Compatibilidad

- ✅ Android 7+ (API 24)
- ✅ ARM64 (`arm64-v8a`)
- ✅ ARM32 (`armeabi-v7a`)
- ✅ APK Universal con ambas arquitecturas
- ✅ APKs específicos para ARM64 y ARM32
- ✅ Native Mod ABI v1

> En dispositivos ARM64 con una instalación ARM32 de WSC, usa el APK ARM32 de WSCLauncher. Un proceso de 64 bits no puede cargar bibliotecas nativas de 32 bits.

## 🧩 Mods

Los mods son paquetes ZIP nativos y pueden ofrecer soporte para una o ambas arquitecturas.

El proyecto incluye **[Player Roles Picker 0.4](examples/player-roles/README.md)** como ejemplo funcional, probado en **ARM64 y ARM32**.

Añade un selector de jugadores para:

- Capitán
- Tiros libres
- Penaltis

Cada mod es responsable de declarar qué versiones y arquitecturas del juego soporta. El launcher valida la compatibilidad antes de cargar el módulo.

## 🚀 Cómo usarlo

1. Instala World Soccer Champs.
2. Instala el APK de WSCLauncher compatible con la arquitectura del juego.
3. Abre **Mods → Añadir mod** y selecciona el ZIP.
4. Activa el mod e inicia el juego desde la pestaña **Inicio**.

Las builds oficiales se distribuirán mediante **GitHub Releases**.

## 🎨 Interfaz

WSCLauncher tiene tres secciones principales:

- 🏠 **Inicio** — estado del juego, compatibilidad y botón Jugar
- 🧩 **Mods** — importar, activar, desactivar, ver detalles y eliminar mods
- ⚙️ **Configuración** — idioma, tema, logs, diagnóstico y licencias

La interfaz usa Material 3, temas claro/oscuro y colores dinámicos cuando están disponibles.

## 🛠️ Compilar

Requisitos principales:

- JDK 17+
- Android SDK 35
- Android NDK 27.2.12479018

```sh
python3 scripts/build-native.py --ndk "$ANDROID_NDK_HOME"
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

La compilación genera:

```text
WSCLauncher-1.0-universal.apk
WSCLauncher-1.0-arm64.apk
WSCLauncher-1.0-arm32.apk
```

## 📚 Documentación

- 📐 [Arquitectura](docs/ARCHITECTURE.md)
- 🧩 [Modding](docs/MODDING.md)
- 🩺 [Solución de problemas](docs/TROUBLESHOOTING.md)
- ⚽ [Player Roles Picker](examples/player-roles/README.md)

## ⚠️ Estado del proyecto

WSCLauncher es experimental y se distribuye mediante sideload. La ejecución del juego depende de la versión, arquitectura y comportamiento de la instalación de WSC.

Los mods nativos se ejecutan dentro del proceso del launcher. Instala únicamente mods de fuentes en las que confíes.

## 📄 Licencia

Distribuido bajo **GPL-3.0**.

Las dependencias, atribuciones y avisos de terceros se encuentran en [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
