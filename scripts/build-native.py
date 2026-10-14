#!/usr/bin/env python3
"""Build the generic host (two ABIs) and the dual-ABI external Player Roles mod."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import tempfile
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--ndk', type=Path, default=os.environ.get('ANDROID_NDK_HOME'))
parser.add_argument("--mod-only", action="store_true", help="Keep existing generic host libraries unchanged.")
args = parser.parse_args()
if not args.ndk:
    parser.error('Set ANDROID_NDK_HOME or pass --ndk (tested: NDK 27.2.12479018).')
bin_dir = args.ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin'
aar = root / 'app/libs/shadowhook-1.0.10.aar'
flags = ['-std=c++17', '-O2', '-fPIC', '-fvisibility=hidden', '-shared', '-static-libstdc++',
         '-Wl,--exclude-libs,ALL', '-Wl,-z,max-page-size=16384', '-Wl,-z,common-page-size=16384',
         '-Wl,--no-undefined', '-Wall', '-Wextra', '-Werror']
build = root / 'examples/player-roles/build'
build.mkdir(parents=True, exist_ok=True)
targets = [('arm64-v8a', 'aarch64-linux-android24', 'arm64'),
           ('armeabi-v7a', 'armv7a-linux-androideabi24', 'arm32')]
libraries = {}
payloads = []

# Prefab headers and lib dirs from the LSPlant, Dobby and shadowhook AARs.
# We use the "standalone" build of LSPlant which statically links libc++ to
# avoid the runtime dependency on libc++_shared.so (the launcher itself uses
# -static-libstdc++, so libc++_shared.so is not packaged).
lsplant_aar = root / 'app/libs/lsplant-standalone-6.4.aar'
dobby_aar = root / 'app/libs/dobby-1.2.aar'
shadowhook_aar = root / 'app/libs/shadowhook-1.0.10.aar'

with tempfile.TemporaryDirectory(prefix='wsc-native-') as temporary:
    for abi, target, _ in ([] if args.mod_only else targets):
        libdir = Path(temporary) / abi
        libdir.mkdir()
        with ZipFile(shadowhook_aar) as archive:
            (libdir / 'libshadowhook.so').write_bytes(archive.read('jni/' + abi + '/libshadowhook.so'))
        with ZipFile(lsplant_aar) as archive:
            (libdir / 'liblsplant.so').write_bytes(archive.read(f'prefab/modules/lsplant/libs/android.{abi}/liblsplant.so'))
        with ZipFile(dobby_aar) as archive:
            # Dobby is shipped as a static archive (libdobby.a). LSPlant links it
            # in at our hooker bridge compilation time so the resulting .so is
            # self-contained.
            (libdir / 'libdobby.a').write_bytes(archive.read(f'prefab/modules/dobby/libs/android.{abi}/libdobby.a'))
        output = root / 'app/src/main/jniLibs' / abi / 'libwscmodhost.so'
        output.parent.mkdir(parents=True, exist_ok=True)
        # Compose include path: shadowhook, lsplant (prefab), dobby (prefab).
        lsplant_include = Path(temporary) / f'lsplant-include-{abi}'
        lsplant_include.mkdir()
        with ZipFile(lsplant_aar) as archive:
            for entry in ['prefab/modules/lsplant/include/lsplant.hpp',
                           'prefab/modules/lsplant/include/utils/hook_helper.hpp',
                           'prefab/modules/lsplant/include/utils/jni_helper.hpp']:
                target_path = lsplant_include / entry.split('/')[-1]
                target_path.write_bytes(archive.read(entry))
            utils_dir = lsplant_include / 'utils'
            utils_dir.mkdir(exist_ok=True)
            (utils_dir / 'hook_helper.hpp').write_bytes(archive.read('prefab/modules/lsplant/include/utils/hook_helper.hpp'))
            (utils_dir / 'jni_helper.hpp').write_bytes(archive.read('prefab/modules/lsplant/include/utils/jni_helper.hpp'))
        dobby_include = Path(temporary) / f'dobby-include-{abi}'
        dobby_include.mkdir()
        with ZipFile(dobby_aar) as archive:
            (dobby_include / 'dobby.h').write_bytes(archive.read('prefab/modules/dobby/include/dobby.h'))
            (dobby_include / 'dobby_symbol_resolver.h').write_bytes(archive.read('prefab/modules/dobby/include/dobby_symbol_resolver.h'))
            (dobby_include / 'bionic_linker_restriction.h').write_bytes(archive.read('prefab/modules/dobby/include/bionic_linker_restriction.h'))
        shadowhook_include = root / 'native/vendor'
        subprocess.run([str(bin_dir / (target + '-clang++')), *flags,
                        '-I' + str(root / 'native/include'),
                        '-I' + str(shadowhook_include),
                        '-I' + str(lsplant_include),
                        '-I' + str(lsplant_include / 'utils'),
                        '-I' + str(dobby_include),
                        str(root / 'native/mod_host.cpp'),
                        str(root / 'native/lsplant_bridge/lsplant_bridge.cpp'),
                        '-L' + str(libdir),
                        '-Wl,--whole-archive', '-llsplant', '-Wl,--no-whole-archive',
                        '-lshadowhook', '-ldobby', '-llog', '-ldl',
                        '-o', str(output)], check=True)
        print(output)
        # Also copy the prebuilt LSPlant and shadowhook .so into jniLibs so
        # they ship inside the APK alongside libwscmodhost.so.
        jni_libs = root / 'app/src/main/jniLibs' / abi
        jni_libs.mkdir(parents=True, exist_ok=True)
        (jni_libs / 'liblsplant.so').write_bytes((libdir / 'liblsplant.so').read_bytes())
        (jni_libs / 'libshadowhook.so').write_bytes((libdir / 'libshadowhook.so').read_bytes())
        # Also extract libpine.so from canyie-pine.aar so Pine can be used on Android 14+.
        pine_aar = root / 'app/libs/canyie-pine.aar'
        with ZipFile(pine_aar) as archive:
            (jni_libs / 'libpine.so').write_bytes(archive.read('jni/' + abi + '/libpine.so'))
        print(jni_libs / 'liblsplant.so')
        print(jni_libs / 'libshadowhook.so')
        print(jni_libs / 'libpine.so')
    for abi, target, profile in targets:
        module = build / abi / 'libplayer_roles.so'
        module.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run([str(bin_dir / (target + '-clang++')), *flags,
                        '-Wl,--version-script=' + str(root / 'examples/player-roles/exports.map'),
                        '-I' + str(root / 'native/include'), str(root / 'examples/player-roles/player_roles.cpp'),
                        str(root / 'examples/player-roles/native_picker.cpp'), '-o', str(module)], check=True)
        subprocess.run([str(bin_dir / 'llvm-strip'), '--strip-unneeded', str(module)], check=True)
        exports = subprocess.check_output([str(bin_dir / 'llvm-nm'), '--dynamic', '--defined-only',
                                           '--extern-only', str(module)], text=True)
        if [line.split()[-1] for line in exports.splitlines() if line.strip()] != ['wsc_mod_init']:
            raise SystemExit(f'Unexpected public symbols in {abi}: {exports}')
        source = (root / 'examples/player-roles/profiles' / (profile + '.h')).read_text()
        game_hash = re.search(r'gameSha256 = "([0-9a-f]{64})"', source).group(1)
        path = 'lib/' + abi + '/libplayer_roles.so'
        binary = module.read_bytes()
        libraries[abi] = {'path': path, 'sha256': hashlib.sha256(binary).hexdigest(),
                          'gameLibrarySha256': game_hash}
        payloads.append((path, binary))
        print(module)
manifest = {
    'format': 2, 'type': 'native', 'api': 1, 'id': 'wsc.player-roles-picker',
    'name': 'Player Roles — seleção de jogador', 'version': '0.4.0',
    'description': 'Abre a tabela de seleção do próprio jogo (cocos), com posição, retrato, avaliação e idade. Perfis ARM64 e ARM32 para WSC 2026.12 (468).',
    'game': {'package': 'com.monkeyibrow.worldsoccerchamps', 'versionCode': 468},
    'libraries': libraries,
    'claims': ['PlayerRolesScene.roleButton', 'PlayerRolesScene.nextPlayer', 'PlayerRolesScene.nextPenaltyPlayer', 'PlayerRolesScene.back']
}
document = (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode()
(build / 'mod.json').write_bytes(document)
output = root / 'examples/player-roles/wsc-player-roles-picker-v0.4.zip'
with ZipFile(output, 'w') as archive:
    for path, content in [('mod.json', document), *payloads]:
        info = ZipInfo(path, date_time=(2026, 10, 6, 0, 0, 0)); info.compress_type = ZIP_DEFLATED
        archive.writestr(info, content)
print(output)
