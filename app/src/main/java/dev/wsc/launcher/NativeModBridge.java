package dev.wsc.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** Generic host services for external native mods; no Player Roles logic or offsets live here. */
final class NativeModBridge {
    private static volatile WeakReference<Activity> current = new WeakReference<>(null);
    private static boolean loaded;
    static void attach(Activity activity) { current = new WeakReference<>(activity); }
    static synchronized void load(String file) {
        if (!loaded) { System.loadLibrary("shadowhook"); System.loadLibrary("wscmodhost"); loaded = true; }
        loadNative(file);
    }
    private static native void loadNative(String path);
    private static native void deliverNative(long request, int selected);
    // Signature is part of native JNI initialization; keep this method and class name stable.
    private static boolean showChoiceFromNative(long request, byte[] title, byte[][] labels, int selected) {
        Activity activity = current.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return false;
        String[] items = new String[labels.length];
        for (int i = 0; i < items.length; i++) items[i] = new String(labels[i], StandardCharsets.UTF_8);
        AtomicBoolean delivered = new AtomicBoolean();
        java.util.function.IntConsumer answer = index -> {
            if (!delivered.compareAndSet(false, true)) return;
            try {
                Class<?> helper = Class.forName("org.cocos2dx.lib.Cocos2dxHelper", false, activity.getClassLoader());
                helper.getDeclaredMethod("runOnGLThread", Runnable.class).invoke(null,
                        (Runnable) () -> deliverNative(request, current.get() == activity ? index : -1));
            } catch (Exception error) { GuestRuntime.log("NATIVE MOD: falha ao entregar escolha no thread GL.", error); }
        };
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) { answer.accept(-1); return; }
            try {
                AlertDialog dialog = new AlertDialog.Builder(activity)
                        .setTitle(new String(title, StandardCharsets.UTF_8))
                        .setSingleChoiceItems(items, selected, (view, index) -> { answer.accept(index); view.dismiss(); })
                        .setNegativeButton("Cancelar", (view, which) -> view.dismiss()).create();
                dialog.setOnDismissListener(view -> answer.accept(-1));
                dialog.show();
            } catch (Exception error) { GuestRuntime.log("NATIVE MOD: não foi possível abrir o diálogo.", error); answer.accept(-1); }
        });
        return true;
    }
}
