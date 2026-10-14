package android.content;
public class Context {
 public android.content.pm.PackageManager manager = new android.content.pm.PackageManager();
 public android.content.res.Resources resources = new android.content.res.Resources();
 public android.app.ActivityManager activityManager = new android.app.ActivityManager();
 public boolean failServiceQuery;
 public ContentResolver getContentResolver(){return new ContentResolver();}
 public android.content.pm.PackageManager getPackageManager(){return manager;}
 public android.content.res.Resources getResources(){return resources;}
 public <T> T getSystemService(Class<T> type) {
  if (failServiceQuery) throw new IllegalStateException("service lookup failed");
  return type.isInstance(activityManager) ? type.cast(activityManager) : null;
 }
}
