package io.github.zopulus.coogle;

import android.app.Activity;
import android.content.SharedPreferences;
import com.oplus.camera.protocal.ui.context.EventMessageUIContainerContext;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.luckypray.dexkit.wrap.DexMethod;

public final class CameraNativeLensTest {
    public interface Listener {
        boolean supported();
        boolean children(Activity activity);
        void refresh();
    }
    public static class NativeMode implements Listener {
        public boolean enabled = true, child, normal = true;
        public int refreshes;
        public boolean gate(String key) { return false; }
        public boolean supported() { return enabled; }
        public boolean children(Activity activity) { return child; }
        public void refresh() { refreshes++; }
        public boolean normalEntry() { return normal; }
    }
    public static class NativeUi {
        public EventMessageUIContainerContext context = new EventMessageUIContainerContext();
        public EventMessageUIContainerContext state() { return context; }
    }
    public static class NativeLock {
        public static boolean locked;
        public static boolean current() { return locked; }
    }
    public static class Presenter {
        public Listener mFeatureListener = new NativeMode();
        public Activity host = new Activity();
        public NativeUi ui = new NativeUi();
        public String mode = "common";
        public boolean front;
        public boolean eligibility() { return false; }
        public String getCameraMode() { return mode; }
        public boolean isFrontCamera() { return front; }
        public Activity getActivityContext() { return host; }
        public NativeUi getMainUIContainer() { return ui; }
    }
    public static class MissingListener {
        public boolean eligibility() { return false; }
        public String getCameraMode() { return "common"; }
        public boolean isFrontCamera() { return false; }
        public Activity getActivityContext() { return null; }
        public NativeUi getMainUIContainer() { return null; }
    }
    private static final Object ORIGINAL = new Object();
    public static void main(String[] args) throws Throwable {
        boolean[] lensEnabled = {true};
        FeatureConfig.attach((SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (p,m,a) -> FeaturePolicy.LENS.equals(a[0]) && lensEnabled[0]));
        List<Method> targets = targets();
        HookInstaller hooks = install(targets);
        check(hooks.installed == 1 && hooks.callbacks.containsKey(Presenter.class.getMethod("eligibility")),
                "Only native presenter eligibility must be hooked");
        Presenter presenter = new Presenter();
        NativeMode state = (NativeMode) presenter.mFeatureListener;
        check(Boolean.TRUE.equals(invoke(hooks, presenter)), "Native common branch was not restored");
        check(state.refreshes == 1, "Native common branch initialization skipped");
        presenter.front = true;
        check(Boolean.FALSE.equals(invoke(hooks, presenter)), "Native front restriction bypassed");
        presenter.front = false; state.child = true;
        check(Boolean.FALSE.equals(invoke(hooks, presenter)), "Native children restriction bypassed");
        state.child = false; state.enabled = false;
        check(Boolean.FALSE.equals(invoke(hooks, presenter)), "Native supported predicate bypassed");
        state.enabled = true; state.normal = false;
        check(Boolean.FALSE.equals(invoke(hooks, presenter)), "Native external-entry restriction bypassed");
        state.normal = true; NativeLock.locked = true; presenter.ui.context.b = true;
        check(Boolean.FALSE.equals(invoke(hooks, presenter)), "Locked secure camera accepted");
        NativeLock.locked = false;
        check(Boolean.TRUE.equals(invoke(hooks, presenter)), "Unlock inside native secure camera did not recover");
        NativeLock.locked = true; presenter.ui.context.b = false;
        check(Boolean.TRUE.equals(invoke(hooks, presenter)), "Native lock/UI conjunction changed");
        presenter.ui.context = null;
        check(Boolean.FALSE.equals(invoke(hooks, presenter)), "Missing secure UI context accepted");
        NativeLock.locked = false; presenter.mode = "groupshot";
        check(invoke(hooks, presenter) == ORIGINAL, "Original groupshot branch replaced");
        for (String mode : new String[]{"night", "portrait", "video", null}) {
            presenter.mode = mode;
            check(invoke(hooks, presenter) == ORIGINAL, "Other native mode branch replaced: " + mode);
        }
        presenter.mode = "common"; presenter.mFeatureListener = null;
        check(Boolean.FALSE.equals(invoke(hooks, presenter)), "Missing native listener accepted");
        presenter.mFeatureListener = state; presenter.host = null;
        check(Boolean.FALSE.equals(invoke(hooks, presenter)), "Missing native activity accepted");
        presenter.mFeatureListener = state; presenter.host = new Activity();
        lensEnabled[0] = false;
        int refreshes = state.refreshes;
        check(invoke(hooks, presenter) == ORIGINAL, "Lens switch off still restored camera entry");
        check(state.refreshes == refreshes, "Disabled camera integration initialized native state");
        check(install(targets).installed == 0, "Lens disabled at process start still installed camera hook");
        lensEnabled[0] = true; presenter.ui.context = new EventMessageUIContainerContext();
        check(Boolean.TRUE.equals(invoke(hooks, presenter)), "Lens switch on did not restore camera entry");
        List<Method> wrong = new java.util.ArrayList<>(targets);
        wrong.set(5, Presenter.class.getMethod("isFrontCamera"));
        check(!CameraLensBinding.valid(wrong), "Instance lock method accepted");
        wrong = new java.util.ArrayList<>(targets);
        wrong.set(1, MissingListener.class.getMethod("eligibility"));
        check(install(wrong).installed == 0, "Missing native field installed a partial hook");
        System.out.println("PASS: default native presenter, children, entry, lock/UI conjunction, unlock recovery and original modes");
    }
    static List<Method> targets() throws Exception {
        return List.of(NativeMode.class.getMethod("gate", String.class), Presenter.class.getMethod("eligibility"),
                Listener.class.getMethod("supported"), Listener.class.getMethod("children", Activity.class),
                NativeMode.class.getMethod("normalEntry"), NativeLock.class.getMethod("current"),
                NativeUi.class.getMethod("state"), Listener.class.getMethod("refresh"));
    }
    private static HookInstaller install(List<Method> targets) throws Exception {
        Path dir = Files.createTempDirectory("coogle-native-camera-test");
        Path apk = dir.resolve("base.apk"), cache = dir.resolve("coogle_app_targets_v1.cache");
        try {
            Files.write(apk, new byte[]{1});
            Properties stored = new Properties();
            stored.setProperty("apk", "2:" + apk + ":" + apk.toFile().lastModified() + ":" + apk.toFile().length());
            String descriptors = targets.stream().map(method -> new DexMethod(method).serialize()).collect(java.util.stream.Collectors.joining("\n"));
            stored.setProperty(FeaturePolicy.CAMERA_LENS, "");
            stored.setProperty(FeaturePolicy.CAMERA_LENS + ".native-v1", descriptors);
            try (var output = Files.newOutputStream(cache)) { stored.store(output, "native camera fixture"); }
            HookInstaller hooks = new HookInstaller();
            CameraLensHooks.install(hooks, CameraNativeLensTest.class.getClassLoader(), apk.toString(), dir.toString());
            return hooks;
        } finally { Files.deleteIfExists(cache); Files.deleteIfExists(apk); Files.deleteIfExists(dir); }
    }
    private static Object invoke(HookInstaller hooks, Presenter presenter) throws Throwable {
        var chain = (XposedInterface.Chain) Proxy.newProxyInstance(XposedInterface.Chain.class.getClassLoader(),
                new Class<?>[]{XposedInterface.Chain.class}, (p,m,a) -> switch (m.getName()) {
                    case "getThisObject" -> presenter;
                    case "proceed" -> ORIGINAL;
                    default -> throw new AssertionError(m.getName());
                });
        return hooks.callback.intercept(chain);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
