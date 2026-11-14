#!/system/bin/sh
# Disable only Coogle's fabricated overlay, preserving every other overlay.
cmd overlay disable --user 0 com.android.shell:CoogleContextualSearch 2>&1 | cat > /dev/null
for package in com.android.hotwordenrollment.xgoogle com.android.hotwordenrollment.okgoogle; do
    am compat reset 255038118 "$package"
done
# User enrollment/model databases are intentionally not erased during uninstall.
