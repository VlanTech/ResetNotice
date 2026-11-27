package com.vlan.resetnotice;

/** Alarm-clock mode is reserved for actual user-visible notifications.
 * A disabled at-time alert is still a normal cycle-rollover alarm. */
final class AlarmPolicy {
    static final int INEXACT = 1, EXACT = 2, CLOCK = 3;
    static boolean visible(ReminderStore.Slot slot, boolean early, long now) {
        if (slot.target <= 0) return false;
        return early ? slot.early && !slot.earlyDelivered && !(slot.oneShot && slot.atDelivered) && slot.target > now
                : slot.at && !slot.atDelivered && !(slot.oneShot && slot.earlyDelivered);
    }
    static int mode(boolean clockRequested, boolean visible, boolean notificationAllowed, boolean exactAllowed) {
        if (!exactAllowed) return INEXACT;
        return clockRequested && visible && notificationAllowed ? CLOCK : EXACT;
    }
}
