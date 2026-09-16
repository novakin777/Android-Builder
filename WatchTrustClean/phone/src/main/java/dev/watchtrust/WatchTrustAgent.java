package dev.watchtrust;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.service.trust.TrustAgentService;
import android.util.Log;

public final class WatchTrustAgent extends TrustAgentService {
    private static final String TAG = "WatchTrust";
    private static final long TRUST_MS = 20_000L;
    private static final long MANUAL_TEST_MS = 30_000L;

    private static volatile WatchTrustAgent instance;
    private static volatile long manualTestUntilElapsed = 0L;

    private KeyguardManager keyguardManager;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                handleScreenOn();
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                Log.i(TAG, "SCREEN_OFF");
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        keyguardManager = getSystemService(KeyguardManager.class);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);

        setManagingTrust(true);
        Log.i(TAG, "TrustAgent created; managingTrust=true; screen receiver registered");
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(screenReceiver);
        } catch (Exception ignored) {
        }

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
        boolean watchEligible = WatchStateStore.isEligible();
        boolean manualArmed = isManualTestArmed();

        Log.i(TAG, "Phone device locked; watchEligible=" + watchEligible
                + " manualArmed=" + manualArmed);

        // IMPORTANT: do not re-grant with DISMISS_KEYGUARD here.
        // onDeviceLocked() is also called when the user intentionally turns
        // the screen off. Re-granting here wakes/unlocks the phone immediately.
        // We now wait for ACTION_SCREEN_ON and only then perform the renewal.
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
            return;
        }

        grantUnlockTrust(manualArmed ? "WatchTrust manual active unlock" : "Xiaomi Watch 5");
    }

    private void handleScreenOn() {
        boolean watchEligible = WatchStateStore.isEligible();
        boolean manualArmed = isManualTestArmed();
        boolean eligible = watchEligible || manualArmed;
        boolean deviceLocked = keyguardManager != null && keyguardManager.isDeviceLocked();

        Log.i(TAG, "SCREEN_ON; deviceLocked=" + deviceLocked
                + " watchEligible=" + watchEligible
                + " manualArmed=" + manualArmed
                + " eligible=" + eligible);

        if (deviceLocked && eligible) {
            grantUnlockTrust(manualArmed
                    ? "WatchTrust manual screen-on unlock"
                    : "Xiaomi Watch 5");
        }
    }

    private void grantUnlockTrust(String message) {
        int flags = FLAG_GRANT_TRUST_TEMPORARY_AND_RENEWABLE
                | FLAG_GRANT_TRUST_INITIATED_BY_USER
                | FLAG_GRANT_TRUST_DISMISS_KEYGUARD;

        grantTrust(message, TRUST_MS, flags);
        Log.i(TAG, "Screen-on unlock grant sent; flags=" + flags + " message=" + message);
    }

    private void grantPassiveTrust() {
        grantTrust("Xiaomi Watch 5", TRUST_MS, FLAG_GRANT_TRUST_TEMPORARY_AND_RENEWABLE);
        Log.i(TAG, "Passive trust granted for " + TRUST_MS + " ms");
    }

    public static void onWatchStateChanged() {
        WatchTrustAgent agent = instance;
        if (agent == null) return;

        if (WatchStateStore.isEligible()) {
            // Keep renewable trust alive while the watch is eligible, but do
            // not force-dismiss keyguard just because the watch state changed.
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
