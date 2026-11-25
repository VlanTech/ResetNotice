package com.vlan.resetnotice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/** Visible, user-requested foreground timer; alarms remain the independent wake-up path. */
public final class TimerService extends Service {
    private static final String TAG = "ResetNoticeTimer";
    private static final String CHANNEL = "active_timers";
    private static final int NOTIFICATION_ID = 198;
    private static TimerService active;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable check = this::reconcile;
    private int lastCount = -1;
    private long lastReset = Long.MIN_VALUE;
    private String lastLabel = "";
    private String lastLocale = "";

    /** Called on the main thread by the activity and authorized alarm/system receivers. */
    static void refresh(Context context) {
        TimerSnapshot snapshot = TimerSnapshot.read(context, System.currentTimeMillis());
        Intent intent = new Intent(context, TimerService.class);
        if (snapshot.count == 0) {
            // Let an in-flight start complete its foreground promotion before stopping.
            if (active != null) active.requestCheck();
            return;
        }
        if (active != null) {
            active.requestCheck();
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (IllegalStateException | SecurityException e) {
            // Android may reject a background start. Independently registered alarms
            // continue to work, and the next user-visible launch can start the service.
            Log.w(TAG, "Foreground timer start unavailable; alarms remain armed", e);
        }
    }

    static boolean isRunning() { return active != null; }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            Context localized = AppPreferences.localized(this);
            NotificationChannel channel = new NotificationChannel(CHANNEL, localized.getString(R.string.channel_bg), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(localized.getString(R.string.channel_bg_desc));
            channel.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // Promoting immediately fulfils startForegroundService's timeout even if
        // the last timer was cleared between the launch request and this callback.
        TimerSnapshot snapshot = TimerSnapshot.read(this, System.currentTimeMillis());
        try {
            Notification notification = notification(snapshot);
            if (Build.VERSION.SDK_INT >= 34)
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTIFICATION_ID, notification);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to enter foreground; alarms remain armed", e);
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        active = this;
        remember(snapshot);
        if (snapshot.count == 0) { stopWhenEmpty(); return START_NOT_STICKY; }
        // A sticky restart may have no intent. Re-read persistent timers and rearm
        // their exact alarms rather than relying on any old in-memory state.
        AlarmEngine.rescheduleAll(this);
        requestCheck();
        return START_STICKY;
    }

    private void requestCheck() {
        handler.removeCallbacks(check);
        handler.post(check);
    }

    private void reconcile() {
        long now = System.currentTimeMillis();
        AlarmEngine.reconcileDue(this, now);
        TimerSnapshot snapshot = TimerSnapshot.read(this, now);
        if (snapshot.count == 0) { stopWhenEmpty(); return; }
        String locale = AppPreferences.localized(this).getResources().getConfiguration().locale.toLanguageTag();
        if (snapshot.count != lastCount || snapshot.nextReset != lastReset || !snapshot.nextLabel.equals(lastLabel) || !locale.equals(lastLocale)) {
            createChannel();
            // Notification permission can change while the service is running.
            try { getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(snapshot)); }
            catch (SecurityException ignored) { }
            remember(snapshot);
        }
        // No wake lock or per-second background spin. AlarmManager wakes sleeping
        // devices; this check provides a fallback while the CPU is already awake.
        handler.postDelayed(check, snapshot.nextCheckDelay(System.currentTimeMillis()));
    }

    private void remember(TimerSnapshot snapshot) {
        lastCount = snapshot.count;
        lastReset = snapshot.nextReset;
        lastLabel = snapshot.nextLabel;
        lastLocale = AppPreferences.localized(this).getResources().getConfiguration().locale.toLanguageTag();
    }

    private Notification notification(TimerSnapshot snapshot) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Intent launch = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(this, NOTIFICATION_ID, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Context localized = AppPreferences.localized(this);
        String detail = snapshot.count == 0 ? localized.getString(R.string.bg_ending) : localized.getString(R.string.next_reset,
                snapshot.nextProfileLabel + " · " + I18n.slot(localized, snapshot.nextSlotId), ResetMath.displayDateTime(snapshot.nextReset, System.currentTimeMillis()));
        b.setSmallIcon(R.drawable.ic_notification).setContentTitle(localized.getString(R.string.fg_title, snapshot.count))
                .setContentText(detail).setStyle(new Notification.BigTextStyle().bigText(detail)).setContentIntent(open)
                .setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE)
                .setCategory(Notification.CATEGORY_SERVICE).setPriority(Notification.PRIORITY_LOW).setShowWhen(false);
        return b.build();
    }

    private void stopWhenEmpty() {
        handler.removeCallbacks(check);
        if (active == this) active = null;
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf();
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        // Swiping the activity from Recents is not a request to stop the timer.
        requestCheck();
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(check);
        if (active == this) active = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
