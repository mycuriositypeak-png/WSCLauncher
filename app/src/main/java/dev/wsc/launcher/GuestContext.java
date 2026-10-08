package dev.wsc.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.ContextThemeWrapper;
import java.lang.reflect.Field;

/** Guest code/resources, host-owned storage. This does not change the Linux UID. */
final class GuestContext extends ContextWrapper {
    private final Context game;
    private final Context host;
    GuestContext(Context host, Context game) { super(host); this.host = host; this.game = game; }
    static boolean bindBeforeCreate(Activity receiver, Class<?> guestType, Context game) throws ReflectiveOperationException {
        if (guestType == null || !guestType.isInstance(receiver)) return false;
        // Activity.attach has already completed. Compiled framework calls can skip
        // entry-point hooks on both attach and attachBaseContext in release builds.
        // Update the actual ContextWrapper field at the observed onCreate boundary.
        Field base = ContextWrapper.class.getDeclaredField("mBase");
        base.setAccessible(true);
        Context original = (Context) base.get(receiver);
        if (original == null) throw new IllegalStateException("A Activity ainda não recebeu um contexto base.");
        if (original instanceof GuestContext) return false;
        Field resources = ContextThemeWrapper.class.getDeclaredField("mResources");
        Field theme = ContextThemeWrapper.class.getDeclaredField("mTheme");
        Field inflater = ContextThemeWrapper.class.getDeclaredField("mInflater");
        resources.setAccessible(true); theme.setAccessible(true); inflater.setAccessible(true);
        // Resolve every field before changing anything. The framework may already
        // have cached host resources/theme during attach; they must be rebuilt from
        // the guest base. Keep window, tokens, ActivityInfo and Application intact.
        base.set(receiver, new GuestContext(original, game));
        resources.set(receiver, null); theme.set(receiver, null); inflater.set(receiver, null);
        return true;
    }
    @Override public ClassLoader getClassLoader() { return game.getClassLoader(); }
    @Override public AssetManager getAssets() { return game.getAssets(); }
    @Override public Resources getResources() { return game.getResources(); }
    @Override public Context createConfigurationContext(Configuration configuration) {
        // ContextThemeWrapper rebuilds its resource cache through this method when
        // an Activity has an override configuration. Keep both sides configured;
        // delegating to the host alone would restore host assets after rebinding.
        return new GuestContext(host.createConfigurationContext(configuration), game.createConfigurationContext(configuration));
    }
    @Override public String getPackageName() { return game.getPackageName(); }
    @Override public String getPackageCodePath() { return game.getPackageCodePath(); }
    @Override public String getPackageResourcePath() { return game.getPackageResourcePath(); }
    @Override public Context getApplicationContext() {
        return GuestRuntime.gameApplication != null ? GuestRuntime.gameApplication : this;
    }
    @Override public ApplicationInfo getApplicationInfo() {
        ApplicationInfo info;
        try { info = new ApplicationInfo(host.getPackageManager().getApplicationInfo(game.getPackageName(), android.content.pm.PackageManager.GET_META_DATA)); }
        catch (android.content.pm.PackageManager.NameNotFoundException e) { throw new IllegalStateException(e); }
        info.dataDir = host.getApplicationInfo().dataDir;
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            info.deviceProtectedDataDir = host.getApplicationInfo().deviceProtectedDataDir;
        }
        // Retain original APK paths and original package UID metadata; never forge signatures.
        return info;
    }
}
