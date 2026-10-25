#!/system/bin/sh
# Hey Google is mirrored from the APK switch; Google/translation support stays on.
# Software hotword detection is used; the DSP audio patch is intentionally removed
# because it can deadlock OPLUS's location HAL on affected ColorOS firmware.
# Configuration is parsed as data, never sourced as root shell code.
load_features() {
    hey=0
    [ -f "$1" ] || return 0
    while IFS= read -r line; do
        case "$line" in
            hey=0) hey=0 ;; hey=1) hey=1 ;;
        esac
    done < "$1"
}
save_features() {
    printf 'hey=%s\n' "$hey" > "$1.tmp" && chmod 0600 "$1.tmp" && mv -f "$1.tmp" "$1"
}

# Called before mounts, or on the new module directory during installation.
# Disabled payloads stay outside system/ so they can be restored without downloads.
select_payload() {
    enabled=$1; relative=$2
    active="$MODDIR/$relative"; parked="$MODDIR/optional/$relative"
    if [ "$enabled" = 1 ]; then source=$parked; target=$active
    else source=$active; target=$parked; fi
    [ -e "$source" ] || return 0
    [ ! -e "$target" ] || { echo "Component path conflict: $relative" >&2; return 1; }
    mkdir -p "${target%/*}" && mv "$source" "$target" || return 1
    changed=1
}
apply_features() {
    changed=0
    select_payload 1 system/priv-app/GoogleQuickSearchBox || return 1
    for relative in system/product/priv-app/CoogleEnrollmentXGoogle \
        system/product/priv-app/CoogleEnrollmentYGoogle \
        system/framework/com.android.hotwordenrollment.common.util.jar \
        system/etc/sysconfig/coogle-hotword-library.xml; do
        select_payload "$hey" "$relative" || return 1
    done
    select_payload 1 system/product/overlay/CoogleContextualSearch || return 1
    # Remove empty mount directories; never remove other payloads or symlinks.
    if [ "$changed" = 1 ]; then
        find "$MODDIR/system" -depth -type d -empty -exec rmdir '{}' \; 2>/dev/null
        mkdir -p "$MODDIR/system"
    fi
}
