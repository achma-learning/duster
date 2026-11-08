# Start here

## 1. Once on the laptop (Fedora)
```bash
sudo dnf install android-tools unzip
unzip duster-headunit-kit.zip -d ~ && cd ~/duster-headunit
chmod +x scripts/*.sh
[ -d duster ] || git clone https://github.com/achma-learning/duster   # only for the light zip
curl -fsSL https://claude.ai/install.sh | bash      # Claude Code
```

## 2. In the car
- Phone hotspot ON → head unit + laptop both on it. Ignition ON.
- Head unit IP: Settings → Wi-Fi → network details (or hotspot device list).
- Developer options → USB debugging ON. Check: `adb connect <IP>:5555`
  - refused → factory menu `16176699` (look for ADB), or a terminal app on the unit:
    `su` then `setprop persist.adb.tcp.port 5555; stop adbd; start adbd`
  - `unauthorized` → accept the prompt on the screen ("Always allow").

## 3. Quick fix you can try by hand (no rebuild)
```bash
./scripts/diag.sh <IP>          # look at the summary
./scripts/set-idle.sh <IP> 60   # 1-minute idle
```
Also: Car settings → General → **Whitelist** → add "Dacia Clock" (auto-start).

## 4. Hand over to Claude Code
```bash
cd ~/duster-headunit && claude
```
First prompt to paste:

> Head unit IP is <IP>. Read CLAUDE.md. Run scripts/diag.sh and tell me why DaciaClock
> doesn't auto-launch. Then set idle to 60 s, guide me through acc-cycle.sh and
> reverse-props.sh, and tell me whether a v17 rebuild is needed. Ask before any change on the unit.
