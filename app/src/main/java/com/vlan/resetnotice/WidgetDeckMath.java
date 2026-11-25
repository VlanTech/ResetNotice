package com.vlan.resetnotice;

/** The vertical list's individual row occupies a whole launcher viewport. */
final class WidgetDeckMath {
    static final int DEFAULT_HEIGHT = 198, MIN_HEIGHT = 166, DIVIDER_DP = 6;
    static final int LAYOUT_VERSION = 2;
    static int rowHeight(int min, int max, boolean landscape) {
        int candidate = landscape ? min : max;
        if (candidate <= 0) candidate = landscape ? max : min;
        if (candidate <= 0) candidate = DEFAULT_HEIGHT;
        return Math.max(MIN_HEIGHT, Math.min(700, candidate));
    }
    static int signature(boolean direct, int height) {
        return LAYOUT_VERSION * 10000 + (direct ? 1000 : 0) + height;
    }
    // Keep bitmap-bearing direct collections comfortably below binder limits.
    static boolean direct(int sdk, int accounts) { return sdk >= 31 && accounts <= 6; }
}
