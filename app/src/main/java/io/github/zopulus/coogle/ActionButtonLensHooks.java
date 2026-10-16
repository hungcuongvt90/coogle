package io.github.zopulus.coogle;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;

/** Replace the scanner's resources and Intent while preserving native key handling and animation. */
final class ActionButtonLensHooks {
    private Resources moduleResources;
    private int colorId, tabId;
    private volatile boolean ready;

    static void install(HookInstaller hooks, ClassLoader loader, String apk, String dataDir) {
        if (!FeatureConfig.enabled(FeaturePolicy.ACTION_BUTTON_LENS)) return;
        int mark = hooks.mark();
        try (AppDexTargets targets = new AppDexTargets(loader, apk, dataDir)) {
            var found = targets.resolve(ActionButtonLensBinding.CACHE_FEATURE);
            if (found.isEmpty()) { hooks.info("ACTION LENS skip: native binding not unique"); return; }
            var binding = new ActionButtonLensBinding(found);
            var state = new ActionButtonLensHooks();
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
            hooks.install(HookInstaller.target(loader, "com.oplus.gesture.GestureApplication").getDeclaredMethod("onCreate"), chain -> {
                Object result = chain.proceed();
                try { state.prepare((Application) chain.getThisObject()); }
                catch (Exception failure) { hooks.error("ACTION LENS resources unavailable; scanner retained", failure); }
                return result;
            });
            hooks.install(binding.label, chain -> state.enabled() ? "智能镜头" : chain.proceed());
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
            for (int i = 6; i < found.size(); i++) hooks.deoptimize(found.get(i));
            hooks.info("ACTION LENS native scanner destination and resources installed");
        } catch (Throwable failure) {
            hooks.unhookSince(mark);
            hooks.error("ACTION LENS incompatible; original scanner retained", failure);
        }
    }

    private void prepare(Context context) throws Exception {
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
