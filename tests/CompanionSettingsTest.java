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
        CompanionSettings control = new CompanionSettings(support);
        for (String state : new String[]{"MISSING", "DISABLED", "UPDATE_REQUIRED"}) {
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
        System.out.println("PASS: concurrent save/resume, staged readiness, unavailable support and commit rollback");
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
