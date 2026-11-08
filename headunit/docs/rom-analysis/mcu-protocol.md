# NWD MCU protocol and system services (KernelService / SettingService)

Static analysis only. No device was contacted. Sources:

| APK | Package | Version | Process / UID |
|---|---|---|---|
| `8925_Carkit_KernelService.apk` | `com.nwd.kernel` | 1.0.8 (108) | `com.nwd.kernel.setting`, sharedUserId `com.nwd.kernel.setting`, `persistent=true`, `debuggable=true` |
| `8925_Carkit_SettingService.apk` | `com.nwd.setting.service` | 1.0.6 (106) | same process and UID as the kernel service |

Decompiled with JADX 1.5.6 into `scratchpad/rom-analysis/mcu/{kernel,setting}`. Class names below are the original (unobfuscated) names.
Log tags appear on the device as `nwdapp_<JLog name>`, for example `nwdapp_UartCommunication` and `nwdapp_DeviceState`.

Confidence markers: **[confirmed]** means read directly in code. **[inferred]** means deduced from code paths. **[uncertain]** means plausible but not proven.

---

## 1. UART link and frame format

### 1.1 Transport [confirmed]
`KernelService.loadUartConfig()` reads `/config/app/UartConfig.ini` (`ro.nwd.config.path`, default `/config`):

- `key_kernel_uart`: device node. It defaults to **`/dev/ttyS3`**, but only when the ini file is empty or missing. If the file exists and has no such key, `OsDeviceManager` is used and there is no MCU link.
- `key_kernel_uart_bitrate`: default **57600** baud.
- Native layer: `com.nwd.SerialPort` loads `libserial_port_nwd.so`.
- Only `KernelService` opens the UART. `SettingService` and other apps reach it through the AIDL binder (see 1.5).

Other keys `UartConfig.ini` can contain: `goto_sleep_flag`, `default_os_volume_state`, `key_need_kernel_start_service` (comma-separated service actions started at boot), `key_is_alway_cvbs_out`, `key_reset_navi_wait_time`, `key_acc_on_not_need_start_app`, `key_can_change_source_when_oncall`, `key_default_allow_change_source_time`, `key_is_keep_launcher_source`, `backcar_delay_exit_time` (default 5), `backcar_auto_switch_front`, `backcar_auto_switch_front_fun`.

### 1.2 Frame layout [confirmed] (`com.nwd.kernel.utils.KernelProtocal`)

```
byte 0   : 0xF0                 HEADCODE (-16)
byte 1   : LEN                  = total_frame_length - 3  (1..250)
byte 2   : TYPE                 module / group
byte 3   : DATATYPE             command within the group
byte 4   : 0x00                 reserved: always 0 from every generator, never read by any parser [inferred]
byte 5.. : DATA                 data length = LEN - 3
last     : CHECKSUM             = (sum of bytes 1 .. n-2) & 0xFF   (LEN + TYPE + DTYPE + reserved + DATA)
```

```java
public static final byte calCheckSum(byte[] data, int offset, int len) {
    byte result = 0;
    for (int i = offset + 1; i < len - 1; i++) result = (byte)(data[i] + result);
    return result;
}
// checkCompleteProtocal: data[0]==-16, length = data[1]; length==0||>250 -> bad; length == len-3 -> verify checksum
```

Worked example: the frame in your logs, `F0 05 00 02 00 00 00 07`, decodes as follows.
- LEN=5, so the total is 8 bytes. TYPE=0 (public), DTYPE=2 (ARM volume state), reserved=0, DATA=`00 00` (16-bit LE `arm_volume`=0).
- Checksum 05+00+02+00+00+00 = **07**, which matches.
- Sender: `MCUDeviceManager.sendArmVolumeState()` → `ActionProtocalUtil.generateARMVolumeState()`. It is a **keep-alive heartbeat**, rescheduled every `SEND_ARM_VOLUME_DELAY_TIME = 3000` ms and also sent on each `com.nwd.action.ACTION_VOLUME_STATE_CHANGE`. The code says 3 s; you saw about 1 s. Other volume-state broadcasts may account for the extra frames. **[uncertain]**
- Logging: `UartCommunication.writeData()` logs each **outgoing** frame (`LOG.print(bArr)`). `KernelService.distribution()` logs each **incoming** frame, except TYPE 3 / DTYPE 2 (radio spam).

`generateNullProtocal()` has a branch for frames longer than 250 bytes (`data[1]=0`, 16-bit length in bytes 2..3). It is buggy: it overwrites TYPE/DTYPE right after. Treat long frames as unsupported or unknown. **[confirmed bug]**

### 1.3 ACK / retransmit [confirmed] (`com.nwd.kernel.utils.AckHelper`)
- The ACK frame is `TYPE 0 / DTYPE 0x0F`, DATA = `[acked_type, acked_dtype]`, for example `F0 05 00 0F 00 tt dd cs`.
- The kernel automatically ACKs these **incoming** frames: TYPE 0 with DTYPE ∈ {0x03, 0x0C, 0x00, 0x05, 0x01, 0x19}, and TYPE 0x7B (factory) DTYPE 0x15.
- These **outgoing** frames wait for an ACK and are resent every 3 s until one arrives: change source (1/3), OS sleep state (0/0x16), OS volume state (0/0x18).

### 1.4 TYPE (group) IDs [confirmed]
| TYPE | Name | | TYPE | Name |
|---|---|---|---|---|
| 0x00 | PUBLIC | | 0x0D | AUDIO |
| 0x01 | ACTION (keys, source, mute, backlight) | | 0x0E | SETTING |
| 0x02 | INIT (settings dump / EEPROM) | | 0x0F | RADAR |
| 0x03 | FM / radio | | 0x10 | VIRTUAL_DISK |
| 0x04 | DVD | | 0x11 | CANBUS |
| 0x05 | IPOD | | 0x12 | BT |
| 0x06 | TV | | 0x14 | MHL |
| 0x07 | AUX | | 0x15 | DVR |
| 0x08 | CAMERA | | 0x16 | MP5 |
| 0x09 | CDC | | 0x17 | DISPLAY_DRIVER_UPDATE |
| 0x0A | DAB | | 0x18 | MCU_CONFIG_UPDATE |
| 0x0B | BACKGROUND | | 0x20 | HBS factory (raw) |
| 0x0C | TPMS | | 0x6F / 0x7A / 0x7B | ARM upgrade / MCU upgrade / FACTORY |

### 1.5 Command table

Direction: **M→A** means MCU to Android; **A→M** means Android to MCU. Offsets are positions in DATA, starting at byte 5.

#### TYPE 0x00 PUBLIC
| DTYPE | Dir | Payload | Meaning / handler |
|---|---|---|---|
| 0x00 | M→A | `[state, default_source_id]` | **MCU power state** (ACC). 0=shutdown, 1=running (ACC on), 2=false shutdown (soft off), 3=delay shutdown (ACC off, going to sleep). See §2. `PublicProtocalUtil.getMCUState` → `onGetMcuState`. ACKed. |
| 0x00 | A→M | `[1]` | ARM boot complete. Resent every 2 s, up to 10 tries, until the MCU reports its state (`SourceMgr.sendBootComplete`). |
| 0x01 | M→A | ASCII | MCU version → `Settings.System mcu_version` |
| 0x02 | A→M | `[lo, hi]` | ARM volume/audio-mix bitmask (`arm_volume`) = **heartbeat** every 3 s |
| 0x03 | M→A | `[lo, hi]` | Device-present bitmask (DVD=1, TV=2, TPMS=4, CDC=8, IPOD=16, USB=32, SD=64, DISK=128, DVR=256, MP5=1024, MHL=2048, AUX=4096) → `mcu_device_state` and `com.nwd.action.ACTION_DEVICE_CHANGE` |
| 0x04 | M→A | `[0/1]` | **Handbrake** (1 = applied) → `mcu_hand_break`, `com.nwd.action.ACTION_HAND_BREAK_STATE_CHANGE` |
| 0x05 | M→A | `[0/1]` | **Reverse / backcar** → `DeviceState.saveBackCarState` ("origin backcar = X,new backcar = Y"). ACKed. |
| 0x06 | M→A | `[0/1]` | **Illumination / headlights (ILL wire)** → `mcu_light_state`, no-source-device bit 2, `com.nwd.ACTION_ILL_STATE_CHANGE` |
| 0x07 | M→A | `[?, signal]` | Video signal present (byte 1) → `mcu_video_signal` |
| 0x08 | A→M | `[0/1]` | Watchdog enable |
| 0x09 | M→A / A→M | — | Watchdog ping / response |
| 0x0A | both | `[index]` | UI language index |
| 0x0C | M→A | `[bits]` | Current controller: bit0=0 → car/MCU UI, bit1=0 → touch at MCU. ACKed. |
| 0x0E | M→A | `[state]` | Screen state (0 → controller to MCU) |
| 0x0F | both | `[type, dtype]` | ACK |
| 0x10 | M→A | `[yy, mm, dd, hh, mi, ss, fmt]` | MCU RTC (year = yy + base). 0 = 12 h. → `com.action.ACTION_SYNC_MCU_TIME` |
| 0x10 | A→M | `[yy mm dd hh mi ss fmt tzH tzM]` | Set MCU clock |
| 0x11 | both | `[0/1]` | MCU video state / allow MCU to switch video (`is_allow_mcu_switch_video`). Used by the handbrake video lock. |
| 0x12 | A→M | 4 bytes | GPS RTC time |
| 0x13 | A→M | `[type, dtype]` | Query: ask the MCU to resend a given frame (for example `[0x11, 0x18]` = CAN battery voltage) |
| 0x14 | M→A | ASCII | DVD version |
| 0x16 | both | `[0/1/2]` | **OS sleep state**. A→M: 0 = OS ready to sleep, 1 = awake, 2 = background tasks done / waiting. M→A: 0 or 2 → stop UART writes; 2 → request system sleep. |
| 0x17 | M→A | `[ch]` | MCU video channel (0 = backcar, 1 = front) |
| 0x18 | A→M | `[state]` | OS volume state (ACK-tracked) |
| 0x19 | M→A | `[type]` | Voltage detect: 0 = low voltage → disables auto-wakeup. ACKed. |
| 0x1A | both | `[0/1]` | Auto-wakeup enable (`persist.nwd.autowakeup`) |
| 0x1B | A→M | `[platform, sub]` | Platform info |

#### TYPE 0x01 ACTION
| DTYPE | Dir | Payload | Meaning |
|---|---|---|---|
| 0x00 | A→M | `[event, x lo, x hi, y lo, y hi]` | Touch coordinate forward (0 up / 1 down / 2 move) |
| 0x01 | M→A | `[keyType, longPress, keyValue]` | **Key event**: panel, steering wheel, IR, CAN keys. `keyType` 0 up / 1 down / 2 long **[uncertain which ones the MCU actually sends]**. Values are in §1.6. |
| 0x01 | A→M | `[keyType, keyValue]` | Inject a key into the MCU (for example POWER=0, EJECT=12, MUTE=2, GSENSOR_CRASH=117) |
| 0x02 | both | `[0/1]` | Mute state / set mute → `mcu_mute_state`, `com.nwd.ACTION_MUTE_STATE_CHANGE` |
| 0x03 | A→M | `[srcType, srcId]` | Request audio/video source change (ACK-tracked) |
| 0x03 | M→A | `[srcType, srcId, ok]` | Source change result → `mcu_current_source`, `com.nwd.action.ACTION_CHANGE_SOURCE` |
| 0x04 | A→M | — | Beep |
| 0x05 | both | `[0/1]` | LCD backlight switch |
| 0x06 | M→A | — | MCU asks to go back to the Android source (log only) |
| 0x07 | both | `[where]` | Pop source (0 = last source, 1/2 = relaunch current, 3 = last app without source change) |
| 0x08 | A→M | `[type]` | 0 = factory reset, 1 = system update, 2 = reboot |
| 0x09 | A→M | — | Display off |
| 0x0B | A→M | `[ms]` | Short mute |

#### TYPE 0x02 INIT (M→A after boot) [confirmed]
- 0x00: public settings dump (`MCUSettings.initAllPublicSetting`): device bitmask, audio maxima, EQ, volumes, DSP. Then `com.android.action.ACTION_INIT_FINISH`.
- 0x01: 32-byte EEPROM block → `com.nwd.action.ACTION_GET_EEPROM_DATA` (`extra_eeprom_data`).

#### TYPE 0x0E SETTING [confirmed]
- M→A:
  - 0x00: all system settings → `mcu_System_setting_state` (bit flags, see §3.3), tricolor RGB, steering-wheel learn state, radio area, misc, video.
  - 0x02: system state.
  - 0x03: RGB.
  - 0x04: steering-wheel learned-key bitmap → `mcu_steering_wheel_state`.
  - 0x05: learn state.
  - 0x0D: learn tips index.
  - 0x06 to 0x0A, 0x0C, 0x0E, 0x0F, 0x15, 0x16: individual settings.
- A→M 0x01 `[functionType, value]`: generic function set, using the `SettingFunctionType` values below.
  - System: 1 = backcar auto, 17 = handbrake check, 18 = autostart by backcar, 19 = light check, 22 = autostart by any key, 42 = key tone, 45 = backcar volume, 50 = backcar mirror.
  - Steering-wheel learn: 4 = begin learn, 5 = end learn.
  - Steering-wheel key assign: 6 = vol+, 7 = vol−, 8 = mute, 9 = prev, 10 = next, 11 = play, 12 = mode, 13 = power, 14 = call, 15 = hang up, 24 = navi, 51 = speech, …
- Other A→M: 0x03 RGB, 0x06 to 0x0A, 0x0C day backlight, 0x0E video param, 0x0F video mode, 0x15 amplifier, 0x16 smart fan, 0x17 mix function.

#### TYPE 0x11 CANBUS (only the parts visible in these APKs)
- M→A: 0x02 CAN box version (`canbus_version`), 0x0C CAN update state, 0x0D CAN update data request, 0x18 battery voltage (`com.nwd.action.ACTION_CAN_BATTERY_VOLTAGE`).
- A→M: 0x06 radar switch, 0x0B enter CAN update, 0x0C update data frame.
- **Reverse, lights and doors do not arrive here.** The Xinpo CAN decoding happens in the MCU or in `CanAllInOne.apk`, not in these two services. **[inferred]**

#### TYPE 0x12 BT [confirmed]
A→M 0x01 `[btStatus]`: 0 = end, 1 = incoming, 2 = outgoing, 3 = online. It is driven by the BT call broadcasts.

### 1.6 MCU key codes (`KernelConstant.McuKeyValue`) [confirmed]
| Code | Key | Code | Key | Code | Key |
|---|---|---|---|---|---|
| 0 | POWER | 12 | EJECT | 23 | MODE |
| 1 | TONE | 14 / 15 | VOL+ / VOL− | 24–28 | ANDROID UP/DOWN/LEFT/RIGHT/OK |
| 2 | MUTE | 16 / 17 | SEEK+ / SEEK− | 30 | EQ |
| 3 | DVD | 18 | ANDROID_BACK | 31 | STOP |
| 4 | RADIO | 19 | ANDROID_MENU | 89 | SPEECH |
| 5 | **NEXT** | 20 | ANDROID_HOME | 96 | RECENT |
| 6 | **PREVIOUS** | 21 | ACCEPT_CALL | 97 | MEDIA |
| 7 | **PLAY** | 22 | CANCEL_CALL | 99 | BT_MUSIC |
| 8 / 9 / 10 / 11 | SETUP / MENU / PHONE / NAVI | 92 / 93 | TURN_LEFT / TURN_RIGHT | 113–116 | CAN AC / seat / fan± |

The full list is in `KernelConstant.McuKeyValue` (about 110 codes). SWITCH_ARM=-15 (0xF1) and SWITCH_CAN=-14 (0xF2).

### 1.7 Reverse ("new backcar"): where it comes from, and `backcar_can_control`

Chain **[confirmed]**:
```
MCU UART  F0 04 00 05 00 <0|1> cs
  -> KernelService.distribution() -> AckHelper auto-ACK (0/5)
  -> MCUDeviceManager.receive -> PublicProtocalUtil.getMCUBackCarState
  -> AbsDeviceManager.onBackcarStateChange -> DeviceState.saveBackCarState(ctx, state)
       LOG "origin backcar = %d,new backcar = %d"
       if changed: Settings.System mcu_backcar_state = state
       if mcu_state not in {3,0}: broadcast com.android.action.ACTION_BACKCAR_STATE_CHANGE (extra_back_state: byte)
       else (ACC off / sleeping): broadcast only when state == 0
```
- "new backcar" is the single data byte of frame TYPE 0 / DTYPE 5, and **the MCU firmware decides it**. Android has no code that reads the reverse wire or the CAN reverse signal directly. Whether the MCU takes it from the REVERSE wire or from the CAN box (via its CAN UART) is decided inside the MCU firmware or its config. These APKs cannot show which. **[inferred]** Test it this way: disconnect the reverse wire and check whether `mcu_backcar_state` still toggles.
- **`backcar_can_control` (Settings.System, int) does NOT gate reverse detection.** In `BackcarSettingUtil` it is read only by `isCanControl()` and in `loadCurSetting()` / `setBackcarTrackSwitch()`:
  ```java
  mbCanControl = FactoryTableKey.getIntValue(cr, "backcar_can_control") > 0;
  if (propertiesInteger != -1 && !mbCanControl) writeDataToTable(cr, "factory_backcar_track_switch", ...);
  public static final void setBackcarTrackSwitch(Context c, int i) { ... if (mbCanControl) return; ... }
  ```
  Meaning: when the value is >0, the CAN app (`CanAllInOne.apk`, which also contains the string) owns the **dynamic trajectory / guide lines** (`factory_backcar_track_switch`, driven by the CAN steering angle). The factory menu and `FactoryConfig` can then no longer change it. The string also appears in `A20CarkitBackcarService.apk`. That APK may use it for the overlay, but this is **[uncertain]** because it was not decompiled here.
- Consumers of the reverse state in SettingService (`AbsBackLightManager`): reverse=1 forces the LCD backlight on, even in soft-off state 2, at 100% brightness. Reverse=0 restores the previous brightness. Night dimming is skipped while reversing.

### 1.8 Illumination and handbrake [confirmed]
- **ILL** (0/6):
  - KernelService sets `mcu_no_source_device_state` bit 2 and broadcasts `com.nwd.ACTION_ILL_STATE_CHANGE` (`extra_ill_state` byte) plus `com.nwd.action.ACTION_NO_SOURCE_DEVICE_CHANGE`.
  - SettingService writes `mcu_light_state`. When system-state bit `0x8000` (LIGHT_STATE / "light check") is set, it dims to `mcu_back_light_night` (default 25), capped by the current brightness.
- **Handbrake** (0/4):
  - `mcu_hand_break`.
  - `com.nwd.action.ACTION_HAND_BREAK_STATE_CHANGE` (`extra_hand_break_state` boolean). It is broadcast **twice**, once by KernelService (`state != 0`) and once by SettingService (`state == 1`).
  - If system-state bit `0x4000` (handbrake check) is set and the handbrake is off while the MCU shows video, it sends `com.nwd.action.ACTION_HAND_BREAK_WARNNING` and blocks MCU video (`ACTION_SET_MCU_CAN_TRANSLATE_VIDEO false`).

---

## 2. ACC / sleep / wake sequence

MCU states (`DeviceState.BOOT_STATE_*`): **1** = START (ACC on), **3** = DELAY_SHUTDOWN (ACC off, grace period), **0** = SHUTDOWN (going to deep sleep), **2** = FALSE_SHUTDOWN (soft power-off, screen off, ACC still on; the backlight wakes for calls and reverse), **-16 (0xF0)** = unknown/initial.

### Boot
1. `BootReceiver` (BOOT_COMPLETED) starts `KernelService`. It opens the UART, sends **ARM boot complete** `F0 04 00 00 00 01 cs` every 2 s (up to 10 times), and starts these services: `com.nwd.radio.service.ACTION_RADIO_SERVICE`, `com.nwd.can.service.ACTION_CAN_SERVICE`, `com.nwd.setting.service.ACTION_SETTING_SERVICE`, `com.nwd.voice.ACTION_START_VOICE_ANALYZE_SERVICE`.
2. The MCU answers with 0/0 `[1, src]` and INIT dumps. The kernel then sends `com.android.action.ACTION_INIT_FINISH`.

### ACC off (state 1 → 3 → 0) [confirmed in kernel; framework parts inferred]
1. M→A `0/0 [3, src]`.
2. `SourceMgr.notifyBootState` broadcasts **`com.nwd.action.ACTION_MCU_STATE_CHANGE`** with **`extra_mcu_state` (byte) = 3**.
3. `accOff()` runs:
   - It saves the current source in `SourceKeeper` (`keepCurrentSource`), resets the app stack, and switches to the launcher (appid 4).
   - It broadcasts **`com.nwd.action.ACTION_MCU_POWER_OFF`** (no extras). SettingService saves the audio settings when it receives it.
   - `AbsBackLightManager` turns the LCD backlight off unless a BT call or reverse is active.
4. The framework (`services.odex`, `NwdManagerService`) receives `ACTION_MCU_STATE_CHANGE` and logs "extra_mcu_state = 3, ACC off". It sets `sys.nwd.mcu.state` / `sys.mcu.last.state`. Those two properties are written **only in the framework**, not in these APKs. **[inferred from strings]**
5. M→A `0/0 [0, src]` (real shutdown). The kernel runs `sync` and clears `mCleanSuccessFlag`. It broadcasts `ACTION_MCU_STATE_CHANGE` with `extra_mcu_state = 0`; the framework logs "system go to sleep...".
6. The framework cleans up and reports each step with **`com.nwd.ACTION_FRAMEWORK_CLEAN_SUCCESS`**, `extra_clean_value` (int bits): 1 = media scan stopped, 2 = install/uninstall stopped, **4 = other apps killed**, 8 = USB disk unmounted. When the bits add up to 15, the kernel sends A→M **OS sleep state 0** `F0 04 00 16 00 00 cs` (ACK-tracked).
7. `com.nwd.ACTION_BACKGROUND_TASK_FINISH` while in state 0 makes the kernel send OS sleep state 2.
8. M→A `0/0x16 [0|2]` stops all UART writes. On `2`, the kernel broadcasts **`com.nwd.android.ACTION_REQUEST_SYSTEM_GOTO_SLEEP`** and the system suspends. If low voltage was reported first (0/0x19 `[0]`), it sends **`com.nwd.ACTION_DISABLE_AUTO_WAKEUP`** instead.
9. Optional timed auto-wake: **`com.nwd.android.ACTION_SYSTEM_AUTO_WAKEUP`** while in state 0 reopens the UART, sends the heartbeat and broadcasts `com.nwd.ACTION_TURNOFF_DEVICE_POWER`. G-sensor crash **`com.nwd.ACTION_OS_CRASH`** does the same and also sends key 117 to the MCU.

### ACC on (state 0/3 → 1)
1. M→A `0/0 [1, src]`. If the previous state was 0, the kernel broadcasts **`com.nwd.ACTION_TURNON_DEVICE_POWER`**, then again after 1 s.
2. It broadcasts **`ACTION_MCU_STATE_CHANGE`** with `extra_mcu_state = 1`, then **`com.android.action.ACTION_INIT_FINISH`**.
3. `accOn()` runs:
   - If `arm_auto_start_navi_when_acc_on` == 1, it launches the navigation app (`navigation_packagename` / `navigation_classname`, delayed by `key_reset_navi_wait_time`). Otherwise it resumes the kept source or the app marked `IsPowerOffMarkApp`.
   - If a background media source was kept, it broadcasts **`com.nwd.ACTION_BACKGROUND_MEDIA_PLAY`** (`extra_app_id` int).
   - It switches the backlight on (`com.nwd.action.ACTION_SET_BACKLIGHT_SWITCH` with `extra_backlight_switch=true`) and resets `arm_volume` to 0.
4. Key events are dropped while the MCU state is not 1, except keys 21, 22, 10, 0, 2, 14, 15, 12 (call, power, mute, volume, eject), which are still broadcast.

**For third-party apps:** register a receiver for `com.nwd.action.ACTION_MCU_STATE_CHANGE`. Read the extra with `intent.getByteExtra("extra_mcu_state", (byte)-16)`; **it is a byte, not an int**. Treat 1 as wake, 3 as ACC off (save state, pause), 0 as about to suspend, and 2 as soft-off. The other option is to observe the `mcu_state` key in `Settings.System`.

---

## 3. Settings.System keys

All of these are `Settings.System` keys written by `SettingTableKey.writeDataToTable` (putInt or putString). R means read, W means written. K = KernelService, S = SettingService.

### 3.1 State and runtime
| Key | Type | R/W | Meaning |
|---|---|---|---|
| `mcu_state` | int | K:W, S:R | MCU power state (0/1/2/3, -16 unknown) |
| `mcu_backcar_state` | int | K:W, S:R | Reverse 0/1 (from 0/5) |
| `mcu_hand_break` | int | K:W, S:R | Handbrake 0/1 |
| `mcu_light_state` | int | S:W | Headlight/ILL 0/1 |
| `mcu_no_source_device_state` | int | K:W, S:R | bit0 = handbrake, bit1 (value 2) = light |
| `mcu_device_state` | int | K:W | MCU device bitmask (§1.5 0/3) |
| `arm_device_state` | int | K:W | ARM devices: SD 0x8000000, USB 0x4000000, USB-iPod 0x2000000, camera 0x10000000 |
| `mcu_current_source` | int | K:W | Current MCU source id |
| `mcu_current_controller` / `mcu_current_touch` | int | K:W | 0 = ARM, 1 = MCU UI / touch |
| `mcu_video_signal` / `mcu_video_state` | int | K/S:W | Video signal present / MCU showing video |
| `is_allow_mcu_switch_video` | int | K:W | MCU may switch video (handbrake lock) |
| `mcu_mute_state` | int | K:W | Mute |
| `arm_volume` | int | K:W | Audio-mix bitmask sent in the heartbeat: bit0 = navi, 1 = BT, 2 = TTS, 3 = TPMS, 4 = ASR, 5 = 3G, 6 = CAN BT, 7 = OnStar, 8 = RDS, 9 = 4G |
| `nwd_arm_volume_type` | int | K:R | ARM volume type (1 = music, 2 = video, 4 = BT music) |
| `bt_phone_status` | int | K:W, S:R | 0 = idle, 1 = incoming, 2 = outgoing, 3 = online |
| `mcu_version`, `dvd_version`, `canbus_version`, `can_version`, `nwd_bt_version` | str | W | Versions |
| `mcu_aux_state` | int | K:W | AUX state |
| `mcu_auto_wakeup_default` | int | S:R | 1 = device supports auto-wakeup |
| `key_bt_state` | int | S:R | BT state; ≤2 lets CANCEL_CALL act as MUTE |
| `lock_screen_state`, `system_ui_visibility`, `show_toast_panel` | int | R/W | UI |

### 3.2 User settings
| Key | Meaning |
|---|---|
| `arm_auto_start_navi_when_acc_on` | 1 = start the navigation app at ACC on instead of restoring the last source (K:R) |
| `navigation_packagename` / `navigation_classname` / `navigation_appname` | Navigation app used by the NAVI key and auto-start |
| `gps_sync_time_switch` | GPS time sync |
| `mcu_back_light_day` / `mcu_back_light_night` (default 25) / `mcu_back_light_max` (=100) / `mcu_back_light_state` | Backlight levels and on/off (S) |
| `key_system_max_backlight` / `key_system_min_backlight` | OS brightness limits computed by `AbsBackLightManager` (S:W) |
| `mcu_System_setting_state` | Bit flags, see 3.3 |
| `arm_bee_switch` | Key beep (bit 13 of system state) |
| `arm_back_car_state`, `arm_hand_break_state` | Declared only; not used in these APKs **[uncertain]** |
| `mcu_steering_wheel_state` / `_ex` | Learned steering-wheel key bitmap (`StreeWheelState` bit indices) |
| `mcu_tricolor_red/green/blue`, `mcu_video_setting_*`, `mcu_radio_area_*`, `mcu_other_setting_*`, `mcu_amplifier_switch`, `mcu_smart_fan_switch`, `mcu_time_sync` | Mirrors of MCU settings |
| `mcu_*` audio keys (`mcu_system_volume`, `mcu_gps_volume`, `mcu_bt_volume`, `mcu_eq`, `mcu_bass`, `mcu_treble`, `mcu_balance`, `mcu_fader`, `mcu_gain_*`, `mcu_dsp_*`, `mcu_max_*`), `dsp_*` | Audio/DSP mirror |
| `os_setting_brake_check`, `os_setting_light_check`, `os_setting_key_bee` | Declared; OS-side copies **[uncertain]** |

### 3.3 `mcu_System_setting_state` bits (`SettingConstant.SystemSettingState`)
| Bit value | Name | | Bit value | Name |
|---|---|---|---|---|
| 0x0001 | DVR | | 0x0080 | AUTO_BACK_CAR |
| 0x0002 | iPod charging | | 0x0200 | BACKCAR_MIRROR |
| 0x0004 | autostart by any key | | 0x0800 | BACKCAR_VOLUME (mute or attenuate while reversing) |
| 0x0008 | default volume | | 0x1000 | SEND_FM |
| 0x0010 | autostart by BT | | 0x2000 | KEY_TONE (beep) |
| 0x0020 | BACK_CAR_START (wake on reverse) | | **0x4000** | **HAND_BRAKE check** |
| 0x0040 | GPS listener (mix) | | **0x8000** | **LIGHT check (ILL dimming)** |

### 3.4 Factory / backcar keys (`FactoryTableKey.FactorySettingTable` + `BackcarSettingUtil`)
| Key | Meaning |
|---|---|
| `backcar_can_control` | >0 = CAN app owns the trajectory lines; blocks track-switch changes (§1.7) |
| `factory_backcar_track_switch` | Dynamic trajectory lines on/off |
| `factory_backcar_assist_line_switch` / `_adjust` | Static guide lines |
| `factory_backcar_radar_switch` | Radar overlay |
| `factory_backcar_video_type` | Camera type; the real value is in `persist.sys.nwd.hacamera.ctrl` ∈ {none, pr1000, nvp6114, nvp6124, nvp-two, pr2000} |
| `backcar_delay_exit_time` | Seconds to stay on the camera after leaving reverse (default 5 from UartConfig) |
| `backcar_auto_switch_front` / `backcar_auto_switch_front_fun` | "1"/"0" strings: auto-switch to the front camera after reverse |
| `factory_can_number`, `factory_steerwheel_type`, `factory_button`, `factory_knob_button`, `factory_feature`, `factory_radio_rds_switch`, `atv_type`, `dtv_type`, `three_lamp_setting_enable`, `voice_analyze_enable/activate`, `ui_style_password`, `*_length` | Factory configuration |
| `nwd_gsensor_feature_enable`, `nwd_hardware_version`, `nwd_have_ipod`, `nwd_disable_report_imcomming_call_info`, `nwd_cvbs_out_type`, `nwd_voice_analyze_switch`, `can_back_car_video_switch`, `can_door_show_state`, `can_parking_radar_state`, `mcu_feature_data_0..7`, `mcu_language_data_1` | Misc |

## 3.5 Broadcast actions (custom)

Extras are listed as `name:type`.

#### Sent by these services (useful to listen for)
| Action | Extras | When |
|---|---|---|
| `com.nwd.action.ACTION_MCU_STATE_CHANGE` | `extra_mcu_state:byte` | MCU state changed (§2) |
| `com.nwd.action.ACTION_MCU_POWER_OFF` | — | ACC off processed |
| `com.android.action.ACTION_INIT_FINISH` | — | MCU init done / ACC on |
| `com.android.action.ACTION_BACKCAR_STATE_CHANGE` | `extra_back_state:byte` | Reverse 0/1 |
| `com.nwd.action.ACTION_HAND_BREAK_STATE_CHANGE` | `extra_hand_break_state:boolean` | Handbrake |
| `com.nwd.action.ACTION_HAND_BREAK_WARNNING` | `extar_warnning_tips`/`extra_warnning_tips:boolean` | Video blocked by the handbrake |
| `com.nwd.ACTION_ILL_STATE_CHANGE` | `extra_ill_state:byte` | Headlights |
| `com.nwd.action.ACTION_NO_SOURCE_DEVICE_CHANGE` | — | Handbrake/light bitmask changed |
| `com.nwd.action.ACTION_KEY_VALUE` | `extra_key_value:byte`, `extra_key_type:byte` | MCU key that is not bound to a source (steering NEXT/PREV/PLAY/VOL/…) |
| `com.nwd.action.ACTION_CHANGE_SOURCE` | `extra_source_id:byte` | MCU source switched |
| `com.nwd.action.ACTION_DEVICE_CHANGE` / `ACTION_ARM_DEVICE_CHANGE` | — | Device inserted or removed |
| `com.nwd.ACTION_MUTE_STATE_CHANGE` | `extra_mute_state:byte` | Mute |
| `com.action.ACTION_SYNC_MCU_TIME` | `extra_mcu_time:long`, `extra_time_format:boolean` | MCU RTC differs by more than 59 s |
| `com.nwd.action.ACTION_VOLUME_STATE_CHANGE` | — | `arm_volume` changed |
| `com.nwd.ACTION_TURNON_DEVICE_POWER` / `ACTION_TURNOFF_DEVICE_POWER` | — | Wake / auto-wake peripherals |
| `com.nwd.android.ACTION_REQUEST_SYSTEM_GOTO_SLEEP` | — | MCU agreed to sleep |
| `com.nwd.ACTION_DISABLE_AUTO_WAKEUP` | — | Low voltage |
| `com.nwd.ACTION_BACKGROUND_MEDIA_PLAY` | `extra_app_id:int` | Resume background media after ACC on |
| `com.nwd.action.ACTION_BEEP_SWITCH_CHANGE`, `com.nwd.action.ACTION_BACKLIGHT_SWITCH_CHANGE`, `com.nwd.ACTION_OS_BACK_LIGHT_STATE_CHANGE` (`extra_light_state`) | | Settings changed |
| `com.nwd.action.ACTION_GET_EEPROM_DATA` | `extra_eeprom_data:byte[]` | EEPROM block |
| `com.nwd.action.ACTION_STOP_APP` | `extra_package_name` | SourceMgr asks for an app to be stopped |
| `com.nwd.ACTION_REQUEST_START_ACTIVITY` | `extra_package_name`, `extra_class_name` | Indirect activity start |
| `com.nwd.action.ACTION_SET_SYSTEM_PROP` | `extra_prop_name`, `extra_prop_value` | Request to set a system property (the handler is outside these APKs) **[uncertain]** |
| `com.android.action_update_backcar_setup` | — | Backcar settings changed |
| `com.nwd.ACTION_AUTO_WAKEUP_CHANGE`, `com.nwd.ACTION_MCU_UPDATE_*`, `com.nwd.action.ACTION_CAN_BATTERY_VOLTAGE` (`extra_voltage_value`) | | Misc |
| `android.intent.action.MEDIA_BUTTON` (ordered, KEYCODE 86 STOP down/up) | | Sent when switching from Android to an MCU source |

#### Received (the services act on these; dynamic receivers with **no permission**, so any app can send them)
| Action | Extras | Effect |
|---|---|---|
| `com.nwd.ACTION_SET_ARM_VOLUME_STATE` | `extra_volume_type:int` (bit index 0..9), `extra_volume_state:int` 0/1 | Sets a bit in `arm_volume` → heartbeat → MCU mixes/ducks ARM audio (for example navi prompt = type 0) |
| `com.nwd.action.ACTION_SET_BACKLIGHT_SWITCH` | `extra_backlight_switch:boolean` | LCD on/off |
| `com.nwd.action.ACTION_SET_BRIGHTNESS` | `extra_brightness:int` | Brightness |
| `com.nwd.action.ACTION_BEEP` | — | MCU beep |
| `com.nwd.action.ACTION_SET_MUTE` | `extra_mute:boolean` | Mute |
| `com.nwd.action.ACTION_REQUEST_SET_SYSTEM_VOLUME` | `extra_volume:int` | MCU master volume |
| `com.nwd.ACTION_REQUEST_SHORT_MUTE` | `extra_short_mute_time:int` | Short mute |
| `com.nwd.action.ACTION_KEY_VALUE` | `extra_key_value:byte` | Fed back: key 0/12 is resent to the MCU (power/eject); SettingService volume handling |
| `com.nwd.action.ACTION_OS_KEY_VALUE` | `extra_key_value:byte` | OS key → MCU key via `/config/app/Os2McuKeyConfig.xml`, then processed like a panel key |
| `com.nwd.action.ACTION_REQUEST_CHANGE_SOURCE` | `extra_source_id:byte` | Direct source change |
| `com.nwd.action.ACTION_REQUEST_GOTO_CURRENT_SOURCE` | — | Return to the current source |
| `com.nwd.action.ACTION_APP_IN_OUT` | `extra_app_id`, `extra_app_operation` (1 in / 2 out), `extra_app_event` | NWD app tells SourceMgr it entered or left |
| `com.nwd.action.ACTION_START_ACTIVITY` | `extra_package_name` | App started (navi detection) |
| `com.nwd.ACTION_SET_CVBS_STATE` | `extra_cvbs_type`, `extra_cvbsout_state` | CVBS out |
| `com.nwd.action.ACTION_SET_MCU_CAN_TRANSLATE_VIDEO` | `extra_can_translate_video:boolean` | Allow MCU video |
| `com.nwd.ACTION_ADD_KEY_EXTRA_APPIDS` | `extra_key:byte`, `extra_sourceitems:ArrayList<SourceItem>` | Add apps to a key's cycle (needs the NWD `SourceItem` Parcelable) |
| `com.nwd.ACTION_FRAMEWORK_CLEAN_SUCCESS`, `ACTION_BACKGROUND_TASK_FINISH`, `com.nwd.android.ACTION_SYSTEM_AUTO_WAKEUP`, `com.nwd.ACTION_OS_CRASH`, `com.nwd.ACTION_OS_SLEEP`/`ACTION_OS_WAKE_UP` (deprecated, send OS sleep state 0/1), `com.nwd.android.ACTION_OS_VOLUME_STATE_CHANGE` (`extra_volume_state`) | | Sleep-flow internals; **do not send** |
| `com.nwd.action.ACTION_FORCED_CALIBRATION_RESOLUTION` (`extra_resolution_mode`), `com.nwd.action.ACTION_QUERY_CAN_BATTERY_VOLTAGE`, `com.nwd.ACTION_CHANGE_VIDEO_CHANNEL` | | Misc |

---

## 4. Factory, hidden and debug switches

- **No numeric factory or hidden menu code is stored in these two APKs.** The known code `16176699` is not present; it must live in the launcher or in `com.android.car.setting`. The only password-like key is `Settings.System ui_style_password` (int, read and written by `FactorySettingUtil.get/setUIStylePassword`), which probably locks UI-style selection. **[uncertain]**
- **Debug or engineering hooks [confirmed]** (`KernelService.mTestBroadcastReceiver`, `MCUDeviceManager`). They are unprotected dynamic receivers:
  - `com.nwd.action.EMU_RECEIVER_MCU_PROTOCAL` + `protocal:byte[]`: **injects a fake MCU→Android frame** into `distribution()`. It goes through the same path as UART data. The ROM uses this itself: `KernelUtils` (line ~168) builds a 1/7 pop-source frame and sends it this way.
  - `com.nwd.action.EMU_SEND_DATA_TO_MCU` + `protocal:byte[]`: writes raw bytes to the MCU UART **without adding a checksum**.
  - `com.nwd.test.ACTION_EMU_NOT_COMMUNICAT_WITH_MCU`: stops all UART writes. **Dangerous**: the heartbeat stops, and the MCU watchdog behaviour is unknown.
  - `com.nwd.ACTION_EMU_NO_COMMUNICATION`: stops writes from resuming after a state frame. **Dangerous.**
  - `am broadcast` cannot pass a `byte[]` extra, so a small helper app is needed to use the first two hooks.
- **Binder API**: `KernelService` is exported (intent filter `com.nwd.kernel.service.KernelService`, no permission). AIDL `com.nwd.kernel.aidl.IKernelFeature`:
  - `request(byte[])` (txn 1): write a frame. The checksum is filled in automatically by `ProtocalUtil.writeDataToMCU`.
  - `registCallback(IKernelCallback)` (txn 2) and `unRegistCallback` (txn 3).
  - The callback `com.nwd.kernel.aidl.IKernelCallback` has `response(byte[])` (1), `responseSourceState(boolean)` (2) and `getCanResponseType()` → `byte[]` of the TYPEs you want (3).
  - Any app can bind it and sniff or send MCU frames. This is the cleanest way to integrate a third-party app.
- `/config/app/` engineering files: `UartConfig.ini`, `KeyRuleConfig.xml` (key → app rules; a default copy is in the APK assets), `Os2McuKeyConfig.xml`, `AppConfig.xml` (`<RestrictApp Package=…>`, used when `sys.nwd.restrict.startactivity=1`), `/mnt/sdcard/ScreenMode.ini` (forced resolution).
- Both manifests have `android:debuggable="true"`. The framework also has `com.nwd.action.ACTION_PRINT_LOG_ENABLE`, `com.nwd.ACTION_AUTO_GET_DEBUG_LOG` and `com.nwd.ACTION_STOP_GET_DEBUG_LOG` (strings in `services.odex`; semantics **[uncertain]**).

---

## 5. Low-risk tweaks (each with undo)

Record the original value first: `settings get system <key>`.

### 5.1 Reverse detection
- **Read the state, do not change it.** Third-party camera or dashcam apps should listen for `com.android.action.ACTION_BACKCAR_STATE_CHANGE` (`getByteExtra("extra_back_state",0)`) or watch `content://settings/system/mcu_backcar_state` with a ContentObserver. The ContentObserver path also works while the broadcast is suppressed in states 3/0. No undo is needed.
- **Diagnose the source** (wire or CAN): watch `logcat -s nwdapp_DeviceState nwdapp_KernelService` while shifting into reverse. `new backcar = 1` means the MCU reports reverse. If it does not toggle with the reverse wire unplugged, the MCU uses the wire only. No change is made.
- **Exit delay:** `settings put system backcar_delay_exit_time 2` shortens how long the camera stays up after reverse. This assumes the backcar app reads the key, which is **[uncertain]**. Undo: put back the old value (default 5), or `settings delete system backcar_delay_exit_time` to fall back to UartConfig.
- **`backcar_can_control`:** change it only if you want the factory menu to control trajectory lines again (`settings put system backcar_can_control 0`). It does **not** fix reverse detection. Undo: restore the recorded value.
- System-state bit 0x0800 (backcar volume) and 0x0020 (wake on reverse) can be changed in the NWD settings UI, which sends 0x0E/0x01. Toggle them back in the same UI to undo.

### 5.2 Keeping apps alive across ACC off/on
- The app kill at ACC off is done by the **framework** (`NwdManagerService`, `com.nwd.app.NwdTaskWhitelist`, reported as clean bit 4). It is not done by these APKs, and changing it needs framework work (separate analysis).
- Low-risk option: `settings put system arm_auto_start_navi_when_acc_on 1`, with `navigation_packagename` / `navigation_classname` pointing at your app. Undo: `settings put system arm_auto_start_navi_when_acc_on 0` and restore the navigation keys.
- In your own app: restart work on `ACTION_MCU_STATE_CHANGE` = 1 or on `com.android.action.ACTION_INIT_FINISH`, and save state on 3. Use a foreground service. Undo: uninstall the app.

### 5.3 Steering keys for third-party media apps
- Steering-wheel keys reach Android as `com.nwd.action.ACTION_KEY_VALUE` (`extra_key_value` byte: 5 = NEXT, 6 = PREV, 7 = PLAY, 2 = MUTE, 14/15 = VOL, 21/22 = call, 23 = MODE, 89 = SPEECH). This only applies when the key is not bound in `KeyRuleConfig.xml`. No code in these APKs converts them to `MEDIA_BUTTON`; stock NWD apps listen for the broadcast directly. **[inferred]**
- **Bridge app (recommended, fully reversible):** receive `ACTION_KEY_VALUE`, then call `AudioManager.dispatchMediaKeyEvent()` with `KEYCODE_MEDIA_NEXT` (87), `PREVIOUS` (88) or `PLAY_PAUSE` (85), sending a down and an up event. You can skip this while an MCU source (radio/AUX) is active by checking `mcu_current_source` != 0. Undo: uninstall the bridge app.
- To remap MODE (23) or another key, edit `/config/app/KeyRuleConfig.xml` (StartMode 0 = single, 1 = iterate, 2 = loop; ExitMode 0 = launcher, 1 = last source, -2 = pass the key through). Changes take effect after a reboot. **Medium risk** because it is a system partition file. Back it up first; undo by restoring the backup.
- Steering-wheel learning (resistive SWC) uses 0x0E/0x01 functionType 4/5/6..59. Use the NWD UI for this, not raw frames.

### Do NOT
- Do not send `EMU_NOT_COMMUNICAT_WITH_MCU` or `ACTION_EMU_NO_COMMUNICATION`.
- Do not inject frames for 0/0x00, 0/0x16 or 1/0x08 (state, sleep, factory reset/reboot).
- Do not write MCU-upgrade (0x7A) or factory (0x7B) frames.

---

## 6. What this means for DaciaClock v17

- **Reverse is not a system property in these services.** They publish it as `Settings.System mcu_backcar_state` (int, 0/1) and as the broadcast `com.android.action.ACTION_BACKCAR_STATE_CHANGE` (`extra_back_state` byte). The property list in v16 (`persist.sys.reverse`, `sys.backcar`, …) does not appear anywhere in these APKs. It may still exist in the framework, so check with `scripts/reverse-props.sh`. Suggested v17 check: `Settings.System.getInt(cr, "mcu_backcar_state", 0) == 1`, OR the broadcast, OR the prop.
- **ACC-on trigger:** `com.nwd.action.ACTION_MCU_STATE_CHANGE` with `extra_mcu_state` (byte) == 1. It is a runtime receiver, so the service must be alive; also read `Settings.System mcu_state`. Before relying on it, verify that it reaches a static manifest receiver of a third-party app after a force-stop **[uncertain]**.
- Skip the idle launch when `mcu_state` != 1. During ACC off/soft-off it is 3, 0 or 2.

## Open questions
1. Whether the MCU takes reverse from the wire or from the Xinpo CAN box. This needs MCU firmware or `CanAllInOne.apk` analysis, or a wire-unplug test.
2. Why the heartbeat appears at 1 s rather than 3 s.
3. Which component handles `ACTION_SET_SYSTEM_PROP` and `ACTION_REQUEST_SYSTEM_GOTO_SLEEP` (`framework.jar` / `NwdHolder`?).
4. The meaning of reserved byte 4 (always 0 in all code seen).
5. The `NwdTaskWhitelist` source and configuration (framework.jar), which controls the ACC-off app kill.
