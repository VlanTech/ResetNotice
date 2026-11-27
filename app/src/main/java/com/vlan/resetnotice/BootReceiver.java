package com.vlan.resetnotice;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/** System boot entry point. No activity launch, task, wake lock or app window.
 * Before first unlock only a single generic user-requested reminder can fire. */
public final class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "ResetNoticeBoot";
    private static final String LOCKED_ALERT = "com.vlan.resetnotice.LOCKED_ALERT";
    private static final int LOCKED_ID = 200;

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (LOCKED_ALERT.equals(action)) {
            if (!BootState.unlocked(context)) notifyLocked(context, intent.getLongExtra("deadline", 0L));
            return;
        }
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action) && !Intent.ACTION_BOOT_COMPLETED.equals(action)) return;
        if (!BootState.unlocked(context)) {
            BootState.lockedBoot(context);
            armLocked(context);
            return;
        }
        // On devices without a secure lock a single full boot recovery is enough.
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)) return;
        long now = System.currentTimeMillis();
        try {
            cancelLocked(context);
            AlarmEngine.rescheduleAll(context);
            TimerSnapshot snapshot = TimerSnapshot.read(context, now);
            BootState.recovered(context, now, snapshot.count, AlarmEngine.exactAllowed(context));
            TimerService.refresh(context);
        } catch (RuntimeException e) {
            BootState.recovered(context, now, -1, false);
            Log.w(TAG, "Boot recovery failed", e);
        }
    }

    private static Intent reminder(Context context) {
        return new Intent(context, BootReceiver.class).setAction(LOCKED_ALERT);
    }
    private static void cancelLocked(Context context) {
        PendingIntent pi = PendingIntent.getBroadcast(context, LOCKED_ID, reminder(context),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) {
            context.getSystemService(AlarmManager.class).cancel(pi);
            pi.cancel();
        }
        // A single-use reminder posted before unlock is already the chosen alert.
        // Keep it visible until dismissed; don't immediately replace it with a second alert.
        if (!BootState.alreadyDeliveredOnce(context, BootState.nextOnceToken(context)))
            context.getSystemService(NotificationManager.class).cancel(LOCKED_ID);
    }
    private static void armLocked(Context context) {
        long deadline = BootState.nextAlert(context), now = System.currentTimeMillis();
        // Do not surface very old reminders or wake up for silent rollovers.
        if (deadline <= 0 || deadline < now - ResetMath.DAY) return;
        if (BootState.alreadyDeliveredOnce(context, BootState.nextOnceToken(context))) return;
        PendingIntent pi = PendingIntent.getBroadcast(context, LOCKED_ID,
                reminder(context).putExtra("deadline", deadline),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        long when = Math.max(now + 500L, deadline);
        try {
            if (AlarmEngine.exactAllowed(context)) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
                return;
            }
        } catch (SecurityException ignored) {}
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
    }
    private static void notifyLocked(Context context, long deadline) {
        long now = System.currentTimeMillis();
        String onceToken = BootState.nextOnceToken(context);
        if (deadline <= 0 || deadline != BootState.nextAlert(context) || deadline < now - ResetMath.DAY ||
                !AlarmEngine.notificationsAllowed(context) || BootState.quietSuppresses(context, deadline, now) ||
                BootState.alreadyDeliveredOnce(context, onceToken)) return;
        if (now < deadline) { armLocked(context); return; }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(AlarmEngine.CHANNEL) == null)
            manager.createNotificationChannel(new NotificationChannel(AlarmEngine.CHANNEL,
                    context.getString(R.string.channel_reset), NotificationManager.IMPORTANCE_HIGH));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context, AlarmEngine.CHANNEL)
                : new Notification.Builder(context).setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        b.setSmallIcon(R.drawable.ic_notification).setContentTitle(context.getString(onceToken.isEmpty() ?
                        R.string.boot_alert_title : R.string.boot_once_alert_title))
                .setContentText(context.getString(onceToken.isEmpty() ? R.string.boot_alert_body : R.string.boot_once_alert_body))
                .setAutoCancel(true).setOnlyAlertOnce(!onceToken.isEmpty())
                .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE);
        try {
            manager.notify(LOCKED_ID, b.build());
            BootState.markOnceDelivered(context, onceToken);
        }
        catch (SecurityException ignored) {}
    }
}
