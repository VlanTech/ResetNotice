package com.vlan.resetnotice;

import java.util.Locale;

/** Identifies the preferred OEM channel. Identification is not enrollment,
 * SDK support detection or authorization to send notifications. */
enum PushVendor {
    OPPO("OPPO PUSH"), VIVO("vivo Push"), XIAOMI("Mi Push"),
    HONOR("HONOR Push"), HUAWEI("HUAWEI Push Kit"), MEIZU("Flyme Push"), NONE("");

    final String displayName;
    PushVendor(String displayName) { this.displayName = displayName; }

    static PushVendor detect(String manufacturer, String brand) {
        String m = normalize(manufacturer), b = normalize(brand);
        // Honor must precede Huawei: some older Honor Android phones identify
        // as Huawei. Runtime SDK support checks will be needed before enrollment.
        if (matches(m, b, "honor")) return HONOR;
        if (matches(m, b, "oppo", "realme", "oneplus", "one plus")) return OPPO;
        if (matches(m, b, "vivo", "iqoo")) return VIVO;
        if (matches(m, b, "xiaomi", "redmi", "poco")) return XIAOMI;
        if (matches(m, b, "huawei")) return HUAWEI;
        if (matches(m, b, "meizu")) return MEIZU;
        return NONE;
    }
    private static String normalize(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }
    private static boolean matches(String m, String b, String... values) {
        for (String v : values) if (v.equals(m) || v.equals(b)) return true;
        return false;
    }
}
