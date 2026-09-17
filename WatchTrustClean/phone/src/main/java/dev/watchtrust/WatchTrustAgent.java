package dev.watchtrust;

import android.content.Context;
import android.os.SystemClock;
import android.service.trust.TrustAgentService;
import android.util.Log;

import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.nio.charset.StandardCharsets;

public final class WatchTrustAgent extends TrustAgentService {
    private static final String TAG = "WatchTrust";
    private static final String QUERY_PATH = "/watchtrust/query";
    private static final long TRUST_MS = 20_000L;
    private static final long MANUAL_TEST_MS = 30_000L;
    private static final long FRESH_CACHE_MS = 5_000L;
    private static final long QUERY_TIMEOUT_MS = 2_500L;

    private static volatile WatchTrustAgent instance;
    private static volatile long manualTestUntilElapsed = 0L;
    private static volatile long pendingQueryUntilElapsed = 0L;
    private static volatile String pendingQueryReason = null;

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
        clearPendingQuery();
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
                + " connected=" + WatchStateStore.isConnected()
                + " ageMs=" + WatchStateStore.ageMs()
                + " manualArmed=" + isManualTestArmed());
    }

    @Override
    public void onDeviceUnlocked() {
        clearPendingQuery();
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

    public static void onBouncerShown(Context context, String reason) {
        WatchTrustAgent agent = instance;
        if (agent == null) {
            Log.w(TAG, "Bouncer signal ignored: TrustAgent is not running");
            return;
        }

        boolean manualArmed = isManualTestArmed();
        long ageMs = WatchStateStore.ageMs();
        boolean freshEligible = WatchStateStore.isEligible() && ageMs <= FRESH_CACHE_MS;

        Log.i(TAG, "Bouncer shown; reason=" + reason
                + " freshEligible=" + freshEligible
                + " connected=" + WatchStateStore.isConnected()
                + " onBody=" + WatchStateStore.isOnBody()
                + " watchUnlocked=" + WatchStateStore.isUnlocked()
                + " ageMs=" + ageMs
                + " manualArmed=" + manualArmed);

        if (manualArmed) {
            grantDismiss(agent, "WatchTrust manual bouncer unlock");
            return;
        }

        if (freshEligible) {
            grantDismiss(agent, "Xiaomi Watch 5 (fresh cache)");
            return;
        }

        pendingQueryUntilElapsed = SystemClock.elapsedRealtime() + QUERY_TIMEOUT_MS;
        pendingQueryReason = reason;
        requestFreshWatchState(context.getApplicationContext());
    }

    private static void requestFreshWatchState(Context context) {
        final byte[] payload = Long.toString(SystemClock.elapsedRealtime())
                .getBytes(StandardCharsets.UTF_8);

        Wearable.getNodeClient(context).getConnectedNodes()
                .addOnSuccessListener(nodes -> {
                    if (nodes.isEmpty()) {
                        Log.w(TAG, "Watch query not sent: no connected Wear nodes");
                        clearPendingQuery();
                        return;
                    }

                    for (Node node : nodes) {
                        Wearable.getMessageClient(context)
                                .sendMessage(node.getId(), QUERY_PATH, payload)
                                .addOnSuccessListener(requestId -> Log.i(TAG,
                                        "Watch query sent: requestId=" + requestId
                                                + " nodeId=" + node.getId()))
                                .addOnFailureListener(e -> Log.w(TAG,
                                        "Watch query send failed: nodeId=" + node.getId(), e));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to enumerate Wear nodes for query", e);
                    clearPendingQuery();
                });
    }

    public static void onWatchStateChanged() {
        WatchTrustAgent agent = instance;
        if (agent == null) return;

        if (!WatchStateStore.isEligible()) {
            clearPendingQuery();
            agent.revokeTrust();
            Log.i(TAG, "Watch became ineligible; trust revoked"
                    + " connected=" + WatchStateStore.isConnected()
                    + " onBody=" + WatchStateStore.isOnBody()
                    + " unlocked=" + WatchStateStore.isUnlocked()
                    + " ageMs=" + WatchStateStore.ageMs());
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (pendingQueryUntilElapsed != 0L && now <= pendingQueryUntilElapsed) {
            String reason = pendingQueryReason;
            clearPendingQuery();
            Log.i(TAG, "Fresh watch response accepted for pending bouncer; reason=" + reason
                    + " ageMs=" + WatchStateStore.ageMs());
            grantDismiss(agent, "Xiaomi Watch 5 (query response)");
        } else if (pendingQueryUntilElapsed != 0L) {
            Log.i(TAG, "Watch query response arrived after timeout; ageMs="
                    + WatchStateStore.ageMs());
            clearPendingQuery();
        }
    }

    private static void grantDismiss(WatchTrustAgent agent, String message) {
        int flags = FLAG_GRANT_TRUST_TEMPORARY_AND_RENEWABLE
                | FLAG_GRANT_TRUST_INITIATED_BY_USER
                | FLAG_GRANT_TRUST_DISMISS_KEYGUARD;

        agent.grantTrust(message, TRUST_MS, flags);
        Log.i(TAG, "Bouncer unlock grant sent; flags=" + flags + " source=" + message);
    }

    private static void clearPendingQuery() {
        pendingQueryUntilElapsed = 0L;
        pendingQueryReason = null;
    }

    public static boolean manualGrantAndDismiss() {
        WatchTrustAgent agent = instance;
        if (agent == null) return false;

        manualTestUntilElapsed = SystemClock.elapsedRealtime() + MANUAL_TEST_MS;

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
        clearPendingQuery();

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
