package dev.wsc.launcher;

import android.app.Application;
import android.content.Context;
import android.os.Process;
import java.io.File;

public final class HostApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        // Make the application Context available to the static Hooker facade so it
        // can read the user-selected hooking backend (Pine / LSPlant / Auto) from
        // SharedPreferences before installing the first Java hook.
        Hooker.setApplicationContext(this);
        // A distinct process ensures every launch gets a clean set of hooks/runtime state.
        String process = readProcessName();
        if (process.endsWith(":guest")) GuestRuntime.prepare(this);
    }
    private static String readProcessName() {
        if (android.os.Build.VERSION.SDK_INT >= 28) return Application.getProcessName();
        try {
            byte[] bytes = new byte[256];
            try (java.io.FileInputStream input = new java.io.FileInputStream("/proc/self/cmdline")) {
                int count = input.read(bytes);
                if (count <= 0) return "";
            }
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8).split("\u0000", 2)[0];
        } catch (Exception e) { return ""; }
    }
}
