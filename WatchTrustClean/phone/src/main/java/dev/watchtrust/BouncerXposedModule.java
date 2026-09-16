package dev.watchtrust;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class BouncerXposedModule extends XposedModule {
    private static final String TAG = "WatchTrustXposed";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final String BOUNCER =
            "com.android.systemui.bouncer.domain.interactor.PrimaryBouncerInteractor";
    private static final String ACTION_BOUNCER_SHOWN =
            "dev.watchtrust.action.BOUNCER_SHOWN";

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!SYSTEMUI.equals(param.getPackageName())) {
            return;
        }

        try {
            ClassLoader classLoader = param.getDefaultClassLoader();
            Class<?> bouncerClass = Class.forName(BOUNCER, false, classLoader);
            Method show = bouncerClass.getDeclaredMethod(
                    "show",
                    String.class,
                    boolean.class
            );
            show.setAccessible(true);

            hook(show).intercept(BouncerHook.INSTANCE);
            Log.i(TAG, "PrimaryBouncerInteractor.show(String, boolean) hook installed");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install bouncer hook", t);
        }
    }

    public static final class BouncerHook implements XposedInterface.Hooker {
        static final BouncerHook INSTANCE = new BouncerHook();

        @Override
        public Object intercept(XposedInterface.Chain chain) throws Throwable {
            String reason = String.valueOf(chain.getArg(0));
            boolean scrimmed = (boolean) chain.getArg(1);

            Log.i(TAG, "PrimaryBouncerInteractor.show reason=" + reason
                    + " scrimmed=" + scrimmed);

            try {
                Context context = findSystemUiContext(chain.getThisObject());
                if (context != null) {
                    Intent intent = new Intent(ACTION_BOUNCER_SHOWN);
                    intent.setPackage("dev.watchtrust");
                    intent.putExtra("reason", reason);
                    intent.putExtra("scrimmed", scrimmed);
                    context.sendBroadcast(intent);
                } else {
                    Log.w(TAG, "Could not obtain SystemUI Context");
                }
            } catch (Throwable t) {
                Log.e(TAG, "Failed to send bouncer signal", t);
            }

            return chain.proceed();
        }

        private static Context findSystemUiContext(Object interactor) {
            if (interactor == null) return null;

            try {
                Field contextField = interactor.getClass().getDeclaredField("context");
                contextField.setAccessible(true);
                Object value = contextField.get(interactor);
                if (value instanceof Context) {
                    return (Context) value;
                }
            } catch (Throwable ignored) {
            }

            try {
                Class<?> activityThread = Class.forName("android.app.ActivityThread");
                Method currentApplication =
                        activityThread.getDeclaredMethod("currentApplication");
                currentApplication.setAccessible(true);
                Object value = currentApplication.invoke(null);
                if (value instanceof Context) {
                    return (Context) value;
                }
            } catch (Throwable ignored) {
            }

            return null;
        }
    }
}
