#!/bin/bash
HERE="$(cd "$(dirname "$0")" && pwd)"   # folder this script lives in
# Installs the dock-style home screen SAFELY: Google TV's home screen stays ON as a safety net.
A=~/platform-tools/adb; D=$HERE; L="$D/changes-log.csv"; U="$D/undo-all.sh"
SVC=com.anshu.home/com.anshu.home.HomeKeyService
$A connect 192.168.1.10:5555 >/dev/null
[ "$($A shell getprop sys.boot_completed </dev/null | tr -d '\r')" = "1" ] || { echo "TV not ready. Wait for its home screen, then run again."; exit 1; }
sh() { $A shell "$@" </dev/null | tr -d '\r'; }
ask() { read -r -p "$1 [y/n] " a; [ "$a" = y ] || [ "$a" = Y ]; }
log() { echo "$(date '+%Y-%m-%d %H:%M'),\"$1\",$2,DONE,$3" >> "$L"; echo "${3/\~\/platform-tools\/adb/\$A}   # undo: $1" >> "$U"; }

# Safety check: Google TV home must be ON. This script never turns it off.
if sh pm list packages -d | grep -q "com.google.android.apps.tv.launcherx"; then
  echo "Google TV's home screen is off - turning it back on first for safety."; sh pm enable com.google.android.apps.tv.launcherx
fi
sh cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity >/dev/null 2>&1

echo; echo "1/2  Install the new dock home screen (replaces the earlier version of the app)"
if ask "Install it?"; then
  $A install -r "$D/Home.apk" </dev/null || exit 1
  log "Installed dock home screen v2" com.anshu.home "~/platform-tools/adb uninstall com.anshu.home"
  sh am start -n com.anshu.home/.HomeActivity >/dev/null; sleep 4
  $A exec-out screencap -p > "$D/scan/dock_preview.png"
  echo "   It's open on the TV now. Have a look (Back/Home still work as normal)."
fi

echo; echo "2/2  Make the remote's Home button open it (Home twice quickly = Google TV home)"
echo "     Google TV's home stays on, so if this ever fails the TV just behaves normally."
if ask "Turn on the Home button helper?"; then
  cur=$(sh settings get secure enabled_accessibility_services)
  if [ -z "$cur" ] || [ "$cur" = "null" ]; then new=$SVC; elif echo "$cur" | grep -q "$SVC"; then new=$cur; else new="$cur:$SVC"; fi
  sh settings put secure enabled_accessibility_services "$new"
  sh settings put secure accessibility_enabled 1
  restore=$([ -z "$cur" ] || [ "$cur" = "null" ] && echo "settings delete secure enabled_accessibility_services" || echo "settings put secure enabled_accessibility_services $cur")
  log "Home button opens dock home screen" accessibility "~/platform-tools/adb shell $restore"
  sleep 4
  echo "   Done. Press Home on the remote to try it."
fi
sh dumpsys accessibility | grep -iE "anshu|Bound services" | head -5 > "$D/scan/dock_service.txt"
echo; echo "Go back to Claude and say: dock done"
