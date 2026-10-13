package io.github.zopulus.coogle;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import java.lang.ref.WeakReference;
import java.util.List;
import android.os.Handler;
import android.os.Looper;

/** One process listener; activities are never retained by the framework service. */
final class SettingsConnection {
    private static WeakReference<MainActivity> activity = new WeakReference<>(null);
    private static XposedService service;
    private static boolean registered;
    private static final ScopeRequests scopes = new ScopeRequests();
    private static final Handler main = new Handler(Looper.getMainLooper());

    static synchronized void connect(MainActivity value) {
        activity = new WeakReference<>(value);
        if (!registered) {
            registered = true;
            XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
                @Override public void onServiceBind(XposedService value) { update(value); }
                @Override public void onServiceDied(XposedService value) { disconnected(value); }
            });
        }
        value.onFrameworkChanged(service);
        value.onScopePending(scopes.pending());
    }

    private static synchronized void update(XposedService value) {
        if (service != value) scopes.cancel();
        service = value;
        MainActivity target = activity.get();
        if (target != null) main.post(() -> {
            synchronized (SettingsConnection.class) {
                if (service != value || activity.get() != target || target.isDestroyed()) return;
                target.onScopePending(scopes.pending());
                target.onFrameworkChanged(value);
            }
        });
    }

    private static synchronized void disconnected(XposedService value) {
        if (service == value) update(null);
    }

    static synchronized void requestScopes(String feature) {
        XposedService current = service;
        if (current == null) return;
        List<String> required = FeatureScopes.required(feature);
        scopes.ensure(new ScopeRequests.Service() {
            public List<String> scope() { return current.getScope(); }
            public void request(List<String> packages, java.util.function.Consumer<String> completion) {
                current.requestScope(packages, new XposedService.OnScopeEventListener() {
                    @Override public void onScopeRequestApproved(List<String> approved) { completion.accept(null); }
                    @Override public void onScopeRequestFailed(String message) {
                        completion.accept("作用域请求未完成，请在 LSPosed 中手动勾选对应应用后重试。");
                    }
                });
            }
        }, required, message -> scopeResult(current, feature, message));
        MainActivity target = activity.get();
        if (target != null) target.onScopePending(scopes.pending());
    }

    private static void scopeResult(XposedService source, String feature, String message) {
        // Never acquire the connection lock from a Binder callback holding the request lock.
        main.post(() -> {
            synchronized (SettingsConnection.class) {
                MainActivity target = activity.get();
                if (service != source || target == null || target.isDestroyed()) return;
                target.onScopePending(false);
                target.onScopeResult(feature, message);
            }
        });
    }

    static synchronized void detach(MainActivity value) {
        if (activity.get() == value) activity.clear();
    }
    static synchronized List<String> currentScope() throws java.io.IOException {
        if (service == null) throw new java.io.IOException("请先激活 Coogle 并连接 LSPosed。");
        return service.getScope();
    }
    private SettingsConnection() { }
}
