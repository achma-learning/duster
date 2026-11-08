# Dacia Clock (`ma.aymane.daciaclock`)

A full-screen Dacia-style clock for the head unit.

| Path | What |
|---|---|
| `apk/DaciaClock-v17.apk` | **current (v17.1)** — opens after 3 min idle **only over the home screen / app drawer**, tap to leave, never in reverse |
| `apk/clock-auto-3min.apk` | v16 — old behaviour: reopens every 3 min even over other apps |
| `source/` | v17.1 source + `build.sh` (no Gradle) — see `source/README.md` |
| `keys/` | signing key `dacia-v17.jks` + `dacia-v17.pass` — required for updates to install over v17 |
| `decompiled-v16/` | approximate decompile of v16 (reference) |
| `DACIACLOCK-V16-ANALYSIS.md` | how v16 worked and why it stopped reopening |

Install: `adb install -r apk/DaciaClock-v17.apk`, then
`adb shell appops set ma.aymane.daciaclock GET_USAGE_STATS allow` and open it once
(`adb shell am start -n ma.aymane.daciaclock/.ClockActivity`). Going from v16 → v17 needs `adb uninstall` first (different key).
