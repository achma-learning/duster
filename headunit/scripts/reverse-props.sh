#!/usr/bin/env bash
# Finds the system property the MCU sets in reverse gear. Read-only.
# Car PARKED, handbrake on, engine running or ignition on. Clutch in, select R, do not release.
# Usage: ./scripts/reverse-props.sh <HEAD_UNIT_IP>
source "$(dirname "$0")/common.sh"
hu_connect
OUT="$ROOT/diag/reverse-$(date +%Y%m%d-%H%M%S)"; mkdir -p "$OUT"
A shell getprop > "$OUT/before.txt"
read -rp ">>> Engage REVERSE now (camera visible), then press Enter... " _
A shell getprop > "$OUT/during.txt"
read -rp ">>> Back to NEUTRAL, then press Enter... " _
A shell getprop > "$OUT/after.txt"
echo "--- props that changed when reverse engaged:"
diff "$OUT/before.txt" "$OUT/during.txt" | grep '^[<>]'
echo "--- and back:"
diff "$OUT/during.txt" "$OUT/after.txt" | grep '^[<>]'
echo "== saved in $OUT  (the property that flips and flips back is the one for Reverse.java)"
