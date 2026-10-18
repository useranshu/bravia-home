<div align="center">

# Bravia Home

**A macOS-inspired home screen for Google TV.**<br>
Lock-screen clock, a frosted Dock, rotating landscape wallpapers. No ads, no rows.<br>
Built for a Sony BRAVIA and installed entirely from a Mac over Wi-Fi.

`Android 14` · `Google TV` · `adb` · `Inter` · `Lucide` · no Gradle, no libraries

</div>

<!-- Add a screenshot: run ./screenshot.sh while the home screen is showing, then uncomment:
<p align="center"><img src="docs/screenshot.png" width="900" alt="Bravia Home on a Sony BRAVIA"></p>
-->

---

## Why

The stock Google TV home screen is a billboard: sponsored banners, "Top picks" rows, and a launcher that wakes up half your streaming apps in the background just to fill those rows. On a 2 GB TV that's also what makes everything feel slow.

Bravia Home brings the Mac's calm to the living room. It borrows the macOS lock screen for the clock, the Dock for apps, and the Mac's habit of greeting you with a new landscape every time you wake it. Then it gets out of the way.

## The Mac, on a TV

| macOS | Bravia Home |
|---|---|
| **Lock-screen clock** | Date above a large, tightly-tracked time with a soft white→lavender fade, centred just above the middle. Set in [Inter](https://rsms.me/inter/), the closest open alternative to SF Pro |
| **Dock** | A frosted-glass shelf that genuinely blurs the wallpaper behind it, with a hairline edge and squircle-style icons |
| **Dock magnification** | The selected icon grows and rises out of the shelf; its neighbours stay put. Remote-friendly: left/right to move, OK to open |
| **Automatically hide and show the Dock** | After 5 s idle the Dock slides away, leaving just the clock and the photo. Press **Down** and it slides back |
| **Wallpapers that change** | A new landscape every time the TV turns on and every 5 minutes, crossfaded and pre-downloaded so it's instant. Falls back to a gradient when offline |
| **Menu-bar weather** | Live temperature and conditions from [Open-Meteo](https://open-meteo.com) (no API key), with [Lucide](https://lucide.dev) icons |

All motion is eased (decelerate in, ease-in-out out) and runs on its own frame clock, so it stays smooth even with the TV's system animations switched off for speed.

## How it plugs into Google TV — safely

The obvious approach (disable Google TV's launcher, set a new default home) **boot-looped this TV**: after an app update Android forgot the default, fell back to Google's recovery screen, and the window manager crashed on every start.

So Bravia Home never touches the system launcher:

- It's a normal app, plus a tiny accessibility service (`HomeKeyService`) that catches the remote's **Home** button and opens Bravia Home.
- **Press Home twice quickly** for Google TV's own home screen.
- If anything ever breaks, Home simply opens Google TV as before — no black screens, no boot loops.

## Build & install

**You need:** a Mac, [Android platform tools](https://developer.android.com/tools/releases/platform-tools) in `~/platform-tools`, and the TV on the same Wi-Fi with **Developer options → USB debugging** turned on.

1. Set your TV's IP in the scripts (default `192.168.1.10`) and your city and coordinates at the top of `HomeActivity.java`.
2. Build and install:

```bash
./7-build-home.sh      # first run downloads Java + Android build tools into ./tools
./12-apply-dock.sh     # installs the app and turns on the Home-button helper (asks before each step)
./13-update-dock.sh    # for later updates
```

The build signs the app with `tools/home.keystore`. **Back it up** — the TV only accepts updates signed with the same key (it's git-ignored on purpose).

## TV clean-up scripts (optional)

Read-only scan first, then nothing changes without a yes/no prompt. Apps are only ever **turned off, never deleted**, and every change is logged with its undo command.

| Script | What it does |
|---|---|
| `1-scan.sh` | Lists every app, free memory, current settings, and takes a screenshot |
| `2-turn-off.sh` | Turns off bloat (Sony promos, demo mode, unused Google extras…) one app at a time |
| `3-restart-check.sh` · `4-check-only.sh` | Restarts the TV and confirms everything stayed off |
| `5-measure.sh` | Measures free memory once the TV has settled after start-up |
| `screenshot.sh` | Saves the current TV screen to `docs/screenshot.png` |

Changes are written to `changes-log.csv`, and `undo-all.sh` puts everything back (both stay local).

## Project structure

```
home-app/
├── AndroidManifest.xml
├── src/com/anshu/home/
│   ├── HomeActivity.java     # the whole UI: clock, weather, carousel, dock, motion
│   ├── HomeKeyService.java   # remote Home button → Bravia Home (double-press → Google TV)
│   └── LucideIcons.java      # Lucide weather icons converted to native drawing commands
├── res/                      # strings + accessibility-service config
└── assets/                   # Inter fonts, default gradient wallpaper
```

Built with `aapt2`, `javac`, `d8` and `apksigner` directly — the whole app is three Java files.

## Credits

- [Inter](https://rsms.me/inter/) by Rasmus Andersson — SIL Open Font License 1.1 (`LICENSES/Inter-OFL.txt`)
- [Lucide](https://lucide.dev) icons — ISC License (`LICENSES/Lucide-ISC.txt`)
- Weather by [Open-Meteo](https://open-meteo.com)
- Wallpaper photos are Bing's daily images, fetched at runtime for personal display. None are included in this repository.

Designed by [Anshu Kumar](https://anshukumar.design). Inspired by macOS; not affiliated with or endorsed by Apple. macOS is a trademark of Apple Inc.
