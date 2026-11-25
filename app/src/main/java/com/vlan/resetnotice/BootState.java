package com.vlan.resetnotice;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.UserManager;
import android.util.Log;

/** Device-protected mirror: only the next visible alert instant and recovery
 * diagnostics. Account data, notification preferences and names stay encrypted
 * in the user's normal credential-protected store. */
final class BootState {
    private static SharedPreferences prefs(Context context) {
        Context storage = Build.VERSION.SDK_INT >= 24 ? context.createDeviceProtectedStorageContext() : context;
        return storage.getSharedPreferences("boot_state", Context.MODE_PRIVATE);
    }

    static boolean unlocked(Context context) {
        if (Build.VERSION.SDK_INT < 24) return true;
        UserManager manager = context.getSystemService(UserManager.class);
        return manager != null && manager.isUserUnlocked();
    }

    static void sync(Context context, TimerSnapshot snapshot) {
        long next = snapshot.nextVisibleAlert == Long.MAX_VALUE ? 0L : snapshot.nextVisibleAlert;
        SharedPreferences p = prefs(context);
        if (p.getLong("next_alert", 0L) != next &&
                !p.edit().putLong("next_alert", next).commit())
            Log.w("ResetNoticeBoot", "Unable to persist Direct Boot alert");
    }

    static long nextAlert(Context context) { return prefs(context).getLong("next_alert", 0L); }
    static void lockedBoot(Context context) {
        prefs(context).edit().putLong("locked_boot", System.currentTimeMillis()).commit();
    }
    static void recovered(Context context, long now, int count, boolean exact) {
        prefs(context).edit().putLong("recovery_time", now).putInt("recovery_count", count)
                .putBoolean("recovery_exact", exact).commit();
    }
    static long recoveryTime(Context context) { return prefs(context).getLong("recovery_time", 0L); }
    static int recoveryCount(Context context) { return prefs(context).getInt("recovery_count", -1); }
    static boolean recoveryExact(Context context) { return prefs(context).getBoolean("recovery_exact", false); }
}
