package io.github.zopulus.coogle;
import android.content.SharedPreferences;
import android.app.ApplicationPackageManager;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.*;

public final class CameraLensEligibilityTest {
    public static void main(String[] args) throws Throwable {
        int[] reads = {0};
        FeatureConfig.attach((SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (p,m,a) -> { reads[0]++; return false; }));
        HookInstaller hooks = new HookInstaller();
        CameraLensEligibilityHooks.install(hooks, CameraLensEligibilityTest.class.getClassLoader());
        Method feature = ApplicationPackageManager.class.getMethod("hasSystemFeature", String.class, int.class);
        Method property = android.os.SystemProperties.class.getMethod("get", String.class, String.class);
        check(Boolean.TRUE.equals(invoke(hooks.callbacks.get(feature), "com.google.lens.feature.CAMERA_INTEGRATION", false)));
        check(Boolean.FALSE.equals(invoke(hooks.callbacks.get(feature), "other.feature", false)));
        check("other.camera,com.oplus.camera".equals(invoke(hooks.callbacks.get(property), "ro.com.google.lens.oem_camera_package", "other.camera")));
        check("com.oplus.camera".equals(invoke(hooks.callbacks.get(property), "ro.com.google.lens.oem_camera_package", "com.oplus.camera")));
        check("original".equals(invoke(hooks.callbacks.get(property), "other.property", "original")));
        check(reads[0] == 0);
        HookInstaller partial = new HookInstaller(); partial.failAt = 2;
        CameraLensEligibilityHooks.install(partial, CameraLensEligibilityTest.class.getClassLoader());
        check(partial.installed == 0);
        System.out.println("PASS: default camera eligibility ignores former switch; exact keys, existing packages and rollback");
    }
    private static Object invoke(XposedInterface.Hooker hook, String key, Object result) throws Throwable {
        check(hook != null);
        var chain = (XposedInterface.Chain) Proxy.newProxyInstance(XposedInterface.Chain.class.getClassLoader(),
                new Class<?>[]{XposedInterface.Chain.class}, (p,m,a) -> switch (m.getName()) {
                    case "getArg" -> key;
                    case "proceed" -> result;
                    default -> throw new AssertionError(m.getName());
                });
        return hook.intercept(chain);
    }
    private static void check(boolean ok) { if (!ok) throw new AssertionError(); }
}
