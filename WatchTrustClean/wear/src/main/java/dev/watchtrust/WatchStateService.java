package dev.watchtrust;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WatchStateService extends Service implements SensorEventListener,
        MessageClient.OnMessageReceivedListener {
    private static final String TAG = "WatchTrust";
    private static final String CHANNEL = "watchtrust_state";
    private static final int NOTIFICATION_ID = 1001;
    private static final String STATE_PATH = "/watchtrust/state";
    private static final String QUERY_PATH = "/watchtrust/query";
    private static final String PHONE_CAPABILITY = "watchtrust_phone";
    private static final long HEARTBEAT_MS = 5_000L;

    private static volatile WatchStateService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private SensorManager sensorManager;
    private Sensor offBodySensor;
    private KeyguardManager keyguard;
    private MessageClient messageClient;
    private volatile boolean onBody;

    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            sendState("heartbeat");
            handler.postDelayed(this, HEARTBEAT_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createChannel();
        startForeground(NOTIFICATION_ID,
                new Notification.Builder(this, CHANNEL)
                        .setContentTitle("WatchTrust")
                        .setContentText("Watch state link active")
                        .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                        .build());

        keyguard = getSystemService(KeyguardManager.class);
        sensorManager = getSystemService(SensorManager.class);
        offBodySensor = sensorManager.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT);

        if (offBodySensor != null) {
            sensorManager.registerListener(this, offBodySensor, SensorManager.SENSOR_DELAY_NORMAL);
            Log.i(TAG, "Off-body sensor registered: " + offBodySensor.getName());
        } else {
            Log.w(TAG, "TYPE_LOW_LATENCY_OFFBODY_DETECT is not available");
        }

        messageClient = Wearable.getMessageClient(this);
        messageClient.addListener(this)
                .addOnSuccessListener(unused -> Log.i(TAG, "Live MessageClient listener registered"))
                .addOnFailureListener(e -> Log.w(TAG, "Failed to register live MessageClient listener", e));

        Log.i(TAG, "WatchStateService onCreate");
        handler.post(heartbeat);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        sendState("start-command");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        handler.removeCallbacksAndMessages(null);
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (messageClient != null) {
            messageClient.removeListener(this)
                    .addOnSuccessListener(unused -> Log.i(TAG, "Live MessageClient listener removed"))
                    .addOnFailureListener(e -> Log.w(TAG, "Failed to remove live MessageClient listener", e));
        }
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT
                && event.values.length > 0) {
            onBody = event.values[0] >= 0.5f;
            Log.i(TAG, "onBody=" + onBody);
            sendState("off-body-change");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onMessageReceived(MessageEvent event) {
        Log.i(TAG, "Live Wear message received: path=" + event.getPath()
                + " sourceNodeId=" + event.getSourceNodeId());

        if (!QUERY_PATH.equals(event.getPath())) {
            return;
        }

        Log.i(TAG, "Watch query received by live service; replying immediately");
        sendState("query-response");
    }

    public static boolean requestImmediateState() {
        WatchStateService service = instance;
        if (service == null) return false;
        service.sendState("listener-service-fallback");
        return true;
    }

    private void sendState(String reason) {
        final boolean unlocked = keyguard != null && !keyguard.isDeviceLocked();
        final boolean body = onBody;
        final String payload = (body ? "1" : "0") + "," + (unlocked ? "1" : "0");

        io.execute(() -> {
            try {
                List<Node> nodes = Tasks.await(Wearable.getNodeClient(this).getConnectedNodes());
                for (Node node : nodes) {
                    Log.i(TAG, "Connected node: id=" + node.getId()
                            + " name=" + node.getDisplayName()
                            + " nearby=" + node.isNearby());
                }

                CapabilityInfo capabilityInfo = Tasks.await(
                        Wearable.getCapabilityClient(this).getCapability(
                                PHONE_CAPABILITY,
                                CapabilityClient.FILTER_REACHABLE));
                Set<Node> capabilityNodes = capabilityInfo.getNodes();
                Log.i(TAG, "Capability " + PHONE_CAPABILITY
                        + " reachableNodes=" + capabilityNodes.size());
                for (Node node : capabilityNodes) {
                    Log.i(TAG, "Capability node: id=" + node.getId()
                            + " name=" + node.getDisplayName()
                            + " nearby=" + node.isNearby());
                }

                byte[] data = payload.getBytes(StandardCharsets.UTF_8);
                for (Node node : nodes) {
                    int requestId = Tasks.await(
                            Wearable.getMessageClient(this).sendMessage(node.getId(), STATE_PATH, data));
                    Log.i(TAG, "sendMessage OK requestId=" + requestId
                            + " nodeId=" + node.getId()
                            + " payload=" + payload
                            + " reason=" + reason);
                }
                Log.i(TAG, "Sent watch state: " + payload
                        + " reason=" + reason
                        + " nodes=" + nodes.size()
                        + " capabilityNodes=" + capabilityNodes.size());
            } catch (Exception e) {
                Log.w(TAG, "Failed to send watch state; reason=" + reason, e);
            }
        });
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "WatchTrust state", NotificationManager.IMPORTANCE_LOW));
        }
    }
}
