package io.github.zopulus.coogle;

import android.content.SharedPreferences;
import com.android.common.config.FeatureOption;
import com.android.launcher3.circlesearch.OplusHideNavHandleController;
import com.android.quickstep.OplusBaseTouchInteractionService;
import com.android.quickstep.inputconsumers.OplusCuiInputConsumer;
import com.oplus.quickstep.common.settingsvalue.NavigationSettingsValueProxy;
import com.oplus.quickstep.navigation.NavigationController;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Modifier;
import java.util.List;

/** Exercise the restored named-observer route and its user-controlled gates. */
public final class NavigationColorOs16Test {
    public static void main(String[] args) throws Throwable {
        boolean[] enabled = {false};
        FeatureConfig.attach((SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (proxy, method, values) -> method.getName().equals("getBoolean") ? enabled[0] : null));
        ClassLoader loader = NavigationColorOs16Test.class.getClassLoader();
        HookInstaller off = new HookInstaller();
        new NavigationHooks(off).install(loader, "unused.apk", null);
        check(off.installed == 0, "Disabled navigation installed hooks");
        enabled[0] = true;
        HookInstaller hooks = new HookInstaller();
        new NavigationHooks(hooks).install(loader, "unused.apk", null);
        check(hooks.installed == 3, "ColorOS 16 named observer fallback did not install: " + hooks.lastError);
        check(hooks.constructorsDeoptimized == 1, "Native handle constructor retained an inlined gate");
        Method circle = FeatureOption.class.getMethod("isSupportCircleToSearch");
        Method hidden = FeatureOption.class.getMethod("isSupportCircleToSearchNavbarHidden");
        Method handle = OplusHideNavHandleController.INSTANCE.class.getMethod("isSupportCircleToSearchHandle");
        check(!Modifier.isStatic(handle.getModifiers()), "Fixture lost the device's instance companion gate");
        check(!hooks.callbacks.containsKey(OplusHideNavHandleController.class.getMethod("isSupportCircleToSearchHandle")),
                "Hooked the static bridge instead of the companion used by native callers");
        check(hooks.deoptimized.contains(OplusHideNavHandleController.class.getMethod("isSupportCircleToSearchHandle")),
                "Static bridge retained an inlined companion gate");
        for (Method caller : List.of(
                OplusCuiInputConsumer.Companion.class.getMethod("canTriggerCui"),
                OplusCuiInputConsumer.Companion.class.getMethod("startService"),
                OplusCuiInputConsumer.mGestureDetector$1.class.getMethod("onShowPress"),
                OplusCuiInputConsumer.mGestureDetector$1.class.getMethod("onLongPress"),
                OplusCuiInputConsumer.mGestureDetector$1.class.getMethod("onScroll"),
                NavigationSettingsValueProxy.class.getMethod("getCircleToSearchGestureState", android.content.Context.class),
                NavigationSettingsValueProxy.Companion.class.getMethod("getCircleToSearchGestureState", android.content.Context.class)))
            check(hooks.deoptimized.contains(caller), "Native caller retained an inlined gate: " + caller);
        for (Method method : List.of(circle, hidden, handle)) expect(hooks, method, true, false);
        NavigationController state = NavigationController.INSTANCE.current;
        state.gesture = false; expect(hooks, handle, false, false); state.gesture = true;
        state.hidden = false; expect(hooks, handle, false, false); state.hidden = true;
        state.circle = false; expect(hooks, handle, false, false); state.circle = true;
        state.broken = true; expect(hooks, handle, false, false); state.broken = false;
        NavigationController.INSTANCE.current = null; expect(hooks, handle, false, false);
        NavigationController.INSTANCE.current = state;
        enabled[0] = false;
        for (Method method : List.of(circle, hidden, handle)) expect(hooks, method, false, true);
        enabled[0] = true;
        Method unrelated = OplusBaseTouchInteractionService.class.getMethod("onCreate");
        for (int failure = 1; failure <= 3; failure++) {
            HookInstaller failed = new HookInstaller();
            failed.install(unrelated, chain -> chain.proceed());
            failed.failAt = failure + 1;
            new NavigationHooks(failed).install(loader, "unused.apk", null);
            check(failed.installed == 1 && failed.callbacks.containsKey(unrelated),
                    "Failed navigation installation leaked hooks or removed unrelated hooks");
        }
        System.out.println("PASS: ColorOS 16 named navigation, live settings, disabled fallback and rollback");
    }

    private static void expect(HookInstaller hooks, Method method, boolean expected, boolean nativeCall) throws Throwable {
        int[] calls = {0};
        var chain = (XposedInterface.Chain) Proxy.newProxyInstance(
                XposedInterface.Chain.class.getClassLoader(), new Class<?>[]{XposedInterface.Chain.class},
                (proxy, invoked, values) -> {
                    if (!invoked.getName().equals("proceed")) throw new AssertionError(invoked.getName());
                    calls[0]++; return false;
                });
        check((boolean) hooks.callbacks.get(method).intercept(chain) == expected && calls[0] == (nativeCall ? 1 : 0),
                "Unexpected gate result: " + method);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
