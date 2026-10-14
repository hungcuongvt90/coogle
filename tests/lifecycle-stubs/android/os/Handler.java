package android.os;
import java.util.*;
public class Handler {
    public record Task(Runnable callback, Object token, long delay) {}
    public static final List<Task> tasks = new ArrayList<>();
    public final List<Message> messages = new ArrayList<>();
    public boolean acceptPosts = true, acceptMessages = true, failMessages;
    public Handler(Looper looper) {}
    public boolean postDelayed(Runnable callback, long delay) { return postDelayed(callback, null, delay); }
    public boolean postDelayed(Runnable callback, Object token, long delay) {
        if (!acceptPosts) return false;
        tasks.add(new Task(callback,token,delay)); return true;
    }
    public void removeCallbacksAndMessages(Object token) { tasks.removeIf(task -> task.token()==token); }
    public void removeCallbacks(Runnable callback) { tasks.removeIf(task -> task.callback()==callback); }
    public boolean sendMessage(Message message) {
        if (failMessages) throw new IllegalStateException("message dispatch failed");
        if (!acceptMessages) return false;
        message.setTarget(this); messages.add(message); return true;
    }
}
