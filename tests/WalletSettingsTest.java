package io.github.zopulus.coogle;

import java.io.IOException;

public final class WalletSettingsTest {
    public static void main(String[] args) throws Exception {
        Fake driver = new Fake();
        WalletSettings.save(true, false, driver, driver, value -> true);
        check(driver.google && driver.gestureEnabled && driver.selections == 1, "Enable did not set default payment and native gesture");
        WalletSettings.save(true, true, driver, driver, value -> true);
        check(driver.selections == 1, "Already default still requested root");
        WalletSettings.save(false, true, driver, driver, value -> true);
        check(driver.google && !driver.gestureEnabled && driver.selections == 1, "Disabling changed payment default or retained module gesture");
        driver.google = false;
        boolean[] memory = {false};
        try { WalletSettings.save(true, false, driver, driver, value -> { memory[0] = value; return !value; }); throw new AssertionError("Failed commit accepted"); }
        catch (IOException expected) { }
        check(!memory[0] && !driver.google && !driver.gestureEnabled && driver.restores == 1, "Failed commit did not restore default, gesture and memory");
        driver.fail = true;
        final int[] commits = {0};
        try { WalletSettings.save(true, false, driver, driver, value -> { commits[0]++; return true; }); throw new AssertionError("Root rejection accepted"); }
        catch (IOException expected) { }
        check(commits[0] == 0 && !driver.google, "Failed setup enabled wallet");
        driver.fail = false; driver.verify = false;
        try { WalletSettings.save(true, false, driver, driver, value -> { commits[0]++; return true; }); throw new AssertionError("Unverified default accepted"); }
        catch (IOException expected) { }
        check(commits[0] == 0 && !driver.google && driver.restores == 2, "Verification failure not rolled back");
        driver.verify = true; driver.gestureFail = true;
        try { WalletSettings.save(true, false, driver, driver, value -> { commits[0]++; return true; }); throw new AssertionError("Gesture failure accepted"); }
        catch (IOException expected) { }
        check(commits[0] == 0 && !driver.google && !driver.gestureEnabled && driver.restores == 3,
                "Gesture failure saved switch or failed to restore payment selection");
        System.out.println("PASS: wallet enable selection, no-op default, disable, commit rollback, root rejection and readback failure");
    }
    static class Fake implements WalletSettings.Payment, WalletSettings.Gesture {
        boolean google, fail, verify = true, gestureEnabled, gestureFail; int selections, restores;
        public WalletSettings.Undo apply(boolean enabled) throws IOException {
            if (gestureFail) throw new IOException("Gesture setup failed");
            boolean old = gestureEnabled; gestureEnabled = enabled; return () -> gestureEnabled = old;
        }
        public boolean isGoogleDefault() { return google && verify; }
        public WalletSettings.Undo selectGoogle() throws IOException {
            if (fail) throw new IOException("Root denied");
            selections++; google = true;
            return () -> { restores++; google = false; };
        }
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
