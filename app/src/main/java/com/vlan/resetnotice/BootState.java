package com.vlan.resetnotice;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.UserManager;
import android.util.Log;

/** Device-protected mirror: only the next visible alert instant, its quiet-hour
 * windows and recovery diagnostics. Account names and credentials stay encrypted
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
        QuietHours global = snapshot.nextAlertGlobalQuiet, account = snapshot.nextAlertAccountQuiet;
        if (p.getLong("next_alert", 0L) == next && snapshot.nextAlertOnceToken.equals(p.getString("next_once_token", ""))
                && global.same(readQuiet(p, "g_")) &&
                account.same(readQuiet(p, "a_")) && p.getBoolean("quiet_mirror_v1", false)) return;
        SharedPreferences.Editor editor = p.edit().putLong("next_alert", next).putBoolean("quiet_mirror_v1", true)
                .putString("next_once_token", snapshot.nextAlertOnceToken);
        writeQuiet(editor, "g_", global);
        writeQuiet(editor, "a_", account);
        if (!editor.commit())
            Log.w("ResetNoticeBoot", "Unable to persist Direct Boot alert");
    }

    private static QuietHours readQuiet(SharedPreferences p, String prefix) {
        return new QuietHours(p.getBoolean(prefix + "enabled", false), p.getInt(prefix + "start", QuietHours.DEFAULT_START),
                p.getInt(prefix + "end", QuietHours.DEFAULT_END));
    }
    private static void writeQuiet(SharedPreferences.Editor e, String prefix, QuietHours quiet) {
        e.putBoolean(prefix + "enabled", quiet.enabled).putInt(prefix + "start", quiet.start).putInt(prefix + "end", quiet.end);
    }
    static boolean quietSuppresses(Context context, long intended, long now) {
        SharedPreferences p = prefs(context);
        // Older versions did not mirror quiet-hour metadata. Wait until the
        // new version has refreshed its snapshot before posting before unlock.
        return !p.getBoolean("quiet_mirror_v1", false) ||
                QuietHours.suppressed(readQuiet(p, "g_"), readQuiet(p, "a_"), intended, now);
    }
    static long nextAlert(Context context) { return prefs(context).getLong("next_alert", 0L); }
    // Opaque numeric timer identity only: no account names or quota text before unlock.
    static String nextOnceToken(Context context) { return prefs(context).getString("next_once_token", ""); }
    static boolean alreadyDeliveredOnce(Context context, String token) {
        return !token.isEmpty() && token.equals(prefs(context).getString("delivered_once_token", ""));
    }
    static boolean onceDelivered(Context context, ReminderStore.Slot slot, boolean early) {
        return alreadyDeliveredOnce(context, slot.onceToken(early));
    }
    static void markOnceDelivered(Context context, String token) {
        if (!token.isEmpty()) prefs(context).edit().putString("delivered_once_token", token).commit();
    }
    static void clearOnceDelivery(Context context, ReminderStore.Slot slot) {
        if (onceDelivered(context, slot, true) || onceDelivered(context, slot, false))
            prefs(context).edit().remove("delivered_once_token").commit();
    }
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
