package io.github.zopulus.coogle;

import java.io.IOException;

/** A wallet enable is committed only after the system payment default is verified. */
final class WalletSettings {
    interface Undo { void restore() throws Exception; }
    interface Payment {
        boolean isGoogleDefault() throws Exception;
        Undo selectGoogle() throws Exception;
    }
    interface Save { boolean commit(boolean enabled); }
    interface Gesture { Undo apply(boolean enabled) throws Exception; }
    static void save(boolean enabled, boolean previous, Payment payment, Gesture gesture, Save save) throws Exception {
        Undo undo = null;
        Undo gestureUndo = null;
        boolean committing = false;
        try {
            if (enabled && !payment.isGoogleDefault()) {
                undo = payment.selectGoogle();
                if (!payment.isGoogleDefault()) throw new IOException("The default payment app setting did not take effect. Check Google Wallet and the system NFC settings.");
            }
            gestureUndo = gesture.apply(enabled);
            committing = true;
            if (!save.commit(enabled)) throw new IOException("Settings were not saved. Check the LSPosed connection.");
        } catch (Exception failure) {
            if (committing) {
                try { if (!save.commit(previous)) throw new IOException("Unable to restore the Wallet switch setting."); }
                catch (Exception rollback) { if (rollback != failure) failure.addSuppressed(rollback); }
            }
            if (gestureUndo != null) {
                try { gestureUndo.restore(); }
                catch (Exception rollback) { if (rollback != failure) failure.addSuppressed(rollback); }
            }
            if (undo != null) {
                try { undo.restore(); }
                catch (Exception rollback) { if (rollback != failure) failure.addSuppressed(rollback); }
            }
            throw failure;
        }
    }
    private WalletSettings() { }
}
