package dev.watchtrust;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class BouncerXposedInit implements IXposedHookLoadPackage {
    private static final String TAG = "WatchTrustXposed";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final String BOUNCER =
            "com.android.systemui.bouncer.domain.interactor.PrimaryBouncerInteractor";
    private static final String ACTION_BOUNCER_SHOWN =
            "dev.watchtrust.action.BOUNCER_SHOWN";
    private static final long DEBOUNCE_MS = 750L;

    private static volatile long lastSignalElapsed;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam == null || !SYSTEMUI.equals(lpparam.packageName)) {
            return;
        }

        Log.i(TAG, "Legacy entrypoint loaded in SystemUI");

        try {
            XposedHelpers.findAndHookMethod(
                    BOUNCER,
                    lpparam.classLoader,
                    "show",
                    String.class,
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            long now = SystemClock.elapsedRealtime();
                            long previous = lastSignalElapsed;
                            if (now - previous < DEBOUNCE_MS) {
                                return;
                            }
                            lastSignalElapsed = now;

                            String reason = "null";
                            boolean scrimmed = false;
                            try {
                                if (param.args != null && param.args.length >= 2) {
                                    reason = String.valueOf(param.args[0]);
                                    scrimmed = (Boolean) param.args[1];
                                }
                            } catch (Throwable t) {
                                Log.w(TAG, "Could not read show() arguments", t);
                            }

                            Log.i(TAG, "PrimaryBouncerInteractor.show observed; reason="
                                    + reason + " scrimmed=" + scrimmed);
                            sendBouncerSignal(reason, scrimmed);
                        }
                    }
            );
            Log.i(TAG, "Legacy PrimaryBouncerInteractor.show(String, boolean) hook installed");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install legacy bouncer hook", t);
        }
    }

    private static void sendBouncerSignal(String reason, boolean scrimmed) {
        try {
            Context context = currentApplication();
            if (context == null) {
                Log.w(TAG, "SystemUI application context unavailable");
                return;
            }

            Intent intent = new Intent(ACTION_BOUNCER_SHOWN);
            intent.setComponent(new ComponentName(
                    "dev.watchtrust",
                    "dev.watchtrust.BouncerReceiver"
            ));
            intent.putExtra("reason", reason);
            intent.putExtra("scrimmed", scrimmed);
            context.sendBroadcast(intent);
            Log.i(TAG, "Bouncer signal broadcast sent");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to send bouncer signal", t);
        }
    }

    private static Context currentApplication() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method method = activityThread.getDeclaredMethod("currentApplication");
            method.setAccessible(true);
            Object application = method.invoke(null);
            if (application instanceof Application) {
                return (Application) application;
            }
        } catch (Throwable t) {
            Log.w(TAG, "ActivityThread.currentApplication failed", t);
        }
        return null;
    }
}
