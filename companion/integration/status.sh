#!/system/bin/sh
MODDIR=${0%/*}
. "$MODDIR/features.sh"
load_features "$MODDIR/applied-features.conf"
if [ -f "$MODDIR/component-error" ]; then
    description='[⚠️Component preparation failed; mounting paused]'
else
    google_status='⚠️Google is not systemized'
    enrollment='⚠️Enrollment components are not ready'
    dsp='⚠️Software hotword is not running'
    flags=$(dumpsys -t 10 package com.google.android.googlequicksearchbox 2>/dev/null | grep -m 1 '^    flags=\[')
    if printf '%s\n' "$flags" | grep -q '[[:space:]]SYSTEM[[:space:]]'; then
        google_status='✅Google system app'
    fi
    if [ "$hey" = 0 ]; then
        enrollment='⏸️Hey Google is not patched'; dsp=''
    else
    library=$(pm list libraries 2>/dev/null)
    if pm path com.android.hotwordenrollment.xgoogle 2>/dev/null | grep -q '^package:' &&
       pm path com.android.hotwordenrollment.okgoogle 2>/dev/null | grep -q '^package:' &&
       printf '%s\n' "$library" | grep -q 'com.android.hotwordenrollment.common.util'; then
        enrollment='✅Enrollment components loaded'
    fi
    state=$(dumpsys -t 10 voiceinteraction 2>/dev/null | /data/adb/ksu/bin/busybox awk -f "$MODDIR/hotword-status.awk")
    case "$state" in
        dsp) dsp='⚠️DSP detection disabled; software detection selected' ;;
        software) dsp='✅Software hotword detection' ;;
        unknown) dsp='⏳Software hotword status unknown' ;;
    esac
    fi
    provider=$(cmd overlay lookup android android:string/config_defaultContextualSearchPackageName 2>/dev/null)
    capability=$(pm has-feature com.google.android.feature.CONTEXTUAL_SEARCH_LIVE_TRANSLATE 0 2>/dev/null)
    if [ "$provider" = com.google.android.googlequicksearchbox ] && [ "$capability" = true ] &&
        [ ! -f "$MODDIR/translation-error" ]; then
        translation='✅Live translation configuration active'
    elif [ -f "$MODDIR/translation-error" ]; then
        translation='⚠️Live translation overlay is incompatible'
    else translation='⚠️Live translation configuration inactive'; fi
    if [ -n "$dsp" ]; then enrollment="$enrollment | $dsp"; fi
    description="[$google_status | $enrollment | $translation]"
    if [ -f "$MODDIR/features.conf" ] && [ -f "$MODDIR/applied-features.conf" ] &&
         ! cmp -s "$MODDIR/features.conf" "$MODDIR/applied-features.conf"; then
        description="$description [⏳Selection saved; reboot required]"
    fi
fi
if ! grep -Fqx "description=$description" "$MODDIR/module.prop"; then
sed '/^description=/d' "$MODDIR/module.prop" > "$MODDIR/module.prop.tmp" || exit 1
printf 'description=%s\n' "$description" >> "$MODDIR/module.prop.tmp" &&
    chmod 0644 "$MODDIR/module.prop.tmp" &&
    mv "$MODDIR/module.prop.tmp" "$MODDIR/module.prop" || exit 1
fi
printf '%s\n' "$description"
