package io.github.zopulus.coogle;

import java.util.ArrayList;
import java.util.List;

/** Orders real dismissal requests after native occlusion handling; never invents authentication. */
public final class WalletDismissQueueTest {
    public static void main(String[] args) {
        resetPrecedesRealRequestAndCallbacksRemainNative();
        fallbackReleasesOnceAtFiveHundredMilliseconds();
        staleTimerCannotReleaseNewBatch();
        fullQueueAndSchedulingFailureKeepOriginalRoute();
        forwardedRequestsUseIdentityAndOnePassOnly();
        releaseWithoutRequestsDoesNotGrantFutureRequests();
        System.out.println("WalletDismissQueueTest passed");
    }

    private static void resetPrecedesRealRequestAndCallbacksRemainNative() {
        Scheduler scheduler = new Scheduler();
        WalletDismissQueue queue = new WalletDismissQueue(scheduler);
        List<String> events = new ArrayList<>();
        NativeRequest first = new NativeRequest("first", events);
        NativeRequest second = new NativeRequest("second", events);
        check(!queue.consumeForwarded(first), "Fresh native request bypassed the queue");
        boolean firstDeferred = queue.defer(first, first::handleOriginally);
        boolean secondDeferred = queue.defer(second, second::handleOriginally);
        check(events.isEmpty(), "Queue forwarded or completed authentication before native reset");
        check(firstDeferred && secondDeferred, "Native requests were not deferred");

        // This is the observed race: native occlusion processing resets the bouncer first.
        events.add("native reset");
        queue.release();
        check(events.equals(List.of("native reset", "first requested", "second requested")),
                "Real requests did not run after native reset in original order");
        check(first.successes == 0 && first.cancellations == 0 && second.successes == 0 && second.cancellations == 0,
                "Queue manufactured an authentication result");

        // Only the original handler owns these results, including cancellation.
        first.originalSuccess.run();
        second.originalCancel.run();
        check(first.successes == 1 && first.cancellations == 0 && second.successes == 0 && second.cancellations == 1,
                "Real success/cancellation callbacks were not preserved");
        queue.release();
        scheduler.runEvenIfRemoved(scheduler.first());
        check(first.requests == 1 && second.requests == 1 && first.successes == 1 && second.cancellations == 1,
                "Repeated release or late timeout forwarded/completed a request twice");
    }

    private static void fallbackReleasesOnceAtFiveHundredMilliseconds() {
        Scheduler scheduler = new Scheduler();
        WalletDismissQueue queue = new WalletDismissQueue(scheduler);
        NativeRequest request = new NativeRequest("timeout", new ArrayList<>());
        check(queue.defer(request, () -> forward(queue, request)), "Fallback request was not queued");
        check(scheduler.first().delay == 500L, "Missing native event must have a 500ms fallback");
        scheduler.advance(499L);
        check(request.requests == 0, "Fallback released before its deadline");
        scheduler.advance(1L);
        check(request.requests == 1 && request.successes == 0 && request.cancellations == 0,
                "Fallback did not forward exactly the original uncompleted authentication request");
        queue.release();
        scheduler.runEvenIfRemoved(scheduler.first());
        check(request.requests == 1, "Timeout/native event race forwarded twice");
    }

    private static void staleTimerCannotReleaseNewBatch() {
        Scheduler scheduler = new Scheduler();
        WalletDismissQueue queue = new WalletDismissQueue(scheduler);
        NativeRequest oldRequest = new NativeRequest("old", new ArrayList<>());
        check(queue.defer(oldRequest, () -> forward(queue, oldRequest)), "Old batch was not queued");
        Scheduled oldTimer = scheduler.first();
        queue.release();
        check(oldTimer.removed, "Native event did not cancel its fallback timer");

        NativeRequest nextRequest = new NativeRequest("next", new ArrayList<>());
        check(queue.defer(nextRequest, () -> forward(queue, nextRequest)), "New batch was not queued");
        scheduler.runEvenIfRemoved(oldTimer);
        check(nextRequest.requests == 0, "Old timeout released a newer occlusion batch");
        queue.release();
        check(oldRequest.requests == 1 && nextRequest.requests == 1, "New batch was lost or old batch replayed");
    }

    private static void fullQueueAndSchedulingFailureKeepOriginalRoute() {
        Scheduler scheduler = new Scheduler();
        WalletDismissQueue queue = new WalletDismissQueue(scheduler);
        List<NativeRequest> accepted = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            NativeRequest request = new NativeRequest("accepted " + i, new ArrayList<>());
            accepted.add(request);
            check(queue.defer(request, () -> forward(queue, request)), "Queue rejected request below its 8-request bound");
        }
        NativeRequest overflow = new NativeRequest("overflow", new ArrayList<>());
        check(!queue.defer(overflow, () -> forward(queue, overflow)), "Full queue suppressed native request instead of falling through");
        check(!queue.consumeForwarded(overflow) && overflow.requests == 0,
                "Rejected overflow request gained a bypass marker or was run by the queue");
        overflow.handleOriginally();
        queue.release();
        check(overflow.requests == 1, "Overflow request was also replayed by the queue");
        for (NativeRequest request : accepted) check(request.requests == 1, "Bounded batch dropped or repeated a request");

        Scheduler failedScheduler = new Scheduler();
        failedScheduler.accept = false;
        WalletDismissQueue failedQueue = new WalletDismissQueue(failedScheduler);
        NativeRequest failed = new NativeRequest("failed schedule", new ArrayList<>());
        check(!failedQueue.defer(failed, () -> forward(failedQueue, failed)), "Failed scheduler suppressed original request");
        check(!failedQueue.consumeForwarded(failed) && failed.requests == 0, "Failed scheduler altered native request");
        failed.handleOriginally();
        failedQueue.release();
        check(failed.requests == 1, "Rejected request survived in queue after schedule failure");
        failedScheduler.accept = true;
        NativeRequest recovered = new NativeRequest("recovered", new ArrayList<>());
        check(failedQueue.defer(recovered, () -> forward(failedQueue, recovered)), "Scheduling failure poisoned later batches");
        failedQueue.release();
        check(recovered.requests == 1 && failed.requests == 1, "Recovery lost new request or replayed failed request");
    }

    private static void forwardedRequestsUseIdentityAndOnePassOnly() {
        Scheduler scheduler = new Scheduler();
        WalletDismissQueue queue = new WalletDismissQueue(scheduler);
        Object first = new EqualRequest();
        Object second = new EqualRequest();
        Object impostor = new EqualRequest();
        List<Object> forwarded = new ArrayList<>();
        check(queue.defer(first, () -> {
            check(!queue.consumeForwarded(impostor), "Equal distinct request stole the forwarded marker");
            check(queue.consumeForwarded(first), "Forwarded request could not pass its hook");
            check(!queue.consumeForwarded(first), "Same forwarded request bypassed twice");
            forwarded.add(first);
        }), "First equal request rejected");
        check(queue.defer(second, () -> {
            check(queue.consumeForwarded(second), "Equal distinct native request was merged/lost");
            check(!queue.consumeForwarded(second), "Second request bypassed twice");
            forwarded.add(second);
        }), "Second equal request rejected");
        queue.release();
        check(forwarded.size() == 2 && forwarded.get(0) == first && forwarded.get(1) == second,
                "Identity-distinct native requests were merged or reordered");
        check(!queue.consumeForwarded(first) && !queue.consumeForwarded(second) && !queue.consumeForwarded(impostor),
                "Forwarded marker remained after original method reentry");
    }

    private static void releaseWithoutRequestsDoesNotGrantFutureRequests() {
        Scheduler scheduler = new Scheduler();
        WalletDismissQueue queue = new WalletDismissQueue(scheduler);
        queue.release();
        queue.release();
        NativeRequest request = new NativeRequest("future", new ArrayList<>());
        check(!queue.consumeForwarded(request), "Empty occlusion event granted a future request authentication bypass");
        check(queue.defer(request, () -> forward(queue, request)) && request.requests == 0,
                "Empty release incorrectly marked subsequent native requests as already safe to forward");
        queue.release();
        check(request.requests == 1, "Fresh batch after empty release did not work");
    }

    private static void forward(WalletDismissQueue queue, NativeRequest request) {
        check(queue.consumeForwarded(request), "Forwarded request would be deferred again on reentry");
        check(!queue.consumeForwarded(request), "Forwarding marker allowed repeated hook reentry");
        request.handleOriginally();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class NativeRequest {
        final String name;
        final List<String> events;
        int requests;
        int successes;
        int cancellations;
        final Runnable originalSuccess = () -> successes++;
        final Runnable originalCancel = () -> cancellations++;

        NativeRequest(String name, List<String> events) { this.name = name; this.events = events; }
        void handleOriginally() { requests++; events.add(name + " requested"); }
    }

    private static final class EqualRequest {
        @Override public boolean equals(Object other) { return other instanceof EqualRequest; }
        @Override public int hashCode() { return 1; }
    }

    private static final class Scheduled {
        final Runnable runnable;
        final long delay;
        final long deadline;
        boolean removed;
        boolean ran;
        Scheduled(Runnable runnable, long delay, long now) {
            this.runnable = runnable; this.delay = delay; this.deadline = now + delay;
        }
    }

    private static final class Scheduler implements WalletDismissQueue.Scheduler {
        final List<Scheduled> scheduled = new ArrayList<>();
        boolean accept = true;
        long now;
        @Override public boolean postDelayed(Runnable runnable, long delay) {
            if (!accept) return false;
            scheduled.add(new Scheduled(runnable, delay, now));
            return true;
        }
        @Override public void remove(Runnable runnable) {
            for (Scheduled item : scheduled) if (item.runnable == runnable) item.removed = true;
        }
        Scheduled first() {
            check(!scheduled.isEmpty(), "No fallback timer was scheduled");
            return scheduled.get(0);
        }
        void advance(long elapsed) {
            now += elapsed;
            for (Scheduled item : new ArrayList<>(scheduled)) {
                if (!item.removed && !item.ran && item.deadline <= now) {
                    item.ran = true;
                    item.runnable.run();
                }
            }
        }
        void runEvenIfRemoved(Scheduled item) { item.runnable.run(); }
    }
}
