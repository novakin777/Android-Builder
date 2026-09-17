package dev.watchtrust;

import android.os.SystemClock;

public final class WatchStateStore {
    // Wear OS can pause normal app heartbeats while the watch sleeps.
    // Keep the last positive state for longer, but revoke immediately on an
    // explicit negative state or peer disconnect.
    private static final long WATCHDOG_MS = 120_000L;

    private static volatile boolean onBody;
    private static volatile boolean unlocked;
    private static volatile boolean connected;
    private static volatile long updatedAt;

    private WatchStateStore() {}

    public static void update(boolean newOnBody, boolean newUnlocked) {
        onBody = newOnBody;
        unlocked = newUnlocked;
        connected = true;
        updatedAt = SystemClock.elapsedRealtime();
    }

    public static void markConnected() {
        connected = true;
    }

    public static void markDisconnected() {
        connected = false;
    }

    public static boolean isEligible() {
        long age = ageMs();
        return connected
                && age >= 0
                && age <= WATCHDOG_MS
                && onBody
                && unlocked;
    }

    public static boolean isOnBody() { return onBody; }
    public static boolean isUnlocked() { return unlocked; }
    public static boolean isConnected() { return connected; }

    public static long ageMs() {
        if (updatedAt == 0L) return Long.MAX_VALUE;
        return SystemClock.elapsedRealtime() - updatedAt;
    }
}
