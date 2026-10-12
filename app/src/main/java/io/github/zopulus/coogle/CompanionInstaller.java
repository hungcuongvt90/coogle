package io.github.zopulus.coogle;

import android.content.Context;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Root is used only after the user asks to check/install the companion. */
public final class CompanionInstaller {
    private static final String DIR="/data/adb/modules/coogle_companion";
    public static String check() throws Exception {
        return state(run(statusCommand(),20));
    }
    public static String setHeyGoogle(boolean enabled) throws Exception {
        return state(run(controlCommand(enabled),20));
    }
    public static String syncHeyGoogle(boolean enabled) throws Exception {
        return state(run(syncCommand(enabled),20));
    }
    static String syncCommand(boolean enabled) {
        return "s=$(" + statusCommand() + "); case \"$s\" in READY|PENDING) "
                + controlCommand(enabled) + ";; *) printf '%s\\n' \"$s\";; esac";
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
                + "if [ ! -f \"$d/module.prop\" ]; then echo MISSING; "
                + "elif [ -f \"$d/disable\" ] || [ -f \"$d/remove\" ] || [ -f \"$d/component-error\" ] || [ -f \"$d/skip_mount\" ]; then echo DISABLED; "
                + "elif ! grep -q '^controlVersion=1$' \"$d/module.prop\" || [ ! -f \"$d/features.sh\" ]; then echo UPDATE_REQUIRED; "
                + "elif [ \"$pending\" = 1 ] || [ ! -f \"$d/applied-features.conf\" ] || "
                + "! cmp -s \"$d/features.conf\" \"$d/applied-features.conf\"; then echo PENDING; else echo READY; fi";
    }
    private static String state(String output) throws java.io.IOException {
        output = output.trim();
        String state = output.substring(output.lastIndexOf('\n') + 1).trim();
        if (!java.util.Set.of("MISSING", "DISABLED", "PENDING", "READY", "UPDATE_REQUIRED").contains(state))
            throw new java.io.IOException("无法读取附属模块状态");
        return state;
    }
    public static void install(Context context) throws Exception {
        Path zip=context.getCacheDir().toPath().resolve("coogle-companion.zip");
        try (InputStream source=context.getAssets().open("coogle-companion.zip")) { Files.copy(source,zip,java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        run("/data/adb/ksu/bin/ksud module install '"+zip.toString().replace("'","'\\''")+"'",120);
    }
    private static String run(String command,int timeout) throws Exception {
        Process process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
        java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
        Thread drain=new Thread(() -> {
            try (InputStream input=process.getInputStream()) {
                byte[] buffer=new byte[4096]; int n;
                while ((n=input.read(buffer))>=0) if (output.size()<65536) output.write(buffer,0,Math.min(n,65536-output.size()));
            } catch (Exception ignored) {}
        },"Coogle-root-output");
        drain.start();
        if (!process.waitFor(timeout,TimeUnit.SECONDS)) { process.destroyForcibly(); drain.join(1000); throw new java.io.IOException("操作超时"); }
        drain.join(1000);
        if (process.exitValue()!=0) throw new java.io.IOException("Root 操作失败，请确认 KernelSU 授权及挂载支持");
        return output.toString(StandardCharsets.UTF_8);
    }
    private CompanionInstaller() {}
}
