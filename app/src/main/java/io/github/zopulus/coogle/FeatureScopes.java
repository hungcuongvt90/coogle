package io.github.zopulus.coogle;

import java.util.List;

/** Required injection targets, all drawn from META-INF/xposed/scope.list. */
final class FeatureScopes {
    private static final String GOOGLE = "com.google.android.googlequicksearchbox";
    static List<String> required(String feature) {
        return switch (feature) {
            case FeaturePolicy.HEY_GOOGLE -> List.of("system", GOOGLE);
            case FeaturePolicy.NAVIGATION -> List.of("system", "com.android.systemui", "com.android.settings", "com.android.launcher", GOOGLE);
            case FeaturePolicy.SEARCH_BAR -> List.of("com.android.launcher");
            case FeaturePolicy.GOOGLE_ENTRY -> List.of("com.android.settings");
            case FeaturePolicy.GMS -> List.of("system");
            case FeaturePolicy.LENS -> List.of(GOOGLE, "com.oplus.camera");
            case FeaturePolicy.WALLET -> List.of("system");
            case FeaturePolicy.QUICK_ENTRIES -> List.of("com.android.systemui", "com.oplus.wallpapers");
            default -> List.of();
        };
    }
    private FeatureScopes() { }
}
