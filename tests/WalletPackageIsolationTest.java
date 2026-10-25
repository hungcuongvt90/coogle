package io.github.zopulus.coogle;

import android.content.SharedPreferences;
import android.os.SystemProperties;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Proxy;

/** Disabled or unverified Wallet packages must retain original authentication. */
public final class WalletPackageIsolationTest {
    public static void main(String[] args) {
        SystemProperties.colorOsVersion = "V17.0";
        check(SystemCompatibility.isSupportedColorOs(), "Test must exercise the eligible platform branch");
        boolean[] enabled = {false};
        FeatureConfig.attach((SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (proxy, method, values) -> {
                    if (method.getName().equals("getBoolean")) return enabled[0];
                    throw new AssertionError("Unexpected preference access: " + method.getName());
                }));
        var param = (XposedModuleInterface.PackageReadyParam) Proxy.newProxyInstance(
                XposedModuleInterface.PackageReadyParam.class.getClassLoader(),
                new Class<?>[]{XposedModuleInterface.PackageReadyParam.class},
                (proxy, method, values) -> {
                    if (method.getName().equals("getPackageName")) return "com.google.android.apps.walletnfcrel";
                    if (enabled[0] && method.getName().equals("getClassLoader")) return WalletPackageIsolationTest.class.getClassLoader();
                    if (enabled[0] && method.getName().equals("getApplicationInfo")) {
                        var info = new android.content.pm.ApplicationInfo(); info.sourceDir = "missing.apk"; info.dataDir = ""; return info;
                    }
                    throw new AssertionError("Wallet code inspected or hooked: " + method.getName());
                });
        var module = new CoogleModule();
        module.onPackageReady(param);
        enabled[0] = true;
        module.onPackageReady(param);
        check(FeatureScopes.required(FeaturePolicy.WALLET).equals(
                java.util.List.of("system", "com.android.systemui", "com.google.android.apps.walletnfcrel")), "Wallet preview scope missing");
        check(module.callbacks.isEmpty(), "Unverified Wallet package installed authentication hooks");
        System.out.println("PASS: disabled/unsupported Wallet retains original authentication");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
