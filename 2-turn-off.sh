#!/bin/bash
HERE="$(cd "$(dirname "$0")" && pwd)"   # folder this script lives in
# Turns OFF (disables) apps one by one, asking before each. Nothing is deleted.
A=~/platform-tools/adb
L=$HERE/changes-log.csv
U=$HERE/undo-all.sh
$A connect 192.168.1.10:5555 >/dev/null
$A get-state >/dev/null 2>&1 || { echo "TV not connected. Turn it on and try again."; exit 1; }
[ -f "$L" ] || echo "when,app,package,result,undo command" > "$L"
[ -f "$U" ] || { echo '#!/bin/bash' > "$U"; echo 'A=~/platform-tools/adb; $A connect 192.168.1.10:5555' >> "$U"; }
APPS=(
"Sony Promos (ads)|com.sony.dtv.promos"
"Sony Select (app recommendations)|com.sony.dtv.sonyselect"
"BRAVIA CORE movie store|com.sonypicturescore"
"Store demo mode|com.sony.dtv.multiscreendemo"
"Sony smart-home connect|com.sony.dtv.m2.seeds.iot"
"Sony smart-home remote start|com.sony.dtv.m2.seeds.iot.remotestart"
"Eco Dashboard|com.sony.dtv.ecodashboard"
"Smart Help|com.sony.dtv.smarthelp"
"Help Guide|com.sony.dtv.helpguidecontents"
"Google Play Games|com.google.android.play.games"
"YouTube Music|com.google.android.youtube.tvmusic"
"Google TV / Play Movies store|com.google.android.videos"
"Google Meet|com.google.android.apps.tachyon"
"Gallery|com.android.gallery3d"
"Camera|com.android.camera2"
"Print service|com.android.printspooler"
"Live wallpapers|com.android.wallpaper.livepicker"
"Ambient mode screensaver|com.google.android.apps.tv.dreamx"
"Basic screensaver|com.android.dreams.basic"
"Google Assistant (remote mic button stops working)|com.google.android.katniss"
"Sony voice adapter|com.sony.dtv.assistantadapter"
"AirPlay (casting from iPhone/Mac stops)|com.sony.dtv.airplayapp"
"Chromecast built-in (casting from phone stops)|com.google.android.apps.mediashell"
"USB media player|com.realtek.tvmediaplay"
"ALTBalaji|com.balaji.alt"
)
ALL=n
for e in "${APPS[@]}"; do
  name="${e%%|*}"; pkg="${e##*|}"
  if ! $A shell pm list packages "$pkg" | grep -qx "package:$pkg"; then echo "  - $name: not on TV, skipping"; continue; fi
  if $A shell pm list packages -d "$pkg" | grep -qx "package:$pkg"; then echo "  - $name: already off"; continue; fi
  if [ "$ALL" != y ]; then
    read -r -p "Turn off $name? [y = yes, n = no, a = yes to all remaining] " ans
    case "$ans" in a|A) ALL=y;; y|Y) ;; *) echo "  kept: $name"; continue;; esac
  fi
  res=$($A shell pm disable-user --user 0 "$pkg" 2>&1 | tr -d '\r')
  if echo "$res" | grep -q "disabled-user"; then st=OFF; else st="FAILED: $res"; fi
  echo "  $st  $name"
  echo "$(date '+%Y-%m-%d %H:%M'),\"$name\",$pkg,$st,~/platform-tools/adb shell pm enable $pkg" >> "$L"
  [ "$st" = OFF ] && echo "\$A shell pm enable $pkg   # $name" >> "$U"
done
chmod +x "$U"
$A shell cat /proc/meminfo | grep MemAvailable > $HERE/scan/mem_after_changes.txt
echo
echo "Finished. Log: changes-log.csv"
echo "To undo everything later: bash $HERE/undo-all.sh"
echo "Go back to Claude and say: done"
