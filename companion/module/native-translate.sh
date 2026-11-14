#!/system/bin/sh
# Pre-mount check only; no Binder calls, runtime hooks or background polling.
prepare_native_translation() {
    local overlay_relative overlay feature backend idmap native_ready value translation_mode
    overlay_relative=system/product/overlay/CoogleContextualSearch
    overlay="$MODDIR/$overlay_relative/CoogleContextualSearch.apk"
    feature=system/etc/permissions/coogle-live-translate.xml
    metamodule=${COOGLE_METAMODULE:-/data/adb/metamodule}
    backend=$(readlink -f "$metamodule" 2>/dev/null)

    translation_mode=static
    # Keep the static product overlay out of metamodule mounts. The runtime
    # overlay uses Android's resource cache instead; its capability XML still
    # needs to be visible to SystemConfig during the boot-time scan.
    if [ -f "$backend/module.prop" ] && grep -Eq '^metamodule=(true|1)$' "$backend/module.prop" &&
       [ ! -f "$backend/disable" ] && [ ! -f "$backend/remove" ]; then
        select_payload 0 "$overlay_relative" || return 1
        overlay="$MODDIR/optional/$overlay_relative/CoogleContextualSearch.apk"
        translation_mode=runtime
    else
        select_payload 1 "$overlay_relative" || return 1
    fi
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
    printf '%s\n' "$translation_mode" > "$MODDIR/translation-mode" || return 1
    if [ "$native_ready" = 1 ]; then
        if [ "$translation_mode" = runtime ]; then
            printf 'runtime-pending\n' > "$MODDIR/translation-error" || return 1
        else
            rm -f "$MODDIR/translation-error"
        fi
    else
        printf 'overlay-incompatible\n' > "$MODDIR/translation-error" || return 1
    fi
}
