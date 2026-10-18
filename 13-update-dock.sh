#!/bin/bash
HERE="$(cd "$(dirname "$0")" && pwd)"   # folder this script lives in
# Updates the dock home screen (same app, new look). Google TV's home stays on. Asks first.
A=~/platform-tools/adb; D=$HERE
$A connect 192.168.1.10:5555 >/dev/null
[ "$($A shell getprop sys.boot_completed </dev/null | tr -d '\r')" = "1" ] || { echo "TV not ready (try: ~/platform-tools/adb kill-server, then run again)."; exit 1; }
if $A shell pm list packages -d </dev/null | grep -q launcherx; then $A shell pm enable com.google.android.apps.tv.launcherx </dev/null; fi
read -r -p "Install the updated dock home screen? [y/n] " a
[ "$a" = y ] || [ "$a" = Y ] || { echo "Skipped."; exit 0; }
$A install -r "$D/Home.apk" </dev/null || exit 1
echo "$(date '+%Y-%m-%d %H:%M'),\"Home screen v4.0 (Lucide weather icons, dock auto-hide 5s with slide, tighter letter spacing, dock corners 12.87, dock: YouTube, Netflix, Prime, Hotstar, Apple TV, Spotify; spacing +5%, corners +10%)\",com.anshu.home,DONE,~/platform-tools/adb uninstall com.anshu.home" >> "$D/changes-log.csv"
sleep 2
$A shell am start -n com.anshu.home/.HomeActivity </dev/null >/dev/null; sleep 4
$A exec-out screencap -p > "$D/scan/home_v3.png"
read -r -p "Change sleep after inactivity from 5 to 10 minutes? [y/n] " b
if [ "$b" = y ] || [ "$b" = Y ]; then
  $A shell settings put system screen_off_timeout 600000 </dev/null
  echo "$(date '+%Y-%m-%d %H:%M'),\"Sleep after 10 minutes (was 5)\",settings,DONE,~/platform-tools/adb shell settings put system screen_off_timeout 300000" >> "$D/changes-log.csv"
  echo "Sleep is now 10 minutes: $($A shell settings get system screen_off_timeout </dev/null | tr -d '\r') ms"
fi
echo "Done. Press Home on the remote to check the Home button still opens it. Then tell Claude: dock updated"
