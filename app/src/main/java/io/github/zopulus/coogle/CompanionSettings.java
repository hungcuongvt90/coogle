package io.github.zopulus.coogle;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;

/** Coordinates root configuration and LSPosed preferences across Activity instances. */
final class CompanionSettings {
    interface Support {
        String check() throws Exception;
        String set(boolean enabled) throws Exception;
        String sync(boolean enabled) throws Exception;
        void install(Context context) throws Exception;
    }
    static final CompanionSettings INSTANCE = new CompanionSettings(new Support() {
        public String check() throws Exception { return CompanionInstaller.check(); }
        public String set(boolean enabled) throws Exception { return CompanionInstaller.setHeyGoogle(enabled); }
        public String sync(boolean enabled) throws Exception { return CompanionInstaller.syncHeyGoogle(enabled); }
        public void install(Context context) throws Exception { CompanionInstaller.install(context); }
    });
    private final Support support;
    CompanionSettings(Support support) { this.support = support; }

    synchronized String check() throws Exception { return support.check(); }

    synchronized String save(SharedPreferences settings, boolean enabled) throws Exception {
        boolean previous = requested(settings);
        boolean previousReady = settings.getBoolean("companion_ready", false);
        boolean previousKnown = settings.getBoolean("companion_known", false);
        try {
            String state = support.set(enabled);
            boolean ready = installed(state);
            boolean known = !"MISSING".equals(state);
            if (enabled && !ready) throw new IOException("请先安装并启用新版附属模块。");
            if (enabled != previous || ready != previousReady || known != previousKnown)
                commit(settings.edit().putBoolean(FeaturePolicy.HEY_GOOGLE, enabled)
                        .putBoolean("companion_ready", ready).putBoolean("companion_known", known));
            return state;
        } catch (Exception failure) {
            try {
                // A failed commit may already have changed the preferences' memory view.
                if (requested(settings) != previous || settings.getBoolean("companion_ready", false) != previousReady
                        || settings.getBoolean("companion_known", false) != previousKnown)
                    commit(settings.edit().putBoolean(FeaturePolicy.HEY_GOOGLE, previous)
                            .putBoolean("companion_ready", previousReady).putBoolean("companion_known", previousKnown));
            } catch (Exception rollback) { if (rollback != failure) failure.addSuppressed(rollback); }
            try { support.set(previous); }
            catch (Exception rollback) { if (rollback != failure) failure.addSuppressed(rollback); }
            throw failure;
        }
    }

    synchronized String sync(SharedPreferences settings) throws Exception {
        String state = support.sync(requested(settings));
        if ("UPDATE_REQUIRED".equals(state)) {
            if (settings.getBoolean("companion_ready", false) || !settings.getBoolean("companion_known", false))
                commit(settings.edit().putBoolean("companion_ready", false).putBoolean("companion_known", true));
        } else if (!installed(state)) {
            // Also clear the saved payload choice, so re-enabling the root module
            // later cannot restore Hey Google while the APK switch is off.
            save(settings, false);
        } else if (!settings.getBoolean("companion_ready", false) || !settings.getBoolean("companion_known", false)) {
            commit(settings.edit().putBoolean("companion_ready", true).putBoolean("companion_known", true));
        }
        return state;
    }

    synchronized String install(Context context, SharedPreferences settings, boolean enableHey) throws Exception {
        boolean enabled = enableHey || requested(settings);
        support.install(context);
        return save(settings, enabled);
    }
    static boolean shouldCheck(SharedPreferences settings) {
        return requested(settings) || settings.getBoolean("companion_ready", false) || settings.getBoolean("companion_known", false);
    }
    private static boolean requested(SharedPreferences settings) {
        return settings.getBoolean(FeaturePolicy.HEY_GOOGLE, FeaturePolicy.defaultValue(FeaturePolicy.HEY_GOOGLE));
    }
    private static boolean installed(String state) { return "READY".equals(state) || "PENDING".equals(state); }
    private static void commit(SharedPreferences.Editor editor) throws IOException {
        if (!editor.commit()) throw new IOException("配置未保存，请检查 LSPosed 连接。");
    }
}
