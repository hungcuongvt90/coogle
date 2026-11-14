package io.github.zopulus.coogle;

import android.os.SystemProperties;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Proxy;

/** Verify the actual module entry points reach hooks on both supported releases. */
public final class SystemCompatibilityDispatchTest {
    private static final RuntimeException REACHED_HOOKS = new RuntimeException("hook class loader requested");

    public static void main(String[] args) {
        verify("V16.1.0", "", true);
        verify("V17.0.0", "", true);
        verify("", "V16.1.0", true);
        verify(null, "V17.0", true);
        verify("V15.0", "V17.0", false);
        verify("V18.0", "", false);
        verify("Android 16", "", false);
        verify("", "", false);
        System.out.println("PASS: ColorOS 16/17 package and system hooks, property fallback and unsupported releases");
    }

    private static void verify(String primary, String display, boolean supported) {
        SystemProperties.colorOsVersion = primary;
        SystemProperties.displayColorOsVersion = display;
        if (SystemCompatibility.isSupportedColorOs() != supported)
            throw new AssertionError("Unexpected eligibility for " + primary + "/" + display);
        var module = new CoogleModule();
        var packageParam = (XposedModuleInterface.PackageReadyParam) Proxy.newProxyInstance(
                SystemCompatibilityDispatchTest.class.getClassLoader(),
                new Class<?>[]{XposedModuleInterface.PackageReadyParam.class},
                (proxy, method, values) -> {
                    if (method.getName().equals("getPackageName")) return "com.android.systemui";
                    if (method.getName().equals("getClassLoader")) throw REACHED_HOOKS;
                    throw new AssertionError("Unexpected package callback: " + method.getName());
                });
        var systemParam = (XposedModuleInterface.SystemServerStartingParam) Proxy.newProxyInstance(
                SystemCompatibilityDispatchTest.class.getClassLoader(),
                new Class<?>[]{XposedModuleInterface.SystemServerStartingParam.class},
                (proxy, method, values) -> {
                    if (method.getName().equals("getClassLoader")) throw REACHED_HOOKS;
                    throw new AssertionError("Unexpected system callback: " + method.getName());
                });
        expectDispatch(() -> module.onPackageReady(packageParam), supported);
        expectDispatch(() -> module.onSystemServerStarting(systemParam), supported);
    }

    private static void expectDispatch(Runnable callback, boolean supported) {
        boolean dispatched = false;
        try {
            callback.run();
        } catch (RuntimeException result) {
            if (result != REACHED_HOOKS) throw result;
            dispatched = true;
        }
        if (dispatched != supported) throw new AssertionError("Hook dispatch did not match platform eligibility");
    }
}
