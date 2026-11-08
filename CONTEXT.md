# duster — AI Context File
_Last synced: 2026-11-08 @ 9e76f5a (+ branch `reorganise`)_

## 1. What This Is (Plain English)
- **In one sentence:** everything for my 2016 Dacia Duster's cheap Android car radio — the little apps I made for it
  (a big clock, a car manual), the original manuals, logos and wallpapers, and a full "how this radio works" investigation
  so I (or an AI) can keep tweaking it.
- **Why it exists:** the aftermarket head unit (Android 6, 1 GB RAM, abandoned by its maker in 2018) is slow and quirky.
  I want it to feel modern — clock on the home screen, offline maps, Android Auto from my phone, working reverse camera —
  without breaking radio, Bluetooth calls, CAN or the camera.
- **Who uses it:** just me (one car). Repo is public; personal data (vehicle reports, network names) is in it on purpose.
- **Vibe:** scrappy-but-careful personal tool. The apps are tiny; the investigation docs are the real value.

## 2. How To Run It
- **Nothing to "run" for the repo itself.** Content is files + APKs you install on the head unit.
- **Install an app on the head unit:** see `headunit/START-HERE.md`. Short version: phone/laptop on the same Wi-Fi as the
  unit (home router blocks devices from seeing each other — use a hotspot), then
  `adb connect <unit-ip>:5555` and `adb install -r apps/dacia-clock/apk/DaciaClock-v17.apk`.
- **Build Dacia Clock from source (Windows, Git Bash):** install JDK 17 + Android SDK `platforms;android-23` and
  `build-tools;34.0.0` (`apps/dacia-clock/source/README.md`), then `./apps/dacia-clock/source/build.sh`.
  Output goes to `apps/dacia-clock/apk/DaciaClock-v17.apk`; signed with `apps/dacia-clock/keys/dacia-v17.jks` (password in
  `dacia-v17.pass`) — both committed on purpose. If they go missing, `build.sh` silently creates a NEW key, which forces an uninstall.
- **Build / deploy:** no CI, no hosting. "Deploy" = `adb install` to the car.
- **Required env vars:** none. `build.sh` optionally reads `JAVA_HOME`, `ANDROID_SDK` (`build.sh:7-8`).

## 3. Tech Stack
- **Language + runtime:** Java 8 source level for Android apps (`build.sh`, javac `-source 8`); target device Android 6.0.1
  (API 23), apps declare minSdk 21 / targetSdk 22 (`apps/dacia-clock/source/AndroidManifest.xml:9`). HTML/ES5 for UIs
  (`duster-manuel.html`, `apps/dacia-clock/source/assets/clock.html`) — WebView is Chromium ~44.
- **Key tools (dev machine, not deps):** aapt / d8 / zipalign / apksigner from build-tools 34, JDK 17 (Temurin),
  adb (platform-tools), JADX 1.5.6 + REA 6.8.0 for ROM analysis, Python 3 helper scripts (`headunit/tools/*.py`).
- **What kind of project:** a mixed personal repo — 3 small Android APKs (+ source for one), docs, assets, shell scripts.
- **External services:** none at runtime. Apps work fully offline. Manual source: notice-utilisation-voiture.fr / archive.org
  (`car/manuals/readme.md`).

## 4. Code Map (The Important Files Only)
- `headunit/docs/HEADUNIT-INTEL.md` — **open this first** for anything about the radio. Evidence-tagged map: hardware,
  access channels (root ADB on Wi-Fi), MCU/CAN/reverse/sleep internals, what was changed, safe recipes, open questions.
- `headunit/docs/rom-analysis/*.md` — deep dives from decompiling the ROM: `backcar` (reverse camera), `can` (Xinpo CAN box),
  `mcu-protocol` (UART frames, power states, keys), `process-rules` (why apps get killed / "process is bad"), `signing`.
- `headunit/docs/DEVICE-PROFILE.md` — hard limits (armeabi-v7a, GLES 2.0, ES5-only WebView, no RTC). French.
- `headunit/docs/CHANGES-ON-UNIT.md` — every change made on the real unit, with its undo command.
- `headunit/docs/FIX-REVERSE.md` — reverse camera fault + next test steps (read the CORRECTION section first).
- `headunit/TODO.md` — exploration plan E1–E15 + upload checklist.
- `apps/dacia-clock/source/` — Dacia Clock v17.1 source. Pillars:
  - `InactivityService.java` — idle timer: opens the clock after 180 s **only if the launcher is in front**; reverse guard.
  - `Foreground.java` — which app is in front (UsageStats; needs `appops ... GET_USAGE_STATS allow`).
  - `Reverse.java` — reverse sources: sysfs node, ROM broadcast `extra_back_state`, `mcu_backcar_state`.
  - `ClockActivity.java` — WebView clock; tap anywhere = leave.
  - `assets/clock.html` — the clock design (ES5, offline, embedded font).
- `apps/dacia-clock/apk/` — `DaciaClock-v17.apk` (current), `clock-auto-3min.apk` (v16, old behaviour — the old root `DaciaClock.apk`, identical bytes).
- `headunit/scripts/*.sh` — ADB helpers (`diag.sh` read-only snapshot, `acc-cycle.sh`, `reverse-props.sh`, `set-idle.sh`).
- `headunit/tools/` — `apkcheck.py` (is an APK compatible with the unit?), `axmldump.py`, `oat2dex.py`.
- `apps/dacia-clock/decompiled-v16/` — approximate decompile of v16, kept for reference only.
- `headunit/rom-dump/` — vendor APKs/ODEX pulled from the unit (backcar, CAN, kernel, settings service, OneKeyClear,
  services.odex) + extracted `services-dex/` — input for JADX/REA. `headunit/diag/` — raw device snapshots and logs
  (getprop, settings, logcat, OsmAnd prefs backups, reverse tests).
- `apps/duster-manual/duster-manuel.html` — offline 4-language car manual app (source of `DusterManual.apk`, `ma.aymane.dustermanual`).
- `apps/duster-pdf/DusterPdf-1.apk` — manual PDFs viewer (`ma.aymane.dusterpdf`); source not in repo.
- `car/manuals/` (owner's manuals PDFs), `car/carlytics/` (vehicle history reports), `assets/logo/`, `assets/wallpaper/`.

## 5. Rules For Editing This Code
- **Car functions first.** Never touch launcher, radio, BT, CAN, reverse camera, MCU/kernel, SystemUI, Settings or WebView
  packages on the unit; never write `/system`; never flash firmware (`headunit/CLAUDE.md`, INTEL §0).
- **Ask the owner before any change on the unit** (`settings put`, `setprop`, `pm disable`, `appops`, install, file writes,
  reboot). Read-only commands are free. Log every change + undo in `headunit/docs/CHANGES-ON-UNIT.md`.
- **Android code:** Java 8, no AndroidX/support libs, no Gradle, minSdk 21 / targetSdk 22 on purpose (permissions granted
  at install), `armeabi-v7a` only, keep `android:hardwareAccelerated="false"` for the WebView clock.
- **HTML/JS for the unit:** ES5 only (`var`, `function`), no CSS variables / grid / `clamp()`, prefix flexbox `-webkit-`,
  animate only `transform`/`opacity`, embed fonts/images, no network (`DEVICE-PROFILE.md` §5).
- **Keys, ROM dumps and logs are committed on purpose** (owner decision 2026-11-08, repo is public): `apps/dacia-clock/keys/`,
  `headunit/rom-dump/`, `headunit/diag/`. Don't remove or regenerate the key; don't "clean them up". Anyone can see the key,
  so only install Dacia Clock APKs built from this repo.
- **Never declare `CATEGORY_HOME`**, never draw a visible `SYSTEM_ALERT_WINDOW` overlay, never write a reverse-camera app.
- Bump `versionCode` for every Dacia Clock release; sign with the same `dacia-v17.jks` or users must uninstall first.

## 6. Fragile Bits & Landmines
- **"process is bad" (ROM rule):** swiping an app out of Recents — or `adb install -r` — makes the ROM refuse every
  background start until the app is opened on screen. Symptom: watchdog alarms log `Unable to launch app … process is bad`.
  Fix after installs: `adb shell am start -n ma.aymane.daciaclock/.ClockActivity`. Never tell the owner to "clear recents".
- **Ignition off = sleep, not reboot.** `BOOT_COMPLETED` almost never fires. Ignition-on launch works only through the ROM
  setting `navigation_packagename` = Dacia Clock + `arm_auto_start_navi_when_acc_on=1`. Don't "simplify" this away.
- **Reverse extra is a byte**, not an int (`extra_back_state`). `am broadcast --ei` cannot fake reverse. `Reverse.java` reads
  any type on purpose (`InactivityService.java` backcar receiver).
- **`backcar_can_control` does not control reverse detection** — earlier docs guessed otherwise; leave it at 1.
- **Android Auto (Open Headunit):** Wireless Mode must be **Headunit Server** — Native needs Bluetooth, which this unit's
  Android stack cannot start. **Never probe phone port 5277 before connecting** — AA treats the probe as a car and hangs.
- **Foreground detection:** if usage access is missing, `Foreground.current()` returns null and the clock never opens
  (fails safe). After reinstalling, re-run `appops set ma.aymane.daciaclock GET_USAGE_STATS allow`.
- **Signing key:** v16's key is lost; v17 uses `apps/dacia-clock/keys/dacia-v17.jks` (alias `dacia`). Losing it again = uninstall/reinstall dance.
- **ODEX'd ROM:** system APKs keep intact `classes.dex`, but `services.jar` exists only as ART-quickened `services.odex`
  (`headunit/tools/oat2dex.py` repairs headers; some methods decompile badly).
- **Windows + Git Bash:** `MSYS_NO_PATHCONV=1` or `/sdcard/...` arguments get mangled; Java tools need `E:/...` paths (`build.sh` uses `pwd -W`).
- **Skip on cleanup passes:** `apps/dacia-clock/decompiled-v16/` looks dead but documents the old behaviour;
  `headunit/rom-dump/` and `headunit/diag/` look like junk but are the evidence base for the docs.
- **Moved 2026-11-08:** root APKs/manuals/logos went into `apps/`, `car/`, `assets/` — old raw GitHub links to root files are broken.

## 7. Current State
- **Last shipped:** Dacia Clock v17.1 (clock only over the home screen, tap to leave, reverse-aware) — tested on the unit;
  debloat of Google services; OsmAnd 4.7.10 + Morocco map, VLC, AndrOBD, Open Headunit (wireless Android Auto working);
  ROM investigation report (`HEADUNIT-INTEL.md` + 5 deep dives).
- **Working on now:** reverse camera never triggers — likely the reverse wire gets no +12 V (`headunit/docs/FIX-REVERSE.md`).
- **Next up:** (1) engine-on reverse test + harness check; (2) exploration pulls E1/E2/E5/E8 (launcher, framework, /config,
  partition backup) per `headunit/TODO.md`; (3) `powernotkill_` entries in `/config/app/OneClearConfig.xml` (needs OK).

## 8. Update Protocol (Verbatim)
> **For the AI Assistant:** When asked to "Update CONTEXT.md":
> 1. Re-run Phase 0 — check for new `GEMINI.md` / `CLAUDE.md` / `.github/` files.
> 2. Re-scan the tree, manifests, and `.github/workflows/` for drift.
> 3. Read our recent conversation for new decisions, fragile bits discovered, or shifted goals.
> 4. Refresh the `_Last synced_` line with today's date and current commit SHA.
> 5. Rewrite — do not append. One clean source of truth. Preserve still-true content, revise the rest.
> 6. Keep §1 and §2 in plain English. Keep the file under ~350 lines.
