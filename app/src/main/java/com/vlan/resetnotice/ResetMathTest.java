package com.vlan.resetnotice;

import java.util.TimeZone;

final class ResetMathTest {
    private static int assertions;
    private static void eq(long expected, long actual) {
        assertions++;
        if (expected != actual) throw new AssertionError(expected + " != " + actual);
    }
    private static void eq(String expected, String actual) {
        assertions++;
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) {
        long now = 1_750_000_000_000L;
        long target = now + ResetMath.duration(0, 5, 0);
        eq(now + 5 * ResetMath.HOUR, target);
        eq(target, ResetMath.trigger(now, target, false, 15, true, false));
        eq(target - 15 * ResetMath.MINUTE, ResetMath.trigger(now, target, true, 15, true, false));
        eq(now + 7 * ResetMath.DAY, now + ResetMath.duration(7, 0, 0));
        eq(ResetMath.NO_ALARM, ResetMath.trigger(now, target, true, 15, false, false));
        eq(ResetMath.NO_ALARM, ResetMath.trigger(now, target, true, 15, true, true));
        eq(ResetMath.NO_ALARM, ResetMath.trigger(now, now - 10, true, 15, true, false));
        eq(now + 500, ResetMath.trigger(now, now + ResetMath.MINUTE, true, 15, true, false));
        eq(now + 500, ResetMath.trigger(now, now - 30 * ResetMath.MINUTE, false, 15, true, false));
        eq(ResetMath.NO_ALARM, ResetMath.trigger(now, now - 2 * ResetMath.DAY, false, 15, true, false));
        eq(ResetMath.NO_ALARM, ResetMath.trigger(now, 0, false, 15, true, false));
        eq("05:00:00", ResetMath.countdown(5 * ResetMath.HOUR));
        eq("7天 00:00:00", ResetMath.countdown(7 * ResetMath.DAY));
        eq("00:00:01", ResetMath.countdown(10));
        eq("00:00:00", ResetMath.countdown(-500L));

        long minute = ResetMath.minuteBoundary(now);
        long fiveHours = minute + 5 * ResetMath.HOUR;
        // Setting at any second of the same device minute gives the same absolute target.
        for (long second = 0; second < 60; second++) {
            long deviceNow = minute + second * 1000L + 456L;
            eq(fiveHours, ResetMath.relativeTarget(deviceNow, 5 * ResetMath.HOUR));
            eq(0L, ResetMath.relativeTarget(deviceNow, ResetMath.duration(7, 0, 0)) % ResetMath.MINUTE);
            eq(544L, ResetMath.delayToNextSecond(deviceNow));
        }
        eq("04:59:13", ResetMath.countdown(fiveHours - (minute + 47_000L)));
        eq("6天 23:59:13", ResetMath.countdown(ResetMath.relativeTarget(minute + 47_000L, 7 * ResetMath.DAY) - (minute + 47_000L)));
        eq(minute + ResetMath.MINUTE, ResetMath.relativeTarget(minute + 59_999L, ResetMath.MINUTE));
        eq(0L, ResetMath.minuteBoundary(0L));
        eq(0L, ResetMath.relativeTarget(minute + 12_000L, 0L));
        eq(1000L, ResetMath.delayToNextSecond(minute));
        eq(1L, ResetMath.delayToNextSecond(minute + 999L));
        eq(fiveHours, ResetMath.trigger(minute, fiveHours, false, 15, true, false));
        eq(fiveHours - 15 * ResetMath.MINUTE, ResetMath.trigger(minute, fiveHours, true, 15, true, false));
        eq(fiveHours, ResetMath.nextTarget(fiveHours, 300, fiveHours - 1));
        eq(fiveHours + 5 * ResetMath.HOUR, ResetMath.nextTarget(fiveHours, 300, fiveHours));
        eq(fiveHours + 5 * ResetMath.HOUR, ResetMath.nextTarget(fiveHours, 300, fiveHours + 27_000L));
        eq(fiveHours + 20 * ResetMath.HOUR, ResetMath.nextTarget(fiveHours, 300, fiveHours + 19 * ResetMath.HOUR));
        eq(fiveHours + 15 * ResetMath.HOUR, ResetMath.latestDue(fiveHours, 300, fiveHours + 19 * ResetMath.HOUR));
        eq(fiveHours + 14 * ResetMath.DAY, ResetMath.nextTarget(fiveHours, ResetMath.WEEK_MINUTES, fiveHours + 8 * ResetMath.DAY));
        eq(0L, ResetMath.nextTarget(0L, 300, fiveHours));
        eq(ResetMath.NO_ALARM, ResetMath.latestDue(fiveHours, 300, fiveHours - 1));
        eq(fiveHours, ResetMath.rolloverTrigger(minute, fiveHours));
        eq(fiveHours + 500L, ResetMath.rolloverTrigger(fiveHours, fiveHours));
        eq(ResetMath.NO_ALARM, ResetMath.rolloverTrigger(fiveHours, 0L));
        eq("5小时", ResetMath.periodLabel(300));
        eq("7天", ResetMath.periodLabel(10080));
        eq("1天2小时15分钟", ResetMath.periodLabel(1575));
        // A recurrence must stay minute-aligned after many delayed wake-ups.
        long recurring = fiveHours;
        for (int cycle = 1; cycle <= 100; cycle++) {
            recurring = ResetMath.nextTarget(recurring, 300, recurring + 42_123L);
            eq(fiveHours + cycle * 5 * ResetMath.HOUR, recurring);
            eq(0L, recurring % ResetMath.MINUTE);
        }
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            eq("1970-01-01 08:00:00", ResetMath.dateTime(0L));
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            eq("1970-01-01 00:00:00", ResetMath.dateTime(0L));
        } finally { TimeZone.setDefault(original); }
        System.out.println(assertions + " time/scheduling assertions passed");
    }
}
