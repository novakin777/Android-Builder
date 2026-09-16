package dev.watchtrust;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        status = new TextView(this);
        root.addView(status);

        Button refresh = new Button(this);
        refresh.setText("Refresh status");
        refresh.setOnClickListener(v -> refreshStatus());
        root.addView(refresh);

        Button grant = new Button(this);
        grant.setText("Manual GRANT + dismiss (20 s)");
        grant.setOnClickListener(v -> {
            boolean ok = WatchTrustAgent.manualGrantAndDismiss();
            status.setText(ok ? "grantTrust() called" : "TrustAgent is not connected");
        });
        root.addView(grant);

        Button revoke = new Button(this);
        revoke.setText("REVOKE");
        revoke.setOnClickListener(v -> {
            boolean ok = WatchTrustAgent.manualRevoke();
            status.setText(ok ? "revokeTrust() called" : "TrustAgent is not connected");
        });
        root.addView(revoke);

        setContentView(root);
        refreshStatus();
    }

    private void refreshStatus() {
        status.setText(
                "Agent running: " + WatchTrustAgent.isRunning()
                + "\nWatch on body: " + WatchStateStore.isOnBody()
                + "\nWatch unlocked: " + WatchStateStore.isUnlocked()
                + "\nWatch state age: " + WatchStateStore.ageMs() + " ms");
    }
}
