package dev.watchtrust;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class WatchMainActivity extends Activity {
    private static final int REQ_BT = 10;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("WatchTrust\nNo root required on watch");
        root.addView(title);

        Button start = new Button(this);
        start.setText("Start state service");
        start.setOnClickListener(v -> startStateService());
        root.addView(start);

        Button stop = new Button(this);
        stop.setText("Stop state service");
        stop.setOnClickListener(v -> stopService(new Intent(this, WatchStateService.class)));
        root.addView(stop);

        setContentView(root);
    }

    private void startStateService() {
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
            return;
        }
        startForegroundService(new Intent(this, WatchStateService.class));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startStateService();
        }
    }
}
