package io.github.zopulus.coogle;

import java.lang.reflect.Method;

/** Supplies only the enabled native OEM Lens callers to Google. */
final class CameraLensEligibilityHooks {
    private static final String FEATURE = "com.google.lens.feature.CAMERA_INTEGRATION";
    private static final String PROPERTY = "ro.com.google.lens.oem_camera_package";
    private static final String CAMERA = "com.oplus.camera";

    static void install(HookInstaller hooks, ClassLoader loader) {
        if (!enabled()) return;
        int mark = hooks.mark();
        try {
            Class<?> pm = HookInstaller.target(loader, "android.app.ApplicationPackageManager");
            boolean installed = false;
            for (Method method : pm.getDeclaredMethods()) {
                if (!method.getName().equals("hasSystemFeature") || method.getReturnType() != boolean.class
                        || method.getParameterCount() < 1 || method.getParameterTypes()[0] != String.class) continue;
                hooks.install(method, chain -> FEATURE.equals(chain.getArg(0)) && enabled()
                        ? true : chain.proceed());
                installed = true;
            }
            if (!installed) throw new NoSuchMethodException("Camera integration feature query missing");
            Method property = HookInstaller.target(loader, "android.os.SystemProperties").getDeclaredMethod("get", String.class, String.class);
            hooks.install(property, chain -> {
                Object original = chain.proceed();
                if (!PROPERTY.equals(chain.getArg(0))) return original;
                boolean cameraEnabled = FeatureConfig.enabled(FeaturePolicy.LENS);
                boolean shortcutEnabled = FeatureConfig.enabled(FeaturePolicy.ACTION_BUTTON_LENS);
                if (!cameraEnabled && !shortcutEnabled) return original;
                String value = (String) original;
                if (cameraEnabled) value = append(value, CAMERA);
                if (shortcutEnabled) value = append(value, ActionButtonLensBinding.PACKAGE);
                return value;
            });
            hooks.info("CAMERA LENS native OEM eligibility declared in Google process");
        } catch (Throwable failure) {
            hooks.unhookSince(mark);
            hooks.error("CAMERA LENS OEM declaration unavailable; original Google behavior retained", failure);
        }
    }
    private static boolean enabled() {
        return FeatureConfig.enabled(FeaturePolicy.LENS) || FeatureConfig.enabled(FeaturePolicy.ACTION_BUTTON_LENS);
    }
    private static String append(String value, String caller) {
        if (value == null || value.isEmpty()) return caller;
        for (String entry : value.split(",")) if (caller.equals(entry.trim())) return value;
        return value + "," + caller;
    }
    private CameraLensEligibilityHooks() {}
}
