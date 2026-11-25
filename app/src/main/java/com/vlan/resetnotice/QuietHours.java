package com.vlan.resetnotice;

import java.util.Calendar;
import java.util.Locale;

/** A recurring local-time window, inclusive start and exclusive end.
 * A disabled window never suppresses a reminder. */
final class QuietHours {
    static final int DEFAULT_START = 23 * 60;
    static final int DEFAULT_END = 8 * 60;
    static final QuietHours OFF = new QuietHours(false, DEFAULT_START, DEFAULT_END);
    final boolean enabled;
    final int start, end;

    QuietHours(boolean enabled, int start, int end) {
        this.start = start >= 0 && start < 1440 ? start : DEFAULT_START;
        this.end = end >= 0 && end < 1440 ? end : DEFAULT_END;
        this.enabled = enabled && this.start != this.end;
    }

    boolean containsMinute(int minute) {
        if (!enabled || minute < 0 || minute >= 1440) return false;
        return start < end ? minute >= start && minute < end : minute >= start || minute < end;
    }
    boolean contains(long timestamp) {
        if (!enabled) return false;
        Calendar local = Calendar.getInstance();
        local.setTimeInMillis(timestamp);
        return containsMinute(local.get(Calendar.HOUR_OF_DAY) * 60 + local.get(Calendar.MINUTE));
    }
    /** Never deliver a quiet-time event later as a catch-up alert, nor deliver
     * a delayed event during quiet time. Independent windows form a union. */
    static boolean suppressed(QuietHours global, QuietHours account, long intended, long now) {
        return global.contains(intended) || account.contains(intended) ||
                global.contains(now) || account.contains(now);
    }
    static String time(int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
    }
    boolean same(QuietHours other) {
        return enabled == other.enabled && start == other.start && end == other.end;
    }
}
