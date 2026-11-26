package com.vlan.resetnotice;

import java.util.List;

/** Exact page selection. No fractional positions and no wrap at either edge. */
final class WidgetPageMath {
    static int selected(List<Integer> accounts, int previous) {
        if (accounts.isEmpty()) return -1;
        return accounts.contains(previous) ? previous : accounts.get(0);
    }
    static int move(List<Integer> accounts, int previous, int direction) {
        int current = selected(accounts, previous);
        if (current < 0 || (direction != -1 && direction != 1)) return current;
        int index = accounts.indexOf(current);
        return accounts.get(Math.max(0, Math.min(accounts.size() - 1, index + direction)));
    }
}
