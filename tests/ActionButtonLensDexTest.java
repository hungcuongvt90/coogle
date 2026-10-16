package io.github.zopulus.coogle;
import dalvik.system.PathClassLoader;
import java.lang.reflect.Method;
import java.util.List;
public final class ActionButtonLensDexTest {
    public static void main(String[] args) {
        try {
            ClassLoader loader = new PathClassLoader(args[0] + ":/system/framework/org.apache.http.legacy.jar"
                    + ":/system_ext/framework/com.android.extensions.appfunctions.jar:/system_ext/framework/androidx.window.extensions.jar"
                    + ":/system_ext/framework/androidx.window.sidecar.jar", ActionButtonLensDexTest.class.getClassLoader());
            List<Method> found;
            try (AppDexTargets targets = new AppDexTargets(loader, args[0], args[1])) {
                found = targets.resolve(ActionButtonLensBinding.CACHE_FEATURE);
            }
            if (found.isEmpty()) throw new AssertionError("Native action-button targets missing");
            var binding = new ActionButtonLensBinding(found);
            for (int i = 0; i < found.size(); i++) System.out.println(i + ": " + found.get(i));
            try (AppDexTargets targets = new AppDexTargets(loader, args[0], args[1])) {
                if (!found.equals(targets.resolve(ActionButtonLensBinding.CACHE_FEATURE))) throw new AssertionError("Native cache changed");
            }
            Class<?> threadClass = Class.forName("android.app.ActivityThread");
            if (android.os.Looper.myLooper() == null) android.os.Looper.prepare();
            Object thread = threadClass.getMethod("systemMain").invoke(null);
            var nativeContext = (android.content.Context) threadClass.getMethod("getSystemContext").invoke(thread);
            var original = new android.content.Intent(ActionButtonLensBinding.ACTION).setPackage(ActionButtonLensBinding.SCANNER)
                    .setData(android.net.Uri.parse("scanner://old")).addFlags(270532608).putExtra("native_marker", true);
            var lens = ActionButtonLensLaunch.destination(original, nativeContext);
            System.out.println("Resolved Lens destination: " + lens);
            var entry = nativeContext.getPackageManager().getActivityInfo(new android.content.ComponentName(
                    ActionButtonLensLaunch.PACKAGE, ActionButtonLensLaunch.ENTRY), 0);
            System.out.println("Public Lens entry: " + entry.name + ", enabled=" + entry.enabled
                    + ", exported=" + entry.exported + ", package=" + entry.packageName);
            if (!android.content.Intent.ACTION_VIEW.equals(lens.getAction()) || lens.getFlags() != 270532608
                    || !"google://lens".equals(lens.getDataString()) || !lens.hasExtra("native_marker")
                    || !"com.google.android.googlequicksearchbox".equals(lens.getPackage())
                    || lens.getComponent() == null || !ActionButtonLensLaunch.ENTRY.equals(lens.getComponent().getClassName())
                    || original.getFlags() != 270532608 || !ActionButtonLensBinding.ACTION.equals(original.getAction()))
                throw new AssertionError("Google app Lens launch or original Intent lost");
            var params = lens.getBundleExtra("lens_activity_params");
            if (params == null || params.getLong("start_activity_time_nanos") <= 0)
                throw new AssertionError("Lens SDK launch parameters missing");
            var token = params.getBinder("lens_activity_binder");
            byte[] challenge = new byte[16];
            for (int i = 0; i < challenge.length; i++) challenge[i] = (byte)i;
            verifyCaller(token, challenge, "77851775".getBytes(java.nio.charset.StandardCharsets.UTF_8), nativeContext.getPackageName());
            verifyCaller(token, new byte[0], new byte[0], nativeContext.getPackageName());
            var empty = android.os.Parcel.obtain();
            var reply = android.os.Parcel.obtain();
            try {
                empty.writeBundle(new android.os.Bundle());
                if (token.transact(1, empty, reply, 0)) throw new AssertionError("Missing caller challenge accepted");
                if (!token.transact(2, empty, null, 0)) throw new AssertionError("SDK result callback rejected");
            } finally { empty.recycle(); reply.recycle(); }
            String mapped = ActionButtonLensLaunch.availabilityPackage(ActionButtonLensBinding.SCANNER, true);
            boolean scannerAvailable = (boolean) binding.availability.invoke(null, ActionButtonLensBinding.SCANNER, 0, nativeContext);
            boolean googleAvailable = (boolean) binding.availability.invoke(null, mapped, 0, nativeContext);
            if (!googleAvailable) throw new AssertionError("Google unavailable to native package/user/hidden-app checks");
            System.out.println("Native availability: scanner=" + scannerAvailable + ", Google=" + googleAvailable);
            System.out.println("PASS: direct Lens SDK destination, native flags/extras; actual caller handshake, empty and missing challenge, result callback");
            System.out.println("PASS: real action-button DEX discovery, full native binding and cached descriptors");
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }

    private static void verifyCaller(android.os.IBinder token, byte[] challenge, byte[] expected, String caller) throws Exception {
        if (token == null) throw new AssertionError("Lens SDK caller token missing");
        var request = android.os.Parcel.obtain();
        var reply = android.os.Parcel.obtain();
        try {
            var input = new android.os.Bundle();
            input.putByteArray("challenge", challenge);
            request.writeBundle(input);
            if (!token.transact(1, request, reply, 0)) throw new AssertionError("Caller handshake rejected");
            reply.readException();
            var result = reply.readBundle(android.os.Bundle.class.getClassLoader());
            if (result == null || result.getInt("version") != 1 || !caller.equals(result.getString("caller_package"))
                    || !java.util.Arrays.equals(expected, result.getByteArray("challenge_reply")))
                throw new AssertionError("Actual caller or Lens SDK challenge response changed");
        } finally { request.recycle(); reply.recycle(); }
    }
}
