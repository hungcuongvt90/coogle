package io.github.zopulus.coogle;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Exposes the retained Oplus menu and click handler, without rebuilding its floating UI. */
final class CameraLensHooks {
    static void install(HookInstaller hooks, ClassLoader loader, String apkPath, String dataDir) {
        if (!FeatureConfig.enabled(FeaturePolicy.CAMERA_LENS)) return;
        int mark = hooks.mark();
        try (AppDexTargets targets = new AppDexTargets(loader, apkPath, dataDir)) {
            var found = targets.resolve(FeaturePolicy.CAMERA_LENS);
            if (found.isEmpty()) { hooks.info("CAMERA LENS skip: native menu target not unique"); return; }
            Method gate = found.get(0);
            Class<?> type = gate.getDeclaringClass();
            Method mode = type.getMethod("getCurrentModeName");
            Method front = type.getMethod("isFrontCamera");
            Method capture = type.getMethod("isCapturing");
            Method record = type.getMethod("isVideoRecording");
            Field activityField = null;
            for (Class<?> parent = type; parent != null; parent = parent.getSuperclass()) {
                for (Field field : parent.getDeclaredFields()) {
                    if (field.getType() == Activity.class && !Modifier.isStatic(field.getModifiers())) {
                        if (activityField != null) throw new NoSuchFieldException("Ambiguous camera Activity");
                        activityField = field;
                    }
                }
            }
            if (activityField == null) throw new NoSuchFieldException("Camera Activity missing");
            activityField.setAccessible(true);
            final Field activity = activityField;
            hooks.install(gate, chain -> {
                if (!"key_show_googlelens_icon".equals(chain.getArg(0))
                        || !FeatureConfig.enabled(FeaturePolicy.CAMERA_LENS)) return chain.proceed();
                Object receiver = chain.getThisObject();
                Activity host = (Activity) activity.get(receiver);
                if (host == null || host.isFinishing() || host.isDestroyed()) return chain.proceed();
                KeyguardManager keyguard = host.getSystemService(KeyguardManager.class);
                Intent intent = host.getIntent();
                String action = intent == null ? null : intent.getAction();
                boolean entry = Intent.ACTION_MAIN.equals(action) || "android.media.action.STILL_IMAGE_CAMERA".equals(action)
                        || (action == null && intent != null && intent.getComponent() != null);
                if (keyguard == null || !CameraLensPolicy.allowed((String) mode.invoke(receiver),
                        (boolean) front.invoke(receiver), keyguard.isDeviceLocked(), entry,
                        (boolean) capture.invoke(receiver), (boolean) record.invoke(receiver))) return chain.proceed();
                Intent lens = new Intent(Intent.ACTION_VIEW).setData(android.net.Uri.parse("googleapp://lens"))
                        .setPackage("com.google.android.googlequicksearchbox");
                return lens.resolveActivity(host.getPackageManager()) != null;
            });
            for (int i = 1; i < found.size(); i++) hooks.deoptimize(found.get(i));
            hooks.info("CAMERA LENS native menu installed: " + gate);
        } catch (Throwable failure) {
            hooks.unhookSince(mark);
            hooks.error("CAMERA LENS incompatible; original camera retained", failure);
        }
    }
    private CameraLensHooks() {}
}
