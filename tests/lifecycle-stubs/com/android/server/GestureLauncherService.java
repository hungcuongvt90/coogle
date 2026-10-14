package com.android.server;

import android.content.Context;

public final class GestureLauncherService {
    public static boolean selected;
    public static int queriedUser = -1, queries;
    public static boolean isWalletDoubleTapPowerSettingEnabled(Context context, int user) {
        queries++; queriedUser = user; return selected;
    }
}
