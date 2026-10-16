package io.github.zopulus.coogle;
import android.content.SharedPreferences;
import android.app.ApplicationPackageManager;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.*;

public final class CameraLensEligibilityTest {
    public static void main(String[] args) throws Throwable {
        boolean[] lensEnabled = {true};
        boolean[] shortcutEnabled = {false};
        var reads = new java.util.ArrayList<String>();
        FeatureConfig.attach((SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (p,m,a) -> {
                    reads.add((String) a[0]);
                    return (FeaturePolicy.LENS.equals(a[0]) && lensEnabled[0])
                            || ("action_button_lens".equals(a[0]) && shortcutEnabled[0]);
                }));
        HookInstaller hooks = new HookInstaller();
        CameraLensEligibilityHooks.install(hooks, CameraLensEligibilityTest.class.getClassLoader());
        Method feature = ApplicationPackageManager.class.getMethod("hasSystemFeature", String.class, int.class);
        Method property = android.os.SystemProperties.class.getMethod("get", String.class, String.class);
        check(Boolean.TRUE.equals(invoke(hooks.callbacks.get(feature), "com.google.lens.feature.CAMERA_INTEGRATION", false)));
        check(Boolean.FALSE.equals(invoke(hooks.callbacks.get(feature), "other.feature", false)));
        reads.clear();
        check("other.camera,com.oplus.camera".equals(invoke(hooks.callbacks.get(property), "ro.com.google.lens.oem_camera_package", "other.camera")));
        check(reads.equals(java.util.List.of(FeaturePolicy.LENS, FeaturePolicy.ACTION_BUTTON_LENS)));
        check("com.oplus.camera".equals(invoke(hooks.callbacks.get(property), "ro.com.google.lens.oem_camera_package", "com.oplus.camera")));
        check("original".equals(invoke(hooks.callbacks.get(property), "other.property", "original")));
        lensEnabled[0] = false;
        check(Boolean.FALSE.equals(invoke(hooks.callbacks.get(feature), "com.google.lens.feature.CAMERA_INTEGRATION", false)));
        check("other.camera".equals(invoke(hooks.callbacks.get(property), "ro.com.google.lens.oem_camera_package", "other.camera")));
        check(Boolean.TRUE.equals(invoke(hooks.callbacks.get(feature), "com.google.lens.feature.CAMERA_INTEGRATION", true)));
        shortcutEnabled[0] = true;
        check(Boolean.TRUE.equals(invoke(hooks.callbacks.get(feature), "com.google.lens.feature.CAMERA_INTEGRATION", false)));
        check("com.oplus.gesture".equals(invoke(hooks.callbacks.get(property), "ro.com.google.lens.oem_camera_package", "")));
        check(" other.camera ,com.oplus.gesture".equals(invoke(hooks.callbacks.get(property), "ro.com.google.lens.oem_camera_package", " other.camera ")));
        check(" com.oplus.gesture ".equals(invoke(hooks.callbacks.get(property), "ro.com.google.lens.oem_camera_package", " com.oplus.gesture ")));
        HookInstaller shortcutOnly = new HookInstaller();
        CameraLensEligibilityHooks.install(shortcutOnly, CameraLensEligibilityTest.class.getClassLoader());
        check(shortcutOnly.installed > 0);
        lensEnabled[0] = true;
        check("com.oplus.camera,com.oplus.gesture".equals(invoke(hooks.callbacks.get(property), "ro.com.google.lens.oem_camera_package", "")));
        check("other.camera,com.oplus.camera,com.oplus.gesture".equals(invoke(hooks.callbacks.get(property), "ro.com.google.lens.oem_camera_package", "other.camera")));
        lensEnabled[0] = false;
        shortcutEnabled[0] = false;
        HookInstaller disabled = new HookInstaller();
        CameraLensEligibilityHooks.install(disabled, CameraLensEligibilityTest.class.getClassLoader());
        check(disabled.installed == 0);
        lensEnabled[0] = true;
        HookInstaller partial = new HookInstaller(); partial.failAt = 2;
        CameraLensEligibilityHooks.install(partial, CameraLensEligibilityTest.class.getClassLoader());
        check(partial.installed == 0);
        System.out.println("PASS: independent camera/shortcut eligibility, all switch combinations, exact keys, existing callers and rollback");
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
