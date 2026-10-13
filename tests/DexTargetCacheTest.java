package io.github.zopulus.coogle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.luckypray.dexkit.wrap.DexMethod;

/** Runs without a DexKit native library: valid caches must not initialize the scanner. */
public final class DexTargetCacheTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("coogle-cache-test");
        Path apk = dir.resolve("base.apk");
        Path cache = dir.resolve("coogle_app_targets_v1.cache");
        try {
            Files.write(apk, new byte[]{1});
            Properties stale = new Properties();
            stale.setProperty("apk", "1:" + apk + ":" + apk.toFile().lastModified() + ":" + apk.toFile().length());
            stale.setProperty(FeaturePolicy.LENS, "");
            try (var output = Files.newOutputStream(cache)) { stale.store(output, "pre-fix negative cache"); }
            try (var targets = new AppDexTargets(DexTargetCacheTest.class.getClassLoader(), apk.toString(), dir.toString())) {
                try { targets.resolve(FeaturePolicy.LENS); throw new AssertionError("Old negative cache survived the locator update"); }
                catch (UnsatisfiedLinkError expected) { /* Cache was invalidated and a fresh scan was requested. */ }
            }

            var create = LensBindingTest.RenamedCreate.class.getDeclaredMethod("renamed");
            var talk = LensBindingTest.RenamedTalk.class.getDeclaredMethod("renamed");
            Properties stored = new Properties();
            stored.setProperty("apk", "2:" + apk + ":" + apk.toFile().lastModified() + ":" + apk.toFile().length());
            stored.setProperty(FeaturePolicy.LENS, new DexMethod(create).serialize() + "\n" + new DexMethod(talk).serialize());
            stored.setProperty(FeaturePolicy.WALLET, "");
            stored.setProperty(FeaturePolicy.CAMERA_LENS, "");
            try (var output = Files.newOutputStream(cache)) { stored.store(output, "fixture"); }
            try (var targets = new AppDexTargets(DexTargetCacheTest.class.getClassLoader(), apk.toString(), dir.toString())) {
                var methods = targets.resolve(FeaturePolicy.LENS);
                if (!methods.equals(java.util.List.of(create, talk))) throw new AssertionError("Cached methods changed");
                if (!targets.resolve(FeaturePolicy.WALLET).isEmpty()) throw new AssertionError("Negative cache ignored");
                try { targets.resolve(FeaturePolicy.CAMERA_LENS); throw new AssertionError("Former camera miss suppressed native lookup"); }
                catch (UnsatisfiedLinkError expected) { /* A new native binding requires its own cache key. */ }
            }
            Files.write(apk, new byte[]{1,2});
            try (var targets = new AppDexTargets(DexTargetCacheTest.class.getClassLoader(), apk.toString(), dir.toString())) {
                try { targets.resolve(FeaturePolicy.WALLET); throw new AssertionError("APK update retained negative cache"); }
                catch (UnsatisfiedLinkError expected) { /* Scanner was attempted; no native library in this test. */ }
            }
            System.out.println("PASS: locator update invalidates old misses; valid caches skip native scanning; APK update invalidates targets");
        } finally {
            Files.deleteIfExists(cache); Files.deleteIfExists(apk); Files.deleteIfExists(dir);
        }
    }
}
