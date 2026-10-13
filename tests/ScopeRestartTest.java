package io.github.zopulus.coogle;

import java.util.List;
import java.nio.charset.StandardCharsets;

public final class ScopeRestartTest {
    public static void main(String[] args) throws Exception {
        String mocks = "am() { printf 'app:%s:%s:%s:%s\\n' \"$1\" \"$2\" \"$3\" \"$4\"; }; "
                + "pidof() { [ \"$1\" = com.android.systemui ] || return 7; echo 4242; }; "
                + "kill() { [ \"$1\" = -TERM ] && [ \"$2\" = 4242 ] || return 8; echo systemui; }; ";
        String command = ScopeRestart.command(List.of("system", "com.android.settings", "com.android.settings", "com.android.systemui"), 0);
        Process process = new ProcessBuilder("C:/Program Files/Git/bin/bash.exe", "-c", mocks + command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        check(process.waitFor() == 0 && output.equals("app:force-stop:--user:0:com.android.settings\nsystemui"), "Scopes not deduplicated or native system UI restart changed: " + output);
        for (var scopes : List.of(List.of("system"), List.<String>of(), List.of("package; echo invalid"))) {
            try { ScopeRestart.command(scopes, 0); throw new AssertionError("Unsafe/empty restart accepted"); }
            catch (java.io.IOException expected) { }
        }
        check(!command.contains("reboot") && !command.contains("system_server"), "App restart rebooted system framework");
        System.out.println("PASS: selected scopes, deduplication, system UI restart and invalid/empty scope boundaries");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
