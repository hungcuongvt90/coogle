package io.github.zopulus.coogle;

import android.content.Context;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import static io.github.zopulus.coogle.HookInstaller.target;
import static io.github.zopulus.coogle.HookInstaller.booleanMethod;

final class NavigationHooks {
    private final HookInstaller hooks;
    private final AtomicBoolean installed = new AtomicBoolean();
    private final AtomicBoolean gateFailureLogged = new AtomicBoolean();

    NavigationHooks(HookInstaller hooks) { this.hooks = hooks; }

    void install(ClassLoader loader) {
        if (!FeatureConfig.enabled(FeaturePolicy.NAVIGATION) || !installed.compareAndSet(false, true)) return;
        int navigationHandleStart = hooks.mark();
        try {
            Class<?> features = target(loader, "com.android.common.config.FeatureOption");
            Class<?> controller = target(loader, "com.android.launcher3.circlesearch.OplusHideNavHandleController");
            Class<?> instance = target(loader, controller.getName() + "$INSTANCE");
            Method circle = booleanMethod(features, "isSupportCircleToSearch");
            Method hidden = booleanMethod(features, "isSupportCircleToSearchNavbarHidden");
            try {
                installGate17(loader, controller, instance, circle, hidden);
            } catch (ReflectiveOperationException unsupported) {
                // The obfuscated 17 gate holder is absent on 16; retry with its named gate.
                hooks.unhookSince(navigationHandleStart);
                installGate16(loader, features, controller, instance, circle, hidden);
            }
        } catch (Throwable error) {
            hooks.unhookSince(navigationHandleStart);
            hooks.error("SKIP incompatible navigation path; navigation hooks removed", error);
        }
    }

    /** 17: the gate lives in the obfuscated navigation holder; bind its native predicates. */
    private void installGate17(ClassLoader loader, Class<?> controller, Class<?> instance,
            Method circle, Method hidden) throws ReflectiveOperationException {
        Class<?> consumer = target(loader, "com.android.quickstep.inputconsumers.OplusCuiInputConsumer");
        Class<?> companion = target(loader, consumer.getName() + "$Companion");
        Class<?> listener = target(loader, "com.android.quickstep.inputconsumers.a");
        Class<?> service = target(loader, "com.android.quickstep.OplusBaseTouchInteractionService");
        GateBinding navigation = GateBinding.bind(target(loader, "com.oplus.quickstep.navigation.a"));
        // Preflight the entire inspected path before installing any behavioral hooks.
        Method supported = booleanMethod(instance, "isSupportCircleToSearchHandle");
        controller.getDeclaredMethod("init");
        companion.getDeclaredMethod("e", int.class, Context.class);
        // Only these three methods change behavior. Native event handlers and dispatch remain original.
        hooks.install(circle, chain -> FeatureConfig.enabled(FeaturePolicy.NAVIGATION) ? true : chain.proceed());
        hooks.install(hidden, chain -> FeatureConfig.enabled(FeaturePolicy.NAVIGATION) ? true : chain.proceed());
        hooks.install(supported, chain -> {
            if (!FeatureConfig.enabled(FeaturePolicy.NAVIGATION)) return chain.proceed();
            boolean eligible;
            try { eligible = navigation.eligible(); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                if (gateFailureLogged.compareAndSet(false, true)) hooks.error("Native observers unavailable; gate closed", error);
                return false;
            }
            return eligible;
        });
        // Only inspected callers of the three gates need ART inlining invalidated.
        int deoptimized = hooks.deoptimizeCallers(controller, "init", "onChange", "isEducationNeeded", "isEducationShowing",
                        "isHandleShowing", "cancelEducation", "showEducation", "c")
                + hooks.deoptimizeCallers(consumer, "n", "onMotionEvent")
                + hooks.deoptimizeCallers(companion, "a", "e")
                + hooks.deoptimizeCallers(listener, "onShowPress", "onLongPress")
                + hooks.deoptimizeCallers(service, "onCreate");
        hooks.info("READY three navigation gates installed; deoptimized callers=" + deoptimized);
    }

    /**
     * 16: the named controller gate checks the launcher's EXP flag internally. Raising that
     * flag here is not an option: FeatureOption's &lt;clinit&gt; resolves brand resources, Vector
     * dispatches onPackageReady before the host Application exists, and an EXP launcher also
     * lights up the native search entry regardless of the search-bar switch. Bind the same
     * native preference checks the gate consults instead, mirroring the 17 holder predicates.
     */
    private void installGate16(ClassLoader loader, Class<?> features, Class<?> controller, Class<?> instance,
            Method circle, Method hidden) throws ReflectiveOperationException {
        Class<?> consumer = target(loader, "com.android.quickstep.inputconsumers.OplusCuiInputConsumer");
        Class<?> companion = target(loader, consumer.getName() + "$Companion");
        Class<?> service = target(loader, "com.android.quickstep.OplusBaseTouchInteractionService");
        controller.getDeclaredMethod("init");
        Class<?> navigation = target(loader, "com.oplus.quickstep.navigation.NavigationController");
        java.lang.reflect.Field holder = navigation.getDeclaredField("INSTANCE");
        holder.setAccessible(true);
        Method get = holder.getType().getMethod("get");
        get.setAccessible(true);
        Method swipe = eligiblePredicate(navigation, "isSwipeSideGesture");
        Method navHidden = eligiblePredicate(navigation, "isGestureNavbarHidden");
        Method circleGesture = eligiblePredicate(navigation, "isCircleToSearchGestureEnable");
        hooks.install(circle, chain -> FeatureConfig.enabled(FeaturePolicy.NAVIGATION) ? true : chain.proceed());
        hooks.install(hidden, chain -> FeatureConfig.enabled(FeaturePolicy.NAVIGATION) ? true : chain.proceed());
        hooks.install(instance.getDeclaredMethod("isSupportCircleToSearchHandle"), chain -> {
            if (!FeatureConfig.enabled(FeaturePolicy.NAVIGATION)) return chain.proceed();
            try {
                Object active = get.invoke(holder.get(null));
                if (active == null) return false;
                return (boolean) swipe.invoke(active) && (boolean) navHidden.invoke(active)
                        && (boolean) circleGesture.invoke(active);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                if (gateFailureLogged.compareAndSet(false, true)) hooks.error("Native observers unavailable; gate closed", error);
                return false;
            }
        });
        // Only inspected callers of the three gates need ART inlining invalidated.
        int deoptimized = hooks.deoptimizeCallers(controller, "init", "onChange", "isEducationNeeded", "isEducationShowing",
                        "isHandleShowing", "cancelEducation", "showEducation", "c")
                + hooks.deoptimizeCallers(consumer, "n", "onMotionEvent")
                + hooks.deoptimizeCallers(companion, "a", "e")
                + hooks.deoptimizeCallers(service, "onCreate");
        hooks.info("READY 16 navigation gates installed; deoptimized callers=" + deoptimized);
    }

    private static Method eligiblePredicate(Class<?> type, String name) throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(name);
        if (method.getReturnType() != boolean.class || java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
            throw new NoSuchMethodException("Invalid native preference predicate: " + name);
        }
        method.setAccessible(true);
        return method;
    }
}
