package dev.watchtrust;

import android.service.trust.TrustAgentService;
import android.util.Log;

public final class WatchTrustAgent extends TrustAgentService {
    private static final String TAG = "WatchTrust";
    private static final long TRUST_MS = 20_000L;

    private static volatile WatchTrustAgent instance;

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
        setManagingTrust(false);
        Log.i(TAG, "TrustAgent destroyed");
        super.onDestroy();
    }

    @Override
    public void onTrustTimeout() {
        Log.i(TAG, "Trust timed out");
    }

    @Override
    public void onDeviceLocked() {
        Log.i(TAG, "Phone device locked");
        if (WatchStateStore.isEligible()) grantPassiveTrust();
    }

    @Override
    public void onDeviceUnlocked() {
        Log.i(TAG, "Phone device unlocked");
    }

    @Override
    public void onUserRequestedUnlock(boolean dismissKeyguard) {
        boolean eligible = WatchStateStore.isEligible();
        Log.i(TAG, "onUserRequestedUnlock dismiss=" + dismissKeyguard + " eligible=" + eligible);

        if (!eligible) {
            revokeTrust();
            return;
        }

        int flags = FLAG_GRANT_TRUST_TEMPORARY_AND_RENEWABLE
                | FLAG_GRANT_TRUST_INITIATED_BY_USER;
        if (dismissKeyguard) flags |= FLAG_GRANT_TRUST_DISMISS_KEYGUARD;
        grantTrust("Xiaomi Watch 5", TRUST_MS, flags);
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
        int flags = FLAG_GRANT_TRUST_TEMPORARY_AND_RENEWABLE
                | FLAG_GRANT_TRUST_INITIATED_BY_USER
                | FLAG_GRANT_TRUST_DISMISS_KEYGUARD;
        agent.grantTrust("WatchTrust manual test", TRUST_MS, flags);
        return true;
    }

    public static boolean manualRevoke() {
        WatchTrustAgent agent = instance;
        if (agent == null) return false;
        agent.revokeTrust();
        return true;
    }

    public static boolean isRunning() {
        return instance != null;
    }
}
