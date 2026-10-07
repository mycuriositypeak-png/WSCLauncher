package dev.wsc.launcher;

import android.content.Context;
import java.lang.reflect.*;
import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

/** Adapt service clients at their initialization boundary; never intercept or ignore a security check. */
final class SdkContextHooks {
    static void install(Context host, Context game, ClassLoader loader) throws Exception {
        SdkContext serviceContext = new SdkContext(host, game);
        int[] count = {0};
        String[] factories = {"com.google.firebase.analytics.FirebaseAnalytics", "com.google.android.gms.internal.measurement.zzez"};
        for (String name : factories) {
            Class<?> type = Class.forName(name, false, loader);
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) || method.getParameterTypes().length == 0
                        || method.getParameterTypes()[0] != Context.class) continue;
                if (!method.getName().equals("getInstance") && !method.getName().equals("getScionFrontendApiImplementation")
                        && !method.getName().equals("zza")) continue;
                Pine.hook(method, adapter(serviceContext, method.toString()));
                count[0]++;
            }
        }
        Class<?> client = Class.forName("com.google.android.gms.common.internal.BaseGmsClient", false, loader);
        for (Constructor<?> constructor : client.getDeclaredConstructors()) {
            if (constructor.getParameterTypes().length == 0 || constructor.getParameterTypes()[0] != Context.class) continue;
            Pine.hook(constructor, adapter(serviceContext, "BaseGmsClient constructor"));
            count[0]++;
        }
        GuestRuntime.log("SDK CONTEXT: " + count[0] + " pontos instalados; pacote=" + serviceContext.getPackageName()
                + "; UID=" + serviceContext.getApplicationInfo().uid + "; UID do processo=" + android.os.Process.myUid(), null);
    }
    private static MethodHook adapter(Context serviceContext, String entry) {
        return new MethodHook() {
            @Override public void beforeCall(Pine.CallFrame frame) {
                Context original = (Context) frame.args[0];
                if (original == null || !GuestRuntime.TARGET.equals(original.getPackageName())) return;
                frame.args[0] = serviceContext;
                GuestRuntime.log("SDK CONTEXT: " + entry + " -> " + serviceContext.getPackageName(), null);
            }
        };
    }
}
