package android.content.pm;
public class PackageManager {
 public boolean installed = true;
 public int flags = 1, reads, activityReads;
 public android.content.res.Resources moduleResources = new android.content.res.Resources();
 public boolean lensExported = true, lensEnabled = true, lensEntryPresent = true;
 public static class NameNotFoundException extends Exception {}
 public ActivityInfo getActivityInfo(android.content.ComponentName component, int options) throws NameNotFoundException {
  activityReads++;
  if (!installed || !lensEntryPresent) throw new NameNotFoundException();
  ActivityInfo info = new ActivityInfo(); info.packageName = component.getPackageName();
  info.exported = lensExported; info.enabled = lensEnabled; return info;
 }
 public android.content.res.Resources getResourcesForApplication(String name) { return moduleResources; }
 public ApplicationInfo getApplicationInfo(String name, int options) throws NameNotFoundException {
  reads++;
  if (!installed) throw new NameNotFoundException();
  ApplicationInfo info = new ApplicationInfo(); info.flags = flags; return info;
 }
}
