package com.vlan.resetnotice;

/** Stable, non-overlapping identity for each account, quota, and event type. */
final class AlarmIdentity {
    static int id(int profileId, int slot, boolean early) {
        if (profileId <= 0 || profileId >= 500_000_000 || slot < 0 || slot > 1)
            throw new IllegalArgumentException("Invalid alarm identity");
        return 1000 + profileId * 4 + slot * 2 + (early ? 0 : 1);
    }
}
