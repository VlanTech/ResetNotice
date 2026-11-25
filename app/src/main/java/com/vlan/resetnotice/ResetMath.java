package com.vlan.resetnotice;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Pure time calculations shared by the UI and alarm engine. */
final class ResetMath {
    static final long MINUTE = 60_000L;
    static final long HOUR = 60 * MINUTE;
    static final long DAY = 24 * HOUR;
    static final long NO_ALARM = -1L;
    static final int DEFAULT_SHORT_MINUTES = 5 * 60;
    static final int WEEK_MINUTES = 7 * 24 * 60;

    private ResetMath() {}

    static long duration(int days, int hours, int minutes) {
        return days * DAY + hours * HOUR + minutes * MINUTE;
    }

    /** All entered reset times use the device clock's minute boundary (XX:XX:00). */
    static long minuteBoundary(long time) {
        return time <= 0 ? 0L : time - time % MINUTE;
    }

    static long relativeTarget(long deviceNow, long duration) {
        return duration <= 0 ? 0L : minuteBoundary(deviceNow) + duration;
    }

    static long delayToNextSecond(long deviceNow) {
        return 1000L - deviceNow % 1000L;
    }

    static String dateTime(long target) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date(target));
    }

    static String displayDateTime(long target, long now) {
        Calendar date = Calendar.getInstance(), current = Calendar.getInstance();
        date.setTimeInMillis(target); current.setTimeInMillis(now);
        boolean sameYear = date.get(Calendar.YEAR) == current.get(Calendar.YEAR);
        return new SimpleDateFormat(sameYear ? "MM-dd HH:mm:ss" : "yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date(target));
    }

    static int validPeriod(int minutes) {
        return Math.max(1, Math.min(WEEK_MINUTES, minutes));
    }

    /** The latest scheduled boundary that has arrived, regardless of wake-up delay. */
    static long latestDue(long target, int periodMinutes, long now) {
        if (target <= 0L || now < target) return NO_ALARM;
        long period = validPeriod(periodMinutes) * MINUTE;
        return target + (now - target) / period * period;
    }

    /** Advance from the original schedule, not the time the app happened to wake. */
    static long nextTarget(long target, int periodMinutes, long now) {
        long due = latestDue(target, periodMinutes, now);
        return due == NO_ALARM ? target : due + validPeriod(periodMinutes) * MINUTE;
    }

    static long rolloverTrigger(long now, long target) {
        return target <= 0 ? NO_ALARM : Math.max(now + 500L, target);
    }

    static String periodLabel(int minutes) {
        return periodLabel(minutes, "天", "小时", "分钟", "");
    }

    static String hoursLabel(int minutes, String hour, String minute, String separator) {
        int h = minutes / 60, m = minutes % 60;
        return (h > 0 ? h + hour : "") + (m > 0 ? (h > 0 ? separator : "") + m + minute : "");
    }

    static String periodLabel(int minutes, String day, String hour, String minute, String separator) {
        int days = minutes / 1440, hours = minutes / 60 % 24, rest = minutes % 60;
        StringBuilder result = new StringBuilder();
        if (days > 0) result.append(days).append(day);
        if (hours > 0) { if (result.length() > 0) result.append(separator); result.append(hours).append(hour); }
        if (rest > 0) { if (result.length() > 0) result.append(separator); result.append(rest).append(minute); }
        return result.toString();
    }

    static long trigger(long now, long target, boolean early, int earlyMinutes,
                        boolean enabled, boolean delivered) {
        if (!enabled || delivered || target <= 0) return NO_ALARM;
        if (early) {
            if (target <= now) return NO_ALARM;
            return Math.max(now + 500L, target - earlyMinutes * MINUTE);
        }
        // Recover a recent reset after a reboot or permission change, without
        // surfacing reminders from an old quota cycle days later.
        if (target < now - DAY) return NO_ALARM;
        return Math.max(now + 500L, target);
    }

    static String countdown(long millis) {
        return countdown(millis, "天");
    }

    static String countdown(long millis, String daySuffix) {
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        long days = seconds / 86400;
        long hours = (seconds / 3600) % 24;
        long minutes = (seconds / 60) % 60;
        long secs = seconds % 60;
        return (days > 0 ? days + daySuffix + " " : "") +
                String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, secs);
    }
}
