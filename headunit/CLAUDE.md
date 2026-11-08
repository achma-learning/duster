# Duster head unit workspace

You are working on a **live car head unit** reachable over ADB/Wi-Fi from this laptop
(Fedora, `android-tools`). Owner: Mohamed-Aymane. The car is a 2016 Dacia Duster
mk1 phase 2, 1.5 dCi. Reply in the language the user writes in.

@docs/DEVICE-PROFILE.md
@docs/DACIACLOCK-V16-ANALYSIS.md

## Layout

- `duster/` — git repo github.com/achma-learning/duster (APKs, manuals, logos, wallpapers). Commit fixes here.
- `decompiled/` — androguard decompilation of `DaciaClock.apk` v16 + its `clock.html`. Approximate; use as the base for a rebuild.
- `scripts/` — `diag.sh` (read-only snapshot), `acc-cycle.sh`, `reverse-props.sh`, `set-idle.sh`. All take the unit IP as 1st arg (saved to `.hu-ip`).
- `diag/` — outputs. Read them instead of re-running commands.

## Current goal

1. DaciaClock (`ma.aymane.daciaclock`) must reopen automatically after **60 s** without touch, every drive, after any ignition cycle.
2. Never while reverse gear is engaged; the stock launcher stays HOME; HOME always returns to it.
3. Order: `diag.sh` → `set-idle.sh <ip> 60` → test → `acc-cycle.sh` → `reverse-props.sh` → only then decide whether a v17 rebuild is needed (see analysis doc).

## Rules on the device

- Read-only commands freely (`getprop`, `dumpsys`, `logcat -d`, `cat`, `ps`, `settings get`).
- **Ask the user before** any `pm uninstall`, `pm clear`, `pm disable*`, `settings put`, `setprop`, `rm`, reboot, or installing an APK. Explain what it changes and how to undo it.
- Never touch the stock launcher, radio, Bluetooth, CANbox or MCU packages. Never write to `/system`.
- Never add `CATEGORY_HOME`, never draw a visible `SYSTEM_ALERT_WINDOW` overlay, never write a reverse-camera app.
- Reverse-gear tests: car parked, handbrake on — ask the user to do the physical action.
- The unit only has power with ignition on; keep sessions short or tell the user to run the engine.
- If the ADB link drops (Wi-Fi/ignition), `adb connect <ip>:5555` again; don't loop forever — tell the user.

## Building an APK

Toolchain without Gradle (see device profile §4): `aapt` → `javac -source 8 -target 8` → `d8`/`dx` → `zipalign` → `apksigner`.
On Fedora: `sudo dnf install android-tools java-17-openjdk-devel` and get the Android build-tools + `platforms;android-23` via `sdkmanager` (cmdline-tools) if `aapt`/`d8` are missing.
minSdk 21 / targetSdk 22, Java 8, no AndroidX, `armeabi-v7a` only. Verify with `apksigner verify --min-sdk-version 21`.
The original signing key is not in this folder: if the user doesn't have `dacia.jks`, a new key requires uninstalling the old app first — ask before doing it.
