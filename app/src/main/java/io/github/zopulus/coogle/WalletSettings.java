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
                if (!payment.isGoogleDefault()) throw new IOException("默认付款应用设置未生效，请检查 Google 钱包与系统 NFC 设置。");
            }
            gestureUndo = gesture.apply(enabled);
            committing = true;
            if (!save.commit(enabled)) throw new IOException("配置未保存，请检查 LSPosed 连接。");
        } catch (Exception failure) {
            if (committing) {
                try { if (!save.commit(previous)) throw new IOException("无法恢复钱包开关配置。"); }
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
