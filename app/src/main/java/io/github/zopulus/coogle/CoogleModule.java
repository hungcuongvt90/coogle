package io.github.zopulus.coogle;

import android.app.Application;
import android.app.Instrumentation;
import io.github.zopulus.coogle.migration.MigrationEntry;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CoogleModule extends XposedModule {
    private final HookInstaller hooks = new HookInstaller(this);
    private final NavigationHooks navigation = new NavigationHooks(hooks);
    private final AtomicBoolean launcherHooksInstalled = new AtomicBoolean();

    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        MigrationEntry.attach(this);
        try { FeatureConfig.attach(getRemotePreferences(FeatureConfig.GROUP)); }
        catch (Throwable failure) { hooks.error("Configuration unavailable; verified defaults retained",failure); }
        hooks.info("Coogle " + BuildConfig.VERSION_NAME + " loaded in " + param.getProcessName() + "; framework=" + getFrameworkName()
                + " API=" + getApiVersion());
    }

    @Override public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        if (!SystemCompatibility.isSupportedColorOs()) return;
        MigrationEntry.attach(this);
        MigrationEntry.hookSystem(param.getClassLoader());
        SignalModelHooks.install(hooks, param.getClassLoader());
        hooks.info("SYSTEM native assistant and hotword initialization complete");
    }

    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (!SystemCompatibility.isSupportedColorOs()) return;
        if ("com.android.launcher".equals(packageName)) {
            installLauncherHooksAfterApplicationCreate(param.getClassLoader());
            return;
        }
        if ("com.google.android.apps.walletnfcrel".equals(packageName)) {
            if (FeatureConfig.enabled(FeaturePolicy.WALLET)) {
                MigrationEntry.attach(this);
                MigrationEntry.hookWalletPreview(param.getClassLoader(), param.getApplicationInfo().sourceDir,
                        param.getApplicationInfo().dataDir);
            }
            return;
        }
        if ("com.oplus.gesture".equals(packageName)) ActionButtonLensHooks.install(hooks, param.getClassLoader(),
                param.getApplicationInfo().sourceDir, param.getApplicationInfo().dataDir);
        if ("com.oplus.camera".equals(packageName)) CameraLensHooks.install(hooks, param.getClassLoader(),
                param.getApplicationInfo().sourceDir, param.getApplicationInfo().dataDir);
        if ("com.android.settings".equals(packageName)) SettingsHooks.installGoogleSettingsEntry(hooks, param.getClassLoader());
        if ("com.android.systemui".equals(packageName) || "com.android.settings".equals(packageName)
                || "com.oplus.wallpapers".equals(packageName)
                || "com.google.android.googlequicksearchbox".equals(packageName)) {
            MigrationEntry.attach(this);
            MigrationEntry.hookPackage(packageName, param.getClassLoader());
            if ("com.google.android.googlequicksearchbox".equals(packageName)) {
                CameraLensEligibilityHooks.install(hooks, param.getClassLoader());
                LensHooks.installForApp(hooks, param.getClassLoader(), param.getApplicationInfo().sourceDir,
                        param.getApplicationInfo().dataDir);
            }
            if ("com.android.settings".equals(packageName) && FeatureConfig.enabled(FeaturePolicy.NAVIGATION)) SettingsHooks.installSettingsHiddenSupport(hooks, param.getClassLoader());
        }
    }

    private void installLauncherHooksAfterApplicationCreate(ClassLoader loader) {
        try {
            Method callApplicationOnCreate = Instrumentation.class.getDeclaredMethod(
                    "callApplicationOnCreate", Application.class);
            hooks.install(callApplicationOnCreate, chain -> {
                Object result = chain.proceed();
                if (launcherHooksInstalled.compareAndSet(false, true)) {
                    installLauncherHooks(loader);
                }
                return result;
            });
        } catch (Throwable failure) {
            hooks.error("Launcher hooks deferred; Application lifecycle hook unavailable", failure);
        }
    }

    private void installLauncherHooks(ClassLoader loader) {
        try {
            MigrationEntry.attach(this);
            SearchBarHooks.install(hooks, loader);
            MigrationEntry.hookPackage("com.android.launcher", loader);
            navigation.install(loader);
        } catch (Throwable failure) {
            hooks.error("Launcher hooks unavailable after Application.onCreate", failure);
        }
    }

}
