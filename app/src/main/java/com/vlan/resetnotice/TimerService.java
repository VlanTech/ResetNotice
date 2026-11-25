package com.vlan.resetnotice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
    private String lastTitle = "";
    private int lastPeriod = -1;
    private boolean screenReceiverRegistered;
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction()) && active == TimerService.this) requestCheck();
        }
    };

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
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenReceiver, filter);
            screenReceiverRegistered = true;
        } catch (RuntimeException e) { Log.w(TAG, "Screen refresh receiver unavailable", e); }
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
        long now = System.currentTimeMillis();
        TimerSnapshot snapshot = TimerSnapshot.read(this, now);
        try {
            Notification notification = notification(snapshot, now);
            if (Build.VERSION.SDK_INT >= 34)
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTIFICATION_ID, notification);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to enter foreground; alarms remain armed", e);
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        active = this;
        remember(snapshot, now);
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
        String title = title(snapshot, AppPreferences.localized(this), now);
        if (snapshot.count != lastCount || snapshot.nextReset != lastReset || snapshot.nextPeriodMinutes != lastPeriod
                || !snapshot.nextLabel.equals(lastLabel) || !locale.equals(lastLocale) || !title.equals(lastTitle)) {
            createChannel();
            // Notification permission can change while the service is running.
            try { getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(snapshot, now)); }
            catch (SecurityException ignored) { }
            remember(snapshot, now);
        }
        // No wake lock or per-second background spin. AlarmManager wakes sleeping
        // devices; this check provides a fallback while the CPU is already awake.
        long current = System.currentTimeMillis();
        handler.postDelayed(check, Math.min(snapshot.nextCheckDelay(current), Math.max(500L, ResetMath.MINUTE - current % ResetMath.MINUTE)));
    }

    private void remember(TimerSnapshot snapshot, long now) {
        lastCount = snapshot.count;
        lastReset = snapshot.nextReset;
        lastLabel = snapshot.nextLabel;
        lastPeriod = snapshot.nextPeriodMinutes;
        lastLocale = AppPreferences.localized(this).getResources().getConfiguration().locale.toLanguageTag();
        lastTitle = title(snapshot, AppPreferences.localized(this), now);
    }

    private String title(TimerSnapshot snapshot, Context localized, long now) {
        return snapshot.count == 0 ? localized.getString(R.string.bg_ending) :
                localized.getString(R.string.notify_countdown_title, I18n.remaining(localized, Math.max(0L, snapshot.nextReset - now)));
    }

    private Notification notification(TimerSnapshot snapshot, long now) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Intent launch = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(this, NOTIFICATION_ID, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Context localized = AppPreferences.localized(this);
        String detail = snapshot.count == 0 ? localized.getString(R.string.bg_ending) : localized.getString(R.string.notify_planned_body,
                snapshot.nextProfileLabel + " · " + I18n.slot(localized, snapshot.nextSlotId, snapshot.nextPeriodMinutes),
                ResetMath.displayDateTime(snapshot.nextReset, now)) + "\n" + localized.getString(R.string.notify_active_count, snapshot.count);
        b.setSmallIcon(R.drawable.ic_notification).setContentTitle(title(snapshot, localized, now))
                .setContentText(detail).setStyle(new Notification.BigTextStyle().bigText(detail)).setContentIntent(open)
                .setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE)
                .setCategory(Notification.CATEGORY_SERVICE).setPriority(Notification.PRIORITY_LOW).setShowWhen(false);
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
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
        // Ensure persisted alarms remain registered before the activity task goes away.
        // A platform/OEM force-stop remains authoritative; this never tries to bypass it.
        AlarmEngine.rescheduleAll(this);
        lastTitle = ""; // refresh the ongoing status if the platform retained the service
        requestCheck();
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(check);
        if (screenReceiverRegistered) unregisterReceiver(screenReceiver);
        if (active == this) active = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
