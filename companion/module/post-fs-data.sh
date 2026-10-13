#!/system/bin/sh
MODDIR=${0%/*}
. "$MODDIR/features.sh"
. "$MODDIR/native-translate.sh"
. "$MODDIR/mount-google.sh"
load_features "$MODDIR/features.conf"
if ! apply_features || ! prepare_google_mount "$MODDIR" || ! prepare_native_translation; then
    touch "$MODDIR/component-error" "$MODDIR/skip_mount"
    hey=0
    save_features "$MODDIR/applied-features.conf"
    exit 1
fi
if [ -f "$MODDIR/component-error" ]; then
    rm -f "$MODDIR/component-error" "$MODDIR/skip_mount"
fi
save_features "$MODDIR/applied-features.conf"
