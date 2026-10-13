package io.github.zopulus.coogle;

/** Defaults for user-controlled settings; scope-controlled integrations have no switch. */
public final class FeaturePolicy {
    public static final String NAVIGATION="navigation", GMS="gms",
            GOOGLE_ENTRY="google_entry", SEARCH_BAR="search_bar", HEY_GOOGLE="hey_google", LENS="lens",
            WALLET="wallet", QUICK_ENTRIES="quick_entries";
    // Locator/cache identifier only; Camera Lens is enabled by the module's app scope.
    public static final String CAMERA_LENS="camera_lens";
    public static boolean defaultValue(String key) {
        return NAVIGATION.equals(key) || LENS.equals(key);
    }
    public static boolean effective(String key, boolean requested, boolean companionReady) {
        return requested && (!HEY_GOOGLE.equals(key) || companionReady);
    }
    private FeaturePolicy() {}
}
