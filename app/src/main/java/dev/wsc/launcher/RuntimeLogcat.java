package dev.wsc.launcher;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;

/** PID-scoped logcat capture with bounded rotation. */
final class RuntimeLogcat {
    private static java.lang.Process capture;
    static synchronized void start(Context context) {
        if (capture != null) {
            try { capture.exitValue(); }
            catch (IllegalThreadStateException running) { return; }
        }
        File folder = context.getFilesDir();
        File output = new File(folder, "guest-logcat.log");
        File messages = new File(folder, "logcat-capture.txt");
        // Only this launcher's diagnostic files are rotated/reset.
        for (String name : Arrays.asList("guest-logcat.log", "guest-logcat.log.1", "guest-logcat.log.2", "guest-exit-trace.bin"))
            new File(folder, name).delete();
        String[] command = {"logcat", "-b", "main", "-b", "system", "-b", "crash", "-v", "threadtime",
                "--pid=" + android.os.Process.myPid(), "-T", "1", "-f", output.getAbsolutePath(),
                "-r", "1024", "-n", "2", "*:V"};
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            if (android.os.Build.VERSION.SDK_INT >= 26) builder.redirectOutput(messages);
            capture = builder.start();
            if (android.os.Build.VERSION.SDK_INT < 26) captureMessages(capture, messages);
            GuestRuntime.log("LOGCAT: captura solicitada para o PID guest; até 3 arquivos de 1 MiB.", null);
            try (FileWriter status = new FileWriter(new File(folder, "logcat-status.txt"), false)) {
                status.write("PID guest: " + android.os.Process.myPid() + "\nComando: " + Arrays.toString(command)
                        + "\nEscopo: logs permitidos pelo Android para o próprio UID; mensagens de outros processos podem não aparecer.\n");
            }
            java.lang.Process running = capture;
            Thread watcher = new Thread(() -> {
                try {
                    int exit = running.waitFor();
                    GuestRuntime.log("LOGCAT: coletor encerrou; status=" + exit + "; consulte logcat-capture.txt.", null);
                } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }, "logcat-monitor");
            watcher.setDaemon(true);
            watcher.start();
        } catch (Exception error) { GuestRuntime.log("LOGCAT: não foi possível iniciar a captura.", error); }
    }
    private static void captureMessages(java.lang.Process process, File output) {
        Thread drain = new Thread(() -> {
            try (InputStream input = process.getInputStream(); FileOutputStream file = new FileOutputStream(output)) {
                byte[] buffer = new byte[4096];
                int remaining = 16384;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    int keep = Math.min(remaining, count);
                    if (keep > 0) { file.write(buffer, 0, keep); remaining -= keep; }
                }
            } catch (Exception error) { Log.w("WSCLogcat", "Falha ao coletar mensagens do logcat", error); }
        }, "logcat-messages");
        drain.setDaemon(true);
        drain.start();
    }
}
