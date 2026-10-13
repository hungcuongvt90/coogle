package io.github.zopulus.coogle;
import dalvik.system.PathClassLoader;
import java.lang.reflect.Method;
import java.util.List;
public final class CameraNativeDexTest {
    public static void main(String[] args) {
        try {
            ClassLoader loader = new PathClassLoader(args[0] + ":/system/framework/org.apache.http.legacy.jar"
                    + ":/product/framework/com.oplus.camera.unit.sdk.jar:/system_ext/framework/androidx.window.extensions.jar"
                    + ":/system_ext/framework/androidx.window.sidecar.jar", CameraNativeDexTest.class.getClassLoader());
            List<Method> found;
            try (AppDexTargets targets = new AppDexTargets(loader, args[0], args[1])) {
                found = targets.resolve(FeaturePolicy.CAMERA_LENS);
            }
            if (found.isEmpty()) throw new AssertionError("Native camera targets missing");
            CameraLensBinding.bind(found);
            for (int i = 0; i < CameraLensBinding.TARGET_COUNT; i++) System.out.println(i + ": " + found.get(i));
            try (AppDexTargets targets = new AppDexTargets(loader, args[0], args[1])) {
                if (!found.equals(targets.resolve(FeaturePolicy.CAMERA_LENS))) throw new AssertionError("Native cache changed");
            }
            System.out.println("PASS: real camera DEX discovery, full native binding and cached descriptors");
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
