#!/usr/bin/env bash
# Set the idle delay (seconds) WITHOUT rebuilding: writes /sdcard/daciaclock.txt and restarts the app.
# Usage: ./scripts/set-idle.sh <HEAD_UNIT_IP> [seconds=60]
source "$(dirname "$0")/common.sh"
SECS="${2:-60}"
hu_connect
A shell "printf 'idle=%s\nboot=20\nlog=1\n' $SECS > /sdcard/daciaclock.txt"
echo "written:"; A shell cat /sdcard/daciaclock.txt
# Settings are read once per process -> restart. force-stop sets 'stopped', am start clears it.
A shell am force-stop "$PKG"
A shell am start -n "$PKG/.ClockActivity" >/dev/null
sleep 3
A logcat -d -s DaciaClock:* | grep -E "lu :|service demarre|SONDE" | tail -3
echo "Done. Press HOME, leave the screen untouched ~${SECS}+15 s: the clock should open."
