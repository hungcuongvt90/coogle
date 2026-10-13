package io.github.zopulus.coogle;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class CompanionSettingsTest {
    public static void main(String[] args) throws Exception {
        concurrentSaveAndResume();
        Prefs prefs = new Prefs(); Support support = new Support();
        check(!CompanionSettings.shouldCheck(prefs.api),"First open requested unsolicited root checks");
        prefs.values.put("companion_ready",true);
        check(CompanionSettings.shouldCheck(prefs.api),"Existing installed companion lost automatic checks");
        prefs.values.clear(); prefs.values.put("companion_known",true);
        check(CompanionSettings.shouldCheck(prefs.api),"Known update lost retry detection");
        prefs.values.clear();
        CompanionSettings control = new CompanionSettings(support);
        for (String state : new String[]{"MISSING", "DISABLED"}) {
            prefs.values.put(FeaturePolicy.HEY_GOOGLE, true); prefs.values.put("companion_ready", true);
            support.state = state; support.enabled = true;
            control.sync(prefs.api);
            check(!prefs.value(FeaturePolicy.HEY_GOOGLE) && !prefs.value("companion_ready"), "Unavailable support retained stale enablement: " + state);
            if (state.equals("DISABLED")) check(!support.enabled, "Disabled support retained enabled payload choice");
            else check(support.enabled, "Missing or legacy support was modified");
            try { control.save(prefs.api, true); throw new AssertionError("Unavailable support accepted"); }
            catch (java.io.IOException expected) {}
            check(!prefs.value(FeaturePolicy.HEY_GOOGLE), "Failed enable changed preferences");
            control.save(prefs.api, false);
        }
        for (boolean enabled : new boolean[]{false,true}) {
            prefs.values.put(FeaturePolicy.HEY_GOOGLE,enabled); prefs.values.put("companion_ready",true);
            support.state="UPDATE_REQUIRED"; support.enabled=enabled;
            control.sync(prefs.api);
            check(prefs.value(FeaturePolicy.HEY_GOOGLE)==enabled && support.enabled==enabled,
                    "Pending payload update discarded the user's Hey Google choice");
            check(!prefs.value("companion_ready") && prefs.value("companion_known"),"Old payload was not remembered as needing an update");
            control.install(null,prefs.api,false);
            check(prefs.value(FeaturePolicy.HEY_GOOGLE)==enabled && support.enabled==enabled,
                    "Companion update changed the saved Hey Google choice");
            check(prefs.value("companion_ready"),"Updated staged payload not marked usable");
        }
        prefs.values.put(FeaturePolicy.HEY_GOOGLE,false);
        control.install(null,prefs.api,true);
        check(prefs.value(FeaturePolicy.HEY_GOOGLE) && support.enabled,"Explicit Hey Google installation did not enable it");
        support.state = "PENDING"; control.save(prefs.api, true);
        check(prefs.value(FeaturePolicy.HEY_GOOGLE) && prefs.value("companion_ready"), "Staged install rejected");
        prefs.commitSucceeds = false;
        try { control.save(prefs.api, false); throw new AssertionError("Failed commit accepted"); }
        catch (java.io.IOException expected) {}
        check(support.enabled && prefs.value(FeaturePolicy.HEY_GOOGLE), "Failed save did not restore previous root setting");
        prefs.changeMemoryOnFailure = true;
        try { control.save(prefs.api, false); throw new AssertionError("Failed in-memory commit accepted"); }
        catch (java.io.IOException expected) {}
        check(support.enabled && prefs.value(FeaturePolicy.HEY_GOOGLE), "Failed commit left preferences and payload out of sync");
        prefs.commitSucceeds = true;
        int commits = prefs.commits; control.sync(prefs.api);
        check(prefs.commits == commits, "Matching automatic sync rewrote preferences");
        System.out.println("PASS: serialized settings, update choice preservation, install intent, known-state detection and rollback");
    }
    private static void concurrentSaveAndResume() throws Exception {
        Prefs prefs = new Prefs(); prefs.values.put(FeaturePolicy.HEY_GOOGLE, true);
        Support support = new Support(); support.pause = true;
        CompanionSettings control = new CompanionSettings(support);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread save = new Thread(() -> { try { control.save(prefs.api, false); } catch (Throwable e) { failure.set(e); } });
        Thread resume = new Thread(() -> { try { control.sync(prefs.api); } catch (Throwable e) { failure.set(e); } });
        save.start(); check(support.entered.await(2, TimeUnit.SECONDS), "Save did not start"); resume.start();
        boolean overlapped = support.synced.await(200, TimeUnit.MILLISECONDS);
        support.release.countDown(); save.join(2000); resume.join(2000);
        check(!save.isAlive() && !resume.isAlive(), "Operations did not finish");
        if (failure.get() != null) throw new AssertionError(failure.get());
        check(!overlapped && !support.enabled && !prefs.value(FeaturePolicy.HEY_GOOGLE), "Resume synchronization restored stale enablement");
    }
    private static final class Support implements CompanionSettings.Support {
        String state = "READY";
        volatile boolean enabled = true;
        boolean pause;
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), synced = new CountDownLatch(1);
        public String check() { return state; }
        public String set(boolean enabled) throws Exception {
            if (!state.equals("MISSING") && !state.equals("UPDATE_REQUIRED")) this.enabled = enabled;
            if (pause) { pause = false; entered.countDown(); CompanionSettingsTest.check(release.await(2, TimeUnit.SECONDS), "Save release timed out"); }
            return state;
        }
        public String sync(boolean enabled) {
            if (state.equals("READY") || state.equals("PENDING")) this.enabled = enabled;
            synced.countDown(); return state;
        }
        public void install(android.content.Context context) { state="PENDING"; }
    }
    private static final class Prefs {
        final Map<String, Boolean> values = new HashMap<>();
        boolean commitSucceeds = true;
        boolean changeMemoryOnFailure;
        int commits;
        final SharedPreferences api = (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (p, m, a) -> {
                    if (m.getName().equals("getBoolean")) return values.getOrDefault((String)a[0], (Boolean)a[1]);
                    if (!m.getName().equals("edit")) throw new AssertionError(m.getName());
                    Map<String, Boolean> pending = new HashMap<>();
                    return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                            new Class<?>[]{SharedPreferences.Editor.class}, (editor, method, args) -> {
                                if (method.getName().equals("putBoolean")) { pending.put((String)args[0], (Boolean)args[1]); return editor; }
                                if (method.getName().equals("commit")) { commits++; if (commitSucceeds || changeMemoryOnFailure) values.putAll(pending); return commitSucceeds; }
                                throw new AssertionError(method.getName());
                            });
                });
        boolean value(String key) { return values.getOrDefault(key, false); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
