package dev.wsc.launcher;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Selectable, scrollable logs and actual license texts; no technical output on Home. */
public final class DocumentActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView content;
    private LinearProgressIndicator progress;
    private boolean logs;
    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(UiPreferences.localized(base)); }
    @Override public void onCreate(Bundle saved) {
        UiPreferences.prepare(this); super.onCreate(saved); UiPreferences.systemBars(this);
        logs = !"licenses".equals(getIntent().getStringExtra("document"));
        UiViews ui = new UiViews(this);
        LinearLayout root = ui.column(); root.setBackgroundColor(ui.color(com.google.android.material.R.attr.colorSurface));
        MaterialToolbar toolbar = new MaterialToolbar(this); toolbar.setTitle(logs ? R.string.logs : R.string.licenses);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationIconTint(ui.color(com.google.android.material.R.attr.colorOnSurface));
        toolbar.setNavigationContentDescription(R.string.back);
        toolbar.setNavigationOnClickListener(v -> finish()); root.addView(toolbar, new LinearLayout.LayoutParams(-1, ui.dp(64)));
        if (logs) {
            android.view.MenuItem refresh = toolbar.getMenu().add(0, 1, 0, R.string.refresh);
            android.view.MenuItem copy = toolbar.getMenu().add(0, 2, 1, R.string.copy_logs);
            ui.menuIcon(refresh, R.drawable.ic_refresh); ui.menuIcon(copy, R.drawable.ic_content_copy);
            refresh.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM);
            copy.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM);
            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) load();
                else {
                    ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(getText(R.string.logs), content.getText()));
                    Snackbar.make(root, R.string.logs_copied, Snackbar.LENGTH_SHORT).show();
                }
                return true;
            });
        }
        progress = new LinearProgressIndicator(this); progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(-1, ui.dp(4)));
        LinearLayout page = ui.column();
        content = ui.body(""); content.setTextIsSelectable(true); content.setTypeface(Typeface.MONOSPACE); content.setTextSize(12);
        content.setLineSpacing(ui.dp(3), 1f); ui.add(page, content, 0);
        root.addView(ui.page(page), new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root); load();
    }
    private void load() {
        progress.setVisibility(View.VISIBLE);
        worker.execute(() -> {
            String result;
            try { result = logs ? readLogs() : readLicenses(); }
            catch (Exception error) { result = getString(R.string.document_missing) + "\n\n" + android.util.Log.getStackTraceString(error); }
            String value = result;
            runOnUiThread(() -> { if (!isDestroyed()) { content.setText(value); progress.setVisibility(View.INVISIBLE); } });
        });
    }
    private String readLogs() throws Exception {
        StringBuilder text = new StringBuilder();
        File diagnostic = new File(getFilesDir(), "guest-diagnostic.log");
        if (!diagnostic.isFile()) text.append(getString(R.string.logs_empty)).append('\n');
        else appendFile(text, diagnostic);
        text.append(ExitDiagnostics.describe(this, diagnostic));
        text.append("\n\n").append(getString(R.string.installed_mods)).append("\n");
        try { text.append(new ModStore(getFilesDir()).describe()); }
        catch (Exception error) { text.append(error).append('\n'); }
        text.append("\n").append(getString(R.string.runtime_mods)).append("\n");
        appendFile(text, new File(getFilesDir(), "mods-runtime.txt"));
        text.append("\n").append(getString(R.string.logs_note));
        return text.toString();
    }
    private static void appendFile(StringBuilder result, File file) throws IOException {
        if (!file.isFile()) return;
        try (InputStream input = new FileInputStream(file)) {
            result.append(new String(ModStore.read(input, 2 * 1024 * 1024), StandardCharsets.UTF_8)).append('\n');
        }
    }
    private String readLicenses() throws Exception {
        StringBuilder result = new StringBuilder();
        for (String file : new String[]{"THIRD_PARTY_NOTICES.txt", "MATERIAL-LICENSE.txt", "ICONS-LICENSE.txt",
                "ANDROIDX-LICENSE.txt", "PINE-NOTICE.txt", "SHADOWHOOK-NOTICE.txt", "LLVM-NOTICE.txt", "GPL-3.0.txt"}) {
            result.append(file).append("\n\n");
            try (InputStream input = getAssets().open("licenses/" + file)) {
                result.append(new String(ModStore.read(input, 512 * 1024), StandardCharsets.UTF_8)).append("\n\n");
            }
        }
        return result.toString();
    }
    @Override protected void onDestroy() { worker.shutdown(); super.onDestroy(); }
}
