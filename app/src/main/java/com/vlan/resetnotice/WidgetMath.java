package com.vlan.resetnotice;

/** A widget can display an elapsed cycle without mutating shared reminder
 * state or sending catch-up notifications. */
final class WidgetMath {
    static long displayTarget(boolean enabled, long savedTarget, int periodMinutes, long now) {
        return !enabled || savedTarget <= 0 ? 0L : ResetMath.nextTarget(savedTarget, periodMinutes, now);
    }
    static long chronometerBase(long elapsedRealtime, long target, long now) {
        return elapsedRealtime + Math.max(0L, target - now);
    }
}
