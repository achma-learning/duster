# CanAllInOne (com.nwd.can.setting): Renault/Dacia Xinpo ("simple") CAN analysis

Source: `CanAllInOne.apk` (versionName `v.18.03.01`, versionCode 13, `android:debuggable="true"`, `persistent="true"`), decompiled with JADX 1.5.6.
Decompiled tree (local only): `C:/Users/tool/AppData/Local/Temp/claude/6b7652970bbc1e95/43be6918-0825-4022-ac3c-f7133a29ab37/scratchpad/rom-analysis/can/`
This is static analysis only. Nothing was run on the device. **[unverified]** marks inferences that the code does not prove.

---

## TL;DR

* `renault.simple.CanManager` is a minimal driver. It handles **steering-wheel keys, rear radar (4 sensors), doors/boot/bonnet, steering angle, A/C, outside temperature, and the CAN-box version string**. It does **not** read gear, reverse, speed, RPM, handbrake, lights or fuel.
* **No Renault/Dacia profile (Xinpo or any other) in this APK parses reverse gear.** A few non-Renault profiles store a `mReverseState` bit, but the app never uses that bit to trigger the camera.
* **CanAllInOne never sends `com.android.action.ACTION_BACKCAR_STATE_CHANGE`.** It only listens for it. Reverse state comes from the NWD kernel/MCU (`mcu_backcar_state`, i.e. the reverse wire into the MCU).
* `has_reverse_control_system` is a per-profile capability flag copied from the XML. In this APK it is saved and exported but **never read**, and it is `false` for every Dacia/Captur profile.
* `backcar_can_control` does not mean "CAN detects reverse". It means "the CAN app owns the reverse-track (trajectory line) setting". It is set to 1 whenever you toggle anything in "Car Kit".
* **There is only one Dacia profile**: Renault -> Dacia -> "All series universal" -> simple. There is no Duster/Logan/Sandero-specific version. The current config is already the only valid Dacia choice.

---

## 1. `com.nwd.can.setting.service.renault.simple`

Files: `CanManager.java`, `CanProtocalUtil.java`, `CapturCanConstant.java`.

### Transport

* `getCanProducer()` returns `CanProducer.getInstance(ProtocalProducer.SIMPLE, UART_BITRATE_38400)`. That is a UART at **38400 baud**.
* Framing is `com.nwd.can.setting.protocal.simple.SimpleCanProtocal`:
  `0x2E | DataType | Len | Data[Len] | Checksum`, where `checksum = (sum(DataType..lastData) & 0xFF) ^ 0xFF`. The head unit ACKs with `0xFF`. A CAN-box firmware update uses DataType `0xE1` and file `can_app.UPDE`.
* Head unit to box:
  * `0x81` REQ_CONNECT_CHECK, payload `01` (start) or `00` (stop). Sent on init and on ACC change (`requestCanBoxStartOrEndCommond`), then re-sent after 150 ms.
  * `0x90` REQ_HOST_SETTING (`requestHostSetting`) is defined but **never called** by this manager.

### Box to head unit (`CapturCanConstant.ResponseType.responseDataTypes = {0x20,0x21,0x22,0x24,0x25,0x27,0x29,0x30}`)

| DataType | Const | Parsed in `CanProtocalUtil.receive` | Contents / output |
|---|---|---|---|
| 0x20 | IND_SWC_INFO | yes, `responseSWCKey` | byte0 = key, byte1 = state (0 up / 1 down / 2 repeat). For keys 3/4 (scroll wheel) byte1 is a **tick count** (capped at 5), and each tick becomes a press+release. Goes to `CarKeyEventManager.handleKeyEvent`. |
| 0x21 | IND_AC_INFO | yes, `responseACState` | A/C on, mode, recirculation, auto, front/rear defog, air direction, fan 0-8, L/R temp (value/2 °C, 0 = Low, 0xFF = High). Goes to `CanDefineSender.showAirCondition`. The UI is suppressed because `has_air=false`. |
| 0x22 | IND_BACK_RADAR_STATE | yes, `responseRadarState` | 4 **rear** sensors (L, LC, RC, R). Raw 1..4 maps to level 10/7/4/1, 0 = none. Goes to `CanDefineSender.showRadar`. There are no front sensors. |
| 0x24 | IND_CARDOOR_INFO | yes, `responseCarDoorInfo` | byte0 bit7 FR, bit6 FL, bit5 RR, bit4 RL, bit3 boot, bit2 bonnet. **Bits 1..0 and any further bytes are ignored.** Goes to `showCarDoor`, which is suppressed while reversing. |
| 0x25 | IND_PARKING_INFO | **no** (listed in the filter, no `case`) | Dropped silently. Meaning unknown **[unverified: parking-aid on/off or similar]**. |
| 0x27 | IND_TEMP_INFO | yes, `responseTempInfo` | Outside temperature, bit7 = sign, value/2 °C. Goes to `showCarInfo`. |
| 0x29 | IND_SWC_ANGLE | yes, `responseSWCAngle` | Signed 16-bit LE steering angle, clamped to ±5888, rescaled to 0..90. Goes to `CanDefineSender.sendSWCAngle`, which **only broadcasts while `mcu_backcar_state==1`** (`com.nwd.action.steerwheel_angle`) and drives dynamic trajectory lines. |
| 0x30 | IND_CAN_VERSION | yes, `responseCanVersion` | ASCII version string. Shown in the Version menu. |

Not present at all: vehicle speed, RPM, gear/reverse, handbrake, lights, fuel, mileage, trip computer, TPMS.

`initCarConfig()`: on first init (`car_config_has_init` flag) it sets `can_backcar_radar_switch=1`.
Menus (`R.xml.headers_menus_renault`): "Central settings" and "Version". `ICommonParams.getNotVisbleMenus` hides Central settings unless the profile is Kadjar-2016-high or Koleos-2017-high, so for Dacia **only Version** is shown. `requestCentralState` and `requestAirSetting` are empty stubs.

### Does any profile parse reverse/gear?

* Renault profiles in the APK: `renault.simple`, `renault.binary`, `renault.basida`, `renault.fenglang`, `renault_captur.{hiworld,raise,xinbas}`, `renault_koleos.{hiworld,xfeiyang}`. `grep -i "reverse|gear|backcar"` over the Renault parsers finds only `mBackCarWiperStart`, a "rear wiper on reverse" *setting* in `renault_captur.raise`. **No reverse-gear decoding.**
* Other "simple" (Xinpo) profiles: `ford_edge/simple` stores `mCarInfo.mReverseState` (offset+1 bit0), `toyota_common/simple` stores `mCurrentGear`, and `mazda3/simple` stores `doorInfo.mBackCarState`. Some non-simple profiles do the same (changan/toyota xfeiyang, ford_mendeo jieyou/siyi, hippo/trumpche daojun). **None of these values is used to change `mcu_backcar_state` or to send `ACTION_BACKCAR_STATE_CHANGE`.** They are display-only fields in `CarInfo`/`DoorInfo`.
* The only writer of `mcu_backcar_state` and sender of the backcar broadcast in the code base is `com.nwd.kernel.source.DeviceState.saveBackCarState()`. That is a kernel-library class and is not called from CanAllInOne. It is the NWD kernel/MCU service path.
* **[unverified]** The Xinpo Renault/Dacia protocol PDF (`欣朴软件-雷诺Renault 达西亚Dacia系列全兼容型串口通信协议V1.01.000.pdf`) is not in the APK. The box *might* carry reverse in 0x24 bits 0..1 or in 0x25, but the app discards both. You could confirm by logging raw UART frames (CanDebugActivity/LogView) while shifting into R.

---

## 2. `has_reverse_control_system`, `backcar_can_control`, reverse camera

### `canfactory_config_has_reverse_control_system` (shared_prefs `canfactorydb.xml`)

* Source: the XML attribute `config:isReverseControlSupport` on `<canprotocal>` in `res/xml/config_factory_carbands_info.xml`. It is read in `CanFactoryFragment` (`canprovider.isReverseControlSupport = ta4.getBoolean(7,false)`) and copied in `onSelecetedCanProvider` to `config.hasReverseControlSystem`.
* It is saved by `AbsCanFactory.saveCanfactoryConfig` and exported by `XmlUtil.writeConfig2Xml` as attribute `hasreversecontrolsystem` in `/sdcard/canconfig.xml` (`ConfigExportManager.SAVE_FILE_PATH`).
* **No code in CanAllInOne reads it** apart from `toString()`. It is presumably read by another NWD app (backcar/system UI) from canconfig.xml or the prefs. **[unverified, check backcar.md / other APKs]**
* In the Renault band it is `true` only for Koleos-2017 (raise/binary/xfeiyang/basida). The name and that usage suggest it means "the OEM reverse/360 camera system is controlled through the CAN box". It does **not** mean reverse detection. For Dacia it is `false`, and that is correct: forcing it on would at best do nothing and at worst make another app expect CAN-controlled camera switching that the Xinpo Dacia box does not provide.

### `backcar_can_control` (Settings.System, `FactoryTableKey.FactorySettingTable.BACKCAR_CAN_CONTROL`)

* Read by `com.nwd.factory.BackcarSettingUtil.isCanControl()` and `loadCurSetting()`. When it is >0, the factory default `backcar_track_switch` from `FactoryConfigSetting` is **not** applied, and `setBackcarTrackSwitch()` (the non-"New" variant) becomes a no-op. In short, the CAN app owns the trajectory-line switch.
* Written by `ConfigSettingActivity` ("Car Kit"): `CanConfigUtil.setBackcarCanControl(ctx, true)` runs at the end of **every** checkbox change. It is also written by `AliosCarFunctionActivity`.
* It has nothing to do with *detecting* reverse.

### Reverse camera interaction

* The app *listens* for `com.android.action.ACTION_BACKCAR_STATE_CHANGE` (`KernelConstant`, extra `extra_back_state`, byte) in `CanService`, `CanReceiver`, `CanBackCarManager`, `CarCameraManager`, `CarSystemStatusManager` and various 360 views. The truth source it checks is `Settings.System mcu_backcar_state == 1` (`CanRadarHelper.isBackCarState`).
* `CanReceiver`: on BACKCAR_STATE_CHANGE with state=1 it posts MSG 0 (`backcartest-->MSG_SHOW_BACKCAR_VIEW`) after 2000 ms in landscape. That shows the radar overlay (`RadarViewNew` when `can_use_new_radar`, else `RadarView4BackCar`) if `can_backcar_radar_switch==1`. With state=0 it posts MSG 1 (`MSG_CLOSE_BACKCAR_VIEW`).
* Radar frames (`ACTION_SHOW_RADAR`, `com.nwd.can.action_show_radar`) only *update* the overlay. The overlay is *opened* only during reverse. "Running radar" (`can_running_radar`) shows radar outside reverse only when `mParkingMode != 0`, which needs **front** sensor levels > 0. Renault.simple has no front sensors, so `can_running_radar=1` has **no effect** here.
* Steering angle is broadcast only while reversing (`sendSWCAngle`).
* Door pop-ups are suppressed while reversing.
* `car_config_door_need_reverse` = **"Reverse display of door"** (swaps L/R in `CarDoorView`). It is not related to reverse gear.
* `can_next_previous_back` = "Inversion of upper and lower curves" (`CanConfigUtil.getMusicRevese`). In `CarKeyEventManager.handleKeyEvent` it **swaps keys 3<->4 and 21<->22**, which is the scroll-wheel direction (next/prev). It is currently **1 (swapped)**.

Bottom line: on this ROM, reverse-camera triggering is entirely the MCU's job (reverse wire / MCU backcar input). The Xinpo CAN app cannot provide it, whatever profile is selected.

---

## 3. Selecting car type/version

* UI: launcher icon **"Car model"** = `com.nwd.can.setting/.ui.canfactory.CanFactoryActivity`. It has a MAIN intent-filter, so it is exported and needs no password in this APK. It is normally reached from factory settings. Next to it are "Car Kit" (`.ui.canconfig.ConfigSettingActivity`, LAUNCHER) and the debug screen `.ui.canfactory.CanDebugActivity`.
* Path: Brand -> Car type -> Car version -> CAN provider. Data comes from `res/xml/config_factory_carbands_info.xml`.
* Renault band options:

| Car type | carversionKey | Providers -> CanManager |
|---|---|---|
| Captur | `carversion_renault_captur` ("All series") | hiworld -> `renault_captur.hiworld`, raise -> `renault_captur.raise`, **simple -> `renault.simple`** |
| Kadjar | `carversion_renault_kadjar_low` | hiworld, raise, xinbas (`renault_captur.*`), binary (`renault.binary`) |
| Kadjar | `carversion_renault_kadjar_high` (2016) | raise, binary |
| Koleos | `_koleos_2013` | hiworld (`renault_koleos.hiworld`), fenglang (`renault.fenglang`) |
| Koleos | `_koleos_2014`, `_koleos_2015` | hiworld |
| Koleos | `_koleos_2017_low`/`_high` (hasAir, portrait, **isReverseControlSupport=true**) | raise, binary (+ high: xfeiyang, basida) |
| **Dacia** | **`carversion_renault_dacia`** ("All series universal", desc `达西亚-全系通用`) | **simple -> `renault.simple` only** |

* There is **no Duster-, Logan- or Sandero-specific entry** (grep of strings and XML for duster/logan/sandero/lada/terrano: 0 hits). Captur+simple maps to the *same* `renault.simple.CanManager`. The only difference is `carversionKey`/names, and it gains nothing.
* Saving (`CanFactoryFragment.onDoConfigSelectedFinish`) does the following:
  1. Writes `/sdcard/canconfig.xml`.
  2. Writes `shared_prefs/canfactorydb.xml` (`canfactory_config_*` keys, MODE_MULTI_PROCESS).
  3. Calls **`CanConfigUtil.resetCarConfig()`**, which resets: track=on, **backcar radar=off**, running radar=off, new radar=on, door-always=off, door-reverse=off, warning title=on, **next/prev inversion=off**, lock=on, `car_config_has_init=0`, and some camera flags.

  The renault.simple `initCarConfig()` then turns radar back on at the next service init. **[likely; it depends on CanService restart / reboot]**

---

## 4. Steering-wheel keys and Android Auto (OpenAuto/"Open Headunit")

Chain: box key (0x20 byte0) -> `CapturCanConstant.SWCKeyMap` -> internal CAN key -> `CarKeyEventManager.mClickKeyMap`/`mLongClickKeyMap` -> NWD MCU key value (`KernelConstant.McuKeyValue`) -> broadcast `com.nwd.action.EMU_RECEIVER_MCU_PROTOCAL` (extra `protocal`, an emulated MCU key frame). The NWD kernel then handles the key as if it were a hardware key.

| Box key | Internal | Short press -> MCU key | Long press (>1 s) | Likely Renault stalk control **[unverified]** |
|---|---|---|---|---|
| 1 | 1 | 14 VOL_UP (repeat) | VOL_UP | Vol + |
| 2 | 2 | 15 VOL_DOWN (repeat) | VOL_DOWN | Vol - |
| 3 | 4 | 6 PREVIOUS | 17 SEEK_DECREASE | scroll wheel one way (tick count) |
| 4 | 3 | 5 NEXT | 16 SEEK_INCREASE | scroll wheel other way |
| 6 | 6 | 2 MUTE | 0 POWER | Mute/pause |
| 7 | 7 | 23 MODE | 2 MUTE | Source |
| 8 | 17 | 11 NAVI (or 89 SPEECH if `key_voice_activation_status`!=0) | - | voice/"OK"? |
| 9 | 5 | 10 PHONE | 22 CANCEL_CALL | Phone |
| 19 | 28 | 16 SEEK_INCREASE | - | ? |
| 20 | 29 | 17 SEEK_DECREASE | - | ? |
| 22 | 23 | 7 PLAY | - | ? |

* In **radio** source (`SOURCE_RADIO`=4) the wheel is remapped: box 3 -> 22 -> MCU 63 PREFEB_DECREASE and box 4 -> 21 -> MCU 62 PREFEB_INCREASE (preset up/down). In music/video/android (-128/-127/0) it maps back to prev/next.
* `can_next_previous_back=1` swaps internal 3<->4 and 21<->22. That is why next and previous may feel inverted on the wheel.
* Short press fires on **release** within 1 s. Long press fires at 1 s while held. Only vol+/- have a repeat map.

For Android Auto / OpenAuto:
* Keys end up as NWD MCU key events. The kernel then turns them into Android media keys / app actions. **[unverified: which Android KeyEvents the kernel emits for NEXT/PREVIOUS/PLAY/MUTE. Check the kernel APK or `getevent`/`logcat` on device]** If AA ignores them, a small listener app could intercept them.
* Hook points for a third-party app (all are broadcasts, so no root is needed to *receive*):
  * `com.nwd.action.keycode.state` (`CanDefine.ACTION_KEYCODE_STATE`, extras `extra_keycode`, `extra_status`) carries the **internal CAN key + state for every SWC event**. It is sent **only if** `Settings.System can_config_debug_is_open == 1` (toggled in CanDebugActivity/LogView).
  * `com.nwd.action.ACTION_KEY_VALUE` (`extra_key_value`, byte) is the MCU key broadcast that CarKeyEventManager itself listens to **[presumed sent by kernel for all keys]**.
  * `com.nwd.action.steerwheel_angle` (extra `EXTRA_STEERWHEEL_ANGLE_LEVEL`) is sent only in reverse.
  * Door, radar and outside-temp broadcasts: `com.nwd.can.action_show_car_door`, `com.nwd.can.action_show_radar`, `com.nwd.android.ACTION_STATUSBAR_STRING`.

---

## 5. Recommended low-risk changes (with undo)

All of these are Settings.System keys or UI toggles. Record the current values before you change anything (`settings get system <key>`). The policy is to change one thing at a time.

1. **Keep the CAN profile as is** (Renault / Dacia / All series / simple). It is the only Dacia mapping, and Captur+simple is the same driver. Re-saving triggers `resetCarConfig()`, so avoid re-saving without a reason.
2. **Fix scroll-wheel direction, if it feels inverted:** Car Kit -> "Inversion of upper and lower curves", or `settings put system can_next_previous_back 0`. Undo: set it back to `1`. Effect: swaps next/prev and preset up/down.
3. **Leave `has_reverse_control_system=false`.** Nothing in this APK reads it, and the Dacia profile does not support it.
4. **Do not expect `backcar_can_control` to enable CAN reverse.** Leave it at 1. Setting it to 0 only lets the factory default override the trajectory-line switch.
5. **`can_running_radar`** is harmless but ineffective, because there is no front radar data. Leave it, or set it to 0. Undo: set it back to `1`.
6. **Diagnostics (temporary):** `settings put system can_config_debug_is_open 1` enables the `com.nwd.action.keycode.state` broadcast, so you can identify each SWC button in logcat or a listener. Undo: set it to `0`. CanDebugActivity/LogView can also show raw UART frames, which is the way to check whether the box sends anything in 0x24 bits 0-1 / 0x25 when reverse is engaged.
7. **Reverse camera:** fix it on the MCU/wiring side (reverse-light feed to the head-unit "BACK"/"REVERSE" input, and the factory backcar settings). See `backcar.md`. The CAN app cannot trigger it.

Per the workspace rules, every `settings put` above must be approved by the owner before it runs. CANbox packages must not be disabled or modified.

### Relevance for DaciaClock reverse detection

Per this code base, the reverse state that the NWD stack uses is **`Settings.System mcu_backcar_state` (1 = reversing)** together with the broadcast **`com.android.action.ACTION_BACKCAR_STATE_CHANGE`** (extra `extra_back_state`, byte). Both are written and sent by the kernel/MCU service (`DeviceState.saveBackCarState`). A 3rd-party app can read the setting with `Settings.System.getInt(cr, "mcu_backcar_state", 0)` and receive the broadcast. That may be more reliable than guessing system properties. **[verify on the unit: `settings get system mcu_backcar_state` while in R, car parked, handbrake on]**

Full undo of the CAN config: restore the backed-up `/data/data/com.nwd.can.setting/shared_prefs/canfactorydb.xml` and `/sdcard/canconfig.xml`, plus the recorded Settings.System values, then reboot.
