package com.vlan.resetnotice;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.HashSet;
import java.util.Set;

/** Rebind only at a weekly display's day/hour boundary, not per second.
 * Non-wakeup alarms never wake a sleeping phone just to redraw the launcher. */
public final class WidgetRefreshReceiver extends BroadcastReceiver {
    private static final String ACTION = "com.vlan.resetnotice.WIDGET_BOUNDARY";
    @Override public void onReceive(Context context, Intent intent) {
        if (ACTION.equals(intent.getAction())) QuotaWidgetProvider.updateAll(context);
    }
    static void schedule(Context c) {
        if (!BootState.unlocked(c) || Build.VERSION.SDK_INT < 24) return;
        try {
            AppWidgetManager widgets = AppWidgetManager.getInstance(c);
            int[] individual = widgets.getAppWidgetIds(QuotaWidgetProvider.component(c));
            boolean all = widgets.getAppWidgetIds(AllAccountsWidgetProvider.component(c)).length > 0;
            Set<Integer> profiles = new HashSet<>();
            if (all) for (ReminderStore.Profile p : ReminderStore.profiles(c)) profiles.add(p.id);
            else for (int id : individual) {
                int profile = WidgetBindingStore.profile(c, id);
                if (profile > 0) profiles.add(profile);
            }
            long now = System.currentTimeMillis(), next = Long.MAX_VALUE;
            for (int profile : profiles) {
                if (ReminderStore.profile(c, profile) == null) continue;
                ReminderStore.Slot week = ReminderStore.load(c, profile, ReminderStore.WEEK);
                long target = WidgetMath.displayTarget(true, week.target, week.periodMinutes, now);
                if (target > 0) next = Math.min(next, WidgetMath.weekParts(target, now).refreshAt);
            }
            Intent delivery = new Intent(c, WidgetRefreshReceiver.class).setAction(ACTION);
            AlarmManager alarms = c.getSystemService(AlarmManager.class);
            if (next == Long.MAX_VALUE) {
                PendingIntent previous = PendingIntent.getBroadcast(c, 202, delivery,
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (previous != null) { alarms.cancel(previous); previous.cancel(); }
                return;
            }
            PendingIntent pi = PendingIntent.getBroadcast(c, 202, delivery,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long when = Math.max(now + 250L, next);
            if (AlarmEngine.exactAllowed(c)) {
                try { alarms.setExact(AlarmManager.RTC, when, pi); return; }
                catch (SecurityException ignored) {}
            }
            alarms.set(AlarmManager.RTC, when, pi);
        } catch (RuntimeException ignored) { /* Periodic widget updates remain a fallback. */ }
    }
}
