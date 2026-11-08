#!/usr/bin/env bash
# Answers: does an ignition (ACC) cycle REBOOT the unit or just SLEEP it?
# And which broadcast/property signals "ACC on"?  Read-only.
# Usage: ./scripts/acc-cycle.sh <HEAD_UNIT_IP>
source "$(dirname "$0")/common.sh"
hu_connect
OUT="$ROOT/diag/acc-$(date +%Y%m%d-%H%M%S)"; mkdir -p "$OUT"
A shell cat /proc/uptime > "$OUT/uptime-before.txt"
A shell getprop > "$OUT/getprop-before.txt"
A shell "ps | grep $PKG" > "$OUT/ps-before.txt"
A logcat -c
echo ">>> Now: switch ignition OFF, wait 15 s, switch it back ON. Waiting for the unit to come back..."
sleep 20
until adb connect "$DEV" 2>/dev/null | grep -q connected && A shell true 2>/dev/null; do sleep 3; printf .; done
echo; sleep 15
A shell cat /proc/uptime > "$OUT/uptime-after.txt"
A shell getprop > "$OUT/getprop-after.txt"
A shell "ps | grep $PKG" > "$OUT/ps-after.txt"
A logcat -d -v time -b main -b system -b events > "$OUT/logcat.txt"
b=$(cut -d' ' -f1 "$OUT/uptime-before.txt" | cut -d. -f1); a=$(cut -d' ' -f1 "$OUT/uptime-after.txt" | cut -d. -f1)
echo "uptime before=${b}s after=${a}s"
if [ "$a" -gt "$b" ]; then echo "=> SLEEP/RESUME (no reboot): BOOT_COMPLETED never fires on ignition."; else echo "=> REAL REBOOT."; fi
[ -s "$OUT/ps-after.txt" ] && echo "app process survived" || echo "app process was KILLED during the cycle"
echo "--- changed properties:"; diff "$OUT/getprop-before.txt" "$OUT/getprop-after.txt" | grep '^[<>]' | head -40
echo "--- interesting broadcasts/log lines:"
grep -iE 'acc|wake|resume|BOOT_COMPLETED|SCREEN_ON|USER_PRESENT|force.?stop|kill|DaciaClock|broadcast.*intent' "$OUT/logcat.txt" | grep -v ' D/' | head -60
echo "== saved in $OUT"
