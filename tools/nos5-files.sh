#!/system/bin/sh
# Nothing2Something: collects the files needed to support a new Nothing OS build.
# Only system apps and Android's framework, the same on every phone with that build:
# no logs, no accounts, no settings, no personal data. Needs root.
#
# Run:  su -c "sh /sdcard/Download/nos5-files.sh"
# Then send:  Download/nos5-files.tar.gz

O=/sdcard/Download/nos5-files
rm -rf $O; mkdir -p $O
# Build info only (model, Nothing OS and Android version, two flags); no serial or IDs.
getprop | grep -E '^\[(ro\.build\.|ro\.product\.(model|brand|device|name)|ro\.system\.build\.version|ro\.nothing|ro\.apk_verity\.mode|debug\.hwui\.renderer)' > $O/props.txt
# The apps the module hooks (base APK and its splits).
for p in com.nothing.launcher com.android.systemui com.android.settings com.android.settings.intelligence com.nothing.ntgamedashboard; do
  mkdir -p $O/$p
  for f in $(pm path $p 2>/dev/null | sed 's/package://'); do cp "$f" $O/$p/; done
done
# The system's own code (system_server and the framework), with its compiled copies if any.
mkdir -p $O/framework
cp /system/framework/services.jar /system/framework/framework.jar /system/framework/framework-res.apk $O/framework/ 2>/dev/null
cp /system/framework/oat/arm64/services.* $O/framework/ 2>/dev/null
# The emoji font (for the dot-matrix covers).
cp /system/fonts/NotoColorEmoji*.ttf $O/ 2>/dev/null
cd /sdcard/Download && tar -czf nos5-files.tar.gz nos5-files && rm -rf nos5-files
echo "Done: Download/nos5-files.tar.gz"
