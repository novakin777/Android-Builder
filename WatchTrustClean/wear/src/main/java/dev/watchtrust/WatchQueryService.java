package dev.watchtrust;

import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public final class WatchQueryService extends WearableListenerService {
    private static final String TAG = "WatchTrust";
    private static final String QUERY_PATH = "/watchtrust/query";

    @Override
    public void onMessageReceived(MessageEvent event) {
        if (!QUERY_PATH.equals(event.getPath())) {
            return;
        }

        Log.i(TAG, "Watch query received: sourceNodeId=" + event.getSourceNodeId());

        if (WatchStateService.requestImmediateState()) {
            Log.i(TAG, "Immediate watch state reply requested from running service");
            return;
        }

        Intent intent = new Intent(this, WatchStateService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Log.i(TAG, "WatchStateService started for query reply");
    }
}
