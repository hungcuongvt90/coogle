package io.github.zopulus.coogle;

import java.nio.charset.StandardCharsets;

/** Execute production payment commands against isolated shell functions. */
public final class WalletPaymentCommandTest {
    private static final String GOOGLE = "com.google.android.gms/.pay.RenamedService";
    public static void main(String[] args) throws Exception {
        String mocks = "value='other.wallet/.Service'; settings() { "
                + "[ \"$1\" = --user ] && [ \"$2\" = 10 ] || return 7; "
                + "case \"$3\" in get) printf '%s\\n' \"$value\";; put) value=$6;; delete) value=null;; esac; }; "
                + "cmd() { [ \"$*\" = 'role add-role-holder --user 10 android.app.role.WALLET com.google.android.gms 0' ] || { echo \"unexpected:$*\"; return 8; }; "
                + "value='" + GOOGLE + "'; }; sleep() { :; }; ";
        Result selected = run(mocks + WalletPayment.selectionCommand(10, true, null));
        check(selected.status == 0, "Native wallet role selection failed: " + selected.output);
        check(run(mocks + WalletPayment.selectionCommand(10, false, GOOGLE)).status == 0, "Legacy selection failed");
        check(run(mocks + "cmd() { return 1; }; " + WalletPayment.selectionCommand(10, true, null)).status != 0, "Role failure accepted");
        check(run(mocks + "cmd() { :; }; " + WalletPayment.selectionCommand(10, true, null)).status != 0, "Unchanged default accepted");
        String previous = "other.wallet/.Service'$(printf UNSAFE)";
        Result restored = run(mocks + WalletPayment.restoreCommand(10, previous, false, "") + "; printf '%s\\n' \"$value\"");
        check(restored.status == 0 && restored.output.equals(previous), "Shell quoting changed prior setting");
        Result cleared = run(mocks + WalletPayment.restoreCommand(10, null, false, "") + "; printf '%s\\n' \"$value\"");
        check(cleared.status == 0 && cleared.output.equals("null"), "Absent setting not restored");
        String roleMocks = "cmd() { printf '%s\\n' \"$*\"; }; ";
        Result restoredRole = run(mocks + roleMocks + WalletPayment.restoreCommand(10, "other.wallet/.Service", true, "other.wallet"));
        check(restoredRole.status == 0 && restoredRole.output.equals("role add-role-holder --user 10 android.app.role.WALLET other.wallet 0"), "Previous role not restored");
        Result clearedRole = run(mocks + roleMocks + WalletPayment.restoreCommand(10, null, true, ""));
        check(clearedRole.status == 0 && clearedRole.output.equals("role clear-role-holders --user 10 android.app.role.WALLET 0"), "Empty role not restored");
        System.out.println("PASS: wallet native role, legacy NFC, denied selection, readback, rollback and shell quoting");
    }
    record Result(int status, String output) { }
    private static Result run(String command) throws Exception {
        // A script file preserves shell quotes across Windows' process argument parser.
        var script = java.nio.file.Files.createTempFile("coogle-payment-command", ".sh");
        try {
            java.nio.file.Files.writeString(script, command, StandardCharsets.UTF_8);
            Process process = new ProcessBuilder("C:/Program Files/Git/bin/bash.exe", script.toString()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return new Result(process.waitFor(), output);
        } finally { java.nio.file.Files.deleteIfExists(script); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
