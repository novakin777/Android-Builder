package dev.watchtrust;

import android.os.SystemClock;
import android.service.trust.TrustAgentService;
import android.util.Log;

public final class WatchTrustAgent extends TrustAgentService {
    private static final String TAG = "WatchTrust";
    private static final long TRUST_MS = 20_000L;
    private static final long MANUAL_TEST_MS = 30_000L;

    private static volatile WatchTrustAgent instance;
    private static volatile long manualTestUntilElapsed = 0L;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        setManagingTrust(true);
        Log.i(TAG, "TrustAgent created; managingTrust=true");
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        manualTestUntilElapsed = 0L;
        setManagingTrust(false);
        Log.i(TAG, "TrustAgent destroyed");
        super.onDestroy();
    }

    @Override
    public void onTrustTimeout() {
        Log.i(TAG, "Trust timed out; manualArmed=" + isManualTestArmed());
    }

    @Override
    public void onDeviceLocked() {
        Log.i(TAG, "Phone device locked; watchEligible=" + WatchStateStore.isEligible()
                + " manualArmed=" + isManualTestArmed());

        // Do not re-grant the manual test here. For the manual test we intentionally let
        // renewable trust downgrade to TRUSTABLE and wait for onUserRequestedUnlock().
        // That tests the same Active Unlock path the watch will use.
        if (WatchStateStore.isEligible()) {
            grantPassiveTrust();
        }
    }

    @Override
    public void onDeviceUnlocked() {
        Log.i(TAG, "Phone device unlocked");
    }

    @Override
    public void onUserRequestedUnlock(boolean dismissKeyguard) {
        boolean watchEligible = WatchStateStore.isEligible();
        boolean manualArmed = isManualTestArmed();
        boolean eligible = watchEligible || manualArmed;

        Log.i(TAG, "onUserRequestedUnlock dismiss=" + dismissKeyguard
                + " watchEligible=" + watchEligible
                + " manualArmed=" + manualArmed
                + " eligible=" + eligible);

        if (!eligible) {
            revokeTrust();
            return;
        }

        int flags = FLAG_GRANT_TRUST_TEMPORARY_AND_RENEWABLE
                | FLAG_GRANT_TRUST_INITIATED_BY_USER;
        if (dismissKeyguard) {
            flags |= FLAG_GRANT_TRUST_DISMISS_KEYGUARD;
        }

        grantTrust(manualArmed ? "WatchTrust manual active unlock" : "Xiaomi Watch 5",
                TRUST_MS, flags);
        Log.i(TAG, "Active unlock grant sent; flags=" + flags);
    }

    private void grantPassiveTrust() {
        grantTrust("Xiaomi Watch 5", TRUST_MS, FLAG_GRANT_TRUST_TEMPORARY_AND_RENEWABLE);
        Log.i(TAG, "Passive trust granted for " + TRUST_MS + " ms");
    }

    public static void onWatchStateChanged() {
        WatchTrustAgent agent = instance;
        if (agent == null) return;

        if (WatchStateStore.isEligible()) {
            agent.grantPassiveTrust();
        } else {
            agent.revokeTrust();
            Log.i(TAG, "Trust revoked because watch is not eligible");
        }
    }

    public static boolean manualGrantAndDismiss() {
        WatchTrustAgent agent = instance;
        if (agent == null) return false;

        manualTestUntilElapsed = SystemClock.elapsedRealtime() + MANUAL_TEST_MS;

        int flags = FLAG_GRANT_TRUST_TEMPORARY_AND_RENEWABLE
                | FLAG_GRANT_TRUST_INITIATED_BY_USER
                | FLAG_GRANT_TRUST_DISMISS_KEYGUARD;
        agent.grantTrust("WatchTrust manual test", TRUST_MS, flags);
        Log.i(TAG, "Manual test armed for " + MANUAL_TEST_MS + " ms; flags=" + flags);
        return true;
    }

    public static boolean manualRevoke() {
        WatchTrustAgent agent = instance;
        manualTestUntilElapsed = 0L;
        if (agent == null) return false;
        agent.revokeTrust();
        Log.i(TAG, "Manual test disarmed; trust revoked");
        return true;
    }

    private static boolean isManualTestArmed() {
        return SystemClock.elapsedRealtime() < manualTestUntilElapsed;
    }

    public static boolean isRunning() {
        return instance != null;
    }
}
