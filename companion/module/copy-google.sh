#!/system/bin/sh
# Sourced by the installer so abort stops installation before an incomplete .replace mount.
PACKAGE=com.google.android.googlequicksearchbox
APP_DIR="$MODPATH/system/priv-app/GoogleQuickSearchBox"
apks=$(pm path "$PACKAGE" 2>/dev/null) || abort "Cannot query installed Google APKs"
[ -n "$apks" ] || abort "Google app is not installed"
mkdir -p "$APP_DIR" || abort "Cannot prepare Google system app directory"
find "$APP_DIR" -maxdepth 1 -type f -name '*.apk' -delete || abort "Cannot clear old Google APKs"
while IFS= read -r line; do
    case "$line" in
        package:*) apk=${line#package:} ;;
        *) abort "Unexpected Google APK path response" ;;
    esac
    [ -s "$apk" ] || abort "Google APK is missing or empty: $apk"
    cp -f "$apk" "$APP_DIR/${apk##*/}" || abort "Cannot copy Google APK: $apk"
done <<EOF
$apks
EOF
# NoMount registers .replace as a directory whiteout before the file rules,
# and its driver then rejects every rule below that directory: the module
# would vanish from /system/priv-app entirely. Keep merge semantics while the
# NoMount driver is active; other mount backends keep replace-directory
# behavior via the .replace marker.
nomount_nm=/data/adb/modules/nomount/bin/nm
if [ -x "$nomount_nm" ] && "$nomount_nm" version >/dev/null 2>&1; then
    rm -f "$APP_DIR/.replace" || abort "Cannot disable replace marker for NoMount"
else
    : > "$APP_DIR/.replace" || abort "Cannot enable replace marker"
fi
