package io.github.zopulus.coogle;

import java.util.*;
import java.util.function.Consumer;

/** One pending request, made only when the user turns a feature on. */
final class ScopeRequests {
    interface Service {
        List<String> scope();
        void request(List<String> packages, Consumer<String> completion);
    }
    private boolean pending;
    private long generation;
    synchronized boolean pending() { return pending; }
    synchronized void cancel() { generation++; pending = false; }

    synchronized void ensure(Service service, List<String> required, Consumer<String> done) {
        if (pending) return;
        try {
            List<String> granted = service.scope();
            List<String> missing = required.stream().distinct().filter(p -> !granted.contains(p)).toList();
            if (missing.isEmpty()) { done.accept(null); return; }
            pending = true;
            long token = ++generation;
            service.request(missing, error -> complete(token, service, required, error, done));
        } catch (RuntimeException failure) {
            pending = false;
            done.accept("无法请求作用域，请检查 LSPosed 连接并手动勾选对应应用。");
        }
    }

    private synchronized void complete(long token, Service service, List<String> required,
                                       String error, Consumer<String> done) {
        if (token != generation || !pending) return;
        pending = false;
        if (error == null) {
            try {
                if (!service.scope().containsAll(required))
                    error = "所需作用域未全部批准，请在 LSPosed 中勾选对应应用后重试。";
            } catch (RuntimeException failure) {
                error = "无法确认作用域，请检查 LSPosed 连接后重试。";
            }
        }
        done.accept(error);
    }
}
