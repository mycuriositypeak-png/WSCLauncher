package dev.wsc.launcher;

import android.app.Application;
import android.content.ContentProvider;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.os.Looper;

/** Replays only the Firebase startup provider declared by the installed game. */
final class GuestProviders {
    private static final String FIREBASE = "com.google.firebase.provider.FirebaseInitProvider";

    static boolean initializeFirebase(Application application, ClassLoader loader) throws Exception {
        if (Looper.myLooper() != Looper.getMainLooper())
            throw new IllegalStateException("O provider do jogo deve iniciar na thread principal.");
        PackageInfo info = application.getPackageManager().getPackageInfo(
                application.getPackageName(), PackageManager.GET_PROVIDERS);
        if (info.providers == null) return false;
        for (ProviderInfo provider : info.providers) {
            if (!FIREBASE.equals(provider.name) || !provider.enabled) continue;
            Class<? extends ContentProvider> type = Class.forName(FIREBASE, false, loader)
                    .asSubclass(ContentProvider.class);
            ContentProvider instance = type.getDeclaredConstructor().newInstance();
            // Android normally attaches providers before Application.onCreate.
            // attachInfo invokes the real SDK's onCreate, with its own startup
            // timing/initialization flags and original manifest/resources intact.
            // This is local initialization, not registration of a host provider.
            instance.attachInfo(application, new ProviderInfo(provider));
            return true;
        }
        return false;
    }
}
