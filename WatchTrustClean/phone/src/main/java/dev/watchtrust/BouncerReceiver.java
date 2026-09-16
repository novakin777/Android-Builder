package dev.watchtrust;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class BouncerReceiver extends BroadcastReceiver {
    private static final String TAG = "WatchTrust";
    private static final String ACTION_BOUNCER_SHOWN =
            "dev.watchtrust.action.BOUNCER_SHOWN";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_BOUNCER_SHOWN.equals(intent.getAction())) {
            return;
        }

        String reason = intent.getStringExtra("reason");
        boolean scrimmed = intent.getBooleanExtra("scrimmed", false);

        Log.i(TAG, "Bouncer signal received; reason=" + reason
                + " scrimmed=" + scrimmed);

        WatchTrustAgent.onBouncerShown(reason);
    }
}
