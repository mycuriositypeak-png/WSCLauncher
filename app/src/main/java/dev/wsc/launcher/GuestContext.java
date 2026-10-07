package dev.wsc.launcher;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import java.io.File;

/** Guest code/resources, host-owned storage. This does not change the Linux UID. */
final class GuestContext extends ContextWrapper {
    private final Context game;
    private final Context host;
    GuestContext(Context host, Context game) { super(host); this.host = host; this.game = game; }
    @Override public ClassLoader getClassLoader() { return game.getClassLoader(); }
    @Override public AssetManager getAssets() { return game.getAssets(); }
    @Override public Resources getResources() { return game.getResources(); }
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
