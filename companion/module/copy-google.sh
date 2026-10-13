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
. "$MODPATH/mount-google.sh"
prepare_google_mount "$MODPATH" || abort "Cannot prepare Google mount mode"
