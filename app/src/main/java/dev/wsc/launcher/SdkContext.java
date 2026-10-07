package dev.wsc.launcher;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.content.res.Resources;

/** Real host package/UID identity for service clients, while retaining resources they read on entry. */
final class SdkContext extends ContextWrapper {
    private final Context game;
    SdkContext(Context host, Context game) { super(host.getApplicationContext()); this.game = game; }
    @Override public Resources getResources() { return game.getResources(); }
    @Override public AssetManager getAssets() { return game.getAssets(); }
    @Override public ClassLoader getClassLoader() { return game.getClassLoader(); }
    // getPackageName, getOpPackageName, getApplicationInfo and getApplicationContext all use the host.
}
