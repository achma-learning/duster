#!/usr/bin/env bash
# Shared helpers. Head-unit IP: first arg, else $HU_IP, else file .hu-ip at repo root.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PKG=ma.aymane.daciaclock
HU_IP="${1:-${HU_IP:-$(cat "$ROOT/.hu-ip" 2>/dev/null)}}"
if [ -z "$HU_IP" ]; then
  echo "Usage: $0 <HEAD_UNIT_IP>   (or: echo 192.168.x.x > .hu-ip)" >&2; exit 1
fi
echo "$HU_IP" > "$ROOT/.hu-ip"
DEV="$HU_IP:5555"
A() { adb -s "$DEV" "$@"; }

hu_connect() {
  command -v adb >/dev/null || { echo "adb missing: sudo dnf install android-tools" >&2; exit 1; }
  adb connect "$DEV" >/dev/null 2>&1
  local st; st=$(adb devices | awk -v d="$DEV" '$1==d{print $2}')
  case "$st" in
    device) echo "[ok] connected to $DEV";;
    unauthorized) echo "[!] accept the 'Allow USB debugging' prompt on the head unit, then re-run" >&2; exit 2;;
    *) echo "[!] cannot reach $DEV (state: ${st:-none}). Same Wi-Fi? ADB TCP enabled? ignition on?" >&2; exit 2;;
  esac
}
