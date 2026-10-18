#!/bin/bash
HERE="$(cd "$(dirname "$0")" && pwd)"   # folder this script lives in
# Checks (no restart) that every turned-off app is still off, and records free memory.
A=~/platform-tools/adb
D=$HERE
$A connect 192.168.1.10:5555 >/dev/null
[ "$($A shell getprop sys.boot_completed </dev/null 2>/dev/null | tr -d '\r')" = "1" ] || { echo "TV not ready yet. Wait until it shows the home screen, then run this again."; exit 1; }
OFFNOW=$($A shell pm list packages -d </dev/null | tr -d '\r')
ok=0; bad=0
echo "Checking apps:"
while IFS=, read -r when name pkg st undo; do
  [ "$pkg" = "package" ] && continue; [ "$st" = "OFF" ] || continue
  if echo "$OFFNOW" | grep -qx "package:$pkg"; then ok=$((ok+1)); echo "  still off  $name"; else bad=$((bad+1)); echo "  BACK ON    $name ($pkg)"; fi
done < "$D/changes-log.csv"
echo; echo "  Still off: $ok   Came back on: $bad"
$A shell cat /proc/meminfo </dev/null > "$D/scan/meminfo_after_reboot.txt"
$A shell ps -A -o RSS,NAME </dev/null > "$D/scan/processes_after_reboot.txt"
$A shell uptime </dev/null > "$D/scan/uptime_after.txt"
echo "$ok,$bad" > "$D/scan/verify_result.txt"
echo; echo "Go back to Claude and say: checked"
