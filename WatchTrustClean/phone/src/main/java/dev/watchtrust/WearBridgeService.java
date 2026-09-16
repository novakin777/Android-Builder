package dev.watchtrust;

import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.nio.charset.StandardCharsets;

public final class WearBridgeService extends WearableListenerService {
    private static final String TAG = "WatchTrust";
    private static final String PATH = "/watchtrust/state";

    @Override
    public void onMessageReceived(MessageEvent event) {
        if (!PATH.equals(event.getPath())) return;

        String payload = new String(event.getData(), StandardCharsets.UTF_8);
        String[] parts = payload.split(",");
        if (parts.length != 2) {
            Log.w(TAG, "Bad watch payload: " + payload);
            return;
        }

        boolean onBody = "1".equals(parts[0]);
        boolean unlocked = "1".equals(parts[1]);
        WatchStateStore.update(onBody, unlocked);
        Log.i(TAG, "Watch state: onBody=" + onBody + " unlocked=" + unlocked);
        WatchTrustAgent.onWatchStateChanged();
    }
}
