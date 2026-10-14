package android.content;
public class Context {
 public android.content.pm.PackageManager manager = new android.content.pm.PackageManager();
 public android.content.res.Resources resources = new android.content.res.Resources();
 public android.app.ActivityManager activityManager = new android.app.ActivityManager();
 public android.app.KeyguardManager keyguardManager = new android.app.KeyguardManager();
 public boolean failServiceQuery;
 public ContentResolver getContentResolver(){return new ContentResolver();}
 public android.content.pm.PackageManager getPackageManager(){return manager;}
 public android.content.res.Resources getResources(){return resources;}
 public <T> T getSystemService(Class<T> type) {
  if (failServiceQuery) throw new IllegalStateException("service lookup failed");
  if (type.isInstance(keyguardManager)) return type.cast(keyguardManager);
  return type.isInstance(activityManager) ? type.cast(activityManager) : null;
 }
}
