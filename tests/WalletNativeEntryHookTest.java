package io.github.zopulus.coogle;

import android.content.SharedPreferences;
import com.android.server.GestureLauncherService;
import com.android.server.policy.OplusWalletController;
import io.github.libxposed.api.XposedInterface;
import io.github.zopulus.coogle.migration.compat.ModernHookRuntime;
import java.lang.reflect.*;
import java.util.List;

/** The module must only suppress the duplicate vendor route after native Wallet is selected. */
public final class WalletNativeEntryHookTest {
    public static void main(String[] args) throws Throwable {
        boolean[] enabled = {true};
        FeatureConfig.attach((SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (p, m, a) -> m.getName().equals("getBoolean") ? enabled[0] : null));
        Class<?> owner = Class.forName("io.github.zopulus.coogle.migration.WalletPowerHooks");
        owner.getMethod("hook", ClassLoader.class).invoke(owner.getField("INSTANCE").get(null), WalletNativeEntryHookTest.class.getClassLoader());
        Method method = OplusWalletController.class.getMethod("isQuickLaunchSupport");
        var module = ModernHookRuntime.INSTANCE.module;
        XposedInterface.Hooker hook = module.callbacks.get(method);
        check(hook != null && module.callbacks.size() == 1, "Native entry guard missing or launch/authentication methods hooked");
        OplusWalletController controller = new OplusWalletController();
        GestureLauncherService.selected = true;
        check(invoke(hook, method, controller, true).equals(false), "Selected native Wallet retained duplicate vendor launch");
        check(GestureLauncherService.queriedUser == 10, "Native selection checked for wrong user");
        GestureLauncherService.selected = false;
        check(invoke(hook, method, controller, true).equals(true), "Inactive native Wallet disabled vendor path");
        int queried = GestureLauncherService.queries;
        GestureLauncherService.selected = true;
        check(invoke(hook, method, controller, false).equals(false) && GestureLauncherService.queries == queried,
                "Already disabled vendor route inspected native state");
        enabled[0] = false;
        check(invoke(hook, method, controller, true).equals(true) && GestureLauncherService.queries == queried,
                "Disabled module changed original behavior");
        System.out.println("PASS: native selection, vendor-route isolation, current user and unchanged disabled behavior");
    }
    private static Object invoke(XposedInterface.Hooker hook, Method method, Object owner, boolean original) throws Throwable {
        int[] proceeds = {0};
        var chain = (XposedInterface.Chain) Proxy.newProxyInstance(XposedInterface.Chain.class.getClassLoader(),
                new Class<?>[]{XposedInterface.Chain.class}, (p, m, a) -> switch (m.getName()) {
                    case "getExecutable" -> method;
                    case "getThisObject" -> owner;
                    case "getArgs" -> List.of();
                    case "proceed" -> { proceeds[0]++; yield original; }
                    default -> throw new AssertionError("Unexpected hook access " + m.getName());
                });
        Object result = hook.intercept(chain);
        check(proceeds[0] == 1, "Original eligibility was skipped");
        return result;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
