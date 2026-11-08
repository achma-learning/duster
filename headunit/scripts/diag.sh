#!/usr/bin/env bash
# Read-only snapshot of the head unit + DaciaClock state. Changes nothing.
# Usage: ./scripts/diag.sh <HEAD_UNIT_IP>
source "$(dirname "$0")/common.sh"
hu_connect
OUT="$ROOT/diag/$(date +%Y%m%d-%H%M%S)"; mkdir -p "$OUT"

A shell getprop                               > "$OUT/getprop.txt"
A shell cat /proc/uptime                      > "$OUT/uptime.txt"
A shell dumpsys package "$PKG"                > "$OUT/package.txt" 2>&1
A shell dumpsys activity services "$PKG"      > "$OUT/services.txt" 2>&1
A shell dumpsys alarm                         > "$OUT/alarm-all.txt" 2>&1
A shell appops get "$PKG"                     > "$OUT/appops.txt" 2>&1
A shell cat /sdcard/daciaclock.txt            > "$OUT/daciaclock.txt" 2>&1
A shell ps                                    > "$OUT/ps.txt" 2>&1
A shell pm list packages -3                   > "$OUT/packages-3rdparty.txt"
A shell dumpsys activity activities           > "$OUT/activities.txt" 2>&1
A shell settings list global                  > "$OUT/settings-global.txt" 2>&1
A shell settings list system                  > "$OUT/settings-system.txt" 2>&1
A shell settings list secure                  > "$OUT/settings-secure.txt" 2>&1
A logcat -d -v time -s DaciaClock:*           > "$OUT/logcat-daciaclock.txt" 2>&1
A logcat -d -v time -b main -b system -b events > "$OUT/logcat-all.txt" 2>&1

echo "== summary =================================="
grep -m1 versionName "$OUT/package.txt"
grep -o 'stopped=[a-z]*' "$OUT/package.txt" | head -1
grep -c "$PKG" "$OUT/alarm-all.txt" | sed 's/^/alarms mentioning app: /'
grep -q "$PKG" "$OUT/ps.txt" && echo "process: RUNNING" || echo "process: NOT running"
grep -q InactivityService "$OUT/services.txt" && echo "InactivityService: RUNNING" || echo "InactivityService: NOT running"
grep -i SYSTEM_ALERT "$OUT/appops.txt" || echo "appops SYSTEM_ALERT_WINDOW: (default/allowed)"
echo "config: $(tr '\n' ' ' < "$OUT/daciaclock.txt")"
echo "uptime: $(cat "$OUT/uptime.txt")"
echo "reverse-candidate props:"; grep -iE 'reverse|backcar|back_car|camera|acc' "$OUT/getprop.txt" | head -20
echo "last DaciaClock log lines:"; tail -15 "$OUT/logcat-daciaclock.txt"
echo "== saved in $OUT"
