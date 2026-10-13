#!/system/bin/sh
MODDIR=${0%/*}
rm -f "$MODDIR/applied-boot-id"
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
save_features "$MODDIR/applied-features.conf" || exit 1
# Installation state is independent of the Hey Google choice.
boot_id=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)
[ -n "$boot_id" ] || exit 1
printf '%s\n' "$boot_id" > "$MODDIR/applied-boot-id.tmp" &&
    chmod 0600 "$MODDIR/applied-boot-id.tmp" &&
    mv -f "$MODDIR/applied-boot-id.tmp" "$MODDIR/applied-boot-id"
