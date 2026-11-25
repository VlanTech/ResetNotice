package com.vlan.resetnotice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Separate, localized settings and about page. */
public final class SettingsActivity extends Activity {
    private int page, card, text, sub, line, accent;
    private TextView batteryValue;
    private TextView bootValue;
    private TextView gmsValue;
    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(AppPreferences.wrap(base)); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        page = getColor(R.color.page); card = getColor(R.color.card); text = getColor(R.color.text_main);
        sub = getColor(R.color.text_sub); line = getColor(R.color.line); accent = getColor(R.color.accent);
        getWindow().setStatusBarColor(page); getWindow().setNavigationBarColor(page);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(page);
        LinearLayout content = column();
        content.setPadding(dp(18), dp(18), dp(18), dp(30));
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.addView(content, new FrameLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels, dp(600)), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        scroll.addView(wrapper, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        LinearLayout header = row();
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back); back.setColorFilter(text);
        back.setBackground(pressable(0x00000000, 24)); back.setPadding(dp(10), dp(10), dp(10), dp(10));
        back.setContentDescription(getString(R.string.back)); Haptics.configure(back);
        back.setOnClickListener(v -> { Haptics.tap(v); finish(); });
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        header.addView(label(getString(R.string.settings), 26, text, true), new LinearLayout.LayoutParams(0, -2, 1));
        add(content, header, 0);

        add(content, label(getString(R.string.appearance), 14, sub, true), 24);
        LinearLayout appearance = panel();
        add(appearance, item(getString(R.string.language), languageName(), v -> chooseLanguage()), 0);
        divider(appearance);
        add(appearance, item(getString(R.string.theme), themeName(), v -> chooseTheme()), 0);
        add(content, appearance, 9);

        add(content, label(getString(R.string.interaction), 14, sub, true), 23);
        LinearLayout interaction = panel();
        LinearLayout hapticRow = row(); hapticRow.setPadding(dp(16), dp(15), dp(14), dp(15));
        LinearLayout hapticLabels = column();
        add(hapticLabels, label(getString(R.string.haptics), 16, text, true), 0);
        add(hapticLabels, label(getString(R.string.haptics_hint), 13, sub, false), 3);
        hapticRow.addView(hapticLabels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch hapticSwitch = new Switch(this);
        hapticSwitch.setContentDescription(getString(R.string.haptics));
        hapticSwitch.setChecked(AppPreferences.haptics(this));
        Haptics.configure(hapticSwitch);
        hapticSwitch.setOnCheckedChangeListener((b, checked) -> {
            AppPreferences.setHaptics(this, checked);
            Haptics.configure(b); if (checked) Haptics.tap(b);
        });
        hapticRow.addView(hapticSwitch); add(interaction, hapticRow, 0);
        add(content, interaction, 9);

        add(content, label(getString(R.string.notifications), 14, sub, true), 23);
        LinearLayout background = panel();
        LinearLayout ongoingRow = row(); ongoingRow.setPadding(dp(16), dp(15), dp(14), dp(15));
        LinearLayout ongoingLabels = column();
        add(ongoingLabels, label(getString(R.string.ongoing_notification), 16, text, true), 0);
        add(ongoingLabels, label(getString(R.string.ongoing_hint), 13, sub, false), 3);
        ongoingRow.addView(ongoingLabels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch ongoingSwitch = new Switch(this);
        ongoingSwitch.setContentDescription(getString(R.string.ongoing_notification));
        ongoingSwitch.setChecked(AppPreferences.ongoingNotification(this));
        Haptics.configure(ongoingSwitch);
        ongoingSwitch.setOnCheckedChangeListener((b, checked) -> {
            Haptics.tap(b);
            AppPreferences.setOngoingNotification(this, checked);
            AlarmEngine.rescheduleAll(this);
            TimerService.refresh(this);
        });
        ongoingRow.addView(ongoingSwitch);
        add(background, ongoingRow, 0);
        divider(background);
        add(background, item(getString(R.string.background_settings), getString(R.string.background_hint), v -> appDetails()), 0);
        divider(background);
        LinearLayout battery = item(getString(R.string.battery_settings), batteryStatus(), v -> batterySettings());
        batteryValue = (TextView)((LinearLayout)battery.getChildAt(0)).getChildAt(1);
        add(background, battery, 0);
        divider(background);
        LinearLayout boot = item(getString(R.string.boot_recovery), bootStatus(), null);
        bootValue = (TextView)((LinearLayout)boot.getChildAt(0)).getChildAt(1);
        add(background, boot, 0);
        divider(background);
        LinearLayout gms = item(getString(R.string.gms_status), gmsStatus(), null);
        gmsValue = (TextView)((LinearLayout)gms.getChildAt(0)).getChildAt(1);
        add(background, gms, 0);
        add(content, background, 9);
        TextView help = label(getString(R.string.background_help), 12, sub, false);
        help.setPadding(dp(5), dp(3), dp(5), 0);
        add(content, help, 7);

        add(content, label(getString(R.string.about), 14, sub, true), 24);
        LinearLayout about = panel();
        add(about, item(getString(R.string.developer), "Vlan_Tech", null), 0);
        divider(about);
        add(about, item(getString(R.string.version), "1.6.0", null), 0);
        divider(about);
        add(about, item(getString(R.string.source_code), "GitHub · ResetNotice", v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/VlanTech/ResetNotice"))); }
            catch (Exception ignored) { }
        }), 0);
        add(content, about, 9);
        TextView privacy = label(getString(R.string.local_only), 12, sub, false); privacy.setGravity(Gravity.CENTER);
        add(content, privacy, 22);
    }

    @Override protected void onResume() {
        super.onResume();
        if (batteryValue != null) batteryValue.setText(batteryStatus());
        if (bootValue != null) bootValue.setText(bootStatus());
        if (gmsValue != null) gmsValue.setText(gmsStatus());
    }

    private String bootStatus() {
        long time = BootState.recoveryTime(this);
        if (time <= 0) return getString(R.string.boot_never);
        String date = ResetMath.displayDateTime(time, System.currentTimeMillis());
        int count = BootState.recoveryCount(this);
        if (count < 0) return getString(R.string.boot_failed, date);
        return getString(BootState.recoveryExact(this) ? R.string.boot_exact : R.string.boot_approximate, date, count);
    }

    private boolean packageEnabled(String name) {
        try { return getPackageManager().getApplicationInfo(name, 0).enabled; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }
    private String gmsStatus() {
        return getString(packageEnabled("com.google.android.gms") && packageEnabled("com.android.vending")
                ? R.string.gms_detected : R.string.gms_unavailable);
    }

    private String batteryStatus() {
        PowerManager manager = getSystemService(PowerManager.class);
        return getString(manager != null && manager.isIgnoringBatteryOptimizations(getPackageName())
                ? R.string.battery_allowed : R.string.battery_restricted);
    }

    private void batterySettings() {
        try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
        catch (Exception ignored) { appDetails(); }
    }

    private String languageName() {
        String[] values = AppPreferences.LANGUAGES;
        for (int i = 1; i < values.length; i++) if (values[i].equals(AppPreferences.language(this))) return AppPreferences.LANGUAGE_NAMES[i];
        return getString(R.string.system_default);
    }
    private String themeName() {
        String v = AppPreferences.theme(this);
        return getString(AppPreferences.LIGHT.equals(v) ? R.string.light_theme : AppPreferences.DARK.equals(v) ? R.string.dark_theme : R.string.system_default);
    }
    private void chooseLanguage() {
        String[] names = AppPreferences.LANGUAGE_NAMES.clone(); names[0] = getString(R.string.system_default);
        int selected = 0;
        for (int i = 0; i < names.length; i++) if (AppPreferences.LANGUAGES[i].equals(AppPreferences.language(this))) selected = i;
        new AlertDialog.Builder(this).setTitle(R.string.language).setSingleChoiceItems(names, selected, (dialog, which) -> {
            AppPreferences.setLanguage(this, AppPreferences.LANGUAGES[which]); dialog.dismiss();
            AlarmEngine.createChannel(this); TimerService.refresh(this); recreate();
        }).setNegativeButton(R.string.cancel, null).show();
    }
    private void chooseTheme() {
        String[] keys = {AppPreferences.SYSTEM, AppPreferences.LIGHT, AppPreferences.DARK};
        String[] names = {getString(R.string.system_default), getString(R.string.light_theme), getString(R.string.dark_theme)};
        int selected = 0;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(AppPreferences.theme(this))) selected = i;
        new AlertDialog.Builder(this).setTitle(R.string.theme).setSingleChoiceItems(names, selected, (dialog, which) -> {
            AppPreferences.setTheme(this, keys[which]); dialog.dismiss(); recreate();
        }).setNegativeButton(R.string.cancel, null).show();
    }
    private void appDetails() {
        try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
        catch (Exception ignored) { Toast.makeText(this, R.string.system_auth, Toast.LENGTH_SHORT).show(); }
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int fill, float radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); return d;
    }
    private RippleDrawable pressable(int fill, float radius) {
        return new RippleDrawable(ColorStateList.valueOf(0x226855A5), shape(fill, radius), shape(0xFFFFFFFF, radius));
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private TextView label(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL)); return v;
    }
    private void add(LinearLayout l, View v, int gap) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(gap); l.addView(v, p); }
    private LinearLayout panel() {
        LinearLayout p = column(); GradientDrawable d = shape(card, 20); d.setStroke(dp(1), line); p.setBackground(d);
        p.setClipToOutline(true); return p;
    }
    private void divider(LinearLayout p) { View d = new View(this); d.setBackgroundColor(line); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1)); lp.leftMargin = dp(16); lp.rightMargin = dp(16); p.addView(d, lp); }
    private LinearLayout item(String title, String value, View.OnClickListener click) {
        LinearLayout row = row(); row.setPadding(dp(16), dp(15), dp(14), dp(15)); row.setMinimumHeight(dp(64));
        LinearLayout labels = column(); add(labels, label(title, 16, text, true), 0); add(labels, label(value, 13, sub, false), 3);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        if (click != null) {
            ImageView arrow = new ImageView(this); arrow.setImageResource(R.drawable.ic_chevron); arrow.setColorFilter(accent); arrow.setRotation(-90f);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(25), dp(25))); row.setBackground(pressable(0x00000000, 15));
            Haptics.configure(row); row.setOnClickListener(v -> { Haptics.tap(v); click.onClick(v); }); row.setFocusable(true);
        }
        return row;
    }
}
