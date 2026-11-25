package com.vlan.resetnotice;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/** A separate, paged history view; historical manual edits are warm colored. */
public final class HistoryActivity extends Activity {
    private static final int PAGE_SIZE = 40;
    private int profileId, page, card, line, text, sub, accent, warning, warningSoft, positive;
    private LinearLayout content, list;
    private TextView more;
    private List<HistoryStore.Entry> entries;
    private int displayed;

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(AppPreferences.wrap(base)); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        profileId = getIntent().getIntExtra("profile_id", -1);
        ReminderStore.Profile profile = ReminderStore.profile(this, profileId);
        if (profile == null) { finish(); return; }
        page = getColor(R.color.page); card = getColor(R.color.card); line = getColor(R.color.line);
        text = getColor(R.color.text_main); sub = getColor(R.color.text_sub); accent = getColor(R.color.accent);
        warning = getColor(R.color.warning); warningSoft = getColor(R.color.warning_soft);
        positive = getColor(R.color.positive);
        getWindow().setStatusBarColor(page); getWindow().setNavigationBarColor(page);
        ScrollView scroll = new ElasticScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(page); scroll.setVerticalScrollBarEnabled(false);
        content = column(); content.setPadding(dp(18), dp(10), dp(18), dp(30));
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.addView(content, new FrameLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels, dp(600)),
                -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        scroll.addView(wrapper, new ScrollView.LayoutParams(-1, -2));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(this); back.setImageResource(R.drawable.ic_back); back.setColorFilter(text);
        back.setContentDescription(getString(R.string.back)); back.setBackground(ripple(0x00000000, 24));
        Haptics.configure(back); back.setOnClickListener(v -> { Haptics.tap(v); finish(); });
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout labels = column();
        add(labels, label(getString(R.string.history_title), 24, text, true), 0);
        add(labels, label(profile.label(), 12, sub, false), 3);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        FrostedTopBar.install(this, scroll, content, header, page, 68);
        add(content, label(getString(R.string.history_hint, HistoryStore.LIMIT), 12, sub, false), 5);
        list = column(); add(content, list, 12);
        more = label(getString(R.string.history_more), 14, accent, true);
        more.setGravity(Gravity.CENTER); more.setPadding(dp(10), dp(14), dp(10), dp(14));
        more.setBackground(ripple(getColor(R.color.accent_soft), 14)); Haptics.configure(more);
        more.setOnClickListener(v -> { Haptics.tap(v); appendPage(PAGE_SIZE); });
        add(content, more, 12);
        if (state != null) displayed = state.getInt("displayed", 0);
    }

    @Override protected void onResume() {
        super.onResume();
        if (list == null) return;
        AlarmEngine.reconcileDue(this, System.currentTimeMillis());
        entries = HistoryStore.entries(this, profileId);
        int wanted = Math.max(PAGE_SIZE, displayed);
        displayed = 0; list.removeAllViews();
        if (entries.isEmpty()) {
            TextView empty = label(getString(R.string.history_empty), 14, sub, false);
            empty.setGravity(Gravity.CENTER); empty.setPadding(dp(14), dp(28), dp(14), dp(28));
            empty.setBackground(shape(card, line, 20)); add(list, empty, 0);
            more.setVisibility(View.GONE);
        } else appendPage(wanted);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putInt("displayed", displayed);
    }

    private void appendPage(int count) {
        if (entries == null) return;
        int end = Math.min(entries.size(), displayed + count);
        while (displayed < end) {
            HistoryStore.Entry entry = entries.get(displayed);
            add(list, entryView(entry), displayed == 0 ? 0 : 9);
            displayed++;
        }
        more.setVisibility(displayed < entries.size() ? View.VISIBLE : View.GONE);
    }
    private View entryView(HistoryStore.Entry e) {
        LinearLayout box = column();
        box.setPadding(dp(15), dp(13), dp(15), dp(13));
        box.setBackground(shape(e.manual() ? warningSoft : card, e.manual() ? warningSoft : line, 18));
        LinearLayout head = new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView quota = label(I18n.slot(this, e.slot, e.period), 15, text, true);
        head.addView(quota, new LinearLayout.LayoutParams(0, -2, 1));
        TextView type = label(getString(e.manual() ? R.string.history_manual : R.string.history_natural), 12,
                e.manual() ? warning : accent, true);
        head.addView(type); add(box, head, 0);
        add(box, label(getString(e.manual() ? R.string.history_modified_at : R.string.history_reset_at, date(e.when)),
                14, e.manual() ? warning : text, true), 8);
        if (e.manual()) {
            add(box, label(getString(R.string.history_change, date(e.previous), date(e.next)), 12, sub, false), 5);
        } else {
            add(box, label(getString(R.string.history_next, date(e.next)), 12, sub, false), 5);
            if (Math.abs(e.recorded - e.when) >= 1000L)
                add(box, label(getString(R.string.history_recorded_at, date(e.recorded)), 12, sub, false), 4);
        }
        String notice = e.notified() ? getString(R.string.history_notice_sent) :
                getString(R.string.history_notice_no, getString(reason(e.status)));
        add(box, label(notice, 13, e.notified() ? positive : e.manual() ? warning : sub, true), 7);
        return box;
    }
    private int reason(int value) {
        switch (value) {
            case HistoryStore.QUIET: return R.string.history_reason_quiet;
            case HistoryStore.DISABLED: return R.string.history_reason_disabled;
            case HistoryStore.PERMISSION: return R.string.history_reason_permission;
            case HistoryStore.MISSED: return R.string.history_reason_missed;
            case HistoryStore.MANUAL: return R.string.history_reason_manual;
            default: return R.string.history_reason_failed;
        }
    }
    private String date(long timestamp) {
        return timestamp <= 0 ? getString(R.string.unset) : ResetMath.displayDateTime(timestamp, System.currentTimeMillis());
    }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL)); return view;
    }
    private void add(LinearLayout parent, View view, int gap) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(gap); parent.addView(view, p);
    }
    private GradientDrawable shape(int fill, int border, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius));
        if (border != 0) d.setStroke(dp(1), border); return d;
    }
    private RippleDrawable ripple(int fill, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(0x226855A5), shape(fill, 0, radius),
                shape(0xFFFFFFFF, 0, radius));
    }
}
