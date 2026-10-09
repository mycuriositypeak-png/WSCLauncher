package dev.wsc.launcher;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.PopupMenu;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import java.util.*;

/** Material UI for the existing launcher; guest startup and mod format stay unchanged. */
public final class LauncherActivity extends AppCompatActivity {
    private LauncherViewModel model;
    private LauncherViewModel.State state;
    private UiViews ui;
    private FrameLayout pageHost;
    private MaterialToolbar toolbar;
    private BottomNavigationView navigation;
    private int selectedTab = R.id.nav_home;
    private long handledEvent;

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(UiPreferences.localized(base)); }
    @Override public void onCreate(Bundle saved) {
        UiPreferences.prepare(this); super.onCreate(saved); UiPreferences.systemBars(this);
        setContentView(R.layout.activity_launcher); ui = new UiViews(this);
        toolbar = findViewById(R.id.toolbar); pageHost = findViewById(R.id.page_host);
        navigation = findViewById(R.id.bottom_navigation);
        if (saved != null) { selectedTab = saved.getInt("tab", R.id.nav_home); handledEvent = saved.getLong("event"); }
        model = new ViewModelProvider(this, new ViewModelProvider.AndroidViewModelFactory(getApplication())).get(LauncherViewModel.class);
        state = model.current();
        navigation.setOnItemSelectedListener(item -> {
            boolean changed = selectedTab != item.getItemId(); selectedTab = item.getItemId();
            render();
            if (changed && selectedTab == R.id.nav_home) model.refresh();
            return true;
        });
        navigation.setSelectedItemId(selectedTab);
        model.states().observe(this, value -> {
            boolean progressOnly = state != null && state.busy == R.string.busy_data_export
                    && value.busy == state.busy && value.event == state.event;
            state = value;
            if (progressOnly) renderProgress(); else render();
            if (value.event > handledEvent) {
                handledEvent = value.event;
                if (value.error != null) errorNotice(value.notice, value.error);
                else if (value.notice != 0) Snackbar.make(pageHost, value.notice, Snackbar.LENGTH_LONG)
                        .setAnchorView(navigation).show();
            }
        });
    }
    @Override protected void onResume() { super.onResume(); if (model != null) model.refresh(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("tab", selectedTab); out.putLong("event", handledEvent); super.onSaveInstanceState(out);
    }
    private void render() {
        if (state == null || ui == null) return;
        pageHost.removeAllViews();
        toolbar.setTitle(selectedTab == R.id.nav_home ? R.string.app_name
                : selectedTab == R.id.nav_mods ? R.string.nav_mods : R.string.nav_settings);
        toolbar.getMenu().clear();
        if (selectedTab != R.id.nav_settings) {
            android.view.MenuItem refresh = toolbar.getMenu().add(0, R.id.action_refresh, 0, R.string.refresh);
            ui.menuIcon(refresh, R.drawable.ic_refresh); refresh.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
            toolbar.setOnMenuItemClickListener(item -> { if (state.busy == 0) model.refresh(); return true; });
            toolbar.getMenu().findItem(R.id.action_refresh).setEnabled(state.busy == 0);
        }
        renderProgress();
        if (selectedTab == R.id.nav_home) home();
        else if (selectedTab == R.id.nav_mods) mods();
        else settings();
    }
    private void renderProgress() {
        LinearProgressIndicator progress = findViewById(R.id.progress);
        boolean indeterminate = state.progress < 0;
        if (progress.isIndeterminate() != indeterminate) {
            progress.setVisibility(View.INVISIBLE); progress.setIndeterminate(indeterminate);
        }
        if (!indeterminate) progress.setProgressCompat(state.progress, false);
        progress.setVisibility(state.busy == 0 ? View.INVISIBLE : View.VISIBLE);
        TextView label = findViewById(R.id.progress_label);
        label.setVisibility(state.busy == 0 ? View.GONE : View.VISIBLE);
        if (state.busy != 0) label.setText(indeterminate ? getString(state.busy)
                : getString(R.string.data_export_progress, state.progress));
    }
    private boolean usable() { return state.loaded && state.busy == 0; }
    private String activeCount() { if (state.enabled.isEmpty()) return getString(R.string.no_active_mods); return getResources().getQuantityString(R.plurals.active_mods, state.enabled.size(), state.enabled.size()); }
    private void home() {
        LinearLayout page = ui.column();
        LinearLayout card = ui.column();
        LinearLayout header = ui.row();
        ImageView gameIcon = ui.icon(R.drawable.ic_sports_esports, 60);
        gameIcon.setBackgroundResource(R.drawable.icon_tile); gameIcon.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
        if (state.game.icon != null) { gameIcon.setImageDrawable(state.game.icon); gameIcon.setImageTintList(null); gameIcon.setPadding(0, 0, 0, 0); }
        header.addView(gameIcon);
        LinearLayout words = ui.column(); LinearLayout.LayoutParams weighted = new LinearLayout.LayoutParams(0, -2, 1);
        weighted.setMarginStart(ui.dp(14)); header.addView(words, weighted);
        ui.add(words, ui.text(getText(R.string.game_name), com.google.android.material.R.style.TextAppearance_Material3_TitleLarge), 6);
        ui.add(words, ui.body(getText(state.game.installed ? R.string.game_installed : R.string.game_missing)), 0);
        ui.add(card, header, 16);
        if (state.game.installed) {
            String version = state.game.versionName == null || state.game.versionName.isEmpty() ? getString(R.string.unknown_version) : state.game.versionName;
            ui.add(card, ui.body(getString(R.string.game_version, version, state.game.versionCode)), 10);
        } else ui.add(card, ui.body(getText(R.string.game_missing_hint)), 10);
        ChipGroup statuses = new ChipGroup(this);
        statuses.addView(ui.chip(activeCount(), false));
        if (state.game.installed) {
            String architecture = state.game.abis.isEmpty() ? getString(R.string.unknown_abi) : displayAbis(state.game.abis);
            statuses.addView(ui.chip(architecture, !state.game.supports(LauncherViewModel.processAbi())));
            statuses.addView(ui.chip(getText(!state.game.supports(LauncherViewModel.processAbi()) ? R.string.abi_mismatch
                    : state.game.referenceProfile() ? R.string.profile_recognized : R.string.profile_unvalidated), !state.game.supports(LauncherViewModel.processAbi())));
        }
        ui.add(card, statuses, 12);
        if (state.game.installed) ui.add(card, ui.body(state.game.supports(LauncherViewModel.processAbi())
                ? getText(R.string.profile_note) : state.game.mismatchMessage(this, LauncherViewModel.processAbi())), 16);
        boolean restart = false;
        try { if (usable() && model.store() != null) restart = GuestSession.needsRestart(this, model.store()); }
        catch (Exception error) { /* The state already blocks invalid selections; detailed errors remain in diagnostics. */ }
        MaterialButton play = ui.button(restart ? R.string.restart_game : R.string.play, R.drawable.ic_play_arrow, this::play);
        play.setId(R.id.play_button);
        play.setEnabled(usable() && state.game.installed && state.game.supports(LauncherViewModel.processAbi()) && state.launchBlock == null);
        ui.add(card, play, 0);
        MaterialCardView gameCard = ui.card(card);
        gameCard.setCardBackgroundColor(ui.color(com.google.android.material.R.attr.colorSurface));
        gameCard.setRadius(getResources().getDimension(R.dimen.game_card_radius)); gameCard.setCardElevation(ui.dp(1));
        ui.add(page, gameCard, 16);
        if (state.launchBlock != null) {
            LinearLayout problem = ui.column(); ui.add(problem, ui.title(getText(state.game.installed && !state.game.supports(LauncherViewModel.processAbi())
                    ? R.string.abi_mismatch : R.string.error_launch)), 8);
            ui.add(problem, ui.button(R.string.view_details, R.drawable.ic_info, () -> showErrorDetails(state.launchBlock)), 0);
            ui.add(page, ui.card(problem), 16);
        }
        LinearLayout mods = ui.column(); ui.add(mods, ui.title(getText(R.string.nav_mods)), 4);
        ui.add(mods, ui.body(activeCount()), 12);
        ui.add(mods, ui.button(R.string.manage_mods, R.drawable.ic_extension, () -> navigation.setSelectedItemId(R.id.nav_mods)), 0);
        ui.add(page, ui.card(mods), 16);
        LinearLayout note = ui.column(); ui.add(note, ui.title(getText(R.string.sandbox_title)), 8);
        ui.add(note, ui.body(getText(R.string.sandbox_note)), 0); ui.add(page, ui.card(note), 0);
        pageHost.addView(ui.page(page));
    }
    private void play() {
        if (!usable() || !state.game.installed) return;
        model.prepareLaunch(store -> {
            if (isFinishing() || isDestroyed()) { model.launchFinished(null); return; }
            try { GuestSession.launch(this, store, () -> model.launchFinished(null)); }
            catch (Exception error) { model.launchFinished(error); }
        });
    }
    private void mods() {
        LinearLayout page = ui.column();
        String counts = (state.mods.isEmpty() ? getString(R.string.no_installed_mods) : getResources().getQuantityString(R.plurals.mods_installed, state.mods.size(), state.mods.size())) + " · " + activeCount();
        ui.heading(page, R.string.nav_mods, counts);
        if (state.mods.isEmpty()) {
            LinearLayout empty = ui.column(); empty.setGravity(Gravity.CENTER_HORIZONTAL);
            ui.gap(empty, 16); empty.addView(ui.icon(R.drawable.ic_extension, 48)); ui.gap(empty, 16);
            TextView title = ui.title(getText(R.string.mods_empty_title)); title.setGravity(Gravity.CENTER); ui.add(empty, title, 8);
            TextView note = ui.body(getText(R.string.mods_empty_note)); note.setGravity(Gravity.CENTER); ui.add(empty, note, 24);
            MaterialButton add = ui.button(R.string.add_mod, R.drawable.ic_add, this::pickMod); add.setId(R.id.add_mod_button); add.setEnabled(usable());
            ui.add(empty, add, 8); ui.add(page, ui.card(empty), 16);
        } else {
            ui.add(page, ui.body(getText(R.string.mods_restart_note)), 16);
            for (ModSpec mod : state.mods) modItem(page, mod);
            ui.gap(page, 88);
        }
        pageHost.addView(ui.page(page));
        if (!state.mods.isEmpty()) {
            ExtendedFloatingActionButton add = new ExtendedFloatingActionButton(this);
            add.setText(R.string.add_mod); add.setIconResource(R.drawable.ic_add); add.setId(R.id.add_mod_button);
            add.setEnabled(usable()); add.setOnClickListener(v -> pickMod());
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
            params.setMargins(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16)); pageHost.addView(add, params);
        }
    }
    private void modItem(LinearLayout page, ModSpec mod) {
        MaterialCardView card = (MaterialCardView) getLayoutInflater().inflate(R.layout.item_mod, page, false);
        card.setTag(mod.id); card.setOnClickListener(v -> details(mod));
        ((TextView) card.findViewById(R.id.mod_name)).setText(mod.name);
        ((TextView) card.findViewById(R.id.mod_version)).setText(getString(R.string.mod_version, mod.version));
        ((TextView) card.findViewById(R.id.mod_description)).setText(mod.description.isEmpty() ? getString(R.string.no_description) : mod.description);
        boolean active = state.enabled.contains(mod.id);
        MaterialSwitch toggle = card.findViewById(R.id.mod_switch); toggle.setChecked(active);
        toggle.setContentDescription(getString(R.string.mod_toggle, mod.name));
        toggle.setEnabled(usable() && (active || compatibilityCode(mod) == R.string.compatible));
        toggle.setOnCheckedChangeListener((button, checked) -> model.toggle(mod, checked));
        ImageButton more = card.findViewById(R.id.mod_more); more.setContentDescription(getString(R.string.mod_menu, mod.name)); more.setEnabled(usable());
        more.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, more);
            menu.setForceShowIcon(true);
            ui.menuIcon(menu.getMenu().add(0, R.id.action_details, 0, R.string.details), R.drawable.ic_info);
            ui.menuIcon(menu.getMenu().add(0, R.id.action_remove, 1, R.string.remove), R.drawable.ic_delete);
            menu.setOnMenuItemClickListener(item -> { if (item.getItemId() == R.id.action_remove) confirmRemove(mod); else details(mod); return true; });
            menu.show();
        });
        ChipGroup chips = card.findViewById(R.id.mod_chips);
        chips.addView(ui.chip(getText(active ? R.string.mod_active : R.string.mod_inactive), false));
        chips.addView(ui.chip(compatibility(mod), compatibilityCode(mod) != R.string.compatible));
        for (String abi : new String[]{"arm64-v8a", "armeabi-v7a"}) {
            boolean supported = mod.libraryFor(abi) != null;
            chips.addView(ui.chip(getString(supported ? R.string.mod_abi_present : R.string.mod_abi_absent,
                    abi.equals("arm64-v8a") ? "ARM64" : "ARM32"), !supported && abi.equals(LauncherViewModel.processAbi())));
        }
        ui.add(page, card, 12);
    }
    private int compatibilityCode(ModSpec mod) {
        if (!state.game.installed) return R.string.requires_game;
        if (mod.gameVersion != state.game.versionCode) return R.string.requires_version;
        if (!state.game.supports(LauncherViewModel.processAbi())) return R.string.abi_mismatch;
        if (mod.libraryFor(LauncherViewModel.processAbi()) == null) return R.string.requires_abi;
        for (ModSpec other : state.mods) if (!other.id.equals(mod.id) && state.enabled.contains(other.id))
            for (String claim : mod.claims) if (other.claims.contains(claim)) return R.string.claims_conflict;
        return R.string.compatible;
    }
    private String compatibility(ModSpec mod) {
        int code = compatibilityCode(mod);
        return code == R.string.requires_version ? getString(code, mod.gameVersion)
                : code == R.string.requires_abi ? getString(code, LauncherViewModel.processAbi()) : getString(code);
    }
    private void details(ModSpec mod) {
        LinearLayout body = ui.column();
        ui.metadata(body, R.string.version, mod.version);
        ui.metadata(body, R.string.description, mod.description.isEmpty() ? getText(R.string.no_description) : mod.description);
        ui.metadata(body, R.string.status, getText(state.enabled.contains(mod.id) ? R.string.mod_active : R.string.mod_inactive));
        ui.metadata(body, R.string.compatibility, compatibility(mod)); ui.add(body, ui.body(getText(R.string.hash_note)), 16);
        LinearLayout advanced = ui.column(); advanced.setVisibility(View.GONE);
        MaterialButton expand = ui.button(R.string.advanced, R.drawable.ic_code, () -> advanced.setVisibility(advanced.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        ui.add(body, expand, 16);
        ui.metadata(advanced, R.string.mod_id, mod.id); ui.metadata(advanced, R.string.required_wsc, Long.toString(mod.gameVersion));
        List<String> abis = new ArrayList<>(); for (ModSpec.NativeLibrary lib : mod.nativeLibraries) abis.add(lib.abi);
        ui.metadata(advanced, R.string.architectures, String.join(", ", abis));
        ui.metadata(advanced, R.string.native_abi, getText(R.string.native_abi_v1));
        ui.metadata(advanced, R.string.claims, String.join("\n", mod.claims)); ui.add(body, advanced, 0);
        new MaterialAlertDialogBuilder(this).setTitle(mod.name).setView(ui.page(body)).setPositiveButton(R.string.close, null).show();
    }
    private void confirmRemove(ModSpec mod) {
        new MaterialAlertDialogBuilder(this).setTitle(getString(R.string.remove_title, mod.name))
                .setMessage(R.string.remove_message).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.remove, (dialog, which) -> { if (usable()) model.remove(mod); }).show();
    }
    private void pickMod() {
        if (!usable()) return;
        Intent document = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        document.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        try { startActivityForResult(document, 41); } catch (Exception error) { errorNotice(R.string.error_title, error); }
    }
    private void pickExport() {
        if (!usable()) return;
        Intent document = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip");
        document.putExtra(Intent.EXTRA_TITLE, "wsc-diagnostico.zip");
        try { startActivityForResult(document, 40); } catch (Exception error) { errorNotice(R.string.error_title, error); }
    }
    private void chooseDataExport() {
        if (state.busy != 0) return;
        GameDataArchive.Mode[] modes = {GameDataArchive.Mode.FULL, GameDataArchive.Mode.LIGHT};
        int[] selected = {1};
        LinearLayout details = ui.column();
        details.setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0);
        TextView description = ui.body(getText(R.string.data_export_light_note));
        ui.add(details, description, 12); ui.add(details, ui.body(getText(R.string.data_export_save_note)), 0);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.export_game_data)
                .setSingleChoiceItems(new String[]{getString(R.string.data_export_full), getString(R.string.data_export_light)}, 1,
                        (dialog, which) -> {
                            selected[0] = which;
                            description.setText(which == 0 ? R.string.data_export_full_note : R.string.data_export_light_note);
                        })
                .setView(details).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.data_export_choose_destination, (dialog, which) -> pickGameDataExport(modes[selected[0]])).show();
    }
    private void pickGameDataExport(GameDataArchive.Mode mode) {
        if (state.busy != 0) return;
        String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        String name = "wsc-dados-" + (mode == GameDataArchive.Mode.FULL ? "completos-" : "leves-") + stamp + ".zip";
        Intent document = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip");
        document.putExtra(Intent.EXTRA_TITLE, name);
        try { startActivityForResult(document, mode == GameDataArchive.Mode.FULL ? 42 : 43); }
        catch (Exception error) { errorNotice(R.string.error_title, error); }
    }
    private void settings() {
        LinearLayout page = ui.column();
        ui.section(page, R.string.section_appearance);
        LinearLayout appearance = ui.column();
        ui.add(appearance, ui.setting(R.drawable.ic_palette, R.string.theme, getText(themeLabel()), null, this::chooseTheme), 0);
        appearance.addView(ui.divider());
        ui.add(appearance, ui.setting(R.drawable.ic_language, R.string.language, getText(UiPreferences.languageLabel(this)), null, this::chooseLanguage), 0);
        appearance.addView(ui.divider());
        MaterialSwitch dynamic = new MaterialSwitch(this); dynamic.setChecked(UiPreferences.dynamic(this));
        dynamic.setContentDescription(getString(R.string.dynamic_colors)); dynamic.setEnabled(usable() && android.os.Build.VERSION.SDK_INT >= 31);
        dynamic.setOnCheckedChangeListener((v, checked) -> { getSharedPreferences(UiPreferences.FILE, 0).edit().putBoolean("dynamic", checked).apply(); recreate(); });
        ui.add(appearance, ui.setting(R.drawable.ic_color_lens, R.string.dynamic_colors,
                getText(android.os.Build.VERSION.SDK_INT >= 31 ? R.string.dynamic_colors_note : R.string.dynamic_unavailable), dynamic, null), 0);
        ui.add(page, appearance, 0);
        ui.section(page, R.string.section_game_data);
        View gameData = ui.setting(R.drawable.ic_download, R.string.export_game_data, getText(R.string.export_game_data_note), null, this::chooseDataExport);
        gameData.setId(R.id.export_game_data_button); gameData.setEnabled(state.busy == 0);
        ui.add(page, gameData, 0);
        ui.section(page, R.string.section_diagnostics);
        LinearLayout diagnostics = ui.column();
        ui.add(diagnostics, ui.setting(R.drawable.ic_article, R.string.view_logs, getText(R.string.view_logs_note), null, () -> document("logs")), 0);
        diagnostics.addView(ui.divider());
        View export = ui.setting(R.drawable.ic_download, R.string.export_diagnostic, getText(R.string.export_note), null, this::pickExport);
        export.setEnabled(usable()); ui.add(diagnostics, export, 0); diagnostics.addView(ui.divider());
        View update = ui.setting(R.drawable.ic_refresh, R.string.update_diagnostic, getText(R.string.update_note), null, () -> document("logs"));
        update.setEnabled(usable()); ui.add(diagnostics, update, 0); ui.add(page, diagnostics, 0);
        ui.section(page, R.string.section_app);
        LinearLayout app = ui.column();
        ui.add(app, ui.setting(R.drawable.ic_info, R.string.about, getString(R.string.version_label, getVersion()), null,
                () -> new MaterialAlertDialogBuilder(this).setTitle(R.string.app_name).setMessage(getString(R.string.about_message, getVersion())).setPositiveButton(R.string.close, null).show()), 0);
        app.addView(ui.divider());
        ui.add(app, ui.setting(R.drawable.ic_article, R.string.licenses, getText(R.string.licenses_note), null, () -> document("licenses")), 0);
        ui.add(page, app, 16); ui.add(page, ui.body(getText(R.string.privacy_note)), 0);
        pageHost.addView(ui.page(page));
    }
    private void document(String mode) { startActivity(new Intent(this, DocumentActivity.class).putExtra("document", mode)); }
    private String getVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception ignored) { return "1.0"; }
    }
    private int themeLabel() {
        int mode = UiPreferences.theme(this);
        return mode == AppCompatDelegate.MODE_NIGHT_YES ? R.string.theme_dark
                : mode == AppCompatDelegate.MODE_NIGHT_NO ? R.string.theme_light : R.string.follow_system;
    }
    private void chooseTheme() {
        if (!usable()) return;
        int[] modes = {AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES};
        String[] labels = {getString(R.string.follow_system), getString(R.string.theme_light), getString(R.string.theme_dark)};
        int index = UiPreferences.theme(this) == modes[1] ? 1 : UiPreferences.theme(this) == modes[2] ? 2 : 0;
        new MaterialAlertDialogBuilder(this).setTitle(R.string.theme).setSingleChoiceItems(labels, index, (dialog, which) -> {
            getSharedPreferences(UiPreferences.FILE, 0).edit().putInt("theme", modes[which]).apply(); dialog.dismiss(); recreate();
        }).setNegativeButton(R.string.cancel, null).show();
    }
    private void chooseLanguage() {
        if (!usable()) return;
        String[] languages = {"pt-BR", "en", "es", "system"};
        String[] labels = {getString(R.string.language_pt), getString(R.string.language_en),
                getString(R.string.language_es), getString(R.string.follow_system)};
        int selected = Arrays.asList(languages).indexOf(UiPreferences.language(this));
        new MaterialAlertDialogBuilder(this).setTitle(R.string.language).setSingleChoiceItems(labels, selected, (dialog, which) -> {
            getSharedPreferences(UiPreferences.FILE, 0).edit().putString("language", languages[which])
                    .remove("system-language").apply(); dialog.dismiss(); recreate();
        }).setNegativeButton(R.string.cancel, null).show();
    }
    private void errorNotice(int title, Exception error) {
        Snackbar.make(pageHost, title == 0 ? R.string.error_title : title, Snackbar.LENGTH_LONG)
                .setAnchorView(navigation).setAction(R.string.view_details, v -> showErrorDetails(error)).show();
    }
    private void showErrorDetails(Exception error) {
        TextView text = ui.body(android.util.Log.getStackTraceString(error)); text.setTextIsSelectable(true);
        text.setTypeface(android.graphics.Typeface.MONOSPACE);
        LinearLayout content = ui.column(); ui.add(content, text, 0);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.error_details).setView(ui.page(content))
                .setPositiveButton(R.string.view_logs, (d, w) -> document("logs")).setNegativeButton(R.string.close, null).show();
    }
    private static String displayAbis(Set<String> abis) {
        List<String> names = new ArrayList<>(); for (String abi : abis) names.add(abi.equals("arm64-v8a") ? "ARM64" : "ARM32");
        return String.join(" / ", names);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == 41) model.install(data.getData()); else if (request == 40) model.export(data.getData());
        else if (request == 42 || request == 43)
            model.exportGameData(data.getData(), request == 42 ? GameDataArchive.Mode.FULL : GameDataArchive.Mode.LIGHT);
    }
}
