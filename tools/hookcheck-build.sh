#!/bin/bash
# Checks the module's hooks on one nothing_archive build, for hookcheck-all.yml.
#
#   tools/hookcheck-build.sh TAG REFS.json BASELINE_DIR REPORTS_DIR [INDEX_DIR]
#
# Unpacks the build (firmware-collect.sh), makes one index per hooked app and one for
# Android's framework ("android": framework.jar, services.jar, framework-res.apk), writes
# REPORTS_DIR/TAG.json (hookcheck.py report) and deletes the build's files again, so a runner
# can check many builds one after another. A build that cannot be unpacked gets a report
# with its error instead, and the next build goes on. With INDEX_DIR the indexes are kept
# there (the baseline's).
set -uo pipefail
T=$1; REFS=$2; BASE=$3; REPORTS=$4; KEEP=${5:-}
WORK=$(mktemp -d)
mkdir -p "$REPORTS"
fail() {
  python3 -c 'import json,sys; json.dump({"tag": sys.argv[1], "error": sys.argv[2]}, open(sys.argv[3], "w"))' \
    "$T" "$1" "$REPORTS/$T.json"
  echo "$T: $1"
  rm -rf "$WORK"
  exit 0
}
bash tools/firmware-collect.sh "$T" "$WORK/fw" > "$WORK/collect.log" 2>&1 \
  || fail "could not be unpacked: $(tail -1 "$WORK/collect.log")"
B="$WORK/fw/$T"
IDX="$WORK/idx"; mkdir -p "$IDX"
for pkg in $PACKAGES; do
  apks=$(find "$B" -path "*/$pkg/*" -name '*.apk' 2>/dev/null)
  if [ -n "$apks" ]; then python3 tools/hookcheck.py index "$IDX/$pkg.json" $apks; fi
done
fwk=$(find "$B" \( -name framework.jar -o -name services.jar -o -name framework-res.apk \) 2>/dev/null)
[ -n "$fwk" ] || fail "no framework.jar or services.jar in the build"
python3 tools/hookcheck.py index "$IDX/android.json" $fwk
python3 tools/hookcheck.py report "$REFS" "${BASE:-$IDX}" "$IDX" "$T" "$REPORTS/$T.json" \
  || fail "the check itself failed"
if [ -n "$KEEP" ]; then mkdir -p "$KEEP" && cp "$IDX"/*.json "$KEEP/"; fi
rm -rf "$WORK"
