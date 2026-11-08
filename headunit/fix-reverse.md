# Fix: reverse camera does not appear

Status: **open** — found 2026-11-08, parked for later.
Until fixed: **reverse with the mirrors, do not rely on the camera.**

## Symptom

Selecting reverse does not show the camera picture. The screen stays on whatever app is in
front (it was Dacia Clock, so it looked like the clock "took over" — it did not).

## What was measured (read-only, ignition on, engine OFF)

Logs and a live monitor during a reverse test (`diag/reverse-incident-20261108-000127/`,
`diag/reverse-live-20261108-000236/`):

- Kernel reverse switch `/sys/devices/soc.0/car_reverse.14/state` stayed **0** the whole time.
- Zero `com.android.action.ACTION_BACKCAR_STATE_CHANGE` events with state 1 in ~20 min of logs;
  `com.nwd.backcar` (BackcarManager) never logged a "show".
- `settings get system mcu_backcar_state` = 0.

- Full 10-min live monitor 2026-11-08 00:02–00:12 (`diag/reverse-live-20261108-000236/`):
  node never left 0; every BACKCAR_STATE_CHANGE carried `backcarState = 0`. Unknown whether
  the engine was running during that window — redo with engine running (step 1).

=> The head unit is **never told** the car is in reverse. The camera app is never asked to show.
Dacia Clock is not the cause (and cannot detect reverse either, for the same reason).

## How reverse works on this unit (learned from logs)

- Signal path: reverse wire / CAN box → MCU → kernel node `car_reverse.14/state` →
  broadcast `com.android.action.ACTION_BACKCAR_STATE_CHANGE` (extra `backcarState`) →
  `com.nwd.backcar` draws the camera view. So the camera **is** an Android-side view.
- Relevant settings (`settings list system`):

  | key | value | meaning (likely) |
  |---|---|---|
  | `backcar_can_control` | **1** | reverse expected from the **CAN box**, not the wire — prime suspect |
  | `mcu_backcar_state` | 0 | last reverse state seen by MCU |
  | `backcar_delay_exit_time` | 5 | seconds the camera stays after leaving R |
  | `camera_ccd` | 3 | rear camera input type |
  | `backcar_auto_switch_front` | 0 | |
  | `factory_backcar_track_switch` | 1 | guide lines |

- `persist.sys.backcar.di.enable` = 0 (system property; meaning unknown, maybe "detect input").

## ⚠ CORRECTION 2026-11-08 (static analysis of com.nwd.backcar + KernelService, see docs/rom-analysis/backcar.md)

- Reverse comes from the **MCU UART (message type 5)** → KernelService `DeviceState.saveBackCarState()` → writes
  `mcu_backcar_state` + broadcasts `ACTION_BACKCAR_STATE_CHANGE` with a **byte** extra `extra_back_state`.
  The **CAN box is not involved**.
- While shown, BackcarManager polls `android.os.Hardware.getReverseCarState()` (very likely `car_reverse.14/state`)
  every 10 ms and closes the camera when it reads 0.
- **`backcar_can_control` does NOT affect reverse detection** (only guide-line config overwrite). **Leave it at 1.**
  The "switch to 0" plan below is obsolete.
- `am broadcast --ei extra_back_state 1` cannot simulate reverse (receiver uses getByteExtra; am on Android 6 has no byte extras).
- **Most likely cause: physical** — the unit's reverse-trigger lead gets no +12 V from the reverse-lamp circuit.
  Morning plan: engine on, R engaged → confirm reverse lamps lit; read `mcu_backcar_state` + `car_reverse.14/state`;
  if both stay 0 → check/measure the harness reverse lead (~12 V in R) / connect it to the reverse-lamp feed.

## CAN box profile (superseded conclusion — see correction above) (read 2026-11-08, `com.nwd.can.setting/shared_prefs/canfactorydb.xml`) — likely root cause

- Provider 欣朴 Xinpo (`canprovider_simple`), brand 雷诺 Renault, type 达西亚 Dacia, version **全系-通用 "all models, generic"**,
  CanManager `com.nwd.can.setting.service.renault.simple.CanManager`.
- `canfactory_config_has_reverse_control_system = false` → this CAN profile provides **no** reverse signal…
- …while `settings system backcar_can_control = 1` → the unit **expects reverse from CAN**. Nobody reports reverse.
- **Plan (morning, engine running):** 1) record a reverse test (script from 2026-11-08: kernel node + mcu_backcar_state +
  full logcat incl. `nwdapp_UartCommunication` frames + CAN lines); 2) if nothing, with OK:
  `settings put system backcar_can_control 0` (undo: `1`) → reverse taken from the **wire**; retest;
  3) if still nothing → wire not connected to the reverse-light feed (multimeter ~12 V on the harness reverse lead in R).
  Prefer the UI toggle if found (Car settings / factory menu `16176699`).

## Dacia Clock v17.1 (2026-11-08) already handles reverse

- Broadcast `com.android.action.ACTION_BACKCAR_STATE_CHANGE` carries **`extra_back_state`** (int, 0/1) — confirmed from v17.1's log.
- v17.1 reads that broadcast, the kernel node `car_reverse.14/state`, `mcu_backcar_state` and the old props.
  In reverse it never opens the clock and closes it if it is showing (`marche arriere -> fermeture`).
- So once the unit really detects reverse (steps below), nothing else is needed in the clock app.
  Re-test then: clock on screen → engage R → clock must disappear and the camera show.

## To do, in order

1. **Repeat the test with the engine RUNNING** (parked, handbrake). The first test was
   ignition on / engine off. Check that the rear reverse lights come on.
   Run a monitor: watch `car_reverse.14/state` + `logcat | grep backcar`.
2. Answer: has the camera *ever* triggered automatically on this unit? Is a reverse trigger
   wire (pink / `BACK` / `REVERSE`) connected to the reverse-light feed?
3. Open the camera manually (AVIN / camera app) → confirms the camera and video input work.
4. If lights on but no signal: switch trigger source CAN → wire. Prefer the UI
   (Car settings or factory menu `16176699`, "reverse detection CAN/line").
   CLI alternative (ask first, reversible): `settings put system backcar_can_control 0`
   (undo: `... 1`). Retest.
5. If still nothing: the wire is not connected / wrong circuit → multimeter at the harness
   (should read ~12 V on the trigger lead in R) or installer.
6. Once the signal works: verify `Reverse.java` in Dacia Clock. v16 checks only
   `persist.sys.reverse`, `sys.backcar`, `persist.sys.backcar`, `sys.reverse.state` — none exist
   here. v17 should read `/sys/devices/soc.0/car_reverse.14/state` and/or listen to
   `ACTION_BACKCAR_STATE_CHANGE`, and **finish the clock** when reverse is engaged.
   Also re-check: ignition-on auto-launch (navigation app = Dacia Clock) must not cover the camera.
