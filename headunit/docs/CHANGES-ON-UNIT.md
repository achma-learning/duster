## 2026-11-08 00:07 — batch 1 (Google) — pm disable
Undo: adb shell pm enable <package>
- com.google.android.apps.mapslite → Package com.google.android.apps.mapslite new state: disabled
- com.android.vending → Package com.android.vending new state: disabled
- com.google.android.configupdater → Package com.google.android.configupdater new state: disabled
- com.google.android.gsf.login → Package com.google.android.gsf.login new state: disabled
- com.google.android.gms → Package com.google.android.gms new state: disabled
- com.google.android.gsf → Package com.google.android.gsf new state: disabled

## 2026-11-08 00:26 — ISRG Root X1 added to user CA store
- /data/misc/user/0/cacerts-added/6187b673.0 (DER, sha256 96BCEC06...DF08C6)
- Undo: adb shell rm /data/misc/user/0/cacerts-added/6187b673.0

## 2026-11-08 01:13 — batch 2 (wallpaper extras) — pm disable
Undo: adb shell pm enable <package>
- com.bizhi.mogudongtai → Package com.bizhi.mogudongtai new state: disabled
- com.android.musicvis → Package com.android.musicvis new state: disabled
- com.android.wallpaper.livepicker → Package com.android.wallpaper.livepicker new state: disabled

## 2026-11-08 01:16 — OsmAnd settings (car profile, text 125%, Weather/Wikipedia/Terrain plugins off)
- Backup: diag/osmand-prefs-exact-20261108-011525
- Undo: force-stop net.osmand.plus, push the backup files back to /data/data/net.osmand.plus/shared_prefs/ (owner u0_a72, mode 660), delete net.osmand.settings.car.xml

## 2026-11-08 03:07 — Dacia Clock v16 -> v17 (new signing key)
- v16 uninstalled, DaciaClock-v17.apk (now apps/dacia-clock/apk/) installed; appops GET_USAGE_STATS allow
- Undo: adb uninstall ma.aymane.daciaclock; adb install apps/dacia-clock/apk/clock-auto-3min.apk

## 2026-11-08 03:43 — animations 0.5x
- before: window_animation_scale=1.0 transition_animation_scale=1.0 animator_duration_scale=1.0
- Undo: adb shell settings put global <key> <old value>

## 2026-11-08 03:43 — clean-up
- pm trim-caches 4G (app caches), deleted tombstones + ANR dumps, NewPipe SD video cache, staging files ISRG-Root-X1.crt + fdroid-archive.html in Download/ (cert already installed)
- Nothing to undo: apps rebuild caches

## 2026-11-08 03:45 — removed on request
- F-Droid uninstalled (org.fdroid.fdroid), ISRG Root X1 user CA removed, fdroid-archive.txt deleted
- Installed apps (OsmAnd, VLC, AndrOBD, Open Headunit) stay. Re-add CA later if Lets Encrypt HTTPS sites fail on Android 6.

