package com.vlan.resetnotice;

import android.content.Context;

final class I18n {
    static String slot(Context c, int id) { return c.getString(id == ReminderStore.WEEK ? R.string.week_quota : R.string.short_quota); }
    static String slot(Context c, ReminderStore.Slot s) { return slot(c, s.id, s.periodMinutes); }
    static String slot(Context c, int id, int periodMinutes) {
        if (id == ReminderStore.WEEK) return c.getString(R.string.week_quota);
        String interval = ResetMath.hoursLabel(periodMinutes, c.getString(R.string.unit_h), c.getString(R.string.unit_m), " ");
        return c.getString(R.string.interval_quota, interval);
    }
    static String remaining(Context c, long millis) {
        if (millis < ResetMath.MINUTE) return c.getString(R.string.under_minute);
        return period(c, (int)Math.min(Integer.MAX_VALUE, (millis + ResetMath.MINUTE - 1) / ResetMath.MINUTE));
    }
    static String countdown(Context c, long millis) { return ResetMath.countdown(millis, c.getString(R.string.unit_d)); }
    static String period(Context c, int minutes) {
        return ResetMath.periodLabel(minutes, c.getString(R.string.unit_d), c.getString(R.string.unit_h), c.getString(R.string.unit_m), " ");
    }
}
