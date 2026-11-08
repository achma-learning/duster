# Head unit — K2001M (Allwinner T3, Android 6.0.1, NWD ROM)

| Path | What |
|---|---|
| `docs/HEADUNIT-INTEL.md` | **start here** — evidence-tagged map: hardware, access levels, internals, changes, recipes, open questions |
| `docs/rom-analysis/` | deep dives: `backcar` (reverse camera), `can` (Xinpo CAN box), `mcu-protocol`, `process-rules`, `signing` |
| `docs/DEVICE-PROFILE.md` | hard limits for apps on this unit (French) |
| `docs/CHANGES-ON-UNIT.md` | every change made on the unit + undo |
| `docs/FIX-REVERSE.md` | reverse camera fault and test plan |
| `TODO.md` | exploration plan (E1–E15) and open items |
| `START-HERE.md` · `CLAUDE.md` | connect over ADB · rules for AI assistants working on the unit |
| `scripts/` | ADB helpers (`diag.sh` read-only snapshot, `acc-cycle.sh`, `reverse-props.sh`, `set-idle.sh`) |
| `tools/` | `apkcheck.py` (is an APK compatible?), `axmldump.py`, `oat2dex.py` |
| `rom-dump/` | vendor APK/ODEX pulled from the unit (input for JADX / REA) |
| `diag/` | raw device snapshots, logs, backups from sessions |

Apps for the unit live in [`../apps/`](../apps/).
