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
        Log.i(TAG, "TrustAgent created; diagnostic mode; managingTrust=true");
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
        Log.i(TAG, "CB onUnlockAttempt successful=" + successful
                + " manualArmed=" + isManualTestArmed());
    }

    @Override
    public void onUserMayRequestUnlock() {
        Log.i(TAG, "CB onUserMayRequestUnlock; watchEligible=" + WatchStateStore.isEligible()
                + " manualArmed=" + isManualTestArmed());
    }

    @Override
    public void onUserRequestedUnlock(boolean dismissKeyguard) {
        Log.i(TAG, "CB onUserRequestedUnlock dismiss=" + dismissKeyguard
                + " watchEligible=" + WatchStateStore.isEligible()
                + " manualArmed=" + isManualTestArmed());

        // Diagnostic build: deliberately DO NOT call grantTrust() here.
        // We first want to see which callback fires exactly when the user swipes
        // to the credential/bouncer screen on this PixelOS Android 17 build.
    }

    public static void onWatchStateChanged() {
        WatchTrustAgent agent = instance;
        if (agent == null) return;

        // Diagnostic build: never auto-unlock from watch state changes.
        // Revoke when watch becomes ineligible so stale trust cannot survive.
        if (!WatchStateStore.isEligible()) {
            agent.revokeTrust();
            Log.i(TAG, "Watch became ineligible; trust revoked");
        } else {
            Log.i(TAG, "Watch eligible state received; diagnostic mode does not grant trust");
        }
    }

    public static boolean manualGrantAndDismiss() {
        WatchTrustAgent agent = instance;
        if (agent == null) return false;

        manualTestUntilElapsed = SystemClock.elapsedRealtime() + MANUAL_TEST_MS;

        // Arm a renewable trust window while the phone is already unlocked.
        // After the screen is turned off, Android can downgrade it to TRUSTABLE.
        // The diagnostic callbacks above will reveal what happens when the user
        // explicitly swipes to the PIN/password bouncer. No callback re-grants.
        int flags = FLAG_GRANT_TRUST_TEMPORARY_AND_RENEWABLE
                | FLAG_GRANT_TRUST_INITIATED_BY_USER;
        agent.grantTrust("WatchTrust diagnostic arm", TRUST_MS, flags);
        Log.i(TAG, "Diagnostic manual window armed for " + MANUAL_TEST_MS
                + " ms; initial renewable grant flags=" + flags);
        return true;
    }

    public static boolean manualRevoke() {
        WatchTrustAgent agent = instance;
        manualTestUntilElapsed = 0L;
        if (agent == null) return false;
        agent.revokeTrust();
        Log.i(TAG, "Diagnostic manual window disarmed; trust revoked");
        return true;
    }

    private static boolean isManualTestArmed() {
        return SystemClock.elapsedRealtime() < manualTestUntilElapsed;
    }

    public static boolean isRunning() {
        return instance != null;
    }
}
