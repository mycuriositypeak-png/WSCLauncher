package dev.wsc.launcher;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Retains in-flight imports/exports across rotation; disk and package scans never block the UI. */
public final class LauncherViewModel extends AndroidViewModel {
    static final class State {
        final GameStatus game;
        final List<ModSpec> mods;
        final Set<String> enabled;
        final boolean loaded;
        final int busy, notice, progress;
        final long event;
        final Exception error, launchBlock;
        State(GameStatus game, List<ModSpec> mods, Set<String> enabled, boolean loaded, int busy,
              int notice, long event, Exception error, Exception launchBlock) {
            this(game, mods, enabled, loaded, busy, notice, event, error, launchBlock, -1);
        }
        State(GameStatus game, List<ModSpec> mods, Set<String> enabled, boolean loaded, int busy,
              int notice, long event, Exception error, Exception launchBlock, int progress) {
            this.game = game; this.mods = mods; this.enabled = enabled; this.loaded = loaded; this.busy = busy;
            this.notice = notice; this.event = event; this.error = error; this.launchBlock = launchBlock;
            this.progress = progress;
        }
    }
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<State> states = new MutableLiveData<>();
    private ModStore store;
    private State current = new State(GameStatus.missing(), Collections.emptyList(), Collections.emptySet(), false, 0, 0, 0, null, null);
    private boolean cleared;
    public LauncherViewModel(Application application) { super(application); states.setValue(current); }
    LiveData<State> states() { return states; }
    State current() { return current; }
    private void publish(State state) { if (!cleared) { current = state; states.setValue(state); } }
    private void working(int label) {
        publish(new State(current.game, current.mods, current.enabled, current.loaded, label, 0, current.event, null, current.launchBlock));
    }
    private State read() throws Exception {
        if (store == null) store = new ModStore(getApplication().getFilesDir());
        GameStatus game = GameStatus.detect(getApplication());
        List<ModSpec> mods = Collections.unmodifiableList(store.list());
        Set<String> enabled = Collections.unmodifiableSet(store.enabledIds());
        Exception block = null;
        try {
            store.fingerprint();
            if (game.installed) {
                if (!game.supports(processAbi())) throw new IOException(game.mismatchMessage(getApplication(), processAbi()));
                validateArchitecture(store.active(game.versionCode));
            }
        } catch (Exception error) { block = error; }
        return new State(game, mods, enabled, true, 0, 0, 0, null, block);
    }
    void refresh() {
        if (cleared || current.busy != 0) return;
        working(R.string.busy_scan);
        worker.execute(() -> {
            try { State value = read(); main.post(() -> success(value, 0)); }
            catch (Exception error) { main.post(() -> failure(error, R.string.error_scan)); }
        });
    }
    private void success(State value, int notice) {
        publish(new State(value.game, value.mods, value.enabled, value.loaded, 0, notice,
                current.event + (notice == 0 ? 0 : 1), null, value.launchBlock));
    }
    private void failure(Exception error, int title) {
        if (cleared) return;
        // Scan/launch failures block retry until refresh; failed imports keep the prior valid selection.
        publish(new State(current.game, current.mods, current.enabled, current.loaded, 0, title,
                current.event + 1, error, title == R.string.error_title ? current.launchBlock : error));
        saveError(error);
    }
    private interface Work { void run() throws Exception; }
    private void mutate(int progress, int notice, Work work) {
        if (cleared || current.busy != 0) return;
        working(progress);
        worker.execute(() -> {
            try { work.run(); State value = read(); main.post(() -> success(value, notice)); }
            catch (Exception error) { main.post(() -> failure(error, R.string.error_title)); }
        });
    }
    void install(Uri source) {
        mutate(R.string.busy_import, R.string.import_success, () -> {
            if (store == null) store = new ModStore(getApplication().getFilesDir());
            try (InputStream input = getApplication().getContentResolver().openInputStream(source)) {
                if (input == null) throw new IOException(getApplication().getString(R.string.file_unavailable));
                store.install(input);
            }
        });
    }
    void toggle(ModSpec mod, boolean enabled) {
        mutate(R.string.busy_update, R.string.mod_changed, () -> {
            long version = enabled ? getApplication().getPackageManager().getPackageInfo(ModSpec.PACKAGE, 0).versionCode : -1;
            if (enabled) validateArchitecture(Collections.singletonList(mod));
            store.setEnabled(mod.id, enabled, version);
        });
    }
    void remove(ModSpec mod) { mutate(R.string.busy_update, R.string.mod_removed, () -> store.remove(mod.id)); }
    void export(Uri target) {
        mutate(R.string.busy_export, R.string.export_success, () -> {
            try (OutputStream output = getApplication().getContentResolver().openOutputStream(target)) {
                if (output == null) throw new IOException(getApplication().getString(R.string.destination_unavailable));
                DiagnosticArchive.write(getApplication(), output);
            }
        });
    }
    void exportGameData(Uri target, GameDataArchive.Mode mode) {
        if (cleared || current.busy != 0) return;
        working(R.string.busy_data_export);
        worker.execute(() -> {
            boolean destinationOpened = false;
            try {
                GuestSession.stopForExport(getApplication());
                GameDataArchive.Plan plan = GameDataArchive.scan(getApplication().getDataDir(), mode);
                try (OutputStream output = getApplication().getContentResolver().openOutputStream(target, "wt")) {
                    if (output == null) throw new IOException(getApplication().getString(R.string.destination_unavailable));
                    destinationOpened = true;
                    GameDataArchive.write(plan, output, getApplication().getPackageName(), percent ->
                            main.post(() -> exportProgress(percent)));
                }
                // Export does not depend on the installed game or valid mod manifests.
                main.post(() -> success(current, R.string.data_export_success));
            } catch (Exception error) {
                if (destinationOpened) {
                    // Remove a partial document rather than presenting it as a usable backup.
                    try { android.provider.DocumentsContract.deleteDocument(getApplication().getContentResolver(), target); }
                    catch (Exception ignored) { }
                }
                main.post(() -> failure(error, R.string.error_title));
            }
        });
    }
    private void exportProgress(int percent) {
        if (cleared || current.busy != R.string.busy_data_export) return;
        publish(new State(current.game, current.mods, current.enabled, current.loaded, current.busy, 0,
                current.event, null, current.launchBlock, percent));
    }
    interface LaunchReady { void ready(ModStore store); }
    void prepareLaunch(LaunchReady callback) {
        if (cleared || current.busy != 0 || !current.game.installed || !current.loaded) return;
        working(R.string.launching);
        worker.execute(() -> {
            try {
                GameStatus game = GameStatus.detect(getApplication());
                if (!game.installed) throw new IOException(getApplication().getString(R.string.game_missing));
                if (!game.supports(processAbi())) throw new IOException(game.mismatchMessage(getApplication(), processAbi()));
                validateArchitecture(store.active(game.versionCode));
                main.post(() -> { if (!cleared) callback.ready(store); });
            } catch (Exception error) { main.post(() -> failure(error, R.string.error_launch)); }
        });
    }
    void launchFinished(Exception error) {
        if (cleared) return;
        if (error != null) failure(error, R.string.error_launch);
        else { success(current, 0); refresh(); }
    }
    ModStore store() { return store; }
    static String processAbi() { return android.os.Process.is64Bit() ? "arm64-v8a" : "armeabi-v7a"; }
    private static void validateArchitecture(List<ModSpec> mods) throws IOException {
        for (ModSpec mod : mods) if (mod.libraryFor(processAbi()) == null)
            throw new IOException(mod.name + ": " + processAbi());
    }
    private void saveError(Exception error) {
        // UI failures remain inspectable and included by the existing diagnostics exporter.
        worker.execute(() -> {
            File log = new File(getApplication().getFilesDir(), "guest-diagnostic.log");
            try (FileWriter out = new FileWriter(log, true)) {
                out.write("\nWSCLauncher 1.0 | UI | " + new Date() + "\n" + android.util.Log.getStackTraceString(error) + "\n");
            } catch (IOException ignored) { }
        });
    }
    @Override protected void onCleared() { cleared = true; worker.shutdown(); }
}
