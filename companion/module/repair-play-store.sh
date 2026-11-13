#!/system/bin/sh
# Magic-mount backends can briefly hide /data/app while PackageManager scans
# system apps.  In that window the Play Store update is left on disk but its
# user package registration is dropped, so the firmware updater wins and the
# launcher icon disappears.  Repair only that narrow state after boot.
PACKAGE=com.android.vending

play_store_data_registered() {
    pm path "$PACKAGE" 2>/dev/null | grep -q '^package:/data/app/'
}

find_play_store_apk() {
    # PackageManager's generated directory contains the package name.  Avoid
    # touching arbitrary APKs and stop at the first complete base APK.
    for apk in $(find /data/app -type f -name base.apk 2>/dev/null); do
        case "$apk" in
            */com.android.vending-*/base.apk)
                [ -s "$apk" ] && { printf '%s\n' "$apk"; return 0; }
                ;;
        esac
    done
    return 1
}

repair_play_store() {
    play_store_data_registered && return 0
    apk=$(find_play_store_apk) || return 0
    [ -n "$apk" ] || return 0
    # -d permits restoring the user update when the firmware updater has a
    # newer versionCode.  -r preserves Play Store data and account state.
    cmd package install -r -d "$apk" >/dev/null 2>&1 || return 1
    # A prior scan may have disabled the package; installation alone keeps
    # that user state, so explicitly restore the launcher-visible package.
    pm enable --user 0 "$PACKAGE" >/dev/null 2>&1 || return 1
    return 0
}

repair_play_store
