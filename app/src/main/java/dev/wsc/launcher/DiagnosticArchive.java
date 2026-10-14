package dev.wsc.launcher;

import android.content.Context;
import android.os.Build;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class DiagnosticArchive {
    private static final String[] FILES = {"guest-diagnostic.log", "guest-logcat.log", "guest-logcat.log.1",
            "guest-logcat.log.2", "logcat-capture.txt", "logcat-status.txt", "guest-exit-trace.bin", "mods-runtime.txt"};
    static void write(Context context, OutputStream destination) throws IOException {
        File diagnostic = new File(context.getFilesDir(), "guest-diagnostic.log");
        String exit = ExitDiagnostics.describe(context, diagnostic);
        String metadata = "WSCLauncher 1.0\nData: " + new Date() + "\nAndroid: " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")\nAparelho: " + Build.MANUFACTURER + " " + Build.MODEL
                + "\nABIs: " + java.util.Arrays.toString(Build.SUPPORTED_ABIS)
                + "\nCaptura logcat: PID do processo guest, limitada ao acesso do próprio aplicativo.\n";
        try (ZipOutputStream zip = new ZipOutputStream(destination)) {
            text(zip, "device-and-exit.txt", metadata + exit);
            try { text(zip, "mods-installed.txt", new ModStore(context.getFilesDir()).describe()); }
            catch (IOException e) { text(zip, "mods-installed.txt", "Falha ao ler mods: " + e); }
            for (String name : FILES) {
                File file = new File(context.getFilesDir(), name);
                if (!file.isFile()) continue;
                // Snapshot the size: a live log writer must not keep export reading forever.
                long remaining = Math.min(file.length(), 2L * 1024 * 1024);
                zip.putNextEntry(new ZipEntry(name));
                try (FileInputStream input = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    while (remaining > 0) {
                        int count = input.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                        if (count < 0) break;
                        zip.write(buffer, 0, count);
                        remaining -= count;
                    }
                }
                zip.closeEntry();
            }
        }
    }
    private static void text(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
