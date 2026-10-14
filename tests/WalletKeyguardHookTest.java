package io.github.zopulus.coogle;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import com.android.internal.policy.IKeyguardDismissCallback;
import com.android.systemui.keyguard.KeyguardViewMediator;
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager;
import io.github.libxposed.api.XposedInterface;
import io.github.zopulus.coogle.migration.compat.ModernHookRuntime;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Exercises the installed Kotlin callbacks against the observed original-handler/reset race. */
public final class WalletKeyguardHookTest {
    private static final String WALLET = "com.google.android.apps.walletnfcrel";
    private static final String QUICKDRAW = "com.google.android.apps.wallet.main.QUICKDRAW";

    public static void main(String[] args) throws Throwable {
        nativeResetPrecedesOriginalDismissal();
        unrelatedRequestsRetainOriginalHandler();
        queuedRequestSurvivesSwitchChange();
        queuedRequestSurvivesUserChange();
        timeoutAndSchedulingFailure();
        failedQueriesRetainOriginalHandler();
        failedResetDoesNotReleaseEarly();
        failedDispatchDoesNotForgeResultsOrPoisonNextRequest();
        System.out.println("WalletKeyguardHookTest passed");
    }

    private static void nativeResetPrecedesOriginalDismissal() throws Throwable {
        // Establish the unmodified failure: a native request followed by reset is cancelled.
        Fixture baseline = new Fixture();
        baseline.mediator.mHandler.handleMessage(baseline.message);
        baseline.manager.reset(true);
        check(baseline.callback.cancelled == 1 && baseline.callback.succeeded == 0,
                "Native test fixture did not reproduce request-before-reset cancellation");

        Fixture fixture = new Fixture();
        fixture.request();
        check(fixture.mediator.handled == 0 && fixture.callback.cancelled == 0 && fixture.callback.succeeded == 0,
                "Hook ran original dismissal or forged authentication before native reset");
        check(Handler.tasks.size() == 1 && Handler.tasks.get(0).delay() == 500L,
                "Accepted request did not receive one 500ms fallback");
        Runnable oldTimer = Handler.tasks.get(0).callback();
        fixture.reset();
        check(fixture.mediator.events.equals(List.of("native reset")) && Handler.tasks.isEmpty(),
                "Hook forwarded during reset or failed to remove fallback");
        check(fixture.mediator.mHandler.messages.size() == 1, "Native reset did not forward original request");
        Message copy = fixture.mediator.mHandler.messages.get(0);
        check(copy != fixture.message && copy.obj == fixture.message.obj && copy.what == fixture.message.what &&
                        copy.arg1 == fixture.message.arg1 && copy.arg2 == fixture.message.arg2 &&
                        copy.getTarget() == fixture.message.getTarget() && copy.getCallback() == fixture.message.getCallback() &&
                        copy.replyTo == fixture.message.replyTo && copy.sendingUid == fixture.message.sendingUid &&
                        copy.getData() != fixture.message.getData() && "original payload".equals(copy.getData().getString("payload")),
                "Original dismissal payload/arguments were rebuilt or lost");
        check(((KeyguardViewMediator.DismissMessage) copy.obj).mCallback == fixture.callback,
                "Module replaced original authentication callback");
        fixture.drain();
        check(fixture.mediator.handled == 1 && fixture.mediator.events.equals(List.of("native reset", "native request")),
                "Real original handler did not run after native reset exactly once");
        check(fixture.callback.succeeded == 0 && fixture.callback.cancelled == 0,
                "Forwarding invented authentication success/cancellation");
        fixture.mediator.activeRequests.get(0).onDismissSucceeded();
        check(fixture.callback.succeeded == 1 && fixture.callback.cancelled == 0,
                "Original success callback was not preserved");
        oldTimer.run();
        check(fixture.mediator.mHandler.messages.isEmpty() && fixture.mediator.handled == 1,
                "Stale fallback replayed completed request");

        Fixture cancelled = new Fixture();
        cancelled.request(); cancelled.reset(); cancelled.drain();
        cancelled.mediator.activeRequests.get(0).onDismissCancelled();
        check(cancelled.callback.cancelled == 1 && cancelled.callback.succeeded == 0,
                "Original cancellation was converted into authentication success");
    }

    private static void unrelatedRequestsRetainOriginalHandler() throws Throwable {
        List<Consumer<Fixture>> changes = List.of(
                f -> f.enabled[0] = false,
                f -> f.task.baseIntent.setAction("android.intent.action.MAIN"),
                f -> f.task.topActivity = new ComponentName("another.app", "Activity"),
                f -> f.task.userId = 11,
                f -> f.mediator.mOccluded = true,
                f -> f.mediator.mShowing = false,
                f -> f.message.obj = "ordinary message",
                f -> f.message.obj = new KeyguardViewMediator.DismissMessage(null, "no callback"),
                f -> f.task.topActivity = null,
                f -> f.task.baseIntent = null,
                f -> f.mediator.mContext.activityManager.runningTasks.clear());
        int index = 0;
        for (Consumer<Fixture> change : changes) {
            Fixture fixture = new Fixture(); change.accept(fixture); fixture.request();
            check(fixture.mediator.handled == 1 && fixture.mediator.lastReceived == fixture.message,
                    "Unrelated request did not retain original handler once: case " + index);
            check(Handler.tasks.isEmpty() && fixture.mediator.mHandler.messages.isEmpty(),
                    "Unrelated request scheduled Wallet work: case " + index++);
        }
    }

    private static void queuedRequestSurvivesSwitchChange() throws Throwable {
        Fixture fixture = new Fixture(); fixture.request(); fixture.enabled[0] = false;
        fixture.reset(); fixture.drain();
        check(fixture.mediator.handled == 1 && Handler.tasks.isEmpty(),
                "Switch change dropped already accepted native authentication request");
        check(fixture.callback.succeeded == 0 && fixture.callback.cancelled == 0,
                "Switch change manufactured authentication result");
    }

    private static void timeoutAndSchedulingFailure() throws Throwable {
        Fixture fallback = new Fixture(); fallback.request();
        Handler.tasks.get(0).callback().run(); fallback.drain();
        check(fallback.mediator.handled == 1 && fallback.mediator.lastReceived.obj == fallback.message.obj,
                "Missing occlusion event stranded original request or changed payload");
        check(fallback.callback.succeeded == 0 && fallback.callback.cancelled == 0,
                "Fallback bypassed original authentication");

        Fixture failed = new Fixture(); failed.mediator.mHandler.acceptPosts = false; failed.request();
        check(failed.mediator.handled == 1 && failed.mediator.lastReceived == failed.message && Handler.tasks.isEmpty(),
                "Scheduling failure suppressed original native request");
        check(failed.mediator.mHandler.messages.isEmpty(), "Rejected scheduling retained request for duplicate forwarding");
    }

    private static void queuedRequestSurvivesUserChange() throws Throwable {
        Fixture fixture = new Fixture(); fixture.request();
        fixture.task.userId = 11; fixture.mediator.mSelectedUserInteractor.userId = 11;
        fixture.reset(); fixture.drain();
        check(fixture.mediator.handled == 1 && fixture.mediator.lastReceived.obj == fixture.message.obj,
                "User switch lost an accepted native callback instead of allowing the original handler to decide");
        check(fixture.callback.succeeded == 0 && fixture.callback.cancelled == 0,
                "Module granted/cancelled authentication on behalf of a different user");
        fixture.mediator.activeRequests.get(0).onDismissCancelled();
        check(fixture.callback.cancelled == 1 && fixture.callback.succeeded == 0,
                "User-switch cancellation from original handler was altered");
    }

    private static void failedQueriesRetainOriginalHandler() throws Throwable {
        for (Consumer<Fixture> change : List.<Consumer<Fixture>>of(
                f -> f.mediator.mContext.failServiceQuery = true,
                f -> f.mediator.mContext.activityManager.failTasks = true,
                f -> f.mediator.mSelectedUserInteractor.fail = true,
                f -> f.mediator.mSelectedUserInteractor = null,
                f -> f.mediator.mContext = null)) {
            Fixture fixture = new Fixture(); change.accept(fixture); fixture.request();
            check(fixture.mediator.handled == 1 && fixture.mediator.lastReceived == fixture.message && Handler.tasks.isEmpty(),
                    "Reflection/service/task-query failure swallowed original native request");
        }
    }

    private static void failedResetDoesNotReleaseEarly() throws Throwable {
        Fixture fixture = new Fixture(); fixture.request(); fixture.manager.fail = true;
        try { fixture.reset(); throw new AssertionError("Native reset failure swallowed"); }
        catch (IllegalStateException expected) { }
        check(fixture.mediator.handled == 0 && fixture.mediator.mHandler.messages.isEmpty() && Handler.tasks.size() == 1,
                "Failed native reset released authentication into unfinished transition");
        Handler.tasks.get(0).callback().run(); fixture.drain();
        check(fixture.mediator.handled == 1, "Native reset failure lost bounded original-request fallback");
    }

    private static void failedDispatchDoesNotForgeResultsOrPoisonNextRequest() throws Throwable {
        for (boolean viaTimer : new boolean[]{false, true}) for (boolean throwsError : new boolean[]{false, true}) {
            Fixture fixture = new Fixture(); fixture.request();
            fixture.mediator.mHandler.acceptMessages = throwsError;
            fixture.mediator.mHandler.failMessages = throwsError;
            if (viaTimer) Handler.tasks.get(0).callback().run(); else fixture.reset();
            check(fixture.mediator.handled == 0 && fixture.callback.succeeded == 0 && fixture.callback.cancelled == 0,
                    "Failed native dispatch forged an authentication result");
            fixture.mediator.mHandler.acceptMessages = true; fixture.mediator.mHandler.failMessages = false;
            fixture.mediator.mOccluded = false; fixture.request(); fixture.reset(); fixture.drain();
            check(fixture.mediator.handled == 1 && fixture.mediator.mHandler.messages.isEmpty(),
                    "Failed dispatch poisoned later native authentication requests");
        }
    }

    private static final class Callback implements IKeyguardDismissCallback {
        int succeeded, cancelled;
        @Override public void onDismissSucceeded() { succeeded++; }
        @Override public void onDismissCancelled() { cancelled++; }
    }

    private static final class Fixture {
        final boolean[] enabled = {true};
        final KeyguardViewMediator mediator = new KeyguardViewMediator();
        final StatusBarKeyguardViewManager manager = new StatusBarKeyguardViewManager(mediator);
        final ActivityManager.RunningTaskInfo task = new ActivityManager.RunningTaskInfo();
        final Callback callback = new Callback();
        final Message message = Message.obtain();
        final Method handle = KeyguardViewMediator.NativeHandler.class.getMethod("handleMessage", Message.class);
        final Method reset = StatusBarKeyguardViewManager.class.getMethod("reset", boolean.class);

        Fixture() throws Throwable {
            Handler.tasks.clear();
            task.topActivity = new ComponentName(WALLET, "PresentationModeActivity");
            task.baseIntent = new Intent(QUICKDRAW); task.userId = 10;
            mediator.mContext.activityManager.runningTasks.add(task);
            message.what = 417; message.arg1 = 23; message.arg2 = 51; message.sendingUid = 10301;
            message.setTarget(mediator.mHandler); message.replyTo = new android.os.Messenger(mediator.mHandler);
            message.getData().putString("payload", "original payload");
            message.obj = new KeyguardViewMediator.DismissMessage(callback, "original reason");
            FeatureConfig.attach((SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                    new Class<?>[]{SharedPreferences.class}, (proxy, method, arguments) -> method.getName().equals("getBoolean") ? enabled[0] : null));
            ModernHookRuntime.INSTANCE.module.callbacks.clear();
            Class<?> hook = Class.forName("io.github.zopulus.coogle.migration.WalletKeyguardHooks");
            hook.getMethod("hook", ClassLoader.class).invoke(hook.getField("INSTANCE").get(null), getClass().getClassLoader());
            check(ModernHookRuntime.INSTANCE.module.callbacks.size() == 2 &&
                    ModernHookRuntime.INSTANCE.module.callbacks.containsKey(handle) && ModernHookRuntime.INSTANCE.module.callbacks.containsKey(reset),
                    "Real Wallet ordering callbacks were not installed");
        }

        void request() throws Throwable { invoke(handle, mediator.mHandler, message); }
        void reset() throws Throwable { invoke(reset, manager, true); }
        void drain() throws Throwable {
            for (Message forwarded : new ArrayList<>(mediator.mHandler.messages)) {
                mediator.mHandler.messages.remove(forwarded);
                invoke(handle, mediator.mHandler, forwarded);
            }
        }
    }

    private static Object invoke(Method method, Object owner, Object argument) throws Throwable {
        XposedInterface.Hooker callback = ModernHookRuntime.INSTANCE.module.callbacks.get(method);
        check(callback != null, "Missing production callback");
        XposedInterface.Chain chain = (XposedInterface.Chain) Proxy.newProxyInstance(XposedInterface.Chain.class.getClassLoader(),
                new Class<?>[]{XposedInterface.Chain.class}, (proxy, accessed, args) -> switch (accessed.getName()) {
                    case "getExecutable" -> method;
                    case "getThisObject" -> owner;
                    case "getArgs" -> List.of(argument);
                    case "proceed" -> {
                        try { yield method.invoke(owner, (Object[]) args[0]); }
                        catch (InvocationTargetException error) { throw error.getCause(); }
                    }
                    default -> throw new AssertionError("Unexpected production hook access " + accessed.getName());
                });
        return callback.intercept(chain);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
