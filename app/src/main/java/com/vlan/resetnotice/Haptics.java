package com.vlan.resetnotice;

import android.view.HapticFeedbackConstants;
import android.view.View;

final class Haptics {
    static void configure(View view) { view.setHapticFeedbackEnabled(AppPreferences.haptics(view.getContext())); }
    static void tap(View view) {
        if (AppPreferences.haptics(view.getContext())) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }
}
