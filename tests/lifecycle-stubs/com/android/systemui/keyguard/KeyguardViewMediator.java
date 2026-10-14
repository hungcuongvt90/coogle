package com.android.systemui.keyguard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import com.android.internal.policy.IKeyguardDismissCallback;
import java.util.ArrayList;
import java.util.List;

public class KeyguardViewMediator {
 public Context mContext = new Context();
 public boolean mShowing = true, mOccluded;
 public SelectedUser mSelectedUserInteractor = new SelectedUser();
 public final NativeHandler mHandler = new NativeHandler(this);
 public final List<String> events = new ArrayList<>();
 public final List<IKeyguardDismissCallback> activeRequests = new ArrayList<>();
 public int handled;
 public Message lastReceived;

 public static class SelectedUser {
  public int userId = 10;
  public boolean fail;
  public int getSelectedUserId() {
   if (fail) throw new IllegalStateException("selected-user reflection failed");
   return userId;
  }
 }
 public static class DismissMessage {
  public final IKeyguardDismissCallback mCallback;
  public final CharSequence mMessage;
  public DismissMessage(IKeyguardDismissCallback callback, CharSequence message) {
   mCallback = callback; mMessage = message;
  }
 }
 public static class NativeHandler extends Handler {
  public final KeyguardViewMediator owner;
  public NativeHandler(KeyguardViewMediator owner) { super(Looper.getMainLooper()); this.owner = owner; }
  public void handleMessage(Message message) {
   owner.handled++; owner.lastReceived = message; owner.events.add("native request");
   if (message.obj instanceof DismissMessage request && request.mCallback != null) owner.activeRequests.add(request.mCallback);
  }
 }
}
