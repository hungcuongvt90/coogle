#!/system/bin/sh
# Reconcile the Google directory before the selected metamodule mounts it.
prepare_google_mount() {
    local google_dir="$1/system/priv-app/GoogleQuickSearchBox" backend
    [ -d "$google_dir" ] || return 1
    backend=$(readlink -f /data/adb/metamodule 2>/dev/null)
    # NoMount can load its driver later in metamount.sh; version probing is too early here.
    if [ -f "$backend/module.prop" ] && grep -q '^id=nomount$' "$backend/module.prop" &&
       [ ! -f "$backend/disable" ] && [ ! -f "$backend/remove" ] &&
       [ ! -f /data/adb/nomount/disable ]; then
        rm -f "$google_dir/.replace"
    elif [ -d /system/priv-app/GoogleQuickSearchBox ]; then
        # .replace only replaces a firmware-shipped directory; ColorOS 16 CN
        # firmware has none, and mounters that cannot create the directory
        # abort the whole module mount when it is present.
        : > "$google_dir/.replace"
    else
        rm -f "$google_dir/.replace"
    fi
}
