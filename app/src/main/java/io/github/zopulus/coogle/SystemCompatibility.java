package io.github.zopulus.coogle;

/** The only platform eligibility requirement is ColorOS major version 17. */
public final class SystemCompatibility {
    private SystemCompatibility() { }

    public static boolean isColorOs17() {
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
        return value != null && value.trim().matches("(?i)V?17(?:\\.[0-9]+)*");
    }
}
