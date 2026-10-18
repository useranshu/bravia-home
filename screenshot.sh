#!/bin/bash
HERE="$(cd "$(dirname "$0")" && pwd)"   # folder this script lives in
# Saves what the TV is showing right now to docs/screenshot.png (for the README).
A=~/platform-tools/adb
$A connect 192.168.1.10:5555 >/dev/null
mkdir -p "$HERE/docs"
$A exec-out screencap -p > "$HERE/docs/screenshot.png" && echo "Saved docs/screenshot.png"
