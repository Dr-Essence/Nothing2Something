#!/bin/bash
# Run by .github/workflows/firmware-inspect.yml on the bundles it downloaded ($1/<bundle>/...).
# Now: tools/hookcheck.py on the apps the session cannot download (Settings, Settings search,
# Gaming), Nothing OS 4.1 (CMF Phone 1) against Nothing OS 5.0 (Phone (3a)).
set -e
FW=$1
OLD=$(ls -d "$FW"/Tetris_B4.1*)
NEW=$(ls -d "$FW"/Asteroids_C5.0*)
apks() { find "$1" -name '*.apk' | grep -E 'com.android.settings/|settings.intelligence|ntgamedashboard' ; find "$1" -name framework.jar -o -name services.jar | grep -v nos ; }
python3 tools/hookcheck.py index old.json $(apks "$OLD")
python3 tools/hookcheck.py index new.json $(apks "$NEW")
python3 tools/hookcheck.py refs refs.json app/src/main/java
python3 tools/hookcheck.py check refs.json old.json new.json | grep -vE 'nlplus|LivingWorlds|HeliBoard'
