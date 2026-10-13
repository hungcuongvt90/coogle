package android.os;
public final class SystemProperties {
    public static String colorOsVersion = "";
    public static String get(String key) { return key.startsWith("ro.build.version.oplusrom") ? colorOsVersion : ""; }
    public static String get(String key, String fallback) { return fallback; }
}
