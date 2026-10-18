#!/bin/bash
HERE="$(cd "$(dirname "$0")" && pwd)"   # folder this script lives in
# Restarts the TV, waits for it, then checks every turned-off app stayed off.
A=~/platform-tools/adb
D=$HERE
$A connect 192.168.1.10:5555 >/dev/null
echo "Restarting the TV..."
$A reboot
sleep 20
echo "Waiting for the TV to come back (up to 3 minutes)..."
for i in $(seq 1 36); do
  $A connect 192.168.1.10:5555 >/dev/null 2>&1
  [ "$($A shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  sleep 5
done
[ "$($A shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] || { echo "TV didn't reconnect. Make sure it's on (not standby), then run this again."; exit 1; }
echo "TV is back. Letting it settle for 60 seconds..."
sleep 60
echo; echo "Checking apps:"
ok=0; bad=0
while IFS=, read -r when name pkg st undo; do
  [ "$pkg" = "package" ] && continue; [ "$st" = "OFF" ] || continue
  if $A shell pm list packages -d "$pkg" </dev/null | grep -qx "package:$pkg"; then ok=$((ok+1)); else bad=$((bad+1)); echo "  BACK ON: $name ($pkg)"; fi
done < "$D/changes-log.csv"
echo "  Still off: $ok   Came back on: $bad"
$A shell cat /proc/meminfo > "$D/scan/meminfo_after_reboot.txt"
$A shell ps -A -o RSS,NAME > "$D/scan/processes_after_reboot.txt"
echo "$ok,$bad" > "$D/scan/verify_result.txt"
echo; echo "Go back to Claude and say: restarted"
