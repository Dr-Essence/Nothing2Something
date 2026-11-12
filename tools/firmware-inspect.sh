#!/bin/bash
# Prints, for each bundle in $1, what one question about the firmware needs (run by
# .github/workflows/firmware-inspect.yml). Now: which views of Nothing's widget cards are frosted
# in Nothing OS 5 (NTFrosted* layouts) and what the same layouts are in Nothing OS 4.1.
FW=$1
for bundle in "$FW"/*/; do
  name=$(basename "$bundle")
  echo "################ $name"
  for apk in $(find "$bundle" -name '*.apk' | grep -E 'hearthstone|cardservice|weather|aiwidget|launcher'); do
    echo "======== ${apk#$bundle}"
    # Files in the APK that mention Nothing's frosted views or contract.
    for entry in $(unzip -Z1 "$apk" | grep -E '^(res/|assets/)'); do
      if unzip -p "$apk" "$entry" 2>/dev/null | grep -aq -E 'NTFrosted|IFrostedBackdrop|frosted'; then
        echo "-- $entry"
        case "$entry" in
          res/*.xml) "$AAPT2" dump xmltree --file "$entry" "$apk" 2>/dev/null | grep -E 'E: |:id\(|background|tag' | head -40 ;;
          *) unzip -p "$apk" "$entry" | grep -ao -E '.{0,80}(NTFrosted|frosted).{0,80}' | head -10 ;;
        esac
      fi
    done
    # Resource names of those files, to compare across versions.
    "$AAPT2" dump resources "$apk" 2>/dev/null | grep -iE 'frosted|lucent' | grep resource | head -40
  done
done
