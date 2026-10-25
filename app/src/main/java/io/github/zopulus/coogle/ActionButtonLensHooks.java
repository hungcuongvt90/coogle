package io.github.zopulus.coogle;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Replace the scanner's resources and Intent while preserving native key handling and animation. */
final class ActionButtonLensHooks {
    private Resources moduleResources;
    private int colorId, tabId;
    private volatile boolean ready;
    private volatile boolean iconless;

    static void install(HookInstaller hooks, ClassLoader loader, String apk, String dataDir) {
        if (!FeatureConfig.enabled(FeaturePolicy.ACTION_BUTTON_LENS)) return;
        int mark = hooks.mark();
        try (AppDexTargets targets = new AppDexTargets(loader, apk, dataDir)) {
            var found = targets.resolve(ActionButtonLensBinding.CACHE_FEATURE);
            var state = new ActionButtonLensHooks();
            if (!found.isEmpty()) {
                installScanner(hooks, loader, new ActionButtonLensBinding(found), state);
                // Only inspected callers of the six binding targets need inlining invalidated.
                for (int i = 6; i < found.size(); i++) hooks.deoptimize(found.get(i));
            } else {
                found = targets.resolve(ActionButtonLensBinding.CACHE_FEATURE_16);
                if (found.isEmpty()) { hooks.info("ACTION LENS skip: native binding not unique"); return; }
                installKeyMagic(hooks, loader, found.get(0), state);
            }
            hooks.install(HookInstaller.target(loader, "com.oplus.gesture.GestureApplication").getDeclaredMethod("onCreate"), chain -> {
                Object result = chain.proceed();
                try { state.prepare((Application) chain.getThisObject()); }
                catch (Exception failure) { hooks.error("ACTION LENS resources unavailable; scanner retained", failure); }
                return result;
            });
            hooks.info("ACTION LENS installed");
        } catch (Throwable failure) {
            hooks.unhookSince(mark);
            hooks.error("ACTION LENS incompatible; original scanner retained", failure);
        }
    }

    /** 17: the whole scanner presentation is re-themed and the destination intent rewritten. */
    private static void installScanner(HookInstaller hooks, ClassLoader loader,
            ActionButtonLensBinding binding, ActionButtonLensHooks state) throws ReflectiveOperationException {
        hooks.install(binding.shortPress, chain -> {
            if (!ActionButtonLensBinding.matchesShortPress((Intent) chain.getArgs().get(0), (String) chain.getArgs().get(1))
                    || !state.enabled()) return chain.proceed();
            try {
                binding.showHint(binding.context(chain.getThisObject()), R.drawable.action_lens_tab);
                return null;
            } catch (ReflectiveOperationException failure) {
                hooks.error("ACTION LENS native hint unavailable; original service retained", failure);
                return chain.proceed();
            }
        });
        hooks.install(binding.availability, chain -> {
            String owner = (String) chain.getArgs().get(0);
            if (!ActionButtonLensBinding.SCANNER.equals(owner)) return chain.proceed();
            String target = ActionButtonLensLaunch.availabilityPackage(owner, state.enabled());
            if (owner.equals(target)) return chain.proceed();
            Object[] args = chain.getArgs().toArray();
            args[0] = target;
            // Native code still checks the selected user's installation and hidden-app state.
            return chain.proceed(args);
        });
        hooks.install(binding.label, chain -> state.enabled() ? "Google Lens" : chain.proceed());
        hooks.install(Resources.class.getDeclaredMethod("getDrawableForDensity", int.class, int.class, Resources.Theme.class), chain -> {
            int id = (int) chain.getArgs().get(0);
            Resources resources = (Resources) chain.getThisObject();
            if ((id == state.colorId || id == state.tabId) && state.enabled()
                    && ActionButtonLensBinding.PACKAGE.equals(resources.getResourcePackageName(id))) {
                int replacement = id == state.colorId ? R.drawable.action_lens_color : R.drawable.action_lens_tab;
                return state.moduleResources.getDrawableForDensity(replacement, (int) chain.getArgs().get(1), null);
            }
            return chain.proceed();
        });
        hooks.install(binding.launch, chain -> {
            Intent intent = (Intent) chain.getArgs().get(0);
            if (intent == null || !ActionButtonLensBinding.matches(
                    (String) chain.getArgs().get(1), (String) chain.getArgs().get(2), intent.getAction()) || !state.enabled())
                return chain.proceed();
            Context context = binding.context(chain.getThisObject());
            Intent lens = ActionButtonLensLaunch.destination(intent, context);
            if (lens == null) return chain.proceed();
            Object[] args = chain.getArgs().toArray();
            args[0] = lens;
            args[2] = ActionButtonLensLaunch.PACKAGE;
            return chain.proceed(args);
        });
        hooks.info("ACTION LENS native scanner destination and resources installed");
    }

    /**
     * 16 has no scanner scene: only the camera keymagic launch exists, so the hook swaps its
     * destination intent and package; hint, label and icon presentation stay native.
     */
    private static void installKeyMagic(HookInstaller hooks, ClassLoader loader,
            Method launcher, ActionButtonLensHooks state) throws ReflectiveOperationException {
        state.iconless = true;
        Field context = ActionButtonLensBinding.uniqueContextField(launcher.getDeclaringClass());
        context.setAccessible(true);
        hooks.install(launcher, chain -> {
            Intent intent = (Intent) chain.getArgs().get(1);
            if (!state.enabled() || !ActionButtonLensBinding.matchesKeyMagic(intent)) return chain.proceed();
            Context owner = (Context) context.get(chain.getThisObject());
            Intent lens = ActionButtonLensLaunch.destination(intent, owner);
            if (lens == null) return chain.proceed();
            Object[] args = chain.getArgs().toArray();
            args[1] = lens;
            args[2] = ActionButtonLensLaunch.PACKAGE;
            // The camera-secure rewrite inside the launcher is keyed on the package argument.
            return chain.proceed(args);
        });
        hooks.info("ACTION LENS 16 camera keymagic destination installed");
    }

    private void prepare(Context context) throws Exception {
        if (iconless) { ready = true; return; }
        // Lens may be hidden before first unlock; only resources determine readiness here.
        Resources resources = context.getResources();
        colorId = resources.getIdentifier("ac_ocrscanner_middle_icon", "drawable", ActionButtonLensBinding.PACKAGE);
        tabId = resources.getIdentifier("ac_tab_icon_ocrscanner", "drawable", ActionButtonLensBinding.PACKAGE);
        if (colorId == 0 || tabId == 0) return;
        moduleResources = context.getPackageManager().getResourcesForApplication("io.github.zopulus.coogle");
        moduleResources.getDrawable(R.drawable.action_lens_color, null);
        moduleResources.getDrawable(R.drawable.action_lens_tab, null);
        ready = true;
    }

    private boolean enabled() { return ready && FeatureConfig.enabled(FeaturePolicy.ACTION_BUTTON_LENS); }
    private ActionButtonLensHooks() { }
}
