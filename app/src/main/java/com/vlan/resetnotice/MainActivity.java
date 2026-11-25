package com.vlan.resetnotice;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int NOTIFICATION_REQUEST = 15;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinkedHashMap<Integer, GroupViews> groups = new LinkedHashMap<>();
    private ScrollView scroll;
    private LinearLayout content, groupList, readiness;
    private TextView groupCount, emptyState;
    private boolean notifyReady, exactReady, readinessInitialized;
    private int page, card, text, sub, accent, onAccent, accentSoft, line, positive, positiveSoft, warning, warningSoft;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateCountdowns();
            // Re-align to the next system-clock second after every frame; no accumulated tick drift.
            handler.postDelayed(this, ResetMath.delayToNextSecond(System.currentTimeMillis()));
        }
    };

    static final class GroupViews {
        ReminderStore.Profile profile;
        LinearLayout root, body, summary, shortBlock;
        FrameLayout bodyClip;
        TextView platform, account, shortSummary, shortDate, weekSummary, weekDate, expandButton;
        SlotViews shortView, weekView;
        boolean expanded;
        ValueAnimator animator;
    }

    static final class SlotViews {
        LinearLayout root;
        TextView countdown, target, status, note, advance, clear, cycle;
        Switch earlySwitch, atSwitch;
        String statusKey = "";
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AlarmEngine.createChannel(this);
        buildPage(savedInstanceState);
        if (savedInstanceState != null) {
            int position = savedInstanceState.getInt("scroll");
            scroll.post(() -> scroll.scrollTo(0, position));
        } else if (getIntent() != null) focusProfile(getIntent().getIntExtra("open_profile", -1));
    }

    @Override public void onResume() {
        super.onResume();
        AlarmEngine.rescheduleAll(this);
        refreshReadiness();
        updateCountdowns();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override public void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    @Override protected void onDestroy() {
        for (GroupViews g : groups.values()) cancelExpansion(g);
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        ArrayList<Integer> expanded = new ArrayList<>();
        for (GroupViews group : groups.values()) if (group.expanded) expanded.add(group.profile.id);
        out.putIntegerArrayList("expanded", expanded);
        out.putInt("scroll", scroll == null ? 0 : scroll.getScrollY());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        focusProfile(intent.getIntExtra("open_profile", -1));
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == NOTIFICATION_REQUEST) {
            AlarmEngine.rescheduleAll(this);
            refreshReadiness();
            updateCountdowns();
        }
    }

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private int color(int id) { return getColor(id); }

    private GradientDrawable rounded(int fill, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private GradientDrawable bordered(int fill, float radius) {
        GradientDrawable d = rounded(fill, radius);
        d.setStroke(dp(1), line);
        return d;
    }

    private TextView label(String value, float size, int c, boolean strong) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(c);
        t.setTypeface(Typeface.create(strong ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        t.setLineSpacing(dp(3), 1f);
        return t;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private void add(LinearLayout parent, View child, int gapTop) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(gapTop);
        parent.addView(child, p);
    }

    private void space(LinearLayout parent, int height) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(-1, dp(height)));
    }

    private void divider(LinearLayout parent) {
        View v = new View(this);
        v.setBackgroundColor(line);
        parent.addView(v, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private TextView button(String title, boolean primary, View.OnClickListener listener) {
        TextView v = label(title, 14, primary ? onAccent : accent, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(8), dp(10), dp(8), dp(10));
        v.setMinHeight(dp(46));
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x226855A5),
                rounded(primary ? accent : accentSoft, 14), rounded(0xFFFFFFFF, 14)));
        v.setClickable(true);
        v.setFocusable(true);
        v.setOnClickListener(listener);
        return v;
    }

    private TextView badge(String title, int fg, int bg) {
        TextView v = label(title, 12, fg, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(10), dp(6), dp(10), dp(6));
        v.setBackground(rounded(bg, 24));
        return v;
    }

    private LinearLayout panel() {
        LinearLayout p = column();
        p.setBackground(bordered(card, 24));
        p.setPadding(dp(18), dp(17), dp(18), dp(17));
        return p;
    }

    private void buildPage(Bundle saved) {
        page = color(R.color.page); card = color(R.color.card); text = color(R.color.text_main);
        sub = color(R.color.text_sub); accent = color(R.color.accent); onAccent = color(R.color.on_accent);
        accentSoft = color(R.color.accent_soft); line = color(R.color.line); positive = color(R.color.positive);
        positiveSoft = color(R.color.positive_soft); warning = color(R.color.warning); warningSoft = color(R.color.warning_soft);

        getWindow().setStatusBarColor(page);
        getWindow().setNavigationBarColor(page);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(page);
        content = column();
        content.setPadding(dp(18), dp(20), dp(18), dp(28));
        FrameLayout wrapper = new FrameLayout(this);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        wrapper.addView(content, new FrameLayout.LayoutParams(Math.min(screenWidth, dp(600)), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        scroll.addView(wrapper, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll); // Once per Activity creation. Never called by a switch or timer.

        LinearLayout hero = row();
        ImageView portrait = new ImageView(this);
        portrait.setImageResource(R.drawable.portrait);
        portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);
        portrait.setBackground(rounded(card, 19));
        portrait.setClipToOutline(true);
        portrait.setContentDescription("应用角色头像");
        hero.addView(portrait, new LinearLayout.LayoutParams(dp(62), dp(62)));
        LinearLayout title = column();
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
        tp.leftMargin = dp(13);
        hero.addView(title, tp);
        add(title, label("重置通知", 28, text, true), 0);
        add(title, label("多平台 · 统一额度提醒", 13, sub, false), 6);
        add(content, hero, 0);
        add(content, label("把各个 AI 账户的重置时间集中管理。", 14, sub, false), 16);

        readiness = panel();
        add(content, readiness, 18);
        refreshReadiness();

        LinearLayout section = row();
        LinearLayout heading = column();
        add(heading, label("AI 与账户", 19, text, true), 0);
        groupCount = label("0 个账户", 12, sub, false);
        add(heading, groupCount, 5);
        section.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView create = button("＋ 新增", true, v -> editProfile(null));
        create.setMinWidth(dp(100));
        section.addView(create, new LinearLayout.LayoutParams(-2, -2));
        add(content, section, 22);
        groupList = column();
        add(content, groupList, 12);
        emptyState = label("还没有 AI 账户。点击「＋ 新增」添加第一个提醒账户。", 14, sub, false);
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setPadding(dp(16), dp(28), dp(16), dp(28));
        emptyState.setBackground(bordered(card, 22));
        add(groupList, emptyState, 0);

        List<ReminderStore.Profile> profiles = ReminderStore.profiles(this);
        ArrayList<Integer> expanded = saved == null ? null : saved.getIntegerArrayList("expanded");
        for (int i = 0; i < profiles.size(); i++) {
            ReminderStore.Profile profile = profiles.get(i);
            addGroup(profile, expanded == null ? i == 0 : expanded.contains(profile.id));
        }
        updateGroupCount();

        TextView foot = label("首次录入重置时间后会按周期自动续排；重启手机或重新打开应用时也会接续下一次提醒。", 12, sub, false);
        foot.setGravity(Gravity.CENTER);
        add(content, foot, 22);
        TextView version = label("重置通知 1.2.0  ·  数据仅保存在本机", 11, sub, false);
        version.setGravity(Gravity.CENTER);
        add(content, version, 10);
        updateCountdowns();
    }

    private void refreshReadiness() {
        boolean notifyOk = AlarmEngine.notificationsAllowed(this), exactOk = AlarmEngine.exactAllowed(this);
        if (readinessInitialized && notifyReady == notifyOk && exactReady == exactOk) return;
        notifyReady = notifyOk; exactReady = exactOk; readinessInitialized = true;
        readiness.removeAllViews();
        LinearLayout header = row();
        header.addView(label("提醒状态", 15, text, true), new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(badge(notifyOk && exactOk ? "已就绪" : "待授权", notifyOk && exactOk ? positive : warning,
                notifyOk && exactOk ? positiveSoft : warningSoft));
        add(readiness, header, 0);
        if (!notifyOk || !exactOk) {
            add(readiness, label(!notifyOk ? "请允许通知，才能在后台收到额度重置提醒。" : "请允许精确提醒，减少锁屏时的提醒延迟。", 13, sub, false), 9);
            if (!notifyOk) add(readiness, button("开启通知权限", true, v -> openNotificationPermission()), 12);
            if (!exactOk) add(readiness, button("授权精确提醒", notifyOk, v -> openExactPermission()), 8);
        } else {
            add(readiness, button("发送测试通知", false, v -> {
                if (AlarmEngine.sendTest(this)) toast("已发送测试通知");
                else openNotificationPermission();
            }), 10);
        }
    }

    private void updateGroupCount() {
        setText(groupCount, groups.size() + " 个账户");
        emptyState.setVisibility(groups.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void addGroup(ReminderStore.Profile profile, boolean expanded) {
        GroupViews g = new GroupViews();
        g.profile = profile;
        g.root = panel();
        g.root.setPadding(dp(14), dp(16), dp(14), dp(13));
        LinearLayout header = row();
        TextView marker = badge("AI", accent, accentSoft);
        marker.setPadding(dp(12), dp(10), dp(12), dp(10));
        header.addView(marker);
        LinearLayout names = column();
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1);
        np.leftMargin = dp(11); np.rightMargin = dp(8);
        g.platform = label(profile.platform, 19, text, true);
        g.account = label(profile.account, 13, sub, false);
        add(names, g.platform, 0);
        add(names, g.account, 6);
        header.addView(names, np);
        TextView edit = button("管理", false, v -> editProfile(g.profile));
        edit.setContentDescription("管理 " + profile.label());
        header.addView(edit, new LinearLayout.LayoutParams(-2, -2));
        add(g.root, header, 0);

        LinearLayout summaryRow = row();
        g.summary = column();
        g.shortBlock = column();
        g.shortSummary = label("", 14, text, true);
        g.shortDate = label("", 12, sub, false);
        g.weekSummary = label("", 14, text, true);
        g.weekDate = label("", 12, sub, false);
        g.shortSummary.setFontFeatureSettings("tnum");
        g.weekSummary.setFontFeatureSettings("tnum");
        g.shortDate.setFontFeatureSettings("tnum");
        g.weekDate.setFontFeatureSettings("tnum");
        add(g.shortBlock, g.shortSummary, 0);
        add(g.shortBlock, g.shortDate, 5);
        add(g.summary, g.shortBlock, 0);
        add(g.summary, g.weekSummary, 11);
        add(g.summary, g.weekDate, 5);
        summaryRow.addView(g.summary, new LinearLayout.LayoutParams(0, -2, 1));
        g.expandButton = button("展开设置 ▾", false, v -> setExpanded(g, !g.expanded, true));
        g.expandButton.setTextSize(13);
        LinearLayout.LayoutParams expandParam = new LinearLayout.LayoutParams(dp(96), -2);
        expandParam.leftMargin = dp(8);
        summaryRow.addView(g.expandButton, expandParam);
        add(g.root, summaryRow, 17);

        // Clip a full-size settings body while animating its container's height. This
        // reveals the cards smoothly without squeezing their controls or re-creating them.
        g.bodyClip = new FrameLayout(this);
        g.bodyClip.setClipChildren(true);
        g.body = column();
        g.bodyClip.addView(g.body, new FrameLayout.LayoutParams(-1, -2));
        add(g.root, g.bodyClip, 12);
        groups.put(profile.id, g);
        add(groupList, g.root, 12);
        rebuildGroupBody(g);
        setExpanded(g, expanded, false);
    }

    private void rebuildGroupBody(GroupViews g) {
        if (g.animator != null) settleExpansion(g);
        g.body.removeAllViews();
        g.shortView = null;
        if (g.profile.shortEnabled) {
            g.shortView = buildSlot(g.profile.id, ReminderStore.SHORT);
            add(g.body, g.shortView.root, 0);
        }
        g.weekView = buildSlot(g.profile.id, ReminderStore.WEEK);
        add(g.body, g.weekView.root, g.profile.shortEnabled ? 12 : 0);
        g.shortBlock.setVisibility(g.profile.shortEnabled ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams weekParams = (LinearLayout.LayoutParams) g.weekSummary.getLayoutParams();
        weekParams.topMargin = g.profile.shortEnabled ? dp(11) : 0;
        g.weekSummary.setLayoutParams(weekParams);
        updateGroup(g, System.currentTimeMillis());
    }

    private void cancelExpansion(GroupViews g) {
        ValueAnimator previous = g.animator;
        g.animator = null;
        if (previous != null) previous.cancel();
    }

    private void settleExpansion(GroupViews g) {
        cancelExpansion(g);
        LinearLayout.LayoutParams clipParams = (LinearLayout.LayoutParams) g.bodyClip.getLayoutParams();
        clipParams.height = -2;
        clipParams.topMargin = dp(12);
        g.bodyClip.setLayoutParams(clipParams);
        FrameLayout.LayoutParams bodyParams = (FrameLayout.LayoutParams) g.body.getLayoutParams();
        bodyParams.height = -2;
        g.body.setLayoutParams(bodyParams);
        g.body.setAlpha(1f);
        g.bodyClip.setVisibility(g.expanded ? View.VISIBLE : View.GONE);
    }

    private void setExpanded(GroupViews g, boolean expanded, boolean animate) {
        boolean visible = g.bodyClip.getVisibility() == View.VISIBLE;
        LinearLayout.LayoutParams clipParams = (LinearLayout.LayoutParams) g.bodyClip.getLayoutParams();
        int startHeight = visible ? (clipParams.height >= 0 ? clipParams.height : g.bodyClip.getHeight()) : 0;
        int startMargin = visible ? clipParams.topMargin : 0;
        float startAlpha = visible ? g.body.getAlpha() : 0f;
        cancelExpansion(g); // Keep the current geometry if the user reverses mid-animation.
        g.expanded = expanded;
        setText(g.expandButton, expanded ? "收起设置 ▴" : "展开设置 ▾");
        g.expandButton.setContentDescription((expanded ? "收起" : "展开") + g.profile.label() + "额度设置");
        int width = g.root.getWidth() - g.root.getPaddingLeft() - g.root.getPaddingRight();
        if (!animate || width <= 0 || !g.root.isLaidOut()
                || (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled())) {
            settleExpansion(g);
            return;
        }

        g.body.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int naturalHeight = g.body.getMeasuredHeight();
        int endHeight = expanded ? naturalHeight : 0;
        int endMargin = expanded ? dp(12) : 0;
        float endAlpha = expanded ? 1f : 0f;
        FrameLayout.LayoutParams bodyParams = (FrameLayout.LayoutParams) g.body.getLayoutParams();
        bodyParams.height = naturalHeight;
        g.body.setLayoutParams(bodyParams);
        clipParams.height = startHeight;
        clipParams.topMargin = startMargin;
        g.bodyClip.setLayoutParams(clipParams);
        g.body.setAlpha(startAlpha);
        g.bodyClip.setVisibility(View.VISIBLE);

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        g.animator = animator;
        animator.setDuration(270L);
        animator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        animator.addUpdateListener(animation -> {
            if (g.animator != animation) return;
            float f = (Float) animation.getAnimatedValue();
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) g.bodyClip.getLayoutParams();
            int h = Math.round(startHeight + (endHeight - startHeight) * f);
            int m = Math.round(startMargin + (endMargin - startMargin) * f);
            if (p.height != h || p.topMargin != m) {
                p.height = h; p.topMargin = m;
                g.bodyClip.setLayoutParams(p);
            }
            g.body.setAlpha(startAlpha + (endAlpha - startAlpha) * f);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (g.animator != animation) return;
                g.animator = null;
                settleExpansion(g);
            }
        });
        animator.start();
    }

    private void focusProfile(int id) {
        GroupViews g = groups.get(id);
        if (g == null) return;
        setExpanded(g, true, false);
        scroll.post(() -> scroll.smoothScrollTo(0, content.getTop() + groupList.getTop() + g.root.getTop()));
    }

    private EditText input(String hint, String initial, int maxLength) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        edit.setHint(hint);
        edit.setText(initial);
        edit.setTextSize(16);
        return edit;
    }

    private void editProfile(ReminderStore.Profile previous) {
        boolean creating = previous == null;
        LinearLayout fields = column();
        fields.setPadding(dp(21), dp(12), dp(21), dp(8));
        add(fields, label("AI 平台名称", 13, sub, true), 0);
        EditText platform = input("例如 ChatGPT、Claude、Gemini", creating ? "" : previous.platform, 40);
        add(fields, platform, 6);
        add(fields, label("账户备注", 13, sub, true), 15);
        EditText account = input("例如 主账号、Plus、备用账号", creating ? "" : previous.account, 48);
        add(fields, account, 6);
        LinearLayout shortRow = row();
        LinearLayout descriptions = column();
        add(descriptions, label("管理短时限额", 15, text, true), 0);
        add(descriptions, label("关闭后仅管理周限额", 12, sub, false), 5);
        shortRow.addView(descriptions, new LinearLayout.LayoutParams(0, -2, 1));
        Switch shortSwitch = new Switch(this);
        shortSwitch.setContentDescription("管理短时限额");
        shortSwitch.setChecked(creating || previous.shortEnabled);
        shortRow.addView(shortSwitch);
        add(fields, shortRow, 19);
        if (!creating) add(fields, label("关闭短时限额会清除该账户的短时重置时间。", 12, sub, false), 10);
        else add(fields, label("账户备注用于区分多个账号，填写自定义名称即可。", 12, sub, false), 10);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(creating ? "新增 AI 账户" : "管理 AI 账户")
                .setView(fields).setNegativeButton("取消", null).setPositiveButton("保存", null);
        if (!creating) builder.setNeutralButton("删除账户", (d, w) -> confirmDelete(previous));
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String ai = platform.getText().toString().trim();
            String name = account.getText().toString().trim();
            if (ai.isEmpty()) { platform.setError("请输入 AI 平台名称"); return; }
            if (name.isEmpty()) name = "默认账户";
            if (ReminderStore.duplicate(this, creating ? -1 : previous.id, ai, name)) {
                account.setError("该 AI 和账户组合已存在"); return;
            }
            if (creating && groups.size() >= ReminderStore.MAX_PROFILES) { toast("已达账户数量上限"); return; }
            ReminderStore.Profile profile = creating
                    ? ReminderStore.add(this, ai, name, shortSwitch.isChecked())
                    : ReminderStore.edit(this, previous.id, ai, name, shortSwitch.isChecked());
            if (profile == null) { dialog.dismiss(); return; }
            if (creating) {
                addGroup(profile, true);
                updateGroupCount();
            } else {
                GroupViews g = groups.get(profile.id);
                if (g != null) {
                    boolean changed = g.profile.shortEnabled != profile.shortEnabled;
                    g.profile = profile;
                    setText(g.platform, profile.platform);
                    setText(g.account, profile.account);
                    if (changed) rebuildGroupBody(g);
                    updateGroup(g, System.currentTimeMillis());
                }
                // Renaming keeps the existing scheduled times; delivery uses current labels.
                if (!profile.shortEnabled) AlarmEngine.cancelSlot(this, profile.id, ReminderStore.SHORT, true);
            }
            dialog.dismiss();
            if (creating) focusProfile(profile.id);
        }));
        dialog.show();
    }

    private void confirmDelete(ReminderStore.Profile profile) {
        new AlertDialog.Builder(this).setTitle("删除账户？")
                .setMessage("删除「" + profile.label() + "」及其所有限额时间、提醒设置。其他账户不受影响。")
                .setNegativeButton("取消", null).setPositiveButton("删除", (d, w) -> {
                    ReminderStore.delete(this, profile.id);
                    AlarmEngine.cancelProfile(this, profile.id);
                    GroupViews g = groups.remove(profile.id);
                    if (g != null) {
                        cancelExpansion(g);
                        groupList.removeView(g.root);
                    }
                    updateGroupCount();
                    toast("已删除账户");
                }).show();
    }

    private SlotViews buildSlot(int profileId, int id) {
        ReminderStore.Slot s = ReminderStore.load(this, profileId, id);
        SlotViews sv = new SlotViews();
        LinearLayout p = column();
        p.setBackground(bordered(page, 18));
        p.setPadding(dp(14), dp(15), dp(14), dp(12));
        sv.root = p;
        LinearLayout header = row();
        TextView marker = badge(id == ReminderStore.SHORT ? "H" : "W", accent, accentSoft);
        header.addView(marker);
        LinearLayout.LayoutParams titleParam = new LinearLayout.LayoutParams(0, -2, 1);
        titleParam.leftMargin = dp(8);
        header.addView(label(s.title(), 17, text, true), titleParam);
        sv.status = badge("待设置", sub, accentSoft);
        header.addView(sv.status);
        add(p, header, 0);
        sv.countdown = label("--:--:--", 31, accent, true);
        sv.countdown.setFontFeatureSettings("tnum");
        add(p, sv.countdown, 14);
        sv.target = label("尚未设置重置时间", 12, sub, false);
        add(p, sv.target, 8);

        LinearLayout buttons = row();
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1);
        left.rightMargin = dp(7);
        buttons.addView(button("设定时间", true, v -> chooseDateTime(profileId, id)), left);
        buttons.addView(button("按剩余时长", false, v -> chooseDuration(profileId, id)), new LinearLayout.LayoutParams(0, -2, 1));
        add(p, buttons, 12);
        if (id == ReminderStore.SHORT) {
            sv.cycle = button("", false, v -> chooseCycle(profileId));
        } else {
            sv.cycle = label("每 7 天自动续排", 13, accent, true);
            sv.cycle.setGravity(Gravity.CENTER);
            sv.cycle.setPadding(dp(6), dp(9), dp(6), dp(9));
        }
        add(p, sv.cycle, 8);
        space(p, 14);
        divider(p);
        space(p, 12);

        LinearLayout earlyRow = row();
        earlyRow.addView(label("提前提醒", 14, text, true), new LinearLayout.LayoutParams(0, -2, 1));
        sv.earlySwitch = new Switch(this);
        sv.earlySwitch.setContentDescription(s.title() + "提前提醒");
        sv.earlySwitch.setChecked(s.early);
        earlyRow.addView(sv.earlySwitch);
        add(p, earlyRow, 0);
        // The advance control always occupies its place, even when disabled.
        // Toggling never removes/rebuilds the view hierarchy, so the switch animation is stable.
        sv.advance = button("提前 " + s.earlyMinutes + " 分钟  ·  修改", false, v -> chooseAdvance(profileId, id));
        add(p, sv.advance, 5);
        LinearLayout atRow = row();
        atRow.addView(label("到点提醒", 14, text, true), new LinearLayout.LayoutParams(0, -2, 1));
        sv.atSwitch = new Switch(this);
        sv.atSwitch.setContentDescription(s.title() + "到点提醒");
        sv.atSwitch.setChecked(s.at);
        atRow.addView(sv.atSwitch);
        add(p, atRow, 10);
        sv.note = label("", 12, sub, false);
        sv.note.setMinHeight(dp(32));
        add(p, sv.note, 11);

        sv.clear = label("清除重置时间", 13, accent, true);
        sv.clear.setGravity(Gravity.CENTER);
        sv.clear.setPadding(dp(8), dp(12), dp(8), dp(6));
        sv.clear.setClickable(true);
        sv.clear.setFocusable(true);
        sv.clear.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("清除" + s.title() + "？")
                .setMessage("清除该额度的时间并停止自动续排、取消未触发提醒。")
                .setNegativeButton("保留", null).setPositiveButton("清除", (d, w) -> {
                    ReminderStore.Slot current = ReminderStore.load(this, profileId, id);
                    current.target = 0;
                    current.earlyDelivered = current.atDelivered = false;
                    save(current);
                    AlarmEngine.cancelSlot(this, profileId, id, true);
                }).show());
        add(p, sv.clear, 1);

        sv.earlySwitch.setOnCheckedChangeListener((b, checked) -> {
            ReminderStore.Slot current = ReminderStore.load(this, profileId, id);
            current.early = checked;
            save(current);
        });
        sv.atSwitch.setOnCheckedChangeListener((b, checked) -> {
            ReminderStore.Slot current = ReminderStore.load(this, profileId, id);
            current.at = checked;
            save(current);
        });
        return sv;
    }

    private void setText(TextView view, String value) {
        if (!value.contentEquals(view.getText())) view.setText(value);
    }

    private String compactTime(ReminderStore.Slot s, long now) {
        if (s.target <= 0) return "未设置";
        if (s.target <= now) return "已到重置时间";
        return ResetMath.countdown(s.target - now);
    }

    private void updateCountdowns() {
        long now = System.currentTimeMillis();
        // Foreground fallback: even if Android delays an alarm while idle, the
        // visible timer advances at the actual system-clock boundary.
        AlarmEngine.reconcileDue(this, now);
        for (GroupViews g : groups.values()) updateGroup(g, now);
    }

    private void updateGroup(GroupViews g, long now) {
        if (g.profile.shortEnabled) {
            ReminderStore.Slot s = ReminderStore.load(this, g.profile.id, ReminderStore.SHORT);
            setText(g.shortSummary, "短时限额 · " + compactTime(s, now));
            setText(g.shortDate, s.target > 0 ? "重置 " + ResetMath.dateTime(s.target) : "尚未设定重置时间");
            if (g.shortView != null) updateSlot(g.shortView, s, now);
        }
        ReminderStore.Slot week = ReminderStore.load(this, g.profile.id, ReminderStore.WEEK);
        setText(g.weekSummary, "周限额 · " + compactTime(week, now));
        setText(g.weekDate, week.target > 0 ? "重置 " + ResetMath.dateTime(week.target) : "尚未设定重置时间");
        if (g.weekView != null) updateSlot(g.weekView, week, now);
    }

    private void updateSlot(SlotViews v, ReminderStore.Slot s, long now) {
        String status, note, countdown, target;
        int fg, bg;
        boolean hasTarget = s.target > 0;
        if (!hasTarget) {
            countdown = "--:--:--";
            target = "尚未设置重置时间";
            status = "待设置"; fg = sub; bg = accentSoft;
            note = "设定时间后，所选提醒将自动生效。";
        } else if (s.target <= now) {
            countdown = "00:00:00";
            target = "计划重置 · " + AlarmEngine.timeLabel(s.target);
            status = "已到点"; fg = positive; bg = positiveSoft;
            note = "正在顺延下一次重置时间。";
        } else {
            countdown = ResetMath.countdown(s.target - now);
            target = "计划重置 · " + AlarmEngine.timeLabel(s.target);
            status = "计时中"; fg = accent; bg = accentSoft;
            if (!s.early && !s.at) note = "提醒均已关闭，计时仍会自动续排。";
            else if (!notifyReady) note = "通知未授权，暂时无法弹出提醒。";
            else if (s.early && s.earlyDelivered) note = s.at ? "提前提醒已发送，将等待到点提醒。" : "提前提醒已发送。";
            else if (s.early && s.target - s.earlyMinutes * ResetMath.MINUTE <= now) note = "已进入提前提醒时间。";
            else note = (s.early ? "提前 " + s.earlyMinutes + " 分钟" : "") + (s.early && s.at ? " · " : "") + (s.at ? "到点提醒" : "") + " 已启用";
        }
        // Only change text that actually changed; no repeated backgrounds/layout resets per second.
        setText(v.countdown, countdown); setText(v.target, target); setText(v.note, note);
        if (!status.equals(v.statusKey)) {
            v.statusKey = status;
            setText(v.status, status);
            v.status.setTextColor(fg);
            v.status.setBackground(rounded(bg, 24));
        }
        setText(v.advance, "提前 " + s.earlyMinutes + " 分钟  ·  修改");
        setText(v.cycle, "每 " + ResetMath.periodLabel(s.periodMinutes) + " 自动续排" +
                (s.id == ReminderStore.SHORT ? " · 修改周期" : ""));
        float alpha = s.early ? 1f : 0.48f;
        if (v.advance.getAlpha() != alpha) v.advance.setAlpha(alpha);
        if (v.advance.isEnabled() != s.early) v.advance.setEnabled(s.early);
        // INVISIBLE keeps card geometry stable while clearing/creating a target.
        int clearVisibility = hasTarget ? View.VISIBLE : View.INVISIBLE;
        if (v.clear.getVisibility() != clearVisibility) v.clear.setVisibility(clearVisibility);
    }

    private void save(ReminderStore.Slot s) {
        ReminderStore.save(this, s);
        AlarmEngine.rescheduleSlot(this, s.profileId, s.id);
        // Update only the affected account. Native Switch objects and scrolling remain untouched.
        GroupViews g = groups.get(s.profileId);
        if (g != null) updateGroup(g, System.currentTimeMillis());
    }

    private void setTarget(int profileId, int id, long target) {
        target = ResetMath.minuteBoundary(target);
        if (target <= System.currentTimeMillis()) { toast("请选择未来的重置时间"); return; }
        ReminderStore.Profile profile = ReminderStore.profile(this, profileId);
        if (profile == null || !profile.allows(id)) return;
        ReminderStore.Slot s = ReminderStore.load(this, profileId, id);
        s.target = target;
        s.earlyDelivered = s.atDelivered = false;
        // Clear any delivered notification from the previous quota cycle.
        AlarmEngine.cancelSlot(this, profileId, id, true);
        save(s);
        toast("已保存 " + profile.label() + " · " + s.title());
    }

    private void chooseDateTime(int profileId, int id) {
        ReminderStore.Slot s = ReminderStore.load(this, profileId, id);
        Calendar selected = Calendar.getInstance();
        selected.setTimeInMillis(s.target > System.currentTimeMillis() ? s.target :
                ResetMath.relativeTarget(System.currentTimeMillis(), s.periodMinutes * ResetMath.MINUTE));
        DatePickerDialog date = new DatePickerDialog(this, (picker, year, month, day) -> {
            selected.set(Calendar.YEAR, year);
            selected.set(Calendar.MONTH, month);
            selected.set(Calendar.DAY_OF_MONTH, day);
            TimePickerDialog time = new TimePickerDialog(this, (t, hour, minute) -> {
                selected.set(Calendar.HOUR_OF_DAY, hour);
                selected.set(Calendar.MINUTE, minute);
                selected.set(Calendar.SECOND, 0);
                selected.set(Calendar.MILLISECOND, 0);
                setTarget(profileId, id, selected.getTimeInMillis());
            }, selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE), true);
            time.setTitle("选择重置时间");
            time.show();
        }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH));
        date.getDatePicker().setMinDate(System.currentTimeMillis());
        date.setTitle("选择重置日期");
        date.show();
    }

    private NumberPicker picker(int min, int max, int initial) {
        NumberPicker p = new NumberPicker(this);
        p.setMinValue(min);
        p.setMaxValue(max);
        p.setValue(Math.max(min, Math.min(max, initial)));
        p.setWrapSelectorWheel(false);
        return p;
    }

    private void pickerColumn(LinearLayout parent, String unit, NumberPicker p) {
        LinearLayout column = column();
        column.setGravity(Gravity.CENTER);
        TextView t = label(unit, 13, sub, true);
        t.setGravity(Gravity.CENTER);
        add(column, t, 0);
        column.addView(p, new LinearLayout.LayoutParams(-1, -2));
        parent.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void chooseDuration(int profileId, int id) {
        ReminderStore.Slot s = ReminderStore.load(this, profileId, id);
        long remain = s.target > System.currentTimeMillis() ? s.target - System.currentTimeMillis() :
                s.periodMinutes * ResetMath.MINUTE;
        long mins = (remain + ResetMath.MINUTE - 1) / ResetMath.MINUTE;
        NumberPicker days = picker(0, 365, (int)(mins / 1440));
        NumberPicker hours = picker(0, 23, (int)(mins / 60 % 24));
        NumberPicker minutes = picker(0, 59, (int)(mins % 60));
        LinearLayout pickers = row();
        pickers.setPadding(dp(14), dp(12), dp(14), dp(8));
        pickerColumn(pickers, "天", days);
        pickerColumn(pickers, "小时", hours);
        pickerColumn(pickers, "分钟", minutes);
        LinearLayout durationFields = column();
        TextView durationHint = label("以手机当前分钟为基准，重置时间的秒数固定为 00。", 12, sub, false);
        durationHint.setPadding(dp(20), dp(12), dp(20), 0);
        add(durationFields, durationHint, 0);
        add(durationFields, pickers, 0);
        AlertDialog d = new AlertDialog.Builder(this).setTitle(s.title() + " · 剩余时长")
                .setView(durationFields).setNegativeButton("取消", null).setPositiveButton("设定", null).create();
        d.setOnShowListener(dialog -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            days.clearFocus(); hours.clearFocus(); minutes.clearFocus();
            long duration = ResetMath.duration(days.getValue(), hours.getValue(), minutes.getValue());
            if (duration <= 0) { toast("剩余时长至少为 1 分钟"); return; }
            setTarget(profileId, id, ResetMath.relativeTarget(System.currentTimeMillis(), duration));
            d.dismiss();
        }));
        d.show();
    }

    private void chooseCycle(int profileId) {
        ReminderStore.Slot s = ReminderStore.load(this, profileId, ReminderStore.SHORT);
        NumberPicker hours = picker(0, 168, s.periodMinutes / 60);
        NumberPicker minutes = picker(0, 59, s.periodMinutes % 60);
        LinearLayout fields = column();
        fields.setPadding(dp(16), dp(12), dp(16), dp(8));
        add(fields, label("每次重置后按此固定周期续排。修改周期不会改变当前这一次的重置时间。", 13, sub, false), 0);
        LinearLayout picks = row();
        pickerColumn(picks, "小时", hours);
        pickerColumn(picks, "分钟", minutes);
        add(fields, picks, 10);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("短时限额 · 循环周期")
                .setView(fields).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            hours.clearFocus(); minutes.clearFocus();
            int period = hours.getValue() * 60 + minutes.getValue();
            if (period < 1 || period > ResetMath.WEEK_MINUTES) { toast("循环周期须为 1 分钟至 7 天"); return; }
            // Reconcile any elapsed cycle using its old period before changing future cycles.
            AlarmEngine.reconcileDue(this, System.currentTimeMillis());
            ReminderStore.Slot current = ReminderStore.load(this, profileId, ReminderStore.SHORT);
            current.periodMinutes = period;
            save(current);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void chooseAdvance(int profileId, int id) {
        ReminderStore.Slot s = ReminderStore.load(this, profileId, id);
        NumberPicker p = picker(1, 10080, s.earlyMinutes);
        LinearLayout body = column();
        body.setGravity(Gravity.CENTER);
        body.setPadding(dp(22), dp(14), dp(22), dp(14));
        add(body, label("选择提前分钟数（1–10080）", 13, sub, false), 0);
        body.addView(p, new LinearLayout.LayoutParams(dp(170), -2));
        AlertDialog d = new AlertDialog.Builder(this).setTitle("提前提醒")
                .setView(body).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        d.setOnShowListener(dialog -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            p.clearFocus();
            ReminderStore.Slot current = ReminderStore.load(this, profileId, id);
            current.earlyMinutes = p.getValue();
            save(current);
            d.dismiss();
        }));
        d.show();
    }

    private void openNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
            boolean asked = ReminderStore.prefs(this).getBoolean("permission_asked", false);
            if (!asked || shouldShowRequestPermissionRationale("android.permission.POST_NOTIFICATIONS")) {
                ReminderStore.prefs(this).edit().putBoolean("permission_asked", true).apply();
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, NOTIFICATION_REQUEST);
                return;
            }
        }
        if (Build.VERSION.SDK_INT >= 26) {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            try { startActivity(i); return; } catch (Exception ignored) { }
        }
        openAppDetails();
    }

    private void openExactPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
                return;
            } catch (Exception ignored) { }
        }
        openAppDetails();
    }

    private void openAppDetails() {
        try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
        catch (Exception ignored) { toast("请在系统设置中手动授权"); }
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
}
