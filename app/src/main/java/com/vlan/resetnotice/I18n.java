package com.vlan.resetnotice;

import android.content.Context;

final class I18n {
    static String slot(Context c, int id) { return c.getString(id == ReminderStore.WEEK ? R.string.week_quota : R.string.short_quota); }
    static String countdown(Context c, long millis) { return ResetMath.countdown(millis, c.getString(R.string.unit_d)); }
    static String period(Context c, int minutes) {
        return ResetMath.periodLabel(minutes, c.getString(R.string.unit_d), c.getString(R.string.unit_h), c.getString(R.string.unit_m), " ");
    }
}
