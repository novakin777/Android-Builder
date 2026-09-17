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
import de.robv.android.xposed.XposedBridge;
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

    static {
        bridgeLog("[WTX] BouncerXposedInit <clinit>");
        Log.i(TAG, "BouncerXposedInit static initializer");
    }

    public BouncerXposedInit() {
        bridgeLog("[WTX] BouncerXposedInit constructor");
        Log.i(TAG, "BouncerXposedInit constructor");
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam == null ? "<null>" : String.valueOf(lpparam.packageName);
        String processName = lpparam == null ? "<null>" : String.valueOf(lpparam.processName);
        bridgeLog("[WTX] handleLoadPackage ENTER package=" + packageName
                + " process=" + processName);

        if (lpparam == null || !SYSTEMUI.equals(lpparam.packageName)) {
            return;
        }

        bridgeLog("[WTX] SystemUI callback confirmed; classLoader=" + lpparam.classLoader);
        Log.i(TAG, "Legacy handleLoadPackage reached for SystemUI");

        try {
            Class<?> bouncerClass = Class.forName(BOUNCER, false, lpparam.classLoader);
            bridgeLog("[WTX] bouncer class resolved: " + bouncerClass);

            Method show = bouncerClass.getDeclaredMethod(
                    "show",
                    String.class,
                    boolean.class
            );
            show.setAccessible(true);
            bridgeLog("[WTX] show(String,boolean) resolved: " + show);

            XposedBridge.hookMethod(show, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
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
                            Object second = param.args[1];
                            if (second instanceof Boolean) {
                                scrimmed = (Boolean) second;
                            }
                        }
                    } catch (Throwable t) {
                        bridgeLog("[WTX] argument read failed: " + t);
                        XposedBridge.log(t);
                    }

                    bridgeLog("[WTX] BOUNCER SHOW observed reason=" + reason
                            + " scrimmed=" + scrimmed);
                    Log.i(TAG, "PrimaryBouncerInteractor.show observed; reason="
                            + reason + " scrimmed=" + scrimmed);
                    sendBouncerSignal(reason, scrimmed);
                }
            });

            bridgeLog("[WTX] DIRECT XposedBridge.hookMethod INSTALLED");
            Log.i(TAG, "Direct legacy bouncer hook installed");
        } catch (Throwable t) {
            bridgeLog("[WTX] hook install FAILED: " + t);
            XposedBridge.log(t);
            Log.e(TAG, "Failed to install direct legacy bouncer hook", t);
        }
    }

    private static void sendBouncerSignal(String reason, boolean scrimmed) {
        try {
            Context context = currentApplication();
            if (context == null) {
                bridgeLog("[WTX] broadcast aborted: SystemUI Application is null");
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
            bridgeLog("[WTX] bouncer broadcast SENT");
            Log.i(TAG, "Bouncer signal broadcast sent");
        } catch (Throwable t) {
            bridgeLog("[WTX] broadcast FAILED: " + t);
            XposedBridge.log(t);
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
            bridgeLog("[WTX] ActivityThread.currentApplication failed: " + t);
            XposedBridge.log(t);
        }
        return null;
    }

    private static void bridgeLog(String message) {
        try {
            XposedBridge.log(message);
        } catch (Throwable ignored) {
            try {
                Log.i(TAG, message);
            } catch (Throwable ignoredAgain) {
            }
        }
    }
}
