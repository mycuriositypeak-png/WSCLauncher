package dev.wsc.launcher;

import android.content.Context;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
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
        // WSC 468's ads SDK also creates clients inside a separately loaded
        // Play Services Dynamite module. Those clients do not inherit the game's
        // BaseGmsClient, so the constructor hook above cannot repair their context.
        // Adapt before the SDK captures/wraps it for either remote or local creators.
        count[0] += installAds(loader, method -> Pine.hook(method, adapter(serviceContext, method.toString())));
        GuestRuntime.log("SDK CONTEXT: " + count[0] + " pontos instalados; pacote=" + serviceContext.getPackageName()
                + "; UID=" + serviceContext.getApplicationInfo().uid + "; UID do processo=" + android.os.Process.myUid(), null);
    }
    static int installAds(ClassLoader loader, Consumer<Method> register) {
        int count = 0;
        String[] types = {
                "com.google.android.gms.ads.MobileAds",
                "com.google.android.gms.ads.internal.client.zzex", // MobileAds settings/initialization in WSC 468.
                "com.google.android.gms.ads.internal.client.zzaz"  // ClientApiBroker in WSC 468.
        };
        for (String name : types) {
            Class<?> type;
            try { type = Class.forName(name, false, loader); }
            catch (ClassNotFoundException absent) {
                GuestRuntime.log("SDK ADS CONTEXT: classe ausente: " + name, null);
                continue;
            }
            List<Method> methods = contextMethods(type);
            int installed = 0;
            for (Method method : methods) {
                // Other public MobileAds entry points can require UI context;
                // keep this public boundary specific to initialization.
                if (name.equals(types[0]) && !method.getName().equals("initialize")) continue;
                register.accept(method);
                installed++;
            }
            count += installed;
            GuestRuntime.log("SDK ADS CONTEXT: " + name + "; " + installed + " pontos instalados.", null);
        }
        return count;
    }
    static List<Method> contextMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (!Modifier.isAbstract(method.getModifiers()) && method.getParameterTypes().length > 0
                    && method.getParameterTypes()[0] == Context.class) methods.add(method);
        }
        // In particular, Activity/View/ObjectWrapper parameters are not Context
        // initialization boundaries. Replacing them would break overlays and UI.
        return methods;
    }
    static boolean adaptArguments(Object[] arguments, Context serviceContext) {
        if (arguments.length == 0 || !(arguments[0] instanceof Context)) return false;
        Context original = (Context) arguments[0];
        if (!GuestRuntime.TARGET.equals(original.getPackageName())) return false;
        arguments[0] = serviceContext;
        return true;
    }
    private static MethodHook adapter(Context serviceContext, String entry) {
        return new MethodHook() {
            @Override public void beforeCall(Pine.CallFrame frame) {
                if (!adaptArguments(frame.args, serviceContext)) return;
                GuestRuntime.log("SDK CONTEXT: " + entry + " -> " + serviceContext.getPackageName(), null);
            }
        };
    }
}
