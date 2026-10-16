package io.github.zopulus.coogle;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

/** Native partner Lens launch: expose the real caller while preserving OEM options. */
final class ActionButtonLensLaunch {
    static final String PACKAGE = "com.google.android.googlequicksearchbox";
    static final String ENTRY = "com.google.android.apps.lens.MainActivity";

    static ComponentName availableEntry(Context context) {
        ComponentName direct = new ComponentName(PACKAGE, ENTRY);
        try {
            var info = context.getPackageManager().getActivityInfo(direct, 0);
            return info.enabled && info.exported && PACKAGE.equals(info.packageName) ? direct : null;
        } catch (PackageManager.NameNotFoundException missing) {
            return null;
        }
    }

    static Intent destination(Intent original, Context context) {
        // Google's exported direct-intent alias avoids a separate gateway-window transition.
        ComponentName direct = availableEntry(context);
        if (direct == null) return null;
        Bundle params = new Bundle();
        params.putBinder("lens_activity_binder", new ActionButtonLensCaller(context.getPackageName()));
        params.putLong("start_activity_time_nanos", SystemClock.elapsedRealtimeNanos());
        return new Intent(original).setComponent(direct).setPackage(PACKAGE)
                .setAction(Intent.ACTION_VIEW).setData(Uri.parse("google://lens"))
                .putExtra("lens_activity_params", params);
    }

    static String availabilityPackage(String original, boolean enabled) {
        return enabled && ActionButtonLensBinding.SCANNER.equals(original) ? PACKAGE : original;
    }

    private ActionButtonLensLaunch() { }
}
