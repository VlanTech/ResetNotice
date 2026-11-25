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
        require(!alarmIds.contains(198), "foreground timer notification ID disjoint");
        require(!alarmIds.contains(200), "Direct Boot reminder notification ID disjoint");
        require(!alarmIds.contains(201), "one-shot background probe ID disjoint");

        FakeContext background = new FakeContext();
        require(TimerSnapshot.read(background, now).count == 0, "no profile needs no foreground service");
        require(!TimerSnapshot.read(background, now).shouldRunService(true), "no timers cannot start service");
        require(TimerSnapshot.read(background, now).nextVisibleAlert == Long.MAX_VALUE, "no timers need no boot alert");
        ReminderStore.Profile bg = ReminderStore.add(background, "ChatGPT", "主账号", true);
        require(TimerSnapshot.read(background, now).count == 0, "unset profile needs no foreground service");
        ReminderStore.Slot bgShort = ReminderStore.load(background, bg.id, ReminderStore.SHORT);
        bgShort.target = now + 2 * ResetMath.HOUR;
        bgShort.early = true;
        bgShort.earlyMinutes = 15;
        bgShort.at = false;
        ReminderStore.save(background, bgShort);
        TimerSnapshot snap = TimerSnapshot.read(background, now);
        require(snap.count == 1 && snap.nextReset == bgShort.target, "active even with at-time notification disabled");
        require(snap.shouldRunService(true) && !snap.shouldRunService(false), "ongoing switch controls service, not timer existence");
        require(snap.nextVisibleAlert == bgShort.target - 15 * ResetMath.MINUTE, "boot mirror early notification");
        require(snap.nextLabel.equals("ChatGPT · 主账号 · 短时限额"), "notification names active timer");
        require(snap.nextEvent == bgShort.target - 15 * ResetMath.MINUTE, "early event is next check");
        require(snap.nextCheckDelay(now) == 60_000L, "background checks are bounded and not per-second");
        require(snap.nextCheckDelay(snap.nextEvent - 10_000L) == 10_000L, "check aligns with early event");
        ReminderStore.Slot bgWeek = ReminderStore.load(background, bg.id, ReminderStore.WEEK);
        bgWeek.target = now + ResetMath.HOUR;
        bgWeek.early = false; bgWeek.at = false;
        ReminderStore.save(background, bgWeek);
        snap = TimerSnapshot.read(background, now);
        require(snap.count == 2 && snap.nextReset == bgWeek.target, "weekly timer selected even with both reminders disabled");
        require(snap.nextEvent == bgWeek.target, "next event selects earliest rollover");
        require(snap.nextVisibleAlert == bgShort.target - 15 * ResetMath.MINUTE, "silent rollovers are not mirrored notifications");
        ReminderStore.markDelivered(background, bg.id, ReminderStore.SHORT, true);
        require(TimerSnapshot.read(background, now).nextVisibleAlert == Long.MAX_VALUE, "delivered early and disabled at do not create boot alert");
        require(AlarmPolicy.visible(bgShort, true, now), "enabled early alert can use alarm clock");
        require(!AlarmPolicy.visible(bgShort, false, now), "disabled at-time notification remains a silent rollover");
        require(!AlarmPolicy.visible(ReminderStore.load(background, bg.id, ReminderStore.SHORT), true, now),
                "delivered early alert cannot become visible alarm clock");
        require(AlarmPolicy.mode(false, true, true, true) == AlarmPolicy.EXACT, "clock mode opt-in only");
        require(AlarmPolicy.mode(true, true, true, true) == AlarmPolicy.CLOCK, "explicit visible alert uses system clock");
        require(AlarmPolicy.mode(true, false, true, true) == AlarmPolicy.EXACT, "silent rollover stays ordinary exact");
        require(AlarmPolicy.mode(true, true, false, true) == AlarmPolicy.EXACT, "blocked notifications stay ordinary exact");
        require(AlarmPolicy.mode(true, true, true, false) == AlarmPolicy.INEXACT, "alarm permission fallback");
        bgWeek.at = true;
        ReminderStore.save(background, bgWeek);
        require(TimerSnapshot.read(background, now).nextVisibleAlert == bgWeek.target, "enabled at-time reminder mirrored");
        require(AlarmPolicy.visible(bgWeek, false, now), "enabled weekly alert is visible");
        bgWeek.at = false;
        ReminderStore.save(background, bgWeek);
        require(TimerSnapshot.read(background, bgWeek.target + 1).count == 2, "overdue recurring timer remains active after process restart");
        ReminderStore.rollForward(background, bgWeek, bgWeek.target + 1);
        require(TimerSnapshot.read(background, now).nextReset == bgShort.target, "snapshot updates after rollover");
        ReminderStore.edit(background, bg.id, "ChatGPT", "主账号", false);
        require(TimerSnapshot.read(background, now).count == 1, "disabled short excluded from service");
        require(TimerSnapshot.read(background, now).nextVisibleAlert == Long.MAX_VALUE, "disabled short removed from boot mirror");
        bgWeek.target = 0;
        ReminderStore.save(background, bgWeek);
        require(TimerSnapshot.read(background, now).count == 0, "clearing final timer ends foreground service");
        require(!TimerSnapshot.read(background, now).shouldRunService(true), "clearing final timer stops ongoing service even if enabled");
        ReminderStore.delete(background, bg.id);
        require(TimerSnapshot.read(background, now).count == 0, "deleting account does not keep stale timers");
        String[][] manufacturers = {{"OPPO","OPPO","OPPO"}, {"OnePlus","OnePlus","OPPO"},{"realme","realme","OPPO"},
                {"vivo","iQOO","VIVO"},{"Xiaomi","Redmi","XIAOMI"},{"Xiaomi","POCO","XIAOMI"},
                {"Huawei","HONOR","HONOR"},{"HONOR","HONOR","HONOR"},{"HUAWEI","HUAWEI","HUAWEI"},
                {"Meizu","MEIZU","MEIZU"},{"Google","Pixel","NONE"},{"unknown","unknown","NONE"}};
        for (String[] manufacturer : manufacturers)
            require(PushVendor.detect(manufacturer[0], manufacturer[1]).name().equals(manufacturer[2]),
                    "preferred OEM channel: " + manufacturer[0] + "/" + manufacturer[1]);
        require(PushVendor.detect(null, null) == PushVendor.NONE, "unknown OEM falls back to local alarms");
        // Daily windows, including midnight, use local clock minutes and are
        // independent across accounts. Global quiet is an additional window.
        QuietHours overnight = new QuietHours(true, 23 * 60, 8 * 60);
        QuietHours lunch = new QuietHours(true, 12 * 60, 14 * 60);
        require(overnight.containsMinute(23 * 60) && overnight.containsMinute(0) && overnight.containsMinute(479),
                "overnight starts inclusively and crosses midnight");
        require(!overnight.containsMinute(8 * 60) && !overnight.containsMinute(22 * 60 + 59), "overnight end exclusive");
        require(lunch.containsMinute(720) && lunch.containsMinute(839) && !lunch.containsMinute(840), "same-day boundaries");
        require(!QuietHours.OFF.containsMinute(0), "disabled quiet window never suppresses");
        require(!new QuietHours(true, 600, 600).enabled, "invalid equal start/end cannot mute forever");
        require(QuietHours.time(0).equals("00:00") && QuietHours.time(1439).equals("23:59"), "quiet labels");

        java.util.Calendar local = java.util.Calendar.getInstance();
        local.clear(); local.set(2026, 5, 10, 22, 0, 0);
        long evening = local.getTimeInMillis();
        long beforeQuiet = evening + 30 * ResetMath.MINUTE, duringQuiet = evening + 2 * ResetMath.HOUR;
        long afterQuiet = evening + 11 * ResetMath.HOUR;
        require(QuietHours.suppressed(overnight, QuietHours.OFF, duringQuiet, afterQuiet), "no delayed replay of quiet event");
        require(QuietHours.suppressed(overnight, QuietHours.OFF, beforeQuiet, duringQuiet), "do not ring during quiet time");
        require(!QuietHours.suppressed(overnight, QuietHours.OFF, afterQuiet, afterQuiet), "normal notification after quiet");
        require(QuietHours.suppressed(QuietHours.OFF, overnight, duringQuiet, duringQuiet), "account-specific window independent");
        FakeContext quietContext = new FakeContext();
        ReminderStore.Profile qa = ReminderStore.add(quietContext, "GPT", "A", true, overnight);
        ReminderStore.Profile qb = ReminderStore.add(quietContext, "Claude", "B", true);
        require(ReminderStore.profile(quietContext, qa.id).quiet.same(overnight), "account quiet persists");
        require(!ReminderStore.profile(quietContext, qb.id).quiet.enabled, "account quiet isolation");
        require(!ReminderStore.globalQuiet(quietContext).enabled, "global defaults off");
        ReminderStore.edit(quietContext, qa.id, "GPT", "Renamed", true);
        require(ReminderStore.profile(quietContext, qa.id).quiet.same(overnight), "renaming preserves existing quiet setting");
        ReminderStore.Slot qaSlot = ReminderStore.load(quietContext, qa.id, ReminderStore.SHORT);
        qaSlot.target = duringQuiet; qaSlot.at = true; ReminderStore.save(quietContext, qaSlot);
        ReminderStore.Slot qbSlot = ReminderStore.load(quietContext, qb.id, ReminderStore.SHORT);
        qbSlot.target = duringQuiet; qbSlot.at = true; ReminderStore.save(quietContext, qbSlot);
        TimerSnapshot quietSnap = TimerSnapshot.read(quietContext, evening);
        require(quietSnap.count == 2 && quietSnap.nextVisibleAlert == duringQuiet && !quietSnap.nextAlertAccountQuiet.enabled,
                "quiet account skipped in Direct Boot visible selection, other account still works");
        ReminderStore.setGlobalQuiet(quietContext, overnight);
        require(ReminderStore.globalQuiet(quietContext).same(overnight), "global quiet persisted");
        require(TimerSnapshot.read(quietContext, evening).nextVisibleAlert == Long.MAX_VALUE, "global quiet filters all visible events");
        require(TimerSnapshot.read(quietContext, evening).count == 2, "global quiet never stops timers");
        ReminderStore.setGlobalQuiet(quietContext, QuietHours.OFF);
        require(ReminderStore.profile(quietContext, qa.id).quiet.same(overnight), "disabling global preserves account-specific settings");
        ReminderStore.markEarlySuppressed(quietContext, qa.id, ReminderStore.SHORT);
        qaSlot = ReminderStore.load(quietContext, qa.id, ReminderStore.SHORT);
        require(qaSlot.earlyDelivered && qaSlot.earlySuppressed, "quiet early event consumed and distinguished from sent");
        require(ReminderStore.rollForward(quietContext, qaSlot, duringQuiet + 1), "quiet event still rolls forward");
        require(!qaSlot.earlyDelivered && !qaSlot.earlySuppressed, "new cycle clears quiet delivery flags");
        ReminderStore.delete(quietContext, qa.id);
        require(ReminderStore.profile(quietContext, qa.id) == null, "deleting profile removes quiet preferences");
        require(ResetMath.countdownLevel(now + 5 * ResetMath.HOUR, 300, now) == 10000, "full cycle starts full");
        require(ResetMath.countdownLevel(now + 150 * ResetMath.MINUTE, 300, now) == 5000, "half cycle has half fill");
        require(ResetMath.countdownLevel(now, 300, now) == 0, "due cycle empty");
        require(ResetMath.countdownLevel(0, 300, now) == 0, "unset cycle empty");
        require(ResetMath.countdownLevel(now + 10 * ResetMath.HOUR, 300, now) == 10000, "duration beyond one cycle clamps full");
        require(ResetMath.countdownLevel(now + 7 * ResetMath.DAY, ResetMath.WEEK_MINUTES, now) == 10000, "weekly progress independent");
        FakeContext widgets = new FakeContext();
        ReminderStore.Profile wa = ReminderStore.add(widgets, "GPT", "A", true);
        ReminderStore.Profile wb = ReminderStore.add(widgets, "Claude", "B", false);
        require(WidgetBindingStore.profile(widgets, 401) == WidgetBindingStore.NONE, "unbound widget");
        require(!WidgetBindingStore.bind(widgets, 0, wa.id), "reject invalid widget id");
        require(!WidgetBindingStore.bind(widgets, 401, -1), "reject invalid profile id");
        require(WidgetBindingStore.bind(widgets, 401, wa.id), "pin first account widget");
        require(WidgetBindingStore.bind(widgets, 402, wb.id), "pin second account widget");
        require(WidgetBindingStore.profile(widgets, 401) == wa.id && WidgetBindingStore.profile(widgets, 402) == wb.id,
                "widget accounts remain independent");
        require(WidgetBindingStore.bind(widgets, 403, wa.id), "multiple widgets may bind the same account");
        ReminderStore.edit(widgets, wa.id, "GPT", "Renamed", true);
        require(WidgetBindingStore.profile(widgets, 401) == wa.id, "rename preserves binding");
        WidgetBindingStore.restore(widgets, new int[]{401,402}, new int[]{501,502});
        require(WidgetBindingStore.profile(widgets, 501) == wa.id && WidgetBindingStore.profile(widgets, 502) == wb.id,
                "widget restoration remaps IDs");
        require(WidgetBindingStore.profile(widgets, 401) == WidgetBindingStore.NONE, "old restored binding removed");
        WidgetBindingStore.delete(widgets, 501);
        require(WidgetBindingStore.profile(widgets, 501) == WidgetBindingStore.NONE &&
                WidgetBindingStore.profile(widgets, 403) == wa.id, "deleting widget does not delete account or sibling widgets");
        ReminderStore.delete(widgets, wa.id);
        require(ReminderStore.profile(widgets, WidgetBindingStore.profile(widgets, 403)) == null, "deleted account shows unbound state");
        require(WidgetMath.displayTarget(false, now + ResetMath.HOUR, 60, now) == 0, "widget hides disabled quota");
        require(WidgetMath.displayTarget(true, 0, 60, now) == 0, "widget hides unset quota");
        require(WidgetMath.displayTarget(true, now + ResetMath.HOUR, 60, now) == now + ResetMath.HOUR, "widget future target unchanged");
        require(WidgetMath.displayTarget(true, now, 60, now) == now + ResetMath.HOUR, "widget rolls exact boundary");
        require(WidgetMath.displayTarget(true, now - 3 * ResetMath.HOUR, 60, now + 5 * ResetMath.MINUTE) == now + ResetMath.HOUR,
                "widget skips elapsed cycles without changing original schedule");
        require(WidgetMath.chronometerBase(12345L, now + ResetMath.HOUR, now) == 12345L + ResetMath.HOUR,
                "widget chronometer maps wall clock to monotonic base");
        require(WidgetMath.chronometerBase(12345L, now - 5000L, now) == 12345L, "widget base never starts expired");
        String[][] brands = {{"ChatGPT Plus","openai"},{"GPT-6.2 Sol","openai"},{"OpenAI","openai"},{"Codex","openai"},{"Claude Pro","claude"},
                {"Anthropic","claude"},{"Google Gemini","gemini"},{"Grok 4","grok"},{"xAI","grok"},{"DeepSeek V4","deepseek"},
                {"通义千问","qwen"},{"Qwen","qwen"},{"Perplexity Pro","perplexity"},{"Microsoft Copilot","copilot"},{"Kimi","kimi"},
                {"月之暗面","kimi"},{"Mistral","mistral"},{"豆包","doubao"},{"智譜 GLM","zhipu"},{"文心一言","wenxin"},
                {"MiniMax","minimax"},{"Llama","meta"},{"Poe","poe"},{"Ollama","ollama"},{"Hugging Face","huggingface"},{"My own system","unknown"}};
        for (String[] entry : brands) require(BrandMatcher.match(entry[0]).equals(entry[1]), "brand mapping: " + entry[0]);
        require(BrandMatcher.match(null).equals("unknown"), "null vendor fallback");
        String[][] nicknames = {{"哈基米","gemini"},{"氢基米","gemini"},{"谷歌","gemini"},{"做题龙·Astra","openai"},{"鸡皮提","openai"},
                {"o/","openai"},{"a/","claude"},{"克洛德","claude"},{"格罗克","grok"},{"马一龙","grok"},{"DS","deepseek"},
                {"小鲸鱼","deepseek"},{"PPLX","perplexity"},{"豆子","doubao"},{"清言","zhipu"},{"文小言","wenxin"},{"羊驼","meta"},{"微軟","copilot"}};
        for (String[] entry : nicknames) require(BrandMatcher.match(entry[0]).equals(entry[1]), "nickname mapping: " + entry[0]);

        FakeContext sort = new FakeContext();
        ReminderStore.Profile first = ReminderStore.add(sort, "GPT", "A", true);
        ReminderStore.Profile secondSort = ReminderStore.add(sort, "Claude", "B", true);
        ReminderStore.Profile third = ReminderStore.add(sort, "Gemini", "C", false);
        java.util.List<Integer> manual = java.util.Arrays.asList(third.id, first.id, secondSort.id);
        require(ReminderStore.reorder(sort, manual), "save manual order");
        require(AccountOrder.manual(sort).equals(manual), "manual order persists");
        require(!ReminderStore.reorder(sort, java.util.Arrays.asList(first.id, first.id, third.id)), "duplicate order rejected");
        require(AccountOrder.manual(sort).equals(manual), "invalid order leaves manual untouched");
        ReminderStore.Slot firstShort = ReminderStore.load(sort, first.id, ReminderStore.SHORT);
        firstShort.target = now + 2 * ResetMath.HOUR; ReminderStore.save(sort, firstShort);
        ReminderStore.Slot secondShort = ReminderStore.load(sort, secondSort.id, ReminderStore.SHORT);
        secondShort.target = now + ResetMath.HOUR; ReminderStore.save(sort, secondShort);
        require(AccountOrder.byNextReset(sort, now).equals(java.util.Arrays.asList(secondSort.id, first.id, third.id)), "earliest deadline first; unset last");
        require(AccountOrder.byNextReset(sort, now + ResetMath.HOUR).equals(java.util.Arrays.asList(first.id, secondSort.id, third.id)), "earliest rolls behind at deadline");
        require(AccountOrder.manual(sort).equals(manual), "auto ordering never overwrites saved manual order");
        ReminderStore.Slot firstWeek = ReminderStore.load(sort, first.id, ReminderStore.WEEK);
        firstWeek.target = now + ResetMath.MINUTE; ReminderStore.save(sort, firstWeek);
        require(AccountOrder.byNextReset(sort, now).get(0) == first.id, "earliest among either kind of quota ranks account");
        require(ReminderStore.load(sort, secondSort.id, ReminderStore.SHORT).target == now + ResetMath.HOUR, "sorting does not modify timers");
        System.out.println(assertions + " profile isolation, migration and alarm identity assertions passed");
    }
}
