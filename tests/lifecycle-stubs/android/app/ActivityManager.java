package android.app;
public class ActivityManager {
 public int lockTaskMode;
 public boolean failTasks;
 public java.util.List<RunningTaskInfo> runningTasks = new java.util.ArrayList<>();
 public int getLockTaskModeState(){return lockTaskMode;}
 public java.util.List<RunningTaskInfo> getRunningTasks(int count) {
  if (failTasks) throw new IllegalStateException("task lookup failed");
  return runningTasks;
 }
 public static class RunningTaskInfo {
  public android.content.ComponentName topActivity;
  public android.content.Intent baseIntent;
  public int userId;
 }
}
