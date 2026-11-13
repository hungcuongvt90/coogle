#!/system/bin/sh
# Pre-mount check only; no Binder calls, runtime hooks or background polling.
prepare_native_translation() {
    local overlay_relative overlay feature backend idmap native_ready value
    overlay_relative=system/product/overlay/CoogleContextualSearch
    overlay="$MODDIR/$overlay_relative/CoogleContextualSearch.apk"
    feature=system/etc/permissions/coogle-live-translate.xml
    metamodule=${COOGLE_METAMODULE:-/data/adb/metamodule}
    backend=$(readlink -f "$metamodule" 2>/dev/null)

    # A check against module storage does not prove that zygote/idmap can open
    # the mounted path. NoMount on the inspected device loses that visibility
    # during a userspace restart. Keep this optional overlay parked with a
    # selected metamodule until boot-time visibility can be verified.
    if [ -f "$backend/module.prop" ] && grep -Eq '^metamodule=(true|1)$' "$backend/module.prop" &&
       [ ! -f "$backend/disable" ] && [ ! -f "$backend/remove" ]; then
        select_payload 0 "$overlay_relative" || return 1
        select_payload 0 "$feature" || return 1
        printf 'overlay-unavailable\n' > "$MODDIR/translation-error" || return 1
        return 0
    fi

    select_payload 1 "$overlay_relative" || return 1
    [ -r "$overlay" ] && { [ -r "$MODDIR/$feature" ] || [ -r "$MODDIR/optional/$feature" ]; } || return 1
    idmap="$MODDIR/native-translate.idmap.tmp"
    native_ready=0
    if /system/bin/idmap2 create --target-apk-path /system/framework/framework-res.apk \
        --overlay-apk-path "$overlay" --idmap-path "$idmap" \
        --policy public --policy system --policy product >/dev/null 2>&1; then
        if value=$(/system/bin/idmap2 lookup --idmap-path "$idmap" --config en \
            --resid android:string/config_defaultContextualSearchPackageName 2>/dev/null); then
            value=${value#*value=\"}
            value=${value%\"}
            [ "$value" != com.google.android.googlequicksearchbox ] || native_ready=1
        fi
    fi
    rm -f "$idmap"
    # Unsupported firmware keeps the rest of the companion active.
    select_payload "$native_ready" "$feature" || return 1
    if [ "$native_ready" = 1 ]; then
        rm -f "$MODDIR/translation-error"
    else
        printf 'overlay-incompatible\n' > "$MODDIR/translation-error" || return 1
    fi
}
