package dev.wsc.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.widget.Toast;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Restart only our own guest process when its loaded mod set differs from disk. */
final class GuestSession {
    static int pid(Activity activity) {
        ActivityManager manager = (ActivityManager) activity.getSystemService(Activity.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes != null) for (ActivityManager.RunningAppProcessInfo process : processes)
            if (process.uid == Process.myUid() && process.processName.equals(activity.getPackageName() + ":guest")) return process.pid;
        return -1;
    }
    static boolean needsRestart(Activity activity, ModStore store) throws IOException {
        if (pid(activity) < 0) return false;
        File report = new File(activity.getFilesDir(), "mods-runtime.txt");
        if (!report.isFile()) return true;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(report), StandardCharsets.UTF_8))) {
            return !("Fingerprint: " + store.fingerprint()).equals(reader.readLine());
        }
    }
    static void launch(Activity activity, ModStore store, Runnable finished) throws IOException {
        boolean restart = needsRestart(activity, store);
        int oldPid = pid(activity);
        if (!restart || oldPid < 0) { open(activity, false); finished.run(); return; }
        ActivityManager manager = (ActivityManager) activity.getSystemService(Activity.ACTIVITY_SERVICE);
        ComponentName stub = new ComponentName(activity, GuestStubActivity.class);
        for (ActivityManager.AppTask task : manager.getAppTasks()) {
            ActivityManager.RecentTaskInfo info = task.getTaskInfo();
            if (info.baseIntent != null && stub.equals(info.baseIntent.getComponent())) task.finishAndRemoveTask();
        }
        stopCollector(activity, oldPid);
        if (pid(activity) == oldPid) Process.killProcess(oldPid); // Recheck the exact own process immediately before killing.
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            private int attempts;
            @Override public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) { finished.run(); return; }
                if (pid(activity) < 0) { open(activity, true); finished.run(); return; }
                if (++attempts >= 30) {
                    Toast.makeText(activity, "O jogo ainda está encerrando. Tente iniciar novamente.", Toast.LENGTH_LONG).show();
                    finished.run(); return;
                }
                handler.postDelayed(this, 100);
            }
        }, 150);
    }
    private static void open(Activity activity, boolean clean) {
        Intent intent = new Intent(activity, GuestStubActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (clean) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
    }
    private static void stopCollector(Activity activity, int parent) {
        // logcat can outlive its parent. Read only children of the verified, own guest PID.
        File children = new File("/proc/" + parent + "/task/" + parent + "/children");
        try (InputStream input = new FileInputStream(children)) {
            String ids = new String(ModStore.read(input, 8192), StandardCharsets.UTF_8).trim();
            for (String id : ids.split("\\s+")) {
                if (!id.matches("[0-9]+")) continue;
                try (InputStream cmd = new FileInputStream("/proc/" + id + "/cmdline")) {
                    String command = new String(ModStore.read(cmd, 8192), StandardCharsets.UTF_8);
                    String[] args = command.split("\u0000");
                    if (args.length == 0 || !(args[0].equals("logcat") || args[0].equals("/system/bin/logcat"))) continue;
                    List<String> arguments = java.util.Arrays.asList(args);
                    if (arguments.contains("--pid=" + parent) && arguments.contains(new File(activity.getFilesDir(), "guest-logcat.log").getAbsolutePath()))
                        Process.killProcess(Integer.parseInt(id));
                } catch (IOException | NumberFormatException ignored) { }
            }
        } catch (IOException ignored) { } // Some Android builds do not expose proc children.
    }
}
