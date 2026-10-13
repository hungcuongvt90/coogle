package io.github.zopulus.coogle;

import java.lang.reflect.Method;

/** Supplies the OEM camera declaration consumed by Google's native Lens eligibility check. */
final class CameraLensEligibilityHooks {
    private static final String FEATURE = "com.google.lens.feature.CAMERA_INTEGRATION";
    private static final String PROPERTY = "ro.com.google.lens.oem_camera_package";
    private static final String CAMERA = "com.oplus.camera";

    static void install(HookInstaller hooks, ClassLoader loader) {
        if (!FeatureConfig.enabled(FeaturePolicy.CAMERA_LENS)) return;
        int mark = hooks.mark();
        try {
            Class<?> pm = HookInstaller.target(loader, "android.app.ApplicationPackageManager");
            boolean installed = false;
            for (Method method : pm.getDeclaredMethods()) {
                if (!method.getName().equals("hasSystemFeature") || method.getReturnType() != boolean.class
                        || method.getParameterCount() < 1 || method.getParameterTypes()[0] != String.class) continue;
                hooks.install(method, chain -> FEATURE.equals(chain.getArg(0)) && FeatureConfig.enabled(FeaturePolicy.CAMERA_LENS)
                        ? true : chain.proceed());
                installed = true;
            }
            if (!installed) throw new NoSuchMethodException("Camera integration feature query missing");
            Method property = HookInstaller.target(loader, "android.os.SystemProperties").getDeclaredMethod("get", String.class, String.class);
            hooks.install(property, chain -> {
                Object original = chain.proceed();
                if (!PROPERTY.equals(chain.getArg(0)) || !FeatureConfig.enabled(FeaturePolicy.CAMERA_LENS)) return original;
                String value = (String) original;
                if (value == null || value.isEmpty()) return CAMERA;
                for (String entry : value.split(",")) if (CAMERA.equals(entry.trim())) return value;
                return value + "," + CAMERA;
            });
            hooks.info("CAMERA LENS native OEM eligibility declared in Google process");
        } catch (Throwable failure) {
            hooks.unhookSince(mark);
            hooks.error("CAMERA LENS OEM declaration unavailable; original Google behavior retained", failure);
        }
    }
    private CameraLensEligibilityHooks() {}
}
