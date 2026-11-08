# Dacia Clock — source (v17.1)

Clean rewrite of the v16 app (only the decompiled APK existed). Same package
`ma.aymane.daciaclock`, same components, same `assets/clock.html`.

## What changed vs v16 (`../apk/clock-auto-3min.apk`)

| | v16 | v17.1 |
|---|---|---|
| Opens after 3 min without touch | always, even over Android Auto / navigation | **only when the stock launcher (home / app drawer) is in front** |
| Tap on the clock | requests fullscreen | **leaves the clock** (back to where you were) |
| Reverse gear | 4 properties that don't exist on this unit | kernel node, ROM broadcast `extra_back_state`, `mcu_backcar_state`; closes the clock in reverse |
| Front-app detection | — | UsageStatsManager (needs `appops set ma.aymane.daciaclock GET_USAGE_STATS allow`); unknown → never opens |

Optional `/sdcard/daciaclock.txt`: `idle=180`, `boot=20`, `log=1`, `home=com.android.launcher`.

## Build (Windows, Git Bash)

Needs JDK 17 (`winget install EclipseAdoptium.Temurin.17.JDK`) and Android SDK
`platforms;android-23` + `build-tools;34.0.0` in `%LOCALAPPDATA%\Android\Sdk`.

    ./build.sh      # -> ../apk/DaciaClock-v17.apk

Signing key: `../keys/dacia-v17.jks` + `../keys/dacia-v17.pass` (git-ignored). **Back them up** —
every future update needs this key. v16 was signed with a different, lost key, so v16 → v17
required one uninstall.

## Install on the head unit

    adb install -r ../apk/DaciaClock-v17.apk
    adb shell appops set ma.aymane.daciaclock GET_USAGE_STATS allow
    adb shell am start -n ma.aymane.daciaclock/.ClockActivity   # an update leaves the ROM's "bad process" flag; opening clears it
