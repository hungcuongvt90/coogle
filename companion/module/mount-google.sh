#!/system/bin/sh
# Reconcile the Google directory before the selected metamodule mounts it.
prepare_google_mount() {
    local google_dir="$1/system/priv-app/GoogleQuickSearchBox" backend
    [ -d "$google_dir" ] || return 1
    backend=$(readlink -f /data/adb/metamodule 2>/dev/null)
    # Metamodule backends (including VFS-style mounts) own the final mount
    # namespace. They do not need KernelSU's .replace marker, and probing the
    # backend driver here is too early because it may load during metamount.sh.
    if [ -f "$backend/module.prop" ] && grep -Eq '^metamodule=(true|1)$' "$backend/module.prop" &&
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
