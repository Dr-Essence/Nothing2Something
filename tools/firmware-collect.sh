#!/bin/bash
# Unpacks one nothing_archive build and keeps the files the module hooks, for
# .github/workflows/firmware.yml and hookcheck-all.yml (moved here from firmware.yml, 0.70.55,
# so both use the same steps).
#
#   tools/firmware-collect.sh TAG OUT_DIR
#
# Needs: gh (GH_TOKEN), 7z, erofs-utils/erofsfuse, sudo, $AAPT2, $PACKAGES (the packages to
# keep). Downloads the build's logical partition images from github.com/$ARCHIVE, mounts
# system, system_ext and product read-only, and keeps in OUT_DIR/TAG/<partition>/:
#   - the hooked apps (their whole folder, splits and oat files with them);
#   - framework.jar, services.jar, framework-res.apk;
#   - the emoji font, fonts.xml and font_fallback.xml, and build.prop;
# plus OUT_DIR/TAG/index.txt: every app of the build with its package and version.
set -euo pipefail
T=$1
OUT="$2/$T"
WORK=$(mktemp -d)
mkdir -p "$OUT" "$WORK/mnt" "$WORK/dl"
echo "# $T" > "$OUT/index.txt"
(cd "$WORK/dl" && gh release download "$T" -R "$ARCHIVE" -p '*image-logical*' -p '*hash*')
first=$(ls "$WORK"/dl/*image-logical.7z* | sort | head -1)
7z l -ba "$first" | awk '{print $NF}' > "$WORK/images.txt"
for part in system system_ext product; do
  img=$(grep -E "(^|/)$part\.img$" "$WORK/images.txt" | head -1 || true)
  if [ -z "$img" ]; then echo "no $part.img in $T"; continue; fi
  7z x -y -o"$WORK/img" "$first" "$img" > /dev/null
  # Android images are EROFS (newer builds) or ext4: mounted read-only either way; EROFS falls
  # back to erofsfuse if the runner's kernel lacks it.
  sudo mount -o loop,ro "$WORK/img/$img" "$WORK/mnt" 2>/dev/null \
    || sudo erofsfuse "$WORK/img/$img" "$WORK/mnt"
  # system.img holds the root with system/ inside it; the others are their own root.
  root="$WORK/mnt"; [ -d "$WORK/mnt/system" ] && root="$WORK/mnt/system"
  mkdir -p "$OUT/$part"
  for f in build.prop etc/fonts.xml etc/font_fallback.xml framework/framework.jar \
      framework/services.jar framework/framework-res.apk; do
    if [ -f "$root/$f" ]; then sudo cp "$root/$f" "$OUT/$part/"; fi
  done
  if [ -d "$root/fonts" ]; then
    sudo find "$root/fonts" -name 'NotoColorEmoji*' -exec cp {} "$OUT/$part/" \;
  fi
  sudo find "$root" -name '*.apk' \( -path '*/app/*' -o -path '*/priv-app/*' \) | while read -r apk; do
    info=$(sudo "$AAPT2" dump badging "$apk" 2>/dev/null | head -1 || true)
    pkg=$(echo "$info" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")
    ver=$(echo "$info" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")
    echo "$part ${apk#$root/} $pkg $ver" >> "$OUT/index.txt"
    for want in $PACKAGES; do
      if [ "$pkg" = "$want" ]; then sudo cp -r "$(dirname "$apk")" "$OUT/$part/$pkg"; fi
    done
  done
  sudo umount "$WORK/mnt" || sudo fusermount -u "$WORK/mnt"
  rm -rf "$WORK/img"
done
sudo chown -R "$USER" "$OUT"
sudo rm -rf "$WORK"
