package io.github.zopulus.coogle;

import android.content.SharedPreferences;
import android.os.SystemProperties;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Proxy;

/** Even an old manually selected Wallet scope must leave its authentication code untouched. */
public final class WalletPackageIsolationTest {
    public static void main(String[] args) {
        SystemProperties.colorOsVersion = "V17.0";
        check(SystemCompatibility.isColorOs17(), "Test must exercise the eligible platform branch");
        boolean[] enabled = {true};
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
                    throw new AssertionError("Wallet code inspected or hooked: " + method.getName());
                });
        var module = new CoogleModule();
        module.onPackageReady(param);
        enabled[0] = false;
        module.onPackageReady(param);
        check(FeatureScopes.required(FeaturePolicy.WALLET).equals(
                java.util.List.of("system", "com.android.systemui")), "Wallet requested app injection");
        System.out.println("PASS: enabled/disabled Wallet package initialization preserves original authentication");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
