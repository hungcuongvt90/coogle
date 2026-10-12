package io.github.zopulus.coogle;

import dalvik.system.DexClassLoader;
import java.lang.reflect.Method;
import java.util.List;

/** Runs the production scanner against synthetic DEX files on Android. */
public final class LensDexTest {
    public static void main(String[] args) {
        try { run(args); }
        catch (Throwable failure) { failure.printStackTrace(System.err); System.exit(1); }
    }
    private static void run(String[] args) throws Exception {
        String directory = args[0];
        System.out.println("Lens DEX scanner test started");
        for (String name : new String[]{"old-event", "new-event", "ambiguous", "wrong-shape", "missing-talk"}) {
            String path = directory + "/" + name + ".zip";
            ClassLoader loader = new DexClassLoader(path, directory, directory, LensDexTest.class.getClassLoader());
            try (AppDexTargets targets = new AppDexTargets(loader, path, null)) {
                System.out.println("Scanning: " + name);
                List<Method> methods = targets.resolve(FeaturePolicy.LENS);
                if (name.endsWith("event")) {
                    check(methods.size() >= 3, name + ": eligibility pair/callers missing");
                    check(methods.get(0).getDeclaringClass().getName().equals("fixture.RenamedCreate"), "Wrong create target");
                    check(methods.get(1).getDeclaringClass().getName().equals("fixture.RenamedTalk"), "Wrong talk target");
                    check(methods.stream().skip(2).anyMatch(m -> m.getDeclaringClass().getName().equals("fixture.Caller")), "Caller graph missing");
                } else check(methods.isEmpty(), name + ": incompatible/ambiguous DEX accepted");
                System.out.println("PASS: " + name);
            }
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
