package dev.wsc.launcher;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Date;

/** OS-level exit record survives a native crash, unlike a Java exception handler. */
final class ExitDiagnostics {
    static String describe(Context context, File diagnostic) {
        if (Build.VERSION.SDK_INT < 30) return "\nENCERRAMENTO: disponível pelo sistema a partir do Android 11.\n";
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            for (ApplicationExitInfo exit : manager.getHistoricalProcessExitReasons(context.getPackageName(), 0, 10)) {
                if (!exit.getProcessName().endsWith(":guest")) continue;
                if (exit.getTimestamp() < diagnostic.lastModified() - 5000) continue;
                return "\nENCERRAMENTO REGISTRADO PELO ANDROID:\n"
                        + "Data: " + new Date(exit.getTimestamp()) + "\n"
                        + "PID: " + exit.getPid() + "\n"
                        + "Motivo: " + reason(exit.getReason()) + " (" + exit.getReason() + ")\n"
                        + "Status: " + exit.getStatus() + "\n"
                        + "Descrição: " + exit.getDescription() + "\n"
                        + saveTrace(context, exit);
            }
            return "\nENCERRAMENTO: nenhum registro recente do processo guest disponível.\n";
        } catch (Exception error) { return "\nENCERRAMENTO: consulta falhou: " + error + "\n"; }
    }
    @android.annotation.TargetApi(30)
    private static String saveTrace(Context context, ApplicationExitInfo exit) {
        // Android 12+ may return a binary protobuf tombstone. Preserve bytes without pretending it is text.
        try (InputStream input = exit.getTraceInputStream()) {
            if (input == null) return "Trace: não disponibilizado pelo Android.\n";
            File temporary = new File(context.getFilesDir(), "guest-exit-trace.part");
            int remaining = 2 * 1024 * 1024;
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                while (remaining > 0) {
                    int count = input.read(buffer, 0, Math.min(buffer.length, remaining));
                    if (count < 0) break;
                    output.write(buffer, 0, count);
                    remaining -= count;
                }
            }
            if (!temporary.renameTo(new File(context.getFilesDir(), "guest-exit-trace.bin")))
                return "Trace: não foi possível salvar o arquivo.\n";
            return "Trace bruto incluído no ZIP (até 2 MiB).\n";
        } catch (Exception error) { return "Trace: consulta falhou: " + error + "\n"; }
    }
    private static String reason(int code) {
        switch (code) {
            case 1: return "saída normal";
            case 2: return "encerramento por sinal";
            case 3: return "memória insuficiente";
            case 4: return "crash Java";
            case 5: return "crash nativo";
            case 6: return "aplicativo sem resposta (ANR)";
            case 10: return "ação do usuário ou force-stop";
            default: return "outro";
        }
    }
}
