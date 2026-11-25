package com.vlan.resetnotice;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;
import java.util.Locale;

/** User-selectable language and theme, independent of saved timer data. */
final class AppPreferences {
    static final String SYSTEM = "system";
    static final String LIGHT = "light";
    static final String DARK = "dark";
    static final String MANUAL = "manual";
    static final String NEXT_RESET = "next_reset";
    static final String[] LANGUAGES = {SYSTEM, "zh-Hans", "zh-Hant", "en", "ja", "ko", "es", "pt", "fr", "it", "de", "ru"};
    static final String[] LANGUAGE_NAMES = {"", "简体中文", "繁體中文", "English", "日本語", "한국어", "Español", "Português", "Français", "Italiano", "Deutsch", "Русский"};

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("ui_preferences", Context.MODE_PRIVATE); }
    static String language(Context c) { return prefs(c).getString("language", SYSTEM); }
    static String theme(Context c) { return prefs(c).getString("theme", SYSTEM); }
    static String order(Context c) { return prefs(c).getString("order", MANUAL); }
    static void setOrder(Context c, String order) { prefs(c).edit().putString("order", order).apply(); }
    static boolean haptics(Context c) { return prefs(c).getBoolean("haptics", true); }
    static void setHaptics(Context c, boolean enabled) { prefs(c).edit().putBoolean("haptics", enabled).apply(); }
    static void setLanguage(Context c, String language) { prefs(c).edit().putString("language", language).apply(); }
    static void setTheme(Context c, String theme) { prefs(c).edit().putString("theme", theme).apply(); }
    static String signature(Context c) { return language(c) + "/" + theme(c) + "/" + haptics(c); }

    static Context wrap(Context base) {
        Configuration config = new Configuration(base.getResources().getConfiguration());
        String language = language(base);
        if (!SYSTEM.equals(language)) {
            Locale locale = Locale.forLanguageTag(language);
            if (Build.VERSION.SDK_INT >= 24) config.setLocales(new LocaleList(locale));
            else config.setLocale(locale);
        }
        String theme = theme(base);
        if (LIGHT.equals(theme) || DARK.equals(theme)) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) |
                    (DARK.equals(theme) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        }
        return base.createConfigurationContext(config);
    }

    static Context localized(Context context) {
        Context app = context.getApplicationContext();
        return wrap(app == null ? context : app);
    }
}
