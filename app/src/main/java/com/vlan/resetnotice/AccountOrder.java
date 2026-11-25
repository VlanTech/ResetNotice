package com.vlan.resetnotice;

import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Automatic order is a projection of persisted manual order, never overwrites it. */
final class AccountOrder {
    static List<Integer> manual(Context context) {
        List<Integer> result = new ArrayList<>();
        for (ReminderStore.Profile p : ReminderStore.profiles(context)) result.add(p.id);
        return result;
    }
    static long next(Context context, ReminderStore.Profile profile, long now) {
        long earliest = Long.MAX_VALUE;
        for (int slot : new int[]{ReminderStore.SHORT, ReminderStore.WEEK}) {
            if (!profile.allows(slot)) continue;
            ReminderStore.Slot timer = ReminderStore.load(context, profile.id, slot);
            if (timer.target > 0) earliest = Math.min(earliest, ResetMath.nextTarget(timer.target, timer.periodMinutes, now));
        }
        return earliest;
    }
    static List<Integer> byNextReset(Context context, long now) {
        List<ReminderStore.Profile> profiles = ReminderStore.profiles(context);
        Map<Integer, Long> times = new HashMap<>();
        List<Integer> result = new ArrayList<>();
        for (ReminderStore.Profile p : profiles) { result.add(p.id); times.put(p.id, next(context, p, now)); }
        // Collections.sort is stable: equal/unset deadlines keep the manual order.
        Collections.sort(result, (a, b) -> Long.compare(times.get(a), times.get(b)));
        return result;
    }
}
