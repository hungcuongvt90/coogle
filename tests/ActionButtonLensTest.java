package io.github.zopulus.coogle;

import android.content.Context;
import android.content.Intent;
import com.oplus.gesture.actionbuttonkey.ActionButtonSettingsActivity;
import java.util.List;

public final class ActionButtonLensTest {
    public static class NativeLauncher {
        public Context nativeContext = new Context();
        public void dispatch(Intent intent, String scene, String owner) { }
        public void bind(Intent intent, String scene) { }
        public static boolean available(String owner, int user, Context context) {
            return !googleHidden && "com.google.android.googlequicksearchbox".equals(owner) && user == 10;
        }
    }
    public static class NativeHint {
        static NativeHint instance = new NativeHint();
        String text; Object icon;
        public static NativeHint get(Context context) { return instance; }
        public void show(int flag, String owner, String text, Object icon, String sideText, boolean show) {
            check(flag == 3 && "io.github.zopulus.coogle".equals(owner) && text.equals(sideText) && !show, "Native hint arguments changed");
            this.text = text; this.icon = icon;
        }
    }
    private static boolean googleHidden;
    public static class AmbiguousLauncher {
        public Context first, second;
        public void dispatch(Intent intent, String scene, String owner) { }
        public void bind(Intent intent, String scene) { }
    }
    public static void main(String[] args) throws Exception {
        var label = ActionButtonSettingsActivity.class.getMethod("scannerLabel");
        var dispatch = NativeLauncher.class.getMethod("dispatch", Intent.class, String.class, String.class);
        var availability = NativeLauncher.class.getMethod("available", String.class, int.class, Context.class);
        var binder = NativeLauncher.class.getMethod("bind", Intent.class, String.class);
        var factory = NativeHint.class.getMethod("get", Context.class);
        var hint = NativeHint.class.getMethod("show", int.class, String.class, String.class, Object.class, String.class, boolean.class);
        var binding = new ActionButtonLensBinding(List.of(label, dispatch, availability, binder, factory, hint));
        var host = new NativeLauncher();
        check(binding.context(host) == host.nativeContext, "Native context not preserved");
        check(!ActionButtonLensBinding.valid(List.of(label)), "Partial binding accepted");
        check(!ActionButtonLensBinding.valid(List.of(label, dispatch)), "Old cache without availability gate accepted");
        check(!ActionButtonLensBinding.valid(List.of(ActionButtonSettingsActivity.class.getMethod("staticLabel"), dispatch, availability, binder, factory, hint)),
                "Static label accepted");
        try {
            new ActionButtonLensBinding(List.of(label, AmbiguousLauncher.class.getMethod("dispatch", Intent.class, String.class, String.class), availability,
                    AmbiguousLauncher.class.getMethod("bind", Intent.class, String.class), factory, hint));
            throw new AssertionError("Ambiguous context accepted");
        } catch (NoSuchFieldException expected) { }
        check(!NativeLauncher.available(ActionButtonLensBinding.SCANNER, 10, host.nativeContext), "Missing scanner fixture invalid");
        String target = ActionButtonLensLaunch.availabilityPackage(ActionButtonLensBinding.SCANNER, true);
        check((boolean) binding.availability.invoke(null, target, 10, host.nativeContext), "Uninstalled scanner still blocks installed Google");
        check(!(boolean) binding.availability.invoke(null, target, 11, host.nativeContext), "Current-user check bypassed");
        googleHidden = true;
        check(!(boolean) binding.availability.invoke(null, target, 10, host.nativeContext), "Native hidden-app check bypassed");
        googleHidden = false;
        check(ActionButtonLensBinding.SCANNER.equals(ActionButtonLensLaunch.availabilityPackage(ActionButtonLensBinding.SCANNER, false)), "Disabled switch alters native availability");
        check("other.app".equals(ActionButtonLensLaunch.availabilityPackage("other.app", true)), "Other app availability redirected");
        check(ActionButtonLensLaunch.availabilityPackage(null, true) == null, "Missing package redirected");
        Intent service = new Intent().setComponent(new android.content.ComponentName(ActionButtonLensBinding.SCANNER,
                "com.oplus.scanner.ui.keymagic.KeyMagicService"));
        check(ActionButtonLensBinding.matchesShortPress(service, "ocrscanner"), "Scanner hint service missed");
        Intent otherOwner = new Intent(service).setComponent(new android.content.ComponentName("other.app",
                "com.oplus.scanner.ui.keymagic.KeyMagicService"));
        check(!ActionButtonLensBinding.matchesShortPress(otherOwner, "ocrscanner"), "Other owner service replaced");
        Intent otherService = new Intent(service).setComponent(new android.content.ComponentName(ActionButtonLensBinding.SCANNER,
                "OtherService"));
        check(!ActionButtonLensBinding.matchesShortPress(otherService, "ocrscanner"), "Other scanner service replaced");
        check(!ActionButtonLensBinding.matchesShortPress(service, "camera"), "Other scene hint replaced");
        check(!ActionButtonLensBinding.matchesShortPress(new Intent(), "ocrscanner"), "Unspecified service replaced");
        check(!ActionButtonLensBinding.matchesShortPress(null, "ocrscanner"), "Null service replaced");
        binding.showHint(host.nativeContext, 42);
        check("长按使用智能镜头".equals(NativeHint.instance.text) && Integer.valueOf(42).equals(NativeHint.instance.icon), "Native Lens hint lost");
        check(ActionButtonLensBinding.matches("ocrscanner", "com.coloros.ocrscanner", "coloros.ocrscanner.intent.action.OSEE"), "Scanner destination missed");
        check(!ActionButtonLensBinding.matches("camera", "com.coloros.ocrscanner", "coloros.ocrscanner.intent.action.OSEE"), "Other scene redirected");
        check(!ActionButtonLensBinding.matches("ocrscanner", "other.app", "coloros.ocrscanner.intent.action.OSEE"), "Other owner redirected");
        check(!ActionButtonLensBinding.matches("ocrscanner", "com.coloros.ocrscanner", "OTHER_ACTION"), "Other action redirected");
        check(!ActionButtonLensBinding.matches(null, null, null), "Missing destination redirected");
        Intent original = new Intent(ActionButtonLensBinding.ACTION).setPackage(ActionButtonLensBinding.SCANNER)
                .setComponent(new android.content.ComponentName(ActionButtonLensBinding.SCANNER, "ScannerActivity"))
                .setData(android.net.Uri.parse("scanner://original")).addFlags(270532608).putExtra("native_marker", true);
        var manager = host.nativeContext.manager;
        Intent lens = ActionButtonLensLaunch.destination(original, host.nativeContext);
        check(lens != original && ActionButtonLensBinding.ACTION.equals(original.getAction())
                && ActionButtonLensBinding.SCANNER.equals(original.getPackage())
                && original.getComponent() != null && "ScannerActivity".equals(original.getComponent().getClassName())
                && original.getFlags() == 270532608 && "scanner://original".equals(original.getDataString()), "Original Intent mutated");
        check(Intent.ACTION_VIEW.equals(lens.getAction()) && "com.google.android.googlequicksearchbox".equals(lens.getPackage())
                && "google://lens".equals(lens.getDataString()), "Google app Lens destination missing");
        check(lens.getFlags() == original.getFlags() && lens.hasExtra("native_marker"), "Native shortcut flags or extras discarded");
        check(lens.getBundleExtra("lens_activity_params") != null, "Direct Lens SDK caller protocol missing");
        check(lens.getBundleExtra("lens_activity_params").getBinder("lens_activity_binder") instanceof ActionButtonLensCaller,
                "Direct Lens SDK caller callback missing");
        check(lens.getComponent() != null && "com.google.android.apps.lens.MainActivity".equals(lens.getComponent().getClassName()),
                "Public Lens entry not preferred");
        manager.lensExported = false;
        check(ActionButtonLensLaunch.destination(original, host.nativeContext) == null, "Non-exported entry targeted");
        manager.lensExported = true; manager.lensEnabled = false;
        check(ActionButtonLensLaunch.destination(original, host.nativeContext) == null, "Disabled entry targeted");
        manager.lensEnabled = true; manager.lensEntryPresent = false;
        check(ActionButtonLensLaunch.destination(original, host.nativeContext) == null, "Removed entry silently uses another route");
        check(!FeaturePolicy.defaultValue("action_button_lens"), "New shortcut feature enabled without user choice");
        var prefs = (android.content.SharedPreferences) java.lang.reflect.Proxy.newProxyInstance(
                android.content.SharedPreferences.class.getClassLoader(), new Class<?>[]{android.content.SharedPreferences.class},
                (p, m, a) -> "action_button_lens".equals(a[0]));
        FeatureConfig.attach(prefs);
        check(FeatureConfig.enabled("action_button_lens") && !FeatureConfig.enabled(FeaturePolicy.LENS), "Shortcut depends on existing Lens switch");
        verifyBootInitialization();
        System.out.println("PASS: uninstalled scanner, native user/hidden-app checks, exact short-press hint, Google destination and independent switch");
    }

    private static void verifyBootInitialization() throws Exception {
        var constructor = ActionButtonLensHooks.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var prepare = ActionButtonLensHooks.class.getDeclaredMethod("prepare", Context.class);
        prepare.setAccessible(true);
        var enabled = ActionButtonLensHooks.class.getDeclaredMethod("enabled");
        enabled.setAccessible(true);
        var state = constructor.newInstance();
        var context = new Context();
        context.manager.lensEntryPresent = false; // First unlock has not exposed Lens yet.
        prepare.invoke(state, context);
        check((boolean) enabled.invoke(state), "Pre-unlock Lens visibility permanently blocked resource initialization");
        check(context.manager.activityReads == 0, "Resource initialization queried the Lens entry");
        var original = new Intent(ActionButtonLensBinding.ACTION);
        check(ActionButtonLensLaunch.destination(original, context) == null, "Unavailable pre-unlock entry targeted");
        context.manager.lensEntryPresent = true;
        check((boolean) enabled.invoke(state) && ActionButtonLensLaunch.destination(original, context) != null,
                "Unlock requires resource reinitialization or a gesture-process restart");

        var incomplete = constructor.newInstance();
        context.resources.missingIdentifiers = true;
        prepare.invoke(incomplete, context);
        check(!(boolean) enabled.invoke(incomplete), "Missing native icons accepted");
        context.resources.missingIdentifiers = false;
        context.manager.moduleResources.missingDrawable = true;
        var missingArtwork = constructor.newInstance();
        try {
            prepare.invoke(missingArtwork, context);
            throw new AssertionError("Missing module artwork accepted");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            check(expected.getCause() instanceof android.content.res.Resources.NotFoundException,
                    "Unexpected resource initialization failure");
        }
        check(!(boolean) enabled.invoke(missingArtwork), "Failed resources left feature ready");
        FeatureConfig.attach(null);
        check(!(boolean) enabled.invoke(state), "Prepared resources bypassed the disabled feature switch");
        System.out.println("PASS: resources initialize before first unlock; next launch recovers without retry, missing resources and disabled switch remain guarded");
    }
    private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
}
