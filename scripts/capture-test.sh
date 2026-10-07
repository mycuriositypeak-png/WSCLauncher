#!/usr/bin/env bash
set -euo pipefail
# Run on a workstation with adb and a connected, authorized Android device matching the game/launcher ABI.
output_dir="${1:-wsc-test}"
mkdir -p "$output_dir"
adb shell am force-stop dev.wsc.launcher
adb logcat -c
adb shell am start -n dev.wsc.launcher/.LauncherActivity
printf '%s\n' 'Toque em Iniciar no launcher e reproduza o resultado; depois pressione Enter aqui.'
read -r
adb logcat -d -v threadtime > "$output_dir/logcat.txt"
adb shell run-as dev.wsc.launcher cat files/guest-diagnostic.log > "$output_dir/guest-diagnostic.log" || true
adb shell ps -A > "$output_dir/processes.txt"
adb shell dumpsys activity activities > "$output_dir/activities.txt"
printf 'Diagnóstico salvo em %s\n' "$output_dir"
