package com.vlan.resetnotice;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Exercises real profile persistence with an in-memory Android preferences substitute. */
public final class ProfileStoreTest {
    private static final class FakeContext extends Context {
        private final MemoryPrefs prefs = new MemoryPrefs();
        @Override public SharedPreferences getSharedPreferences(String name, int mode) { return prefs; }
    }
    private static final class MemoryPrefs implements SharedPreferences {
        private final Map<String, Object> data = new HashMap<>();
        public Map<String, ?> getAll() { return new HashMap<>(data); }
        public String getString(String key, String def) { return (String)data.getOrDefault(key, def); }
        public boolean getBoolean(String key, boolean def) { return (Boolean)data.getOrDefault(key, def); }
        public long getLong(String key, long def) { return (Long)data.getOrDefault(key, def); }
        public int getInt(String key, int def) { return (Integer)data.getOrDefault(key, def); }
        public boolean contains(String key) { return data.containsKey(key); }
        public Editor edit() {
            return new Editor() {
                private final Map<String, Object> changes = new HashMap<>();
                private final Set<String> removals = new HashSet<>();
                public Editor putString(String k, String v) { changes.put(k, v); return this; }
                public Editor putBoolean(String k, boolean v) { changes.put(k, v); return this; }
                public Editor putLong(String k, long v) { changes.put(k, v); return this; }
                public Editor putInt(String k, int v) { changes.put(k, v); return this; }
                public Editor remove(String k) { removals.add(k); return this; }
                public boolean commit() {
                    for (String key : removals) data.remove(key);
                    data.putAll(changes); return true;
                }
            };
        }
    }
    private static int assertions;
    private static void require(boolean expression, String label) {
        assertions++;
        if (!expression) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        FakeContext empty = new FakeContext();
        require(ReminderStore.profiles(empty).isEmpty(), "fresh install is empty");
        require(ReminderStore.profiles(empty).isEmpty(), "fresh initialization is idempotent");

        FakeContext context = new FakeContext();
        long now = ResetMath.minuteBoundary(1_750_000_000_000L);
        long shortTarget = now + 5 * ResetMath.HOUR, weekTarget = now + 7 * ResetMath.DAY;
        context.prefs.edit().putLong("slot_0_target", shortTarget + 17_000L).putBoolean("slot_0_early", true)
                .putInt("slot_0_early_minutes", 30).putBoolean("slot_0_early_delivered", true)
                .putLong("slot_1_target", weekTarget + 41_000L).putBoolean("slot_1_at", false).commit();
        ReminderStore.Profile old = ReminderStore.profiles(context).get(0);
        require(old.id == 1 && old.platform.equals("ChatGPT") && old.shortEnabled, "legacy migration profile");
        require(ReminderStore.profiles(context).size() == 1, "migration idempotent");
        ReminderStore.Slot legacyShort = ReminderStore.load(context, old.id, ReminderStore.SHORT);
        require(legacyShort.target == shortTarget && legacyShort.early && legacyShort.earlyMinutes == 30
                && legacyShort.earlyDelivered, "legacy short time, options, delivery flag");
        require(ReminderStore.load(context, old.id, ReminderStore.WEEK).target == weekTarget
                && !ReminderStore.load(context, old.id, ReminderStore.WEEK).at, "legacy week time and options");

        // Simulate upgrading an already-migrated 1.1.0 installation with multiple accounts.
        FakeContext v2 = new FakeContext();
        v2.prefs.edit().putBoolean("profiles_initialized_v2", true).putString("profile_ids_v2", "4,8")
                .putInt("next_profile_id_v2", 9)
                .putString("profile_4_platform", "ChatGPT").putString("profile_4_account", "主账号")
                .putBoolean("profile_4_short_enabled", true)
                .putLong("profile_4_slot_0_target", shortTarget + 47_000L)
                .putBoolean("profile_4_slot_0_early_delivered", true)
                .putLong("profile_4_slot_1_target", weekTarget + 34_000L)
                .putString("profile_8_platform", "Claude").putString("profile_8_account", "Pro")
                .putBoolean("profile_8_short_enabled", false)
                .putLong("profile_8_slot_1_target", weekTarget + ResetMath.DAY + 1_234L).commit();
        require(ReminderStore.profiles(v2).size() == 2, "v2 profiles preserved");
        require(ReminderStore.load(v2, 4, ReminderStore.SHORT).target == shortTarget, "v2 short target aligned");
        require(ReminderStore.load(v2, 4, ReminderStore.SHORT).earlyDelivered, "v2 delivery flag preserved");
        require(ReminderStore.load(v2, 4, ReminderStore.WEEK).target == weekTarget, "v2 week target aligned");
        require(!ReminderStore.profile(v2, 8).shortEnabled && ReminderStore.load(v2, 8, ReminderStore.SHORT).target == 0,
                "v2 weekly-only account unchanged");
        require(ReminderStore.load(v2, 8, ReminderStore.WEEK).target == weekTarget + ResetMath.DAY, "second v2 account aligned separately");
        require(ReminderStore.add(v2, "Gemini", "主账号", true).id == 9, "v2 profile IDs retained");
        ReminderStore.Slot recurring = ReminderStore.load(v2, 4, ReminderStore.SHORT);
        require(recurring.periodMinutes == 300, "short period defaults to five hours");
        require(ReminderStore.load(v2, 4, ReminderStore.WEEK).periodMinutes == 10080, "weekly period defaults to seven days");
        recurring.periodMinutes = 240;
        recurring.at = false;
        recurring.earlyDelivered = true;
        recurring.atDelivered = true;
        ReminderStore.save(v2, recurring);
        require(!ReminderStore.rollForward(v2, recurring, shortTarget - 1L), "no premature rollover");
        require(ReminderStore.rollForward(v2, recurring, shortTarget + 4 * ResetMath.HOUR + 123L), "roll even with at-time notification disabled");
        ReminderStore.Slot advanced = ReminderStore.load(v2, 4, ReminderStore.SHORT);
        require(advanced.target == shortTarget + 8 * ResetMath.HOUR, "catch-up keeps original cadence");
        require(advanced.periodMinutes == 240 && !advanced.at, "custom period and switches preserved");
        require(!advanced.earlyDelivered && !advanced.atDelivered, "delivery flags reset for next cycle");
        require(!ReminderStore.rollForward(v2, advanced, shortTarget + 4 * ResetMath.HOUR + 123L), "rollover idempotent");
        require(ReminderStore.load(v2, 8, ReminderStore.WEEK).target == weekTarget + ResetMath.DAY, "other account not altered by rollover");

        ReminderStore.Profile claude = ReminderStore.add(context, "Claude", "Pro", true);
        ReminderStore.Profile second = ReminderStore.add(context, "ChatGPT", "第二账户", false);
        require(claude.id != second.id && old.id != claude.id, "stable distinct IDs");
        require(ReminderStore.profiles(context).size() == 3, "three account records");
        require(!second.allows(ReminderStore.SHORT) && second.allows(ReminderStore.WEEK), "weekly-only account");
        require(ReminderStore.duplicate(context, -1, "claude", "pro"), "duplicate check case insensitive");
        require(!ReminderStore.duplicate(context, claude.id, "Claude", "Pro"), "self excluded for editing");
        ReminderStore.Slot claudeWeek = ReminderStore.load(context, claude.id, ReminderStore.WEEK);
        claudeWeek.target = now + 2 * ResetMath.DAY + 29_000L; claudeWeek.early = true;
        ReminderStore.save(context, claudeWeek);
        require(ReminderStore.load(context, claude.id, ReminderStore.WEEK).target == now + 2 * ResetMath.DAY,
                "fresh slot saves always align to whole minute");
        require(ReminderStore.load(context, old.id, ReminderStore.WEEK).target == weekTarget, "separate account timers");
        require(ReminderStore.load(context, second.id, ReminderStore.WEEK).target == 0, "no timer contamination");
        ReminderStore.markDelivered(context, claude.id, ReminderStore.WEEK, false);
        require(ReminderStore.load(context, claude.id, ReminderStore.WEEK).atDelivered, "delivery flag isolated");
        require(!ReminderStore.load(context, old.id, ReminderStore.WEEK).atDelivered, "other delivery flag unchanged");
        ReminderStore.Profile renamed = ReminderStore.edit(context, old.id, "GPT", "主号", true);
        require(renamed.id == old.id && renamed.label().equals("GPT · 主号"), "metadata edit preserves ID");
        require(ReminderStore.load(context, old.id, ReminderStore.SHORT).target == shortTarget, "rename retains timer");
        ReminderStore.edit(context, old.id, "GPT", "主号", false);
        require(ReminderStore.load(context, old.id, ReminderStore.SHORT).target == 0, "disabling short clears target");
        require(ReminderStore.load(context, old.id, ReminderStore.WEEK).target == weekTarget, "disabling short preserves week");
        legacyShort.target = now + ResetMath.HOUR;
        ReminderStore.save(context, legacyShort);
        require(ReminderStore.load(context, old.id, ReminderStore.SHORT).target == 0, "disabled short rejects stale write");
        ReminderStore.edit(context, old.id, "GPT", "主号", true);
        require(ReminderStore.profile(context, old.id).shortEnabled && ReminderStore.load(context, old.id, ReminderStore.SHORT).target == 0, "re-enable not stale");
        ReminderStore.delete(context, claude.id);
        require(ReminderStore.profile(context, claude.id) == null, "profile removed");
        require(ReminderStore.load(context, claude.id, ReminderStore.WEEK).target == 0, "deleted timer removed");
        require(ReminderStore.profiles(context).size() == 2, "other groups retained");
        require(ReminderStore.load(context, old.id, ReminderStore.WEEK).target == weekTarget, "deletion isolated");
        ReminderStore.Profile newClaude = ReminderStore.add(context, "Claude", "Pro", true);
        require(newClaude.id > second.id && newClaude.id != claude.id, "deleted IDs never reused");

        Set<Integer> alarmIds = new HashSet<>();
        for (int profile = 1; profile <= 100; profile++)
            for (int slot = 0; slot <= 1; slot++)
                for (boolean early : new boolean[]{true, false})
                    require(alarmIds.add(AlarmIdentity.id(profile, slot, early)), "alarm identity collision");
        require(!alarmIds.contains(100) && !alarmIds.contains(199), "legacy/test IDs are disjoint");
        System.out.println(assertions + " profile isolation, migration and alarm identity assertions passed");
    }
}
