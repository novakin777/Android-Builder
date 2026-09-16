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
        Log.i(TAG, "CB onTrustTimeout; manualArmed=" + isManualTestArmed());
    }

    @Override
    public void onDeviceLocked() {
        Log.i(TAG, "CB onDeviceLocked; watchEligible=" + WatchStateStore.isEligible()
                + " manualArmed=" + isManualTestArmed());
    }

    @Override
    public void onDeviceUnlocked() {
        Log.i(TAG, "CB onDeviceUnlocked");
    }

    @Override
    public void onUnlockAttempt(boolean successful) {
        Log.i(TAG, "CB onUnlockAttempt successful=" + successful);
    }

    @Override
    public void onUserMayRequestUnlock() {
        Log.i(TAG, "CB onUserMayRequestUnlock");
    }

    @Override
    public void onUserRequestedUnlock(boolean dismissKeyguard) {
        Log.i(TAG, "CB onUserRequestedUnlock dismiss=" + dismissKeyguard);
    }

    public static void onBouncerShown(String reason) {
        WatchTrustAgent agent = instance;
        if (agent == null) {
            Log.w(TAG, "Bouncer signal ignored: TrustAgent is not running");
            return;
        }

        boolean watchEligible = WatchStateStore.isEligible();
        boolean manualArmed = isManualTestArmed();
        boolean eligible = watchEligible || manualArmed;

        Log.i(TAG, "Bouncer shown; reason=" + reason
                + " watchEligible=" + watchEligible
                + " manualArmed=" + manualArmed
                + " eligible=" + eligible);

        if (!eligible) {
            return;
        }

        int flags = FLAG_GRANT_TRUST_TEMPORARY_AND_RENEWABLE
                | FLAG_GRANT_TRUST_INITIATED_BY_USER
                | FLAG_GRANT_TRUST_DISMISS_KEYGUARD;

        agent.grantTrust(
                manualArmed
                        ? "WatchTrust manual bouncer unlock"
                        : "Xiaomi Watch 5",
                TRUST_MS,
                flags
        );

        Log.i(TAG, "Bouncer unlock grant sent; flags=" + flags);
    }

    public static void onWatchStateChanged() {
        WatchTrustAgent agent = instance;
        if (agent == null) return;

        if (!WatchStateStore.isEligible()) {
            agent.revokeTrust();
            Log.i(TAG, "Watch became ineligible; trust revoked");
        }
    }

    public static boolean manualGrantAndDismiss() {
        WatchTrustAgent agent = instance;
        if (agent == null) return false;

        manualTestUntilElapsed =
                SystemClock.elapsedRealtime() + MANUAL_TEST_MS;

        int flags = FLAG_GRANT_TRUST_TEMPORARY_AND_RENEWABLE
                | FLAG_GRANT_TRUST_INITIATED_BY_USER;

        agent.grantTrust("WatchTrust manual arm", TRUST_MS, flags);
        Log.i(TAG, "Manual test armed for " + MANUAL_TEST_MS
                + " ms; flags=" + flags);
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
