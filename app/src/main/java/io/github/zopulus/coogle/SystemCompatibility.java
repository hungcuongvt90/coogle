package io.github.zopulus.coogle;

/** The supported platform range is ColorOS 16 and 17 (Android 16 and 17). */
public final class SystemCompatibility {
    private SystemCompatibility() { }

    public static boolean isSupportedColorOs() {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            String version = (String) properties.getMethod("get", String.class)
                    .invoke(null, "ro.build.version.oplusrom");
            if (version == null || version.isEmpty()) {
                version = (String) properties.getMethod("get", String.class)
                        .invoke(null, "ro.build.version.oplusrom.display");
            }
            return matchesVersion(version);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return false;
        }
    }

    static boolean matchesVersion(String value) {
        return majorVersion(value) >= 16;
    }

    /** The ColorOS major version, or 0 when the value is not a supported release. */
    static int majorVersion(String value) {
        if (value == null) return 0;
        String trimmed = value.trim();
        if (!trimmed.matches("(?i)V?(?:16|17)(?:\\.[0-9]+)*")) return 0;
        return Integer.parseInt(trimmed.replaceFirst("(?i)^V", "").split("\\.")[0]);
    }

    /** firmware generation of the running system; 16 and 17 diverge in launcher and gesture anchors. */
    static int colorOsMajor() {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            String version = (String) properties.getMethod("get", String.class)
                    .invoke(null, "ro.build.version.oplusrom");
            if (version == null || version.isEmpty()) {
                version = (String) properties.getMethod("get", String.class)
                        .invoke(null, "ro.build.version.oplusrom.display");
            }
            return majorVersion(version);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return 0;
        }
    }
}
