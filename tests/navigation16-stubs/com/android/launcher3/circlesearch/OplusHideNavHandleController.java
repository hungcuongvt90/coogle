package com.android.launcher3.circlesearch;

public final class OplusHideNavHandleController {
    public static final INSTANCE INSTANCE = new INSTANCE();
    public void init() { }
    public static boolean isSupportCircleToSearchHandle() {
        return INSTANCE.isSupportCircleToSearchHandle();
    }
    public static final class INSTANCE {
        // The phone's DEX has an instance companion method plus an outer static bridge.
        public final boolean isSupportCircleToSearchHandle() { return false; }
    }
}
