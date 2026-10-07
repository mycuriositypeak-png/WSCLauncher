# Third-party notices

## FusionCore

https://github.com/All-Of-Us-Mods/FusionCore

Architecture reference for in-process cross-package game hosting. The bundled custom Pine AAR is copied from `libs/canyie-pine.aar` at revision `e3aa9b4ffc3545d545810161f4401bf6cffc29ab`. FusionCore is distributed under GPL-3.0. This project includes that license in LICENSE and is distributed under GPL-3.0.

## Pine

https://github.com/canyie/pine

Android ART method-hooking library. The [upstream README at revision 216d910f18b18430a5d21c510affb221a9833a55](https://github.com/canyie/pine/blob/216d910f18b18430a5d21c510affb221a9833a55/README.md#license) identifies the Anti 996 License, Version 1.0, and credits canyie (Pine), Swift Gan (AndroidELF) and jmpews (Dobby). The upstream notice and referenced license text are preserved verbatim in [native/vendor/PINE-NOTICE](native/vendor/PINE-NOTICE).

The AAR contains its Java API and native binaries for ARM32/ARM64. The launcher produces separate ARM32 and ARM64 APKs, each containing its matching native binary. The custom binary originates from FusionCore revision `e3aa9b4ffc3545d545810161f4401bf6cffc29ab`; the upstream README revision identifies the notice source, not the custom binary's corresponding source. Exact corresponding source and applicable license for that custom binary have not been independently verified. Preserve this provenance limitation when redistributing; no relicensing of the custom binary is asserted.

## Geode

https://github.com/geode-sdk/geode

Reference only. No Geode code or Geometry Dash bindings are included.

## ShadowHook

https://github.com/bytedance/android-inline-hook

Version 1.0.10, Maven artifact com.bytedance.android:shadowhook. Native inline hook dependency for the generic module host. MIT license; full notice preserved in native/vendor/shadowhook.h. AAR included in app/libs. Player Roles bindings were independently mapped for World Soccer Champs.

## LLVM libc++ / compiler runtime

Native host and module use static libc++ from Android NDK 27.2.12479018. LLVM Apache-2.0 license with LLVM exceptions; NDK toolchain notices are preserved in native/vendor/LLVM-NOTICE.

## Material Components, AndroidX and Material Icons

The launcher UI uses Google Material Components for Android 1.12.0 and AndroidX AppCompat/Lifecycle. Material navigation/action vectors are from [Google Material Design Icons](https://github.com/google/material-design-icons), converted from the official SVG paths. These dependencies/icons are distributed under Apache-2.0; license texts are included in `app/src/main/assets/licenses/` and shown by the launcher.

The UI was independently implemented with Android Views using [NextBep/BepInEx.Android.Launcher](https://github.com/NextBep/BepInEx.Android.Launcher/tree/7943be19a80f959648f3fbf667f0d5e24e1330d5) as a visual reference. No UI source code, branded assets, runtime or loader from that project is included.
