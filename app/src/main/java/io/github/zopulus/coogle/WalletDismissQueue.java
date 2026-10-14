package io.github.zopulus.coogle;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/** Main-thread ordering of native requests; authentication remains with the original handler. */
public final class WalletDismissQueue {
    public interface Scheduler {
        boolean postDelayed(Runnable callback, long delay);
        void remove(Runnable callback);
    }

    private record Request(Object identity, Runnable forward) { }
    private final Scheduler scheduler;
    private final List<Request> pending = new ArrayList<>();
    private final IdentityHashMap<Object, Boolean> forwarded = new IdentityHashMap<>();
    private Runnable timeout;

    public WalletDismissQueue(Scheduler scheduler) { this.scheduler = scheduler; }

    public boolean consumeForwarded(Object request) { return forwarded.remove(request) != null; }

    public boolean hasPending() { return !pending.isEmpty(); }

    public boolean defer(Object request, Runnable forward) {
        if (pending.size() >= 8) return false;
        pending.add(new Request(request, forward));
        if (pending.size() == 1) {
            Runnable timer = new Runnable() {
                @Override public void run() { if (timeout == this) release(); }
            };
            timeout = timer;
            boolean scheduled;
            try { scheduled = scheduler.postDelayed(timer, 500L); }
            catch (RuntimeException failure) {
                timeout = null;
                pending.clear();
                throw failure;
            }
            if (!scheduled) {
                timeout = null;
                pending.clear();
                return false;
            }
        }
        return true;
    }

    public void release() {
        Runnable timer = timeout;
        timeout = null;
        List<Request> batch = List.copyOf(pending);
        pending.clear();
        if (timer != null) scheduler.remove(timer);
        for (Request request : batch) {
            forwarded.put(request.identity(), Boolean.TRUE);
            request.forward().run();
        }
    }
}
