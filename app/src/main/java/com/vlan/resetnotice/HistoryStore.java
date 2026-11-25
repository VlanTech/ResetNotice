package com.vlan.resetnotice;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Local per-account reset log. Slot and history updates share one preference
 * transaction; no old events are invented on an upgrade. */
final class HistoryStore {
    static final int LIMIT = 1000;
    static final int SENT = 1, QUIET = 2, DISABLED = 3, PERMISSION = 4, MISSED = 5,
            FAILED = 6, MANUAL = 7;

    static final class Entry {
        final long when, recorded, previous, next;
        final int slot, period, status;
        Entry(long when, long recorded, long previous, long next, int slot, int period, int status) {
            this.when = when; this.recorded = recorded; this.previous = previous; this.next = next;
            this.slot = slot; this.period = period; this.status = status;
        }
        boolean manual() { return status == MANUAL; }
        boolean notified() { return status == SENT; }
        String encode() { return when + "," + recorded + "," + previous + "," + next + "," + slot + "," + period + "," + status; }
    }
    private static String key(int profile) { return "profile_" + profile + "_history_v1"; }
    static List<Entry> entries(Context c, int profile) { return read(ReminderStore.prefs(c), profile); }
    private static ArrayList<Entry> read(SharedPreferences p, int profile) {
        ArrayList<Entry> result = new ArrayList<>();
        String data = p.getString(key(profile), "");
        for (String row : data.split("\\n")) {
            if (result.size() >= LIMIT) break;
            String[] cols = row.split(",");
            if (cols.length != 7) continue;
            try {
                Entry e = new Entry(Long.parseLong(cols[0]), Long.parseLong(cols[1]), Long.parseLong(cols[2]),
                        Long.parseLong(cols[3]), Integer.parseInt(cols[4]), Integer.parseInt(cols[5]), Integer.parseInt(cols[6]));
                if (e.when > 0 && (e.slot == ReminderStore.SHORT || e.slot == ReminderStore.WEEK) &&
                        e.status >= SENT && e.status <= MANUAL) result.add(e);
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }
    private static void write(SharedPreferences.Editor editor, int profile, ArrayList<Entry> entries) {
        // New entries are inserted first, retaining insertion order at equal
        // wall-clock timestamps. The bound also handles very long offline gaps.
        Collections.sort(entries, (a,b) -> Long.compare(b.when, a.when));
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(LIMIT, entries.size()); i++) {
            if (i > 0) text.append('\n');
            text.append(entries.get(i).encode());
        }
        editor.putString(key(profile), text.toString());
    }
    static void manual(SharedPreferences p, SharedPreferences.Editor editor, int profile, int slot,
                       int period, long previous, long next, long now) {
        if (previous == next) return;
        ArrayList<Entry> entries = read(p, profile);
        entries.add(0, new Entry(now, now, previous, next, slot, period, MANUAL));
        write(editor, profile, entries);
    }
    static void rollovers(SharedPreferences p, SharedPreferences.Editor editor, ReminderStore.Slot slot,
                          long now, int latestStatus) {
        long due = ResetMath.latestDue(slot.target, slot.periodMinutes, now);
        if (due == ResetMath.NO_ALARM) return;
        long period = ResetMath.validPeriod(slot.periodMinutes) * ResetMath.MINUTE;
        long count = Math.min(LIMIT, 1 + (due - slot.target) / period);
        ArrayList<Entry> entries = read(p, slot.profileId);
        for (long i = count - 1; i >= 0; i--) {
            long boundary = due - i * period;
            entries.add(0, new Entry(boundary, now, boundary, boundary + period, slot.id,
                    slot.periodMinutes, i == 0 ? latestStatus : MISSED));
        }
        write(editor, slot.profileId, entries);
    }
}
