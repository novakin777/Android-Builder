package dev.watchtrust;

import android.os.SystemClock;

public final class WatchStateStore {
    private static final long MAX_AGE_MS = 20_000L;

    private static volatile boolean onBody;
    private static volatile boolean unlocked;
    private static volatile long updatedAt;

    private WatchStateStore() {}

    public static void update(boolean newOnBody, boolean newUnlocked) {
        onBody = newOnBody;
        unlocked = newUnlocked;
        updatedAt = SystemClock.elapsedRealtime();
    }

    public static boolean isEligible() {
        long age = SystemClock.elapsedRealtime() - updatedAt;
        return age >= 0 && age <= MAX_AGE_MS && onBody && unlocked;
    }

    public static boolean isOnBody() { return onBody; }
    public static boolean isUnlocked() { return unlocked; }

    public static long ageMs() {
        if (updatedAt == 0L) return Long.MAX_VALUE;
        return SystemClock.elapsedRealtime() - updatedAt;
    }
}
