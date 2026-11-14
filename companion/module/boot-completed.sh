#!/system/bin/sh
MODDIR=${0%/*}
. "$MODDIR/features.sh"
load_features "$MODDIR/applied-features.conf"
/system/bin/sh "$MODDIR/runtime-translate.sh" 2>&1 | cat > "$MODDIR/translation.log"
# Restore a user-installed Play Store update if a metamodule hid it during the
# boot-time package scan. This is independent of the Hey Google switch.
/system/bin/sh "$MODDIR/repair-play-store.sh" > "$MODDIR/play-store-repair.log" 2>&1
if [ "$hey" = 1 ] && [ ! -f "$MODDIR/component-error" ]; then
    sleep 30
    # Binder shell commands require pipe FDs rather than module-directory FDs.
    /system/bin/sh "$MODDIR/setup.sh" 2>&1 | cat > /dev/null
fi
/system/bin/sh "$MODDIR/status.sh" > /dev/null 2>&1
