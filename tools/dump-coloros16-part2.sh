#!/system/bin/sh
# Coogle ColorOS 16 dump, part 2: the pieces the first archive lost (companion
# module state, hook logs, audio config) plus the clock plugin and scanner app
# needed to verify the remaining obfuscated bindings.
#
# Usage: adb push tools/dump-coloros16-part2.sh /data/local/tmp/ && \
#        adb shell sh /data/local/tmp/dump-coloros16-part2.sh && \
#        adb pull /data/local/tmp/coogle-c16-part2.tar.gz

OUT=/data/local/tmp/coogle-c16-part2
ARCHIVE=/data/local/tmp/coogle-c16-part2.tar.gz

if [ "$(id -u)" != 0 ]; then
    echo "Elevating with su (approve the KernelSU prompt)..."
    exec su -c "sh $0"
fi

rm -rf "$OUT" "$ARCHIVE" "${ARCHIVE%.gz}"
mkdir -p "$OUT/module" "$OUT/logs" "$OUT/audio" "$OUT/apk" "$OUT/text"

echo "== Coogle C16 dump part 2"

# Companion module state (the first archive was truncated before this folder).
M=/data/adb/modules/coogle_companion
if [ -d "$M" ]; then
    for f in module.prop features.conf applied-features.conf audio-state \
        translation-error component-error audio-patched.sha256 disable remove \
        skip_mount applied-boot-id; do
        [ -f "$M/$f" ] && cp -f "$M/$f" "$OUT/module/"
    done
    ls -laR "$M" > "$OUT/module/listing.txt" 2>/dev/null
    [ -f "$M/status.sh" ] && /system/bin/sh "$M/status.sh" > "$OUT/module/status-output.txt" 2>&1
else
    echo "companion module not installed" > "$OUT/module/MISSING"
fi
ls -la /data/adb/modules > "$OUT/module/all-modules.txt" 2>/dev/null
for d in /data/adb/modules/*/; do
    [ -f "$d/module.prop" ] && grep -E '^(id|version|description)=' "$d/module.prop" | \
        sed "s|^|$(basename "$d"): |"
done > "$OUT/module/all-modules.txt" 2>/dev/null
{
    ls -la /data/adb/metamodule 2>/dev/null
    grep -E '^(id|name)=' /data/adb/metamodule/module.prop 2>/dev/null
    ls -la /data/adb/nomount 2>/dev/null
    ls -la /data/adb/modules_update 2>/dev/null
} > "$OUT/module/metamodule.txt"

# Hook logs.
L=/data/adb/lspd
ls -laR "$L" > "$OUT/logs/lspd-listing.txt" 2>/dev/null
for f in "$L"/log/*.log; do
    [ -f "$f" ] || continue
    tail -n 4000 "$f" > "$OUT/logs/$(basename "$f")" 2>/dev/null
done
ls -la "$L/prefs" > "$OUT/logs/prefs-listing.txt" 2>/dev/null

# Audio configuration (the companion's patch source) and its mount state.
{
    echo "--- /odm/etc ---"; ls -laR /odm/etc 2>/dev/null
    echo "--- /vendor/etc/audio ---"; ls -laR /vendor/etc/audio 2>/dev/null
} > "$OUT/audio/listing.txt"
[ -f /odm/etc/resourcemanager.xml ] && cp -f /odm/etc/resourcemanager.xml "$OUT/audio/resourcemanager.xml"
grep -E 'resourcemanager|GoogleQuickSearchBox|Enrollment' /proc/self/mountinfo \
    > "$OUT/audio/mounts.txt" 2>/dev/null

# Obfuscated-binding sources still to verify.
[ -f /system_ext/app/KeyguardClockBase/KeyguardClockBase.apk ] && \
    cp -f /system_ext/app/KeyguardClockBase/KeyguardClockBase.apk "$OUT/apk/"
[ -f /product/app/OcrScanner/OcrScanner.apk ] && \
    cp -f /product/app/OcrScanner/OcrScanner.apk "$OUT/apk/"

# Voice interaction state for the companion diagnosis.
dumpsys -t 10 voiceinteraction > "$OUT/text/voiceinteraction.txt" 2>/dev/null
pm list libraries 2>/dev/null | grep -i 'hotword\|enrollment' > "$OUT/text/libraries-hotword.txt"
pm path com.android.hotwordenrollment.xgoogle > "$OUT/text/hotword-xgoogle.txt" 2>&1
pm path com.android.hotwordenrollment.okgoogle > "$OUT/text/hotword-okgoogle.txt" 2>&1
am compat 2>/dev/null | grep -c 255038118 > "$OUT/text/compat-filter.txt"

tar -czf "$ARCHIVE" -C /data/local/tmp coogle-c16-part2 2>/dev/null \
    || tar -cf "${ARCHIVE%.gz}" -C /data/local/tmp coogle-c16-part2 2>/dev/null
chmod 0644 "$ARCHIVE" "${ARCHIVE%.gz}" 2>/dev/null

echo "== Part 2 complete."
ls -la "$ARCHIVE" "${ARCHIVE%.gz}" 2>/dev/null
echo "Pull it with:  adb pull $ARCHIVE"
