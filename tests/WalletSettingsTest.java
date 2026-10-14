package io.github.zopulus.coogle;

import java.io.IOException;

/** The wallet toggle owns its power gesture, never the default payment provider. */
public final class WalletSettingsTest {
    public static void main(String[] args) throws Exception {
        Fake gesture = new Fake();
        boolean[] memory = {false};
        WalletSettings.save(true, false, gesture, value -> { memory[0] = value; return true; });
        check(memory[0] && gesture.enabled, "Enable did not save the native gesture and switch");
        WalletSettings.save(false, true, gesture, value -> { memory[0] = value; return true; });
        check(!memory[0] && !gesture.enabled, "Disable retained the native gesture or switch");
        for (boolean throwing : new boolean[]{false, true}) {
            try {
                WalletSettings.save(true, false, gesture, value -> {
                    memory[0] = value;
                    if (value && throwing) throw new IllegalStateException("Preference write failed");
                    return !value;
                });
                throw new AssertionError("Failed preference commit accepted");
            } catch (IOException | IllegalStateException expected) { }
            check(!memory[0] && !gesture.enabled, "Failed enable did not restore gesture and switch");
        }
        WalletSettings.save(true, false, gesture, value -> { memory[0] = value; return true; });
        try {
            WalletSettings.save(false, true, gesture, value -> { memory[0] = value; return value; });
            throw new AssertionError("Failed disable accepted");
        } catch (IOException expected) { }
        check(memory[0] && gesture.enabled, "Failed disable lost the previous enabled state");
        gesture.fail = true;
        int[] commits = {0};
        try {
            WalletSettings.save(false, true, gesture, value -> { commits[0]++; return true; });
            throw new AssertionError("Failed native gesture accepted");
        } catch (IOException expected) { }
        check(commits[0] == 0 && gesture.enabled, "Failed gesture committed the switch");
        gesture.fail = false;
        gesture.restoreFail = true;
        try {
            WalletSettings.save(false, true, gesture, value -> false);
            throw new AssertionError("Failed commit and rollback accepted");
        } catch (IOException expected) {
            check(expected.getSuppressed().length == 2, "Preference or gesture rollback failure lost");
        }
        System.out.println("PASS: gesture-only wallet enable/disable, commit rollback, setup and rollback failures");
    }
    static final class Fake implements WalletSettings.Gesture {
        boolean enabled, fail, restoreFail;
        public WalletSettings.Undo apply(boolean value) throws IOException {
            if (fail) throw new IOException("Native gesture setup failed");
            boolean previous = enabled;
            enabled = value;
            return () -> {
                if (restoreFail) throw new IOException("Native gesture rollback failed");
                enabled = previous;
            };
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
