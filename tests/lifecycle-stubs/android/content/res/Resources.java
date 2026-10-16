package android.content.res;
public class Resources {
 public String provider = "com.google.android.googlequicksearchbox";
 public boolean missingIdentifiers, missingDrawable;
 public static class Theme {}
 public static class NotFoundException extends RuntimeException {}
 public int getIdentifier(String name,String type,String owner){return missingIdentifiers ? 0 : 1;}
 public String getString(int id){return provider;}
 public android.graphics.drawable.Drawable getDrawable(int id, Theme theme) {
  if (missingDrawable) throw new NotFoundException();
  return new android.graphics.drawable.Drawable();
 }
}
