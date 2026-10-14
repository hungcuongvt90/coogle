package com.android.server;

import android.content.Context;

public final class GestureLauncherService {
    public static boolean selected;
    public static boolean fail;
    public static int queriedUser = -1, queries;
    public static boolean isWalletDoubleTapPowerSettingEnabled(Context context, int user) {
        queries++; queriedUser = user;
        if (fail) throw new IllegalStateException("Native selection unavailable");
        return selected;
    }
}
