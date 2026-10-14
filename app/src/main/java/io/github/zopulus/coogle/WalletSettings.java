package io.github.zopulus.coogle;

import java.io.IOException;

/** Commit the wallet switch only after configuring its native power gesture. */
final class WalletSettings {
    interface Undo { void restore() throws Exception; }
    interface Save { boolean commit(boolean enabled); }
    interface Gesture { Undo apply(boolean enabled) throws Exception; }
    static void save(boolean enabled, boolean previous, Gesture gesture, Save save) throws Exception {
        Undo gestureUndo = null;
        boolean committing = false;
        try {
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
            throw failure;
        }
    }
    private WalletSettings() { }
}
