#!/bin/bash
HERE="$(cd "$(dirname "$0")" && pwd)"   # folder this script lives in
# Read-only: waits until the TV has been on 5 minutes, then measures free memory fairly.
A=~/platform-tools/adb; D=$HERE/scan
$A connect 192.168.1.10:5555 >/dev/null
up=$($A shell cut -d. -f1 /proc/uptime </dev/null | tr -d '\r')
if [ "$up" -lt 300 ]; then w=$((300-up)); echo "TV just started. Waiting $w seconds so start-up apps settle (leave the TV on the home screen)..."; sleep $w; fi
$A shell input keyevent KEYCODE_HOME </dev/null; sleep 20
$A shell cat /proc/meminfo </dev/null > "$D/meminfo_settled.txt"
$A shell ps -A -o RSS,NAME </dev/null > "$D/processes_settled.txt"
for p in com.sony.dtv.m2.seeds.iot com.sony.dtv.m2.seeds.iot.remotestart com.sony.dtv.ecodashboard com.sony.dtv.airplayapp; do
  echo "== $p"; $A shell dumpsys package $p </dev/null | grep -E "enabled=|User 0:|flags=\[|SYSTEM|PERSISTENT" | head -6
done > "$D/sony_check.txt"
$A shell pm list packages -d </dev/null > "$D/off_now.txt"
grep MemAvailable "$D/meminfo_settled.txt"
echo "Go back to Claude and say: measured"
