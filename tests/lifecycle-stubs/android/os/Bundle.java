package android.os;
public class Bundle {
 private final java.util.Map<String, Object> values = new java.util.HashMap<>();
 public Bundle() {}
 public Bundle(Bundle original) { values.putAll(original.values); }
 public void putString(String key, String value) { values.put(key, value); }
 public String getString(String key) { return (String) values.get(key); }
 public void putInt(String key, int value) { values.put(key, value); }
 public int getInt(String key, int fallback) { return values.containsKey(key) ? (Integer) values.get(key) : fallback; }
}
