package com.android.server.policy;

import android.content.Context;

public final class PhoneWindowManagerExtImpl {
    public Context mContext = new Context();
    public int mCurrentUserId = 11;
    public boolean mShutdownChordEnabled;
    public static int sSosPowerKeyDownCount = 5;
    public boolean isCameraGestureEnabled() { return mShutdownChordEnabled && sSosPowerKeyDownCount != 3; }
}
