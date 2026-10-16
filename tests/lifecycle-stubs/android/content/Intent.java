package android.content;
public class Intent {
 public static final String ACTION_VIEW="android.intent.action.VIEW";
 private String action;
 private String owner;
 private android.net.Uri uri;
 private ComponentName component;
 private int flags;
 private final java.util.Map<String,Object> extras = new java.util.HashMap<>();
 public Intent() {}
 public Intent(String action) { this.action = action; }
 public Intent(Intent other) { action=other.action; owner=other.owner; uri=other.uri; component=other.component; flags=other.flags; extras.putAll(other.extras); }
 public Intent setPackage(String value) { owner=value; return this; }
 public String getPackage() { return owner; }
 public Intent setComponent(ComponentName value) { component=value; return this; }
 public ComponentName getComponent() { return component; }
 public Intent setData(android.net.Uri value) { uri=value; return this; }
 public String getDataString() { return uri==null?null:uri.toString(); }
 public Intent addFlags(int value) { flags|=value; return this; }
 public int getFlags() { return flags; }
 public String getAction() { return action; }
 public Intent setAction(String action) { this.action = action; return this; }
 public Intent putExtra(String key, boolean value) { extras.put(key,value); return this; }
 public Intent putExtra(String key, int value) { extras.put(key,value); return this; }
 public Intent putExtra(String key, android.os.Bundle value) { extras.put(key,value); return this; }
 public android.os.Bundle getBundleExtra(String key) { return (android.os.Bundle) extras.get(key); }
 public boolean hasExtra(String key) { return extras.containsKey(key); }
}
