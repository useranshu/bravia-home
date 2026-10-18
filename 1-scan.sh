#!/bin/bash
HERE="$(cd "$(dirname "$0")" && pwd)"   # folder this script lives in
# Read-only scan of the TV. Changes nothing.
A=~/platform-tools/adb
D=$HERE/scan
mkdir -p "$D"
$A connect 192.168.1.10:5555 >/dev/null
$A get-state >/dev/null 2>&1 || { echo "TV not connected. Turn it on and try again."; exit 1; }
echo "Scanning TV (read-only)..."
{ for p in ro.product.manufacturer ro.product.model ro.build.version.release ro.build.version.sdk ro.build.fingerprint; do echo "$p=$($A shell getprop $p)"; done; $A shell wm size; $A shell wm density; } > "$D/device.txt"
$A shell pm list packages -s  | sed 's/package://' | sort > "$D/system.txt"
$A shell pm list packages -3  | sed 's/package://' | sort > "$D/installed.txt"
$A shell pm list packages -d  | sed 's/package://' | sort > "$D/already_off.txt"
$A shell pm list packages -f  | sort > "$D/paths.txt"
$A shell cat /proc/meminfo > "$D/meminfo_before.txt"
$A shell dumpsys meminfo > "$D/dumpsys_meminfo_before.txt"
$A shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN > "$D/home.txt"
$A shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME > "$D/home_all.txt"
$A shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER > "$D/tv_apps.txt"
$A shell dumpsys tv_input > "$D/tv_input.txt"
$A shell dumpsys input_method | grep -iE "mCurMethodId|mEnabledInputMethods|InputMethodInfo|packageName" > "$D/keyboard.txt"
$A shell dumpsys usagestats > "$D/usage.txt" 2>/dev/null
$A shell dumpsys activity services | grep -E "ServiceRecord\{" | sed -E 's/.* ([a-zA-Z0-9_.]+)\/.*/\1/' | sort | uniq -c | sort -rn > "$D/running_services.txt"
$A shell ps -A -o RSS,NAME > "$D/processes.txt"
{ for s in "global window_animation_scale" "global transition_animation_scale" "global animator_duration_scale" "system screen_off_timeout" "secure screensaver_enabled" "secure screensaver_activate_on_sleep" "secure screensaver_components" "secure sleep_timeout" "secure attentive_timeout"; do echo "$s=$($A shell settings get $s)"; done; } > "$D/settings_before.txt"
$A exec-out screencap -p > "$D/home_before.png"
echo "Done. $(wc -l < "$D/system.txt") built-in apps, $(wc -l < "$D/installed.txt") apps you installed."
echo "Go back to Claude and say: scan done"
