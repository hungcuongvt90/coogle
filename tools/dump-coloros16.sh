#!/system/bin/sh
# Coogle ColorOS 16 diagnostic dump.
#
# Collects every device file and state the Coogle module reads, patches or
# hooks: framework jars, scoped app APKs, the audio resourcemanager the
# companion patches, companion module markers, overlay state and hook logs.
#
# Usage: sh dump-coloros16.sh [full|lite]
#   full (default): everything, including Google/Wallet/hotword APKs (~1-2 GB)
#   lite          : framework + core ColorOS apps only (quick first pass)

MODE="${1:-full}"
OUT=/data/local/tmp/coogle-coloros16
ARCHIVE=/data/local/tmp/coogle-coloros16.tar.gz

if [ "$(id -u)" != 0 ]; then
    echo "Elevating with su (approve the KernelSU prompt)..."
    exec su -c "sh $0 $MODE"
fi

rm -rf "$OUT" "$ARCHIVE" "${ARCHIVE%.gz}"
mkdir -p "$OUT/text" "$OUT/apk" "$OUT/framework" "$OUT/audio" \
    "$OUT/module" "$OUT/logs"

echo "== Coogle ColorOS 16 dump (mode: $MODE)"

# --- 1. Properties, packages, services ------------------------------------
getprop > "$OUT/text/getprop.txt" 2>/dev/null
pm list packages -f > "$OUT/text/packages.txt" 2>/dev/null
pm list libraries > "$OUT/text/libraries.txt" 2>/dev/null
pm list features > "$OUT/text/features.txt" 2>/dev/null
service list > "$OUT/text/services.txt" 2>/dev/null
grep -c '' "$OUT/text/services.txt" >/dev/null 2>&1
grep -i 'contextual\|voiceinteraction\|soundtrigger' "$OUT/text/services.txt" \
    > "$OUT/text/services-relevant.txt" 2>/dev/null

# --- 2. Overlay state (live translation + wallet gesture config) ----------
cmd overlay list > "$OUT/text/overlay-list.txt" 2>/dev/null
cmd overlay lookup android android:string/config_defaultContextualSearchPackageName \
    > "$OUT/text/overlay-contextual-lookup.txt" 2>&1
cmd overlay lookup --user 0 android android:integer/config_doubleTapPowerGestureMode \
    > "$OUT/text/overlay-doubletap-lookup.txt" 2>&1

# --- 3. Assistant / payment settings (grep-filtered, no private data) ------
SETTING_PAT='assistant|circle_to_search|quick_turn|double_tap_power|nfc_payment_default|oplus_wallet|oplus_home_controller|power_button_long_press|key_chord|voice_interaction|speech|breeno|op_assist|hotword|keyphrase'
for ns in system secure global; do
    settings list "$ns" 2>/dev/null | grep -Ei "$SETTING_PAT" \
        > "$OUT/text/settings-$ns.txt"
done

# --- 4. Voice interaction / hotword state ----------------------------------
dumpsys -t 10 voiceinteraction > "$OUT/text/voiceinteraction.txt" 2>/dev/null
aflags list 2>/dev/null | grep -Ei 'wallet|lens|assistant|hotword' \
    > "$OUT/text/aflags-relevant.txt"
[ -s "$OUT/text/aflags-relevant.txt" ] || echo "aflags unavailable" > "$OUT/text/aflags-relevant.txt"

# --- 5. Scoped app APKs -----------------------------------------------------
CORE_APPS="com.android.systemui com.android.launcher com.android.settings com.oplus.wallpapers com.oplus.gesture"
EXTRA_APPS="com.oplus.camera com.google.android.googlequicksearchbox com.google.android.apps.walletnfcrel com.android.hotwordenrollment.xgoogle com.android.hotwordenrollment.okgoogle"
APPS="$CORE_APPS"
[ "$MODE" = lite ] || APPS="$APPS $EXTRA_APPS"
for p in $APPS; do
    dir="$OUT/apk/$p"
    mkdir -p "$dir"
    paths=$(pm path "$p" 2>/dev/null)
    if [ -z "$paths" ]; then
        echo "not installed" > "$dir/MISSING"
        continue
    fi
    printf '%s\n' "$paths" > "$dir/paths.txt"
    for line in $paths; do
        apk=${line#package:}
        [ -f "$apk" ] && cp -f "$apk" "$dir/" 2>/dev/null
    done
    dumpsys package "$p" 2>/dev/null \
        | grep -E 'versionName|versionCode|flags=' > "$dir/dumpsys.txt"
done

# --- 6. Framework jars (system_server + internal resources) ---------------
for f in /system/framework/framework-res.apk /system/framework/framework.jar \
    /system/framework/services.jar; do
    [ -f "$f" ] && cp -f "$f" "$OUT/framework/" 2>/dev/null
done
{
    ls -laR /system/framework 2>/dev/null
    echo "--- system_ext ---"; ls -laR /system_ext/framework 2>/dev/null
    echo "--- product ---";   ls -laR /product/framework 2>/dev/null
    echo "--- odm ---";       ls -laR /odm/framework 2>/dev/null
} > "$OUT/text/framework-listing.txt"
find /system/framework /system_ext/framework /product/framework /odm/framework \
    -maxdepth 1 -type f -iname '*oplus*.jar' 2>/dev/null \
    | while IFS= read -r jar; do cp -f "$jar" "$OUT/framework/" 2>/dev/null; done

# --- 7. Audio configuration (the companion's patch source) -----------------
{
    echo "--- /odm/etc ---";     ls -laR /odm/etc 2>/dev/null
    echo "--- /vendor/etc/audio ---"; ls -laR /vendor/etc/audio 2>/dev/null
    echo "--- /vendor/etc ---"; ls -la /vendor/etc 2>/dev/null | head -60
} > "$OUT/text/audio-listing.txt"
for f in /odm/etc/resourcemanager.xml /vendor/etc/audio/resourcemanager.xml \
    /vendor/etc/audio_policy_configuration.xml; do
    [ -f "$f" ] && cp -f "$f" "$OUT/audio/$(echo "$f" | tr '/' '_')"
done
[ -f "$OUT/audio/_odm_etc_resourcemanager.xml" ] || \
    echo "missing" > "$OUT/audio/odm-resourcemanager-MISSING"
grep -F 'resourcemanager' /proc/self/mountinfo \
    > "$OUT/text/mountinfo-resourcemanager.txt" 2>/dev/null

# --- 8. Companion module state ---------------------------------------------
M=/data/adb/modules/coogle_companion
if [ -d "$M" ]; then
    for f in module.prop features.conf applied-features.conf audio-state \
        translation-error component-error audio-patched.sha256 disable remove \
        skip_mount mount-google.sh features.sh prepare-audio.sh; do
        [ -f "$M/$f" ] && cp -f "$M/$f" "$OUT/module/" 2>/dev/null
    done
    ls -laR "$M" 2>/dev/null > "$OUT/module/listing.txt"
    /system/bin/sh "$M/status.sh" > "$OUT/module/status-output.txt" 2>&1
else
    echo "companion module not installed" > "$OUT/module/MISSING"
fi
{
    ls -la /data/adb 2>/dev/null
    echo "--- ksu ---"; ls -la /data/adb/ksu/bin 2>/dev/null
    echo "--- metamodule ---"; ls -la /data/adb/metamodule 2>/dev/null
    grep -E '^id=' /data/adb/metamodule/module.prop 2>/dev/null
    /data/adb/ksu/bin/ksud -V 2>/dev/null || ksud -V 2>/dev/null || echo "ksud not callable"
} > "$OUT/text/root-env.txt"

# --- 9. Hook logs (last 3000 lines per LSPosed log) ------------------------
L=/data/adb/lspd/log
ls -la "$L" > "$OUT/logs/listing.txt" 2>/dev/null
for f in "$L"/*.log; do
    [ -f "$f" ] || continue
    tail -n 3000 "$f" > "$OUT/logs/$(basename "$f")" 2>/dev/null
done

# --- 10. Manifest and archive ----------------------------------------------
{
    echo "mode=$MODE date=$(date)"
    du -sk "$OUT"/* 2>/dev/null
    echo "total: $(du -sk "$OUT" 2>/dev/null | cut -f1) KB"
} > "$OUT/MANIFEST.txt"

tar -czf "$ARCHIVE" -C /data/local/tmp coogle-coloros16 2>/dev/null \
    || tar -cf "${ARCHIVE%.gz}" -C /data/local/tmp coogle-coloros16 2>/dev/null
chmod 0644 "$ARCHIVE" "${ARCHIVE%.gz}" 2>/dev/null

echo "== Dump complete."
ls -la "$ARCHIVE" "${ARCHIVE%.gz}" 2>/dev/null
echo "Pull it with:  adb pull $ARCHIVE"
