# Duster head unit workspace

You are working on a **live car head unit** reachable over ADB/Wi-Fi from the owner's laptop
(Windows + Git Bash since 2026-11-08; Fedora also works). Owner: Mohamed-Aymane. The car is a 2016 Dacia Duster
mk1 phase 2, 1.5 dCi. Reply in the language the user writes in.

@docs/DEVICE-PROFILE.md
@docs/HEADUNIT-INTEL.md

## Layout

(Repo layout since 2026-11-08 — this folder is `headunit/` in github.com/achma-learning/duster; read `../CONTEXT.md` first.)
- `docs/` — `HEADUNIT-INTEL.md` (start here), `DEVICE-PROFILE.md`, `CHANGES-ON-UNIT.md` (every change + undo), `FIX-REVERSE.md`, `rom-analysis/`.
- `scripts/` — `diag.sh` (read-only snapshot), `acc-cycle.sh`, `reverse-props.sh`, `set-idle.sh`. All take the unit IP as 1st arg (saved to `.hu-ip`).
- `tools/` — `apkcheck.py`, `axmldump.py`, `oat2dex.py`.
- `rom-dump/` — vendor APK/ODEX pulled from the unit; `diag/` — outputs/logs. Read them instead of re-running commands.
- Dacia Clock lives in `../apps/dacia-clock/` (`source/`, `apk/`, `keys/`, `decompiled-v16/`).

## Current goal (updated 2026-11-08)

1. Done: Dacia Clock v17.1 — opens after 180 s idle only over the home screen, tap to leave, opens at ignition-on via the ROM navigation-app setting, reverse-aware.
2. Now: reverse camera never triggers — see `docs/FIX-REVERSE.md` (likely the reverse wire; test with engine running).
3. Next: read-only exploration E1–E15 (`TODO.md`), results into `docs/HEADUNIT-INTEL.md` + `docs/rom-analysis/`.

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
Dacia Clock v17+ is signed with `../apps/dacia-clock/keys/dacia-v17.jks` (alias `dacia`, password in `dacia-v17.pass`) — `../apps/dacia-clock/source/build.sh` uses it. The v16 key is lost.
