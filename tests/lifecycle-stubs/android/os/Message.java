package android.os;
public final class Message {
 public int what, arg1, arg2, sendingUid;
 public Object obj;
 public Messenger replyTo;
 private Handler target;
 private Runnable callback;
 private Bundle data;
 public boolean recycled;
 public static Message obtain() { return new Message(); }
 public static Message obtain(Handler target, Runnable callback) {
  Message message = new Message(); message.target = target; message.callback = callback; return message;
 }
 public static Message obtain(Message original) {
  Message copy = new Message();
  copy.what = original.what; copy.arg1 = original.arg1; copy.arg2 = original.arg2;
  copy.obj = original.obj; copy.replyTo = original.replyTo; copy.sendingUid = original.sendingUid;
  copy.target = original.target; copy.callback = original.callback;
  copy.data = original.data == null ? null : new Bundle(original.data);
  return copy;
 }
 public Handler getTarget() { return target; }
 public void setTarget(Handler target) { this.target = target; }
 public Runnable getCallback() { return callback; }
 public Bundle getData() { if (data == null) data = new Bundle(); return data; }
 public Bundle peekData() { return data; }
 public void setData(Bundle data) { this.data = data; }
 public void recycle() { recycled = true; }
}
