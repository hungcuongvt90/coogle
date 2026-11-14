package android.os;
public final class SystemProperties {
    public static String colorOsVersion = "";
    public static String displayColorOsVersion = "";
    public static String get(String key) {
        return switch (key) {
            case "ro.build.version.oplusrom" -> colorOsVersion;
            case "ro.build.version.oplusrom.display" -> displayColorOsVersion;
            default -> "";
        };
    }
    public static String get(String key, String fallback) { return fallback; }
}
