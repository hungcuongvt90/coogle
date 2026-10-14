package com.android.systemui.statusbar.phone;
import com.android.systemui.keyguard.KeyguardViewMediator;
public class StatusBarKeyguardViewManager {
 public final KeyguardViewMediator mediator;
 public boolean fail;
 public StatusBarKeyguardViewManager(KeyguardViewMediator mediator) { this.mediator = mediator; }
 public void reset(boolean hideBouncerWhenShowing) {
  if (fail) throw new IllegalStateException("native reset failed");
  mediator.events.add("native reset"); mediator.mOccluded = true;
  for (var callback : mediator.activeRequests) callback.onDismissCancelled();
  mediator.activeRequests.clear();
 }
}
