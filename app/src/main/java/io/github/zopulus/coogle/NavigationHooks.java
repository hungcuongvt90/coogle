package io.github.zopulus.coogle;

import android.content.Context;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;
import static io.github.zopulus.coogle.HookInstaller.target;
import static io.github.zopulus.coogle.HookInstaller.booleanMethod;

final class NavigationHooks {
    private final HookInstaller hooks;
    private final AtomicBoolean installed = new AtomicBoolean();
    private final AtomicBoolean gateFailureLogged = new AtomicBoolean();

    NavigationHooks(HookInstaller hooks) { this.hooks = hooks; }

    void install(ClassLoader loader, String apkPath, String dataDir) {
        if (!FeatureConfig.enabled(FeaturePolicy.NAVIGATION) || !installed.compareAndSet(false, true)) return;
        int navigationHandleStart = hooks.mark();
        try {
            Class<?> features;
            try { features = target(loader, "com.android.common.config.FeatureOption"); }
            catch (ClassNotFoundException renamed) {
                try (var targets = new AppDexTargets(loader, apkPath, dataDir)) {
                    NavigationBinding binding = targets.resolveNavigation();
                    if (binding == null) throw new NoSuchMethodException("Native navigation path not uniquely found");
                    installRenamed(binding);
                }
                return;
            }
            Class<?> controller = target(loader, "com.android.launcher3.circlesearch.OplusHideNavHandleController");
            Class<?> instance = target(loader, controller.getName() + "$INSTANCE");
            Class<?> consumer = target(loader, "com.android.quickstep.inputconsumers.OplusCuiInputConsumer");
            Class<?> companion = target(loader, consumer.getName() + "$Companion");
            Class<?> service = target(loader, "com.android.quickstep.OplusBaseTouchInteractionService");
            Class<?> listener;
            Class<?> nativeSettings = null;
            Class<?> nativeSettingsHolder = null;
            GateBinding navigation;
            try {
                listener = target(loader, "com.android.quickstep.inputconsumers.a");
                navigation = GateBinding.bind(target(loader, "com.oplus.quickstep.navigation.a"));
                companion.getDeclaredMethod("e", int.class, Context.class);
            } catch (ReflectiveOperationException unsupported17) {
                // ColorOS 16 keeps named observers and has a different dispatch companion.
                // Bind their live settings instead of raising the launcher's global EXP flag.
                Class<?> observer = target(loader, "com.oplus.quickstep.navigation.NavigationController");
                navigation = GateBinding.bind(observer.getDeclaredField("INSTANCE"), java.util.List.of(
                        observer.getDeclaredMethod("isSwipeSideGesture"),
                        observer.getDeclaredMethod("isGestureNavbarHidden"),
                        observer.getDeclaredMethod("isCircleToSearchGestureEnable")));
                listener = target(loader, consumer.getName() + "$mGestureDetector$1");
                nativeSettings = target(loader, "com.oplus.quickstep.common.settingsvalue.NavigationSettingsValueProxy");
                // The raw DEX names this holder Companion; JADX's Kotlin alias is INSTANCE.
                nativeSettingsHolder = target(loader, nativeSettings.getName() + "$Companion");
            }
            // Preflight the entire inspected path before installing any behavioral hooks.
            Method circle = booleanMethod(features, "isSupportCircleToSearch");
            Method hidden = booleanMethod(features, "isSupportCircleToSearchNavbarHidden");
            // ColorOS 16 calls an instance companion method; newer optimized builds
            // make it static. Both have the same zero-argument boolean contract.
            Method supported = instance.getDeclaredMethod("isSupportCircleToSearchHandle");
            if (supported.getReturnType() != boolean.class || Modifier.isNative(supported.getModifiers())
                    || Modifier.isAbstract(supported.getModifiers()))
                throw new NoSuchMethodException("Incompatible native navigation handle gate");
            controller.getDeclaredMethod("init");
            // Only these three methods change behavior. Native event handlers and dispatch remain original.
            hooks.install(circle, chain -> FeatureConfig.enabled(FeaturePolicy.NAVIGATION) ? true : chain.proceed());
            hooks.install(hidden, chain -> FeatureConfig.enabled(FeaturePolicy.NAVIGATION) ? true : chain.proceed());
            installSupportGate(supported, navigation);
            // Only inspected callers of the three gates need ART inlining invalidated.
            for (var constructor : controller.getDeclaredConstructors()) hooks.deoptimize(constructor);
            int deoptimized = hooks.deoptimizeCallers(controller, "init", "onChange", "isEducationNeeded", "isEducationShowing",
                            "isHandleShowing", "cancelEducation", "showEducation", "isSupportCircleToSearchHandle", "c")
                    + hooks.deoptimizeCallers(consumer, "n", "onMotionEvent")
                    + hooks.deoptimizeCallers(companion, "a", "e", "canTriggerCui", "startService")
                    + hooks.deoptimizeCallers(listener, "onShowPress", "onLongPress", "onScroll")
                    + hooks.deoptimizeCallers(service, "onCreate");
            if (nativeSettings != null) {
                // The native observer defaults to the feature gate when no setting is saved.
                // Both Kotlin entry points can contain an inlined, domestic false value.
                deoptimized += hooks.deoptimizeCallers(nativeSettings, "getCircleToSearchGestureState")
                        + hooks.deoptimizeCallers(nativeSettingsHolder, "getCircleToSearchGestureState");
            }
            hooks.info("READY three navigation gates installed; deoptimized callers=" + deoptimized);
        } catch (Throwable error) {
            hooks.unhookSince(navigationHandleStart);
            hooks.error("SKIP incompatible navigation path; navigation hooks removed", error);
        }
    }

    private void installRenamed(NavigationBinding binding) {
        for (var caller : binding.callers) hooks.deoptimize(caller);
        for (Method gate : java.util.List.of(binding.circle, binding.hidden, binding.gemini))
            hooks.install(gate, chain -> FeatureConfig.enabled(FeaturePolicy.NAVIGATION) ? true : chain.proceed());
        installSupportGate(binding.supported, binding.navigation);
        hooks.info("READY native navigation and Gemini gates installed; callers=" + binding.callers.size());
    }

    private void installSupportGate(Method supported, GateBinding navigation) {
        hooks.install(supported, chain -> {
            if (!FeatureConfig.enabled(FeaturePolicy.NAVIGATION)) return chain.proceed();
            try { return navigation.eligible(); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                if (gateFailureLogged.compareAndSet(false, true)) hooks.error("Native observers unavailable; gate closed", error);
                return false;
            }
        });
    }
}
