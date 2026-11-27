package com.vlan.resetnotice;

/** A widget can display an elapsed cycle without mutating shared reminder
 * state or sending catch-up notifications. */
final class WidgetMath {
    static long displayTarget(boolean enabled, long savedTarget, int periodMinutes, long now) {
        return displayTarget(enabled, savedTarget, periodMinutes, now, false);
    }
    static long displayTarget(boolean enabled, long savedTarget, int periodMinutes, long now, boolean oneShot) {
        return !enabled || savedTarget <= 0 ? 0L : ResetMath.nextTarget(savedTarget, periodMinutes, now, oneShot);
    }
    static long chronometerBase(long elapsedRealtime, long target, long now) {
        return elapsedRealtime + Math.max(0L, target - now);
    }
    static int glassAlpha(int transparency) {
        return Math.round(255f * (100 - Math.max(0, Math.min(100, transparency))) / 100f);
    }
    static final class WeekParts {
        final long days, clockTarget, refreshAt;
        final boolean padHours;
        WeekParts(long days, long clockTarget, long refreshAt, boolean padHours) {
            this.days = days; this.clockTarget = clockTarget; this.refreshAt = refreshAt; this.padHours = padHours;
        }
    }
    /** Native launcher chronometers can tick within the current day; only
     * the day and hour-format transitions need a new binding. */
    static WeekParts weekParts(long target, long now) {
        long remain = Math.max(0, target - now), days = remain / ResetMath.DAY;
        long chunk = remain % ResetMath.DAY, clockTarget = target - days * ResetMath.DAY;
        boolean padHours = chunk < ResetMath.HOUR;
        long next = Long.MAX_VALUE;
        if (remain > 0) {
            if (!padHours) next = clockTarget - ResetMath.HOUR + 1;
            else if (days > 0) next = clockTarget + 1;
        }
        return new WeekParts(days, clockTarget, next, padHours);
    }
}
