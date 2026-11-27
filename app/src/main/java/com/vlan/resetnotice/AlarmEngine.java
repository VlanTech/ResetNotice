package com.vlan.resetnotice;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;


final class AlarmEngine {
    static final String CHANNEL = "quota_reset";
    static final String ACTION = "com.vlan.resetnotice.REMIND";
    static final String PROBE_ACTION = "com.vlan.resetnotice.BACKGROUND_TEST";
    private static final int PROBE_ID = 201;

    static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            Context localized = AppPreferences.localized(context);
            NotificationChannel channel = new NotificationChannel(CHANNEL, localized.getString(R.string.channel_reset),
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(localized.getString(R.string.channel_reset_desc));
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
        }
    }

    static boolean notificationsAllowed(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
            if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) return false;
        }
        return true;
    }

    static boolean exactAllowed(Context context) {
        if (Build.VERSION.SDK_INT < 31) return true;
        return context.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    private static Intent intent(Context context, int profile, int slot, boolean early) {
        return new Intent(context, ReminderReceiver.class).setAction(ACTION)
                .putExtra("profile", profile).putExtra("slot", slot).putExtra("early", early);
    }

    private static void cancel(Context context, int request, Intent intent) {
        PendingIntent pi = PendingIntent.getBroadcast(context, request, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) {
            context.getSystemService(AlarmManager.class).cancel(pi);
            pi.cancel();
        }
    }

    static void rescheduleAll(Context context) {
        createChannel(context);
        // Version 1 used request codes 100–103. Cancel those alarms after migration.
        Intent legacy = new Intent(context, ReminderReceiver.class).setAction(ACTION);
        for (int request = 100; request <= 103; request++) cancel(context, request, legacy);
        // Also catches up cycles missed while the phone was off or the app was stopped.
        reconcileDue(context, System.currentTimeMillis());
        for (ReminderStore.Profile p : ReminderStore.profiles(context)) {
            rescheduleSlot(context, p.id, ReminderStore.SHORT);
            rescheduleSlot(context, p.id, ReminderStore.WEEK);
        }
        BootState.sync(context, TimerSnapshot.read(context, System.currentTimeMillis()));
    }

    static void cancelSlot(Context context, int profile, int slot, boolean removeNotifications) {
        for (boolean early : new boolean[]{true, false}) {
            int request = AlarmIdentity.id(profile, slot, early);
            cancel(context, request, intent(context, profile, slot, early));
            if (removeNotifications) context.getSystemService(NotificationManager.class).cancel(request);
        }
    }

    static void cancelProfile(Context context, int profile) {
        cancelSlot(context, profile, ReminderStore.SHORT, true);
        cancelSlot(context, profile, ReminderStore.WEEK, true);
    }

    static void rescheduleSlot(Context context, int profile, int slot) {
        ReminderStore.Profile p = ReminderStore.profile(context, profile);
        if (p == null || !p.allows(slot)) { cancelSlot(context, profile, slot, true); return; }
        ReminderStore.Slot s = ReminderStore.load(context, profile, slot);
        schedule(context, s, true);
        schedule(context, s, false);
    }

    private static void schedule(Context context, ReminderStore.Slot s, boolean early) {
        int request = AlarmIdentity.id(s.profileId, s.id, early);
        long now = System.currentTimeMillis();
        // The at-time alarm is also the cycle rollover. It must run even if the
        // user disables the at-time notification (or denies notifications entirely).
        long when = early ? ResetMath.trigger(now, s.target, true, s.earlyMinutes, s.early, s.earlyDelivered || (s.oneShot && s.atDelivered))
                : ResetMath.rolloverTrigger(now, s.target);
        if (when == ResetMath.NO_ALARM) {
            cancel(context, request, intent(context, s.profileId, s.id, early));
            return;
        }
        Intent delivery = intent(context, s.profileId, s.id, early).putExtra("target", s.target)
                .putExtra("minutes", s.earlyMinutes);
        PendingIntent pi = PendingIntent.getBroadcast(context, request, delivery,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        ReminderStore.Profile profile = ReminderStore.profile(context, s.profileId);
        long intended = early ? s.target - s.earlyMinutes * ResetMath.MINUTE : s.target;
        boolean visible = AlarmPolicy.visible(s, early, now) && profile != null &&
                !QuietHours.suppressed(ReminderStore.globalQuiet(context), profile.quiet, intended, when);
        scheduleSystemAlarm(context, when, pi, visible, request, s.profileId);
    }

    /** Every user-visible alarm can opt into Android's actual alarm-clock API;
     * never use it for silent rollover, a keepalive heartbeat or background start. */
    private static int scheduleSystemAlarm(Context context, long when, PendingIntent pi,
                                           boolean visible, int request, int profileId) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        int mode = AlarmPolicy.mode(AppPreferences.clockAlarmMode(context), visible,
                notificationsAllowed(context), exactAllowed(context));
        // Setting the same PendingIntent replaces the prior alarm. Do not cancel
        // a valid alarm first: the process could be reclaimed between the two calls.
        if (mode == AlarmPolicy.CLOCK) {
            try {
                Intent open = new Intent(context, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra("open_profile", profileId);
                PendingIntent show = PendingIntent.getActivity(context, request, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                manager.setAlarmClock(new AlarmManager.AlarmClockInfo(when, show), pi);
                return AlarmPolicy.CLOCK;
            } catch (SecurityException ignored) { /* Permission can change. Fall back to ordinary alarm. */ }
        }
        if (mode != AlarmPolicy.INEXACT) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
                return AlarmPolicy.EXACT;
            } catch (SecurityException ignored) { /* Permission may change while scheduling. */ }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        return AlarmPolicy.INEXACT;
    }

    static void startProbe(Context context) {
        createChannel(context);
        long expected = System.currentTimeMillis() + AlarmDiagnostics.PROBE_DELAY;
        AlarmDiagnostics.startProbe(context, expected);
        armProbe(context, expected);
    }

    static void cancelLegacyProbe(Context context) {
        cancel(context, PROBE_ID, new Intent(context, ProbeReceiver.class).setAction(PROBE_ACTION));
        context.getSystemService(NotificationManager.class).cancel(PROBE_ID);
    }

    static void armProbe(Context context, long expected) {
        Intent probe = new Intent(context, ProbeReceiver.class).setAction(PROBE_ACTION).putExtra("expected", expected);
        PendingIntent pi = PendingIntent.getBroadcast(context, PROBE_ID, probe,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            AlarmDiagnostics.probeMode(context, scheduleSystemAlarm(context,
                    Math.max(System.currentTimeMillis() + 500, expected), pi, true, PROBE_ID, -1));
        } catch (RuntimeException e) {
            AlarmDiagnostics.probeMode(context, -1);
        }
    }

    static boolean sendProbeNotification(Context context, long expected, long actual) {
        createChannel(context);
        Context localized = AppPreferences.localized(context);
        return notify(context, PROBE_ID, -1, localized.getString(R.string.probe_delivered_title),
                localized.getString(R.string.probe_message, timeLabel(expected), timeLabel(actual)));
    }

    static String timeLabel(long target) {
        return ResetMath.displayDateTime(target, System.currentTimeMillis());
    }

    static void deliver(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        int profileId = intent.getIntExtra("profile", -1);
        int slot = intent.getIntExtra("slot", -1);
        if (slot != ReminderStore.SHORT && slot != ReminderStore.WEEK) return;
        ReminderStore.Profile profile = ReminderStore.profile(context, profileId);
        if (profile == null || !profile.allows(slot)) return;
        boolean early = intent.getBooleanExtra("early", false);
        ReminderStore.Slot s = ReminderStore.load(context, profileId, slot);
        long now = System.currentTimeMillis();
        if (s.target <= 0 || s.target != intent.getLongExtra("target", -1L)) return;
        if (early) {
            if (!s.early || s.earlyDelivered ||
                    s.earlyMinutes != intent.getIntExtra("minutes", -1)) return;
        }
        long intended = early ? s.target - s.earlyMinutes * ResetMath.MINUTE : s.target;
        if (now < intended) { schedule(context, s, early); return; }

        AlarmDiagnostics.alarmReceived(context, intended, now);
        createChannel(context);
        reconcileDue(context, now);
    }

    /** Reconcile each due cycle from its saved calendar boundary. Called by alarms,
     * boot/time-change recovery and the foreground system-clock ticker. */
    static void reconcileDue(Context context, long now) {
        boolean changed = false;
        boolean rolledAny = false;
        QuietHours global = ReminderStore.globalQuiet(context);
        for (ReminderStore.Profile p : ReminderStore.profiles(context)) {
            for (int quota : new int[]{ReminderStore.SHORT, ReminderStore.WEEK}) {
                if (!p.allows(quota)) continue;
                ReminderStore.Slot current = ReminderStore.load(context, p.id, quota);
                if (current.target <= 0) continue;
                boolean rolled = false;
                if (current.target <= now) {
                    long due = current.latestDue(now);
                    long next = current.nextTarget(now);
                    // On long offline gaps only the most recent reset is relevant.
                    // Never emit many old notifications or repeat the same delivered cycle.
                    int notificationStatus;
                    if (current.oneShot && (current.earlyDelivered || BootState.onceDelivered(context, current, true)))
                        notificationStatus = current.earlySuppressed ? HistoryStore.QUIET : HistoryStore.EARLY_SENT;
                    else if (current.oneShot && BootState.onceDelivered(context, current, false))
                        notificationStatus = HistoryStore.SENT;
                    else if (current.oneShot && !current.at && current.early)
                        notificationStatus = due < now - ResetMath.DAY ? HistoryStore.MISSED :
                                QuietHours.suppressed(global, p.quiet, due - current.earlyMinutes * ResetMath.MINUTE, now) ? HistoryStore.QUIET :
                                !notificationsAllowed(context) ? HistoryStore.PERMISSION : HistoryStore.MISSED;
                    else if (!current.at) notificationStatus = HistoryStore.DISABLED;
                    else if (due == current.target && current.atDelivered) notificationStatus = HistoryStore.SENT;
                    else if (due < now - ResetMath.DAY) notificationStatus = HistoryStore.MISSED;
                    else if (QuietHours.suppressed(global, p.quiet, due, now)) notificationStatus = HistoryStore.QUIET;
                    else if (!notificationsAllowed(context)) notificationStatus = HistoryStore.PERMISSION;
                    else {
                        Context localized = AppPreferences.localized(context);
                        String label = p.account + " · " + I18n.slot(localized, current);
                        String body = current.oneShot ?
                                localized.getString(R.string.notify_once_done_body, label, timeLabel(due)) :
                                localized.getString(R.string.notify_done_body, label, timeLabel(due), timeLabel(next));
                        boolean submitted = notify(context, AlarmIdentity.id(p.id, current.id, false), p.id,
                                localized.getString(R.string.notify_due_title, p.platform),
                                body);
                        if (submitted && current.oneShot) {
                            current.atDelivered = true;
                            ReminderStore.markDelivered(context, p.id, current.id, false);
                        }
                        notificationStatus = submitted ? HistoryStore.SENT : HistoryStore.FAILED;
                    }
                    rolled = ReminderStore.rollForward(context, current, now, notificationStatus);
                }
                boolean earlySent = deliverEarly(context, p, current, now, global);
                changed |= rolled || earlySent;
                rolledAny |= rolled;
                if (rolled) schedule(context, current, false);
                if (rolled || earlySent) schedule(context, current, true);
            }
        }
        if (changed) BootState.sync(context, TimerSnapshot.read(context, now));
        if (rolledAny) QuotaWidgetProvider.updateAll(context);
    }

    private static boolean deliverEarly(Context context, ReminderStore.Profile profile, ReminderStore.Slot s, long now, QuietHours global) {
        if (s.target <= 0 || !s.early || s.earlyDelivered || (s.oneShot && s.atDelivered) || now >= s.target
                || now < s.target - s.earlyMinutes * ResetMath.MINUTE) return false;
        long intended = s.target - s.earlyMinutes * ResetMath.MINUTE;
        if (BootState.onceDelivered(context, s, true)) {
            s.earlyDelivered = true;
            ReminderStore.markDelivered(context, s.profileId, s.id, true);
            return true;
        }
        if (QuietHours.suppressed(global, profile.quiet, intended, now)) {
            // Consume this cycle's early event even when no notification was sent;
            // neither a later ticker nor an unlock should replay it.
            s.earlyDelivered = true;
            s.earlySuppressed = true;
            ReminderStore.markEarlySuppressed(context, profile.id, s.id);
            return true;
        }
        if (!notificationsAllowed(context)) return false;
        Context localized = AppPreferences.localized(context);
        String slotName = I18n.slot(localized, s);
        String label = profile.label() + " · " + slotName;
        String remaining = I18n.remaining(localized, s.target - now);
        String content = localized.getString(R.string.notify_planned_body, label, timeLabel(s.target));
        if (!notify(context, AlarmIdentity.id(profile.id, s.id, true), profile.id, localized.getString(R.string.notify_countdown_title, remaining), content)) return false;
        s.earlyDelivered = true;
        ReminderStore.markDelivered(context, profile.id, s.id, true);
        return true;
    }

    static boolean sendTest(Context context) {
        createChannel(context);
        Context localized = AppPreferences.localized(context);
        return notify(context, 199, -1, localized.getString(R.string.test_title), localized.getString(R.string.test_message));
    }

    private static boolean notify(Context context, int notificationId, int profileId, String title, String message) {
        if (!notificationsAllowed(context)) return false;
        Intent launch = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("open_profile", profileId);
        PendingIntent content = PendingIntent.getActivity(context, notificationId, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) builder = new Notification.Builder(context, CHANNEL);
        else builder = new Notification.Builder(context).setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        builder.setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.portrait))
                .setContentTitle(title).setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(content).setAutoCancel(true).setShowWhen(true)
                .setCategory(Notification.CATEGORY_REMINDER);
        try {
            context.getSystemService(NotificationManager.class).notify(notificationId, builder.build());
            return true;
        } catch (RuntimeException ignored) { return false; }
    }
}
