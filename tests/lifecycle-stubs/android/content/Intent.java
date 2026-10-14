package android.content;
public class Intent {
 private String action;
 private final java.util.Map<String,Object> extras = new java.util.HashMap<>();
 public Intent() {}
 public Intent(String action) { this.action = action; }
 public String getAction() { return action; }
 public Intent setAction(String action) { this.action = action; return this; }
 public Intent putExtra(String key, boolean value) { extras.put(key,value); return this; }
 public Intent putExtra(String key, int value) { extras.put(key,value); return this; }
 public boolean hasExtra(String key) { return extras.containsKey(key); }
}
