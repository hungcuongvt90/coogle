package io.github.zopulus.coogle;

/** Shared platform eligibility for the supported ColorOS 16 and 17 hook routes. */
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
        return value != null && value.trim().matches("(?i)V?(?:16|17)(?:\\.[0-9]+)*");
    }
}
