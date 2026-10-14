package android.content;
public class Intent {
 private String action;
 public Intent() {}
 public Intent(String action) { this.action = action; }
 public String getAction() { return action; }
 public Intent setAction(String action) { this.action = action; return this; }
}
