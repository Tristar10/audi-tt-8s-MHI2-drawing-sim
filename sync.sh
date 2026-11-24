#!/bin/sh
# Copies the head unit's layout code, the cockpit photos and the test sender from an altscreen
# checkout (branch telemetry, the source of truth) into this repo. Usage: sh sync.sh [altscreen port dir]
set -e
cd "$(dirname "$0")"
ALT=${1:-../altscreen_telemetry/ports/tt-8s-mu1462}
for f in TeleLayout TeleIcons TeleStrip TeleLink TeleGlide TeleUi; do
    cp "$ALT/hmi/src/local/tt/$f.java" src/local/tt/
done
cp "$ALT/docs/photos/cockpit-large-view.jpg" "$ALT/docs/photos/cockpit-small-view.jpg" photos/
cp "$ALT/gauges/scripts/gauge-send.py" tools/
echo "synced from $ALT ($(git -C "$ALT" rev-parse --short HEAD 2>/dev/null || echo '?'))"
