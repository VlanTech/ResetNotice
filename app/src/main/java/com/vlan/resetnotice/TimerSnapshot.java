package com.vlan.resetnotice;

import android.content.Context;

/** The active recurring timers; empty when every timer has been cleared. */
final class TimerSnapshot {
    int count;
    long nextReset = Long.MAX_VALUE;
    long nextEvent = Long.MAX_VALUE;
    // Earliest user-enabled, non-quiet notification, not a silent rollover.
    // Mirror only its timestamp and relevant quiet windows for Direct Boot.
    long nextVisibleAlert = Long.MAX_VALUE;
    QuietHours nextAlertGlobalQuiet = QuietHours.OFF, nextAlertAccountQuiet = QuietHours.OFF;
    String nextLabel = "";
    String nextProfileLabel = "";
    int nextSlotId;
    int nextPeriodMinutes;

    static TimerSnapshot read(Context context, long now) {
        TimerSnapshot snapshot = new TimerSnapshot();
        QuietHours global = ReminderStore.globalQuiet(context);
        for (ReminderStore.Profile p : ReminderStore.profiles(context)) {
            for (int id : new int[]{ReminderStore.SHORT, ReminderStore.WEEK}) {
                if (!p.allows(id)) continue;
                ReminderStore.Slot s = ReminderStore.load(context, p.id, id);
                if (s.target <= 0) continue;
                snapshot.count++;
                if (s.target < snapshot.nextReset) {
                    snapshot.nextReset = s.target;
                    snapshot.nextLabel = p.label() + " · " + s.title();
                    snapshot.nextProfileLabel = p.label();
                    snapshot.nextSlotId = s.id;
                    snapshot.nextPeriodMinutes = s.periodMinutes;
                }
                snapshot.nextEvent = Math.min(snapshot.nextEvent, s.target);
                if (s.at && !s.atDelivered) snapshot.considerVisible(s.target, global, p.quiet);
                if (s.early && !s.earlyDelivered) {
                    long early = s.target - s.earlyMinutes * ResetMath.MINUTE;
                    if (s.target > now)
                        snapshot.considerVisible(early, global, p.quiet);
                    // Already-due reminders are reconciled once per normal check,
                    // rather than repeatedly spinning if permission was denied.
                    if (early > now) snapshot.nextEvent = Math.min(snapshot.nextEvent, early);
                }
            }
        }
        return snapshot;
    }

    private void considerVisible(long when, QuietHours global, QuietHours account) {
        if (when <= 0 || when >= nextVisibleAlert || QuietHours.suppressed(global, account, when, when)) return;
        nextVisibleAlert = when;
        nextAlertGlobalQuiet = global;
        nextAlertAccountQuiet = account;
    }

    boolean shouldRunService(boolean ongoingNotification) { return ongoingNotification && count > 0; }

    long nextCheckDelay(long now) {
        if (count == 0) return 60_000L;
        return Math.max(500L, Math.min(60_000L, nextEvent - now));
    }
}
