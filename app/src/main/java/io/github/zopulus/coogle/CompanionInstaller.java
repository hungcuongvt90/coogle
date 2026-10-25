package io.github.zopulus.coogle;

import android.content.Context;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import static io.github.zopulus.coogle.RootShell.run;

/** Root is used only after the user asks to check/install the companion. */
public final class CompanionInstaller {
    private static final String DIR="/data/adb/modules/coogle_companion";
    static final int REQUIRED_PAYLOAD_REVISION = 5;
    public static String check() throws Exception {
        return state(run(statusCommand(),20));
    }
    public static String setHeyGoogle(boolean enabled) throws Exception {
        return state(run(controlCommand(enabled),20));
    }
    static String controlCommand(boolean enabled) {
        return "for d in " + DIR + " /data/adb/modules_update/coogle_companion; do "
                + "if [ -f \"$d/features.sh\" ] && grep -q '^controlVersion=1$' \"$d/module.prop\" 2>/dev/null; then "
                + "if ! printf 'hey=" + (enabled ? "1" : "0") + "\\n' | cmp -s - \"$d/features.conf\"; then "
                + "printf 'hey=" + (enabled ? "1" : "0") + "\\n' > \"$d/features.conf.tmp\" && "
                + "chmod 0600 \"$d/features.conf.tmp\" && mv -f \"$d/features.conf.tmp\" \"$d/features.conf\" || exit 1; fi; fi; done; "
                + statusCommand();
    }
    private static String statusCommand() {
        return "d="+DIR+"; pending=0; if [ -f /data/adb/modules_update/coogle_companion/module.prop ]; then "
                + "d=/data/adb/modules_update/coogle_companion; pending=1; fi; "
                + "r=$(sed -n 's/^payloadRevision=//p' \"$d/module.prop\" 2>/dev/null); "
                + "case \"$r\" in ''|*[!0-9]*) r=0;; esac; "
                + "if [ ! -f \"$d/module.prop\" ]; then echo MISSING; "
                + "elif [ -f " + DIR + "/disable ] || [ -f " + DIR + "/remove ] || "
                + "[ -f \"$d/disable\" ] || [ -f \"$d/remove\" ] || [ -f \"$d/component-error\" ] || [ -f \"$d/skip_mount\" ]; then echo DISABLED; "
                + "elif ! grep -q '^controlVersion=1$' \"$d/module.prop\" || [ ! -f \"$d/features.sh\" ] "
                + "|| [ ! -f \"$d/mount-google.sh\" ] || ! [ \"$r\" -ge " + REQUIRED_PAYLOAD_REVISION + " ] 2>/dev/null; then echo UPDATE_REQUIRED; "
                + "elif [ \"$pending\" = 1 ]; then echo PENDING; "
                + "else b=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null); [ -n \"$b\" ] || exit 1; "
                + "a=$(cat \"$d/applied-boot-id\" 2>/dev/null); "
                + "if [ \"$a\" = \"$b\" ]; then echo READY; else echo PENDING; fi; fi";
    }
    private static String state(String output) throws java.io.IOException {
        output = output.trim();
        String state = output.substring(output.lastIndexOf('\n') + 1).trim();
        if (!java.util.Set.of("MISSING", "DISABLED", "PENDING", "READY", "UPDATE_REQUIRED").contains(state))
            throw new java.io.IOException("Unable to read the companion module status.");
        return state;
    }
    public static void install(Context context) throws Exception {
        Path zip=context.getCacheDir().toPath().resolve("coogle-companion.zip");
        try (InputStream source=context.getAssets().open("coogle-companion.zip")) { Files.copy(source,zip,java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        run("/data/adb/ksu/bin/ksud module install '"+zip.toString().replace("'","'\\''")+"' && "
                + "rm -f " + DIR + "/applied-boot-id /data/adb/modules_update/coogle_companion/applied-boot-id",120);
    }
    private CompanionInstaller() {}
}
