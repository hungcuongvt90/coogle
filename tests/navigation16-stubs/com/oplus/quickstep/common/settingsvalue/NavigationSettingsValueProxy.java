package com.oplus.quickstep.common.settingsvalue;

import android.content.Context;

public final class NavigationSettingsValueProxy {
    public static final Companion Companion = new Companion();
    public static int getCircleToSearchGestureState(Context context) {
        return Companion.getCircleToSearchGestureState(context);
    }
    public static final class Companion {
        public int getCircleToSearchGestureState(Context context) { return 0; }
    }
}
