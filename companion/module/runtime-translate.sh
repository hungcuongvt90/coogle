#!/system/bin/sh
# Run only after Android has booted. Never expose the static overlay through a
# metamodule: Android owns this single-resource overlay and its idmap paths.
MODDIR=${0%/*}
OVERLAY=com.android.shell:CoogleContextualSearch
RESOURCE=android:string/config_defaultContextualSearchPackageName
PROVIDER=com.google.android.googlequicksearchbox
CAPABILITY=com.google.android.feature.CONTEXTUAL_SEARCH_LIVE_TRANSLATE

disable_translation_overlay() {
    cmd overlay disable --user 0 "$OVERLAY" 2>&1 | cat > /dev/null
}

apply_runtime_translation() {
    if [ -f "$MODDIR/disable" ] || [ -f "$MODDIR/remove" ] ||
       [ -f "$MODDIR/component-error" ] || [ -f "$MODDIR/skip_mount" ]; then
        disable_translation_overlay
        return 0
    fi
    mode=$(cat "$MODDIR/translation-mode" 2>/dev/null)
    case "$mode" in
        static) disable_translation_overlay; return 0 ;;
        runtime) ;;
        *) return 0 ;;
    esac
    # An incompatible preflight must not be converted into runtime success.
    if grep -qx 'overlay-incompatible' "$MODDIR/translation-error" 2>/dev/null; then
        disable_translation_overlay
        return 0
    fi
    provider=$(cmd overlay lookup --user 0 android "$RESOURCE" 2>/dev/null)
    if [ "$provider" != "$PROVIDER" ]; then
        # Command substitution gives Binder pipe FDs, independent of the
        # caller's log-file context. Keep command errors in the startup log.
        output=$(cmd overlay fabricate --target android --name CoogleContextualSearch \
            "$RESOURCE" 0x03 "$PROVIDER" 2>&1) &&
        output=$(cmd overlay enable --user 0 "$OVERLAY" 2>&1) &&
        provider=$(cmd overlay lookup --user 0 android "$RESOURCE" 2>/dev/null)
        if [ "$provider" != "$PROVIDER" ]; then
            printf '%s\n' "$output"
            disable_translation_overlay
            printf 'runtime-overlay-failed\n' > "$MODDIR/translation-error"
            return 1
        fi
    fi
    # The old companion parked the XML. A live repair can update the provider,
    # but must not claim success until SystemConfig has loaded the capability.
    if [ "$(pm has-feature "$CAPABILITY" 0 2>/dev/null)" != true ]; then
        printf 'capability-pending\n' > "$MODDIR/translation-error"
        return 0
    fi
    rm -f "$MODDIR/translation-error"
}

apply_runtime_translation
