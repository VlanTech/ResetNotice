package com.vlan.resetnotice;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Shared editor; global changes persist immediately, account changes persist
 * with the surrounding account dialog. */
final class QuietHoursEditor extends LinearLayout {
    interface Changed { void onChanged(QuietHours quiet); }
    private QuietHours quiet;
    private final TextView from, until;
    private final Changed listener;
    private final int text, sub, accent, soft;

    QuietHoursEditor(Context c, QuietHours initial, boolean global, Changed listener) {
        super(c);
        this.quiet = initial; this.listener = listener;
        text = c.getColor(R.color.text_main); sub = c.getColor(R.color.text_sub);
        accent = c.getColor(R.color.accent); soft = c.getColor(R.color.accent_soft);
        setOrientation(VERTICAL);
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = new LinearLayout(c);
        labels.setOrientation(VERTICAL);
        labels.addView(label(c.getString(global ? R.string.quiet_global : R.string.quiet_account), 16, text, true));
        labels.addView(label(c.getString(global ? R.string.quiet_global_hint : R.string.quiet_account_hint), 12, sub, false));
        row.addView(labels, new LayoutParams(0, -2, 1));
        Switch toggle = new Switch(c);
        toggle.setContentDescription(c.getString(global ? R.string.quiet_global : R.string.quiet_account));
        toggle.setChecked(quiet.enabled); Haptics.configure(toggle);
        row.addView(toggle);
        addView(row, new LayoutParams(-1, -2));
        LinearLayout times = new LinearLayout(c); times.setGravity(Gravity.CENTER_VERTICAL);
        from = timeButton(); until = timeButton();
        LayoutParams a = new LayoutParams(0, -2, 1); a.rightMargin = dp(6);
        times.addView(from, a);
        times.addView(until, new LayoutParams(0, -2, 1));
        LayoutParams timeParams = new LayoutParams(-1, -2); timeParams.topMargin = dp(12);
        addView(times, timeParams);
        updateTimes();
        toggle.setOnCheckedChangeListener((b, checked) -> {
            Haptics.tap(b);
            if (checked && quiet.start == quiet.end) {
                toggle.setChecked(false);
                Toast.makeText(c, R.string.quiet_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            change(new QuietHours(checked, quiet.start, quiet.end));
        });
        from.setOnClickListener(v -> { Haptics.tap(v); choose(true); });
        until.setOnClickListener(v -> { Haptics.tap(v); choose(false); });
    }
    QuietHours value() { return quiet; }
    private void change(QuietHours value) {
        quiet = value; updateTimes();
        if (listener != null) listener.onChanged(value);
    }
    private void updateTimes() {
        from.setText(getContext().getString(R.string.quiet_from, QuietHours.time(quiet.start)));
        until.setText(getContext().getString(R.string.quiet_until, QuietHours.time(quiet.end)));
    }
    private void choose(boolean start) {
        int value = start ? quiet.start : quiet.end;
        TimePickerDialog picker = new TimePickerDialog(getContext(), (v, hour, minute) -> {
            int selected = hour * 60 + minute;
            int a = start ? selected : quiet.start, b = start ? quiet.end : selected;
            if (a == b) {
                Toast.makeText(getContext(), R.string.quiet_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            change(new QuietHours(quiet.enabled, a, b));
        }, value / 60, value % 60, true);
        picker.setTitle(start ? R.string.quiet_start_title : R.string.quiet_end_title);
        picker.show();
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return view;
    }
    private TextView timeButton() {
        TextView view = label("", 14, accent, true);
        view.setGravity(Gravity.CENTER); view.setMinHeight(dp(44)); view.setPadding(dp(6), dp(9), dp(6), dp(9));
        GradientDrawable fill = new GradientDrawable(); fill.setColor(soft); fill.setCornerRadius(dp(12));
        GradientDrawable mask = new GradientDrawable(); mask.setColor(0xFFFFFFFF); mask.setCornerRadius(dp(12));
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x226855A5), fill, mask));
        Haptics.configure(view); view.setFocusable(true);
        return view;
    }
}
