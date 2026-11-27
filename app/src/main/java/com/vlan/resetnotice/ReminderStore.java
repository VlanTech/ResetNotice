package com.vlan.resetnotice;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** One persistent profile per AI/account. Profile identifiers are never recycled. */
final class ReminderStore {
    static final int SHORT = 0;
    static final int WEEK = 1;
    static final int MAX_PROFILES = 100;
    private static final String STORE = "reset_reminders_v1";
    private static final String IDS = "profile_ids_v2";
    private static final String NEXT = "next_profile_id_v2";
    private static final String INITIALIZED = "profiles_initialized_v2";
    private static final String CLOCK_ALIGNED = "clock_minute_alignment_v3";

    static final class Profile {
        final int id;
        final String platform, account;
        final boolean shortEnabled;
        final QuietHours quiet;
        Profile(int id, String platform, String account, boolean shortEnabled) {
            this(id, platform, account, shortEnabled, QuietHours.OFF);
        }
        Profile(int id, String platform, String account, boolean shortEnabled, QuietHours quiet) {
            this.id = id; this.platform = platform; this.account = account; this.shortEnabled = shortEnabled;
            this.quiet = quiet;
        }
        String label() { return platform + " · " + account; }
        boolean allows(int slot) { return slot == WEEK || (slot == SHORT && shortEnabled); }
    }
    static final class Slot {
        final int profileId, id;
        long target;
        long completedAt;
        boolean oneShot;
        boolean early, at, earlyDelivered, atDelivered, earlySuppressed;
        int earlyMinutes, periodMinutes;
        Slot(int profileId, int id) { this.profileId = profileId; this.id = id; }
        String title() { return id == WEEK ? "周限额" : "短时限额"; }
        boolean completed() { return oneShot && target <= 0 && completedAt > 0; }
        long nextTarget(long now) { return ResetMath.nextTarget(target, periodMinutes, now, oneShot); }
        long latestDue(long now) { return ResetMath.latestDue(target, periodMinutes, now, oneShot); }
        String onceToken(boolean early) {
            return oneShot && target > 0 ? profileId + ":" + id + ":" + target + ":" + early : "";
        }
        void normalize() {
            if (id != SHORT) oneShot = false;
            if (!oneShot || target > 0) completedAt = 0L;
            // In single-use mode only one reminder is selected. At-time is the default
            // if an upgraded recurring timer had both reminders enabled.
            if (oneShot && early && at) early = false;
        }
    }
    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }
    private static String profileKey(int id, String field) { return "profile_" + id + "_" + field; }
    private static String slotKey(int profileId, int slot, String field) {
        return "profile_" + profileId + "_slot_" + slot + "_" + field;
    }
    private static String legacyKey(int slot, String field) { return "slot_" + slot + "_" + field; }

    private static QuietHours readQuiet(SharedPreferences p, String prefix) {
        return new QuietHours(p.getBoolean(prefix + "enabled", false),
                p.getInt(prefix + "start", QuietHours.DEFAULT_START), p.getInt(prefix + "end", QuietHours.DEFAULT_END));
    }
    private static void writeQuiet(SharedPreferences.Editor e, String prefix, QuietHours quiet) {
        e.putBoolean(prefix + "enabled", quiet.enabled).putInt(prefix + "start", quiet.start).putInt(prefix + "end", quiet.end);
    }
    static QuietHours globalQuiet(Context context) { return readQuiet(prefs(context), "global_quiet_"); }
    static void setGlobalQuiet(Context context, QuietHours quiet) {
        SharedPreferences.Editor e = prefs(context).edit();
        writeQuiet(e, "global_quiet_", quiet);
        e.commit();
    }

    private static void writeProfile(SharedPreferences.Editor editor, Profile profile) {
        editor.putString(profileKey(profile.id, "platform"), profile.platform)
                .putString(profileKey(profile.id, "account"), profile.account)
                .putBoolean(profileKey(profile.id, "short_enabled"), profile.shortEnabled);
        writeQuiet(editor, profileKey(profile.id, "quiet_"), profile.quiet);
    }
    private static Slot readSlot(SharedPreferences p, int profileId, int slot, boolean legacy) {
        String prefix = legacy ? "slot_" + slot + "_" : "profile_" + profileId + "_slot_" + slot + "_";
        Slot s = new Slot(profileId, slot);
        s.target = p.getLong(prefix + "target", 0L);
        s.early = p.getBoolean(prefix + "early", false);
        s.at = p.getBoolean(prefix + "at", true);
        s.earlyMinutes = p.getInt(prefix + "early_minutes", slot == WEEK ? 60 : 15);
        s.periodMinutes = slot == WEEK ? ResetMath.WEEK_MINUTES : ResetMath.validPeriod(
                p.getInt(prefix + "period_minutes", ResetMath.DEFAULT_SHORT_MINUTES));
        s.earlyDelivered = p.getBoolean(prefix + "early_delivered", false);
        s.earlySuppressed = p.getBoolean(prefix + "early_suppressed", false);
        s.atDelivered = p.getBoolean(prefix + "at_delivered", false);
        s.oneShot = slot == SHORT && p.getBoolean(prefix + "one_shot", false);
        s.completedAt = p.getLong(prefix + "completed_at", 0L);
        s.normalize();
        return s;
    }
    private static void writeSlot(SharedPreferences.Editor editor, Slot s) {
        editor.putLong(slotKey(s.profileId, s.id, "target"), s.target)
                .putBoolean(slotKey(s.profileId, s.id, "early"), s.early)
                .putBoolean(slotKey(s.profileId, s.id, "at"), s.at)
                .putInt(slotKey(s.profileId, s.id, "early_minutes"), s.earlyMinutes)
                .putInt(slotKey(s.profileId, s.id, "period_minutes"), s.periodMinutes)
                .putBoolean(slotKey(s.profileId, s.id, "early_delivered"), s.earlyDelivered)
                .putBoolean(slotKey(s.profileId, s.id, "early_suppressed"), s.earlySuppressed)
                .putBoolean(slotKey(s.profileId, s.id, "at_delivered"), s.atDelivered)
                .putBoolean(slotKey(s.profileId, s.id, "one_shot"), s.oneShot)
                .putLong(slotKey(s.profileId, s.id, "completed_at"), s.completedAt);
    }

    // Upgrade atomically, retaining both independent v1 timers and delivery flags.
    private static synchronized void initialize(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.getBoolean(INITIALIZED, false)) {
            boolean legacy = p.contains(legacyKey(SHORT, "target")) || p.contains(legacyKey(WEEK, "target"));
            SharedPreferences.Editor e = p.edit();
            if (legacy) {
                Profile profile = new Profile(1, "ChatGPT", "默认账户", true);
                writeProfile(e, profile);
                writeSlot(e, readSlot(p, 1, SHORT, true));
                writeSlot(e, readSlot(p, 1, WEEK, true));
            }
            e.putString(IDS, legacy ? "1" : "").putInt(NEXT, legacy ? 2 : 1)
                    .putBoolean(INITIALIZED, true).commit();
        }
        // v1/v2 duration entries could carry the seconds of the Save tap. Align
        // all stored targets once; leave account IDs, switches and delivery flags intact.
        if (!p.getBoolean(CLOCK_ALIGNED, false)) {
            SharedPreferences.Editor e = p.edit();
            for (int profileId : ids(p)) {
                for (int slot : new int[]{SHORT, WEEK}) {
                    String key = slotKey(profileId, slot, "target");
                    long prior = p.getLong(key, 0L);
                    if (prior > 0 && prior % ResetMath.MINUTE != 0)
                        e.putLong(key, ResetMath.minuteBoundary(prior));
                }
            }
            e.putBoolean(CLOCK_ALIGNED, true).commit();
        }
    }
    private static List<Integer> ids(SharedPreferences p) {
        List<Integer> result = new ArrayList<>();
        for (String v : p.getString(IDS, "").split(",")) {
            if (v.isEmpty()) continue;
            try {
                int id = Integer.parseInt(v);
                if (id > 0 && !result.contains(id)) result.add(id);
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }
    private static String encodeIds(List<Integer> ids) {
        StringBuilder result = new StringBuilder();
        for (int id : ids) { if (result.length() > 0) result.append(','); result.append(id); }
        return result.toString();
    }
    private static Profile readProfile(SharedPreferences p, int id) {
        String platform = p.getString(profileKey(id, "platform"), null);
        if (platform == null) return null;
        return new Profile(id, platform, p.getString(profileKey(id, "account"), "默认账户"),
                p.getBoolean(profileKey(id, "short_enabled"), true), readQuiet(p, profileKey(id, "quiet_")));
    }
    static List<Profile> profiles(Context context) {
        initialize(context);
        SharedPreferences p = prefs(context);
        List<Profile> result = new ArrayList<>();
        for (int id : ids(p)) {
            Profile profile = readProfile(p, id);
            if (profile != null) result.add(profile);
        }
        return result;
    }
    static synchronized boolean reorder(Context context, List<Integer> newOrder) {
        initialize(context);
        SharedPreferences p = prefs(context);
        List<Integer> original = ids(p);
        if (newOrder.size() != original.size() || !new HashSet<>(newOrder).equals(new HashSet<>(original))) return false;
        if (original.equals(newOrder)) return true;
        return p.edit().putString(IDS, encodeIds(newOrder)).commit();
    }
    static Profile profile(Context context, int id) {
        initialize(context);
        return id > 0 ? readProfile(prefs(context), id) : null;
    }
    static boolean duplicate(Context context, int exceptId, String platform, String account) {
        for (Profile p : profiles(context)) {
            if (p.id != exceptId && p.platform.equalsIgnoreCase(platform) && p.account.equalsIgnoreCase(account)) return true;
        }
        return false;
    }
    static synchronized Profile add(Context context, String platform, String account, boolean shortEnabled) {
        return add(context, platform, account, shortEnabled, QuietHours.OFF);
    }
    static synchronized Profile add(Context context, String platform, String account, boolean shortEnabled, QuietHours quiet) {
        initialize(context);
        SharedPreferences p = prefs(context);
        List<Integer> ids = ids(p);
        if (ids.size() >= MAX_PROFILES) throw new IllegalStateException("Profile limit reached");
        if (duplicate(context, -1, platform, account)) throw new IllegalArgumentException("Duplicate profile");
        int id = p.getInt(NEXT, 1);
        if (id <= 0 || id >= 500_000_000) throw new IllegalStateException("Profile ID space exhausted");
        Profile profile = new Profile(id, platform, account, shortEnabled, quiet);
        ids.add(id);
        SharedPreferences.Editor e = p.edit();
        writeProfile(e, profile);
        e.putString(IDS, encodeIds(ids)).putInt(NEXT, id + 1).commit();
        return profile;
    }
    static synchronized Profile edit(Context context, int id, String platform, String account, boolean shortEnabled) {
        Profile previous = profile(context, id);
        if (previous == null) return null;
        return edit(context, id, platform, account, shortEnabled, previous.quiet);
    }
    static synchronized Profile edit(Context context, int id, String platform, String account, boolean shortEnabled, QuietHours quiet) {
        Profile previous = profile(context, id);
        if (previous == null) return null;
        if (duplicate(context, id, platform, account)) throw new IllegalArgumentException("Duplicate profile");
        Profile result = new Profile(id, platform, account, shortEnabled, quiet);
        SharedPreferences p = prefs(context);
        SharedPreferences.Editor editor = p.edit();
        writeProfile(editor, result);
        if (previous.shortEnabled && !shortEnabled) {
            Slot shortSlot = readSlot(p, id, SHORT, false);
            HistoryStore.manual(p, editor, id, SHORT, shortSlot.periodMinutes,
                    shortSlot.target, 0, System.currentTimeMillis());
            shortSlot.target = 0;
            shortSlot.completedAt = 0;
            shortSlot.earlyDelivered = shortSlot.atDelivered = shortSlot.earlySuppressed = false;
            writeSlot(editor, shortSlot);
        }
        editor.commit();
        return result;
    }
    static synchronized void delete(Context context, int id) {
        initialize(context);
        SharedPreferences p = prefs(context);
        List<Integer> ids = ids(p);
        ids.remove(Integer.valueOf(id));
        SharedPreferences.Editor e = p.edit().putString(IDS, encodeIds(ids));
        String prefix = "profile_" + id + "_";
        for (String key : p.getAll().keySet()) if (key.startsWith(prefix)) e.remove(key);
        e.commit();
    }
    static Slot load(Context context, int profileId, int slot) {
        initialize(context);
        return readSlot(prefs(context), profileId, slot, false);
    }
    static void save(Context context, Slot s) {
        Profile p = profile(context, s.profileId);
        if (p == null || !p.allows(s.id)) return;
        s.target = ResetMath.minuteBoundary(s.target);
        s.periodMinutes = s.id == WEEK ? ResetMath.WEEK_MINUTES : ResetMath.validPeriod(s.periodMinutes);
        s.normalize();
        SharedPreferences.Editor e = prefs(context).edit();
        writeSlot(e, s);
        e.commit();
    }
    static synchronized void saveManual(Context context, Slot s, long now) {
        Profile p = profile(context, s.profileId);
        if (p == null || !p.allows(s.id)) return;
        s.target = ResetMath.minuteBoundary(s.target);
        s.periodMinutes = s.id == WEEK ? ResetMath.WEEK_MINUTES : ResetMath.validPeriod(s.periodMinutes);
        s.normalize();
        SharedPreferences prefs = prefs(context);
        Slot before = readSlot(prefs, s.profileId, s.id, false);
        SharedPreferences.Editor e = prefs.edit();
        HistoryStore.manual(prefs, e, s.profileId, s.id, s.periodMinutes, before.target, s.target, now);
        writeSlot(e, s);
        e.commit();
    }
    static boolean rollForward(Context context, Slot s, long now) {
        return rollForward(context, s, now, HistoryStore.MISSED);
    }
    static synchronized boolean rollForward(Context context, Slot s, long now, int notificationStatus) {
        long next = s.nextTarget(now);
        if (next == s.target) return false;
        Profile p = profile(context, s.profileId);
        if (p == null || !p.allows(s.id)) return false;
        SharedPreferences prefs = prefs(context);
        Slot saved = readSlot(prefs, s.profileId, s.id, false);
        if (saved.target != s.target || saved.oneShot != s.oneShot) return false;
        SharedPreferences.Editor e = prefs.edit();
        HistoryStore.rollovers(prefs, e, s, now, notificationStatus);
        long previousTarget = s.target;
        long previousCompletion = s.completedAt;
        boolean previousEarly = s.earlyDelivered, previousAt = s.atDelivered, previousSuppressed = s.earlySuppressed;
        s.target = next;
        s.completedAt = s.oneShot ? previousTarget : 0L;
        s.earlyDelivered = s.atDelivered = s.earlySuppressed = false;
        writeSlot(e, s);
        if (e.commit()) return true;
        s.target = previousTarget;
        s.completedAt = previousCompletion;
        s.earlyDelivered = previousEarly; s.atDelivered = previousAt; s.earlySuppressed = previousSuppressed;
        return false;
    }
    static void markDelivered(Context context, int profileId, int slot, boolean early) {
        Profile p = profile(context, profileId);
        if (p != null && p.allows(slot)) prefs(context).edit()
                .putBoolean(slotKey(profileId, slot, early ? "early_delivered" : "at_delivered"), true).commit();
    }
    static void markEarlySuppressed(Context context, int profileId, int slot) {
        Profile p = profile(context, profileId);
        if (p != null && p.allows(slot)) prefs(context).edit()
                .putBoolean(slotKey(profileId, slot, "early_delivered"), true)
                .putBoolean(slotKey(profileId, slot, "early_suppressed"), true).commit();
    }
}
