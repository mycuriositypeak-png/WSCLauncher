package dev.wsc.launcher;

import android.app.Application;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowVMRuntime;
import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w393dp-h852dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LauncherUiTests {
    private Application app() { return RuntimeEnvironment.getApplication(); }
    private void installGame(String abi, int version) throws Exception {
        ShadowVMRuntime.setIs64Bit(true);
        File apk = new File(app().getCacheDir(), "test-installed-game.zip");
        // Synthetic UI package fixture, never loaded as a game or included in the output APK.
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(apk))) {
            zip.putNextEntry(new ZipEntry("lib/" + abi + "/libWorldSoccerChamps.so")); zip.write(new byte[]{0}); zip.closeEntry();
        }
        PackageInfo info = new PackageInfo(); info.packageName = ModSpec.PACKAGE; info.versionName = "2026.12"; info.versionCode = version;
        info.applicationInfo = new ApplicationInfo(); info.applicationInfo.packageName = ModSpec.PACKAGE;
        info.applicationInfo.sourceDir = apk.getAbsolutePath(); info.applicationInfo.flags = ApplicationInfo.FLAG_INSTALLED;
        shadowOf(app().getPackageManager()).installPackage(info);
    }
    private ModSpec installMod(boolean enabled) throws Exception {
        ModStore store = new ModStore(app().getFilesDir());
        ModSpec mod;
        try (InputStream input = new FileInputStream("../examples/player-roles/wsc-player-roles-picker-v0.4.zip")) { mod = store.install(input); }
        if (enabled) store.setEnabled(mod.id, true, 468); return mod;
    }
    private LauncherViewModel model(LauncherActivity activity) {
        return new ViewModelProvider(activity).get(LauncherViewModel.class);
    }
    private void await(LauncherActivity activity) throws Exception {
        long deadline = System.currentTimeMillis() + 10000;
        do {
            shadowOf(Looper.getMainLooper()).idle();
            if (model(activity).current().busy == 0 && model(activity).current().loaded) return;
            Thread.sleep(10);
        } while (System.currentTimeMillis() < deadline);
        fail("UI background work did not settle: " + model(activity).current().busy);
    }
    private ActivityController<LauncherActivity> open() throws Exception {
        ActivityController<LauncherActivity> controller = Robolectric.buildActivity(LauncherActivity.class).setup().visible();
        await(controller.get()); return controller;
    }
    private void tab(LauncherActivity activity, int id) {
        ((BottomNavigationView) activity.findViewById(R.id.bottom_navigation)).setSelectedItemId(id);
        shadowOf(Looper.getMainLooper()).idle();
    }
    private boolean text(View root, String expected) {
        if (root instanceof TextView && ((TextView) root).getText().toString().contains(expected)) return true;
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) if (text(group.getChildAt(i), expected)) return true;
        }
        return false;
    }
    private void previewIcon(LauncherActivity activity, String name) throws Exception {
        android.graphics.drawable.Drawable icon = activity.getDrawable(R.mipmap.ic_launcher);
        Bitmap bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        icon.setBounds(0, 0, 256, 256); icon.draw(new Canvas(bitmap));
        File directory = new File("build/ui-previews"); directory.mkdirs();
        try (OutputStream out = new FileOutputStream(new File(directory, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
    }
    private void preview(LauncherActivity activity, String name) throws Exception {
        View root = activity.findViewById(R.id.launcher_root);
        root.measure(View.MeasureSpec.makeMeasureSpec(393, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(804, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 393, 804);
        Bitmap bitmap = Bitmap.createBitmap(393, 804, Bitmap.Config.ARGB_8888); root.draw(new Canvas(bitmap));
        File directory = new File("build/ui-previews"); assertTrue(directory.isDirectory() || directory.mkdirs());
        try (OutputStream out = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
        bitmap.recycle();
    }
    @Test public void missingGameDisablesPlayAndKeepsAllThreeTabsAvailable() throws Exception {
        try (ActivityController<LauncherActivity> controller = open()) {
            LauncherActivity activity = controller.get();
            assertFalse(activity.findViewById(R.id.play_button).isEnabled());
            assertTrue(text(activity.findViewById(R.id.page_host), activity.getString(R.string.game_missing)));
            preview(activity, "missing-game");
            BottomNavigationView nav = activity.findViewById(R.id.bottom_navigation); assertEquals(3, nav.getMenu().size());
            tab(activity, R.id.nav_mods); assertTrue(text(activity.findViewById(R.id.page_host), activity.getString(R.string.mods_empty_title)));
            assertTrue(activity.findViewById(R.id.add_mod_button).isEnabled()); preview(activity, "empty-mods");
            tab(activity, R.id.nav_settings); assertTrue(text(activity.findViewById(R.id.page_host), activity.getString(R.string.language)));
            assertEquals(View.VISIBLE, nav.getVisibility());
        }
    }
    @Test public void foregroundRefreshDetectsInstallationAndUninstallationWithoutClickingPlay() throws Exception {
        try (ActivityController<LauncherActivity> controller = open()) {
            LauncherActivity activity = controller.get(); assertFalse(activity.findViewById(R.id.play_button).isEnabled());
            controller.pause(); installGame("arm64-v8a", 468); controller.resume(); await(activity);
            assertTrue(activity.findViewById(R.id.play_button).isEnabled());
            assertTrue(text(activity.findViewById(R.id.page_host), "0 mods ativos"));
            assertTrue(text(activity.findViewById(R.id.page_host), "2026.12"));
            assertTrue(text(activity.findViewById(R.id.page_host), "ARM64")); preview(activity, "installed-no-mods");
            controller.pause(); shadowOf(app().getPackageManager()).removePackage(ModSpec.PACKAGE); controller.resume(); await(activity);
            assertFalse(activity.findViewById(R.id.play_button).isEnabled());
        }
    }
    @Test public void playerRolesActiveStateAndSwitchUseTheExistingStore() throws Exception {
        installGame("arm64-v8a", 468); ModSpec mod = installMod(true);
        try (ActivityController<LauncherActivity> controller = open()) {
            LauncherActivity activity = controller.get(); assertTrue(activity.findViewById(R.id.play_button).isEnabled());
            assertTrue(text(activity.findViewById(R.id.page_host), "1 mod ativo"));
            tab(activity, R.id.nav_mods);
            MaterialSwitch toggle = activity.findViewById(R.id.page_host).findViewWithTag(mod.id).findViewById(R.id.mod_switch); assertTrue(toggle.isChecked());
            toggle.performClick(); await(activity);
            assertFalse(((MaterialSwitch) activity.findViewById(R.id.page_host).findViewWithTag(mod.id).findViewById(R.id.mod_switch)).isChecked());
            assertFalse(new ModStore(app().getFilesDir()).enabledIds().contains(mod.id)); preview(activity, "inactive-mod");
        }
    }
    @Test public void inactiveAndIncompatibleModsRemainVisibleAndCannotBeActivated() throws Exception {
        installGame("arm64-v8a", 469); ModSpec mod = installMod(false);
        try (ActivityController<LauncherActivity> controller = open()) {
            LauncherActivity activity = controller.get(); assertTrue(activity.findViewById(R.id.play_button).isEnabled());
            tab(activity, R.id.nav_mods);
            MaterialSwitch toggle = activity.findViewById(R.id.page_host).findViewWithTag(mod.id).findViewById(R.id.mod_switch);
            assertFalse(toggle.isChecked()); assertFalse(toggle.isEnabled());
            assertTrue(text(activity.findViewById(R.id.page_host), "468"));
        }
    }
    @Test public void gameArchitectureMismatchAndActiveVersionMismatchBlockPlay() throws Exception {
        installGame("armeabi-v7a", 468);
        try (ActivityController<LauncherActivity> controller = open()) {
            assertFalse(controller.get().findViewById(R.id.play_button).isEnabled());
            assertTrue(text(controller.get().findViewById(R.id.page_host), "Instale a versão ARM32"));
            preview(controller.get(), "architecture-mismatch-arm32-game");
        }
        installGame("arm64-v8a", 469); installMod(true);
        try (ActivityController<LauncherActivity> controller = open()) {
            assertFalse(controller.get().findViewById(R.id.play_button).isEnabled());
        }
    }
    @Test public void arm32ProcessRecommendsArm64LauncherForAnArm64Game() throws Exception {
        installGame("arm64-v8a", 468); ShadowVMRuntime.setIs64Bit(false);
        try (ActivityController<LauncherActivity> controller = open()) {
            assertFalse(controller.get().findViewById(R.id.play_button).isEnabled());
            assertTrue(text(controller.get().findViewById(R.id.page_host), "Instale a versão ARM64"));
            preview(controller.get(), "architecture-mismatch-arm64-game");
        }
    }
    @Test public void dualAbiPlayerRolesCanActivateInAnArm32Process() throws Exception {
        installGame("armeabi-v7a", 468); ShadowVMRuntime.setIs64Bit(false); ModSpec mod = installMod(false);
        try (ActivityController<LauncherActivity> controller = open()) {
            LauncherActivity activity = controller.get();
            assertTrue(activity.findViewById(R.id.play_button).isEnabled()); tab(activity, R.id.nav_mods);
            assertTrue(text(activity.findViewById(R.id.page_host), "ARM64 disponível"));
            assertTrue(text(activity.findViewById(R.id.page_host), "ARM32 disponível"));
            MaterialSwitch toggle = activity.findViewById(R.id.page_host).findViewWithTag(mod.id).findViewById(R.id.mod_switch);
            assertTrue(toggle.isEnabled()); toggle.performClick(); await(activity);
            assertTrue(new ModStore(app().getFilesDir()).enabledIds().contains(mod.id));
            tab(activity, R.id.nav_home); assertTrue(activity.findViewById(R.id.play_button).isEnabled());
            preview(activity, "arm32-game-active-mod");
        }
    }
    @Test public void realPlayerRolesZipImportsDisabledAndRemovesWithoutDeletingGameData() throws Exception {
        installGame("arm64-v8a", 468);
        File save = new File(app().getFilesDir(), "simulated-save.txt");
        try (FileWriter out = new FileWriter(save)) { out.write("keep"); }
        try (ActivityController<LauncherActivity> controller = open()) {
            LauncherActivity activity = controller.get();
            model(activity).install(Uri.fromFile(new File("../examples/player-roles/wsc-player-roles-picker-v0.4.zip").getCanonicalFile()));
            assertFalse(activity.findViewById(R.id.play_button).isEnabled()); await(activity);
            assertEquals(1, model(activity).current().mods.size()); assertTrue(model(activity).current().enabled.isEmpty());
            ModSpec mod = model(activity).current().mods.get(0); model(activity).remove(mod); await(activity);
            assertTrue(new ModStore(app().getFilesDir()).list().isEmpty()); assertTrue(save.isFile());
        }
    }
    @Test public void busyImportSurvivesRecreationAndDisablesConcurrentActions() throws Exception {
        installGame("arm64-v8a", 468);
        try (ActivityController<LauncherActivity> controller = open()) {
            LauncherActivity activity = controller.get(); tab(activity, R.id.nav_mods);
            LauncherViewModel retained = model(activity);
            retained.install(Uri.fromFile(new File("../examples/player-roles/wsc-player-roles-picker-v0.4.zip").getCanonicalFile()));
            assertFalse(activity.findViewById(R.id.add_mod_button).isEnabled()); preview(activity, "busy-import");
            controller.recreate(); await(controller.get());
            assertSame(retained, model(controller.get())); assertEquals(1, retained.current().mods.size());
        }
    }
    @Test public void logsAndLicenseDocumentsOpenAndContainUsefulSelectableText() throws Exception {
        try (ActivityController<DocumentActivity> controller = Robolectric.buildActivity(DocumentActivity.class,
                new Intent(app(), DocumentActivity.class).putExtra("document", "logs")).setup()) {
            long deadline = System.currentTimeMillis() + 5000;
            while (!text(controller.get().getWindow().getDecorView(), "Nenhuma tentativa") && System.currentTimeMillis() < deadline) {
                shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10);
            }
            assertTrue(text(controller.get().getWindow().getDecorView(), "Nenhuma tentativa"));
        }
        try (ActivityController<DocumentActivity> controller = Robolectric.buildActivity(DocumentActivity.class,
                new Intent(app(), DocumentActivity.class).putExtra("document", "licenses")).setup()) {
            long deadline = System.currentTimeMillis() + 5000;
            while (!text(controller.get().getWindow().getDecorView(), "GNU GENERAL PUBLIC LICENSE") && System.currentTimeMillis() < deadline) {
                shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10);
            }
            assertTrue(text(controller.get().getWindow().getDecorView(), "GNU GENERAL PUBLIC LICENSE"));
        }
    }
    @Test @Config(sdk = 24) @GraphicsMode(GraphicsMode.Mode.LEGACY)
    public void materialNavigationWorksOnMinimumSupportedAndroid() throws Exception {
        try (ActivityController<LauncherActivity> controller = open()) {
            LauncherActivity activity = controller.get();
            assertFalse(activity.findViewById(R.id.play_button).isEnabled());
            tab(activity, R.id.nav_settings);
            assertTrue(text(activity.findViewById(R.id.page_host), activity.getString(R.string.dynamic_unavailable)));
            previewIcon(activity, "launcher-icon-android24");
        }
    }
    @Test public void diagnosticsExportUsesOriginalArchiveAndFailedImportKeepsValidPlayState() throws Exception {
        installGame("arm64-v8a", 468);
        try (ActivityController<LauncherActivity> controller = open()) {
            LauncherActivity activity = controller.get();
            File bad = new File(app().getCacheDir(), "invalid.zip");
            try (FileWriter out = new FileWriter(bad)) { out.write("invalid"); }
            model(activity).install(Uri.fromFile(bad)); await(activity);
            assertNotNull(model(activity).current().error);
            assertTrue(activity.findViewById(R.id.play_button).isEnabled());
            File output = new File(app().getCacheDir(), "diagnostics.zip");
            model(activity).export(Uri.fromFile(output)); await(activity);
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(output)) {
                assertNotNull(zip.getEntry("device-and-exit.txt"));
                assertNotNull(zip.getEntry("mods-installed.txt"));
                assertNotNull(zip.getEntry("guest-diagnostic.log"));
            }
        }
    }
    private View findText(View root, String expected) {
        if (root instanceof TextView && ((TextView) root).getText().toString().equals(expected)) return root;
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), expected); if (found != null) return found;
            }
        }
        return null;
    }
    private void clickRow(LauncherActivity activity, int label) {
        View view = findText(activity.findViewById(R.id.page_host), activity.getString(label)); assertNotNull(view);
        while (!view.isClickable()) view = (View) view.getParent(); view.performClick();
    }
    private android.widget.ListView findList(View root) {
        if (root instanceof android.widget.ListView) return (android.widget.ListView) root;
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.ListView found = findList(group.getChildAt(i)); if (found != null) return found;
            }
        }
        return null;
    }
    private void selectModMenu(LauncherActivity activity, ModSpec mod, int action) {
        activity.findViewById(R.id.page_host).findViewWithTag(mod.id).findViewById(R.id.mod_more).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        org.robolectric.shadows.ShadowWindowManagerImpl windows = org.robolectric.shadow.api.Shadow.extract(activity.getWindowManager());
        for (View window : windows.getViews()) {
            android.widget.ListView list = findList(window); if (list == null) continue;
            for (int i = 0; i < list.getAdapter().getCount(); i++) {
                Object item = list.getAdapter().getItem(i);
                if (item instanceof android.view.MenuItem && ((android.view.MenuItem) item).getItemId() == action) {
                    list.performItemClick(list.getAdapter().getView(i, null, list), i, list.getAdapter().getItemId(i)); return;
                }
            }
        }
        fail("Requested mod menu action was not displayed");
    }
    @Test public void modDetailsAndRemovalConfirmationUseMaterialDialogs() throws Exception {
        installGame("arm64-v8a", 468); ModSpec mod = installMod(true);
        try (ActivityController<LauncherActivity> controller = open()) {
            LauncherActivity activity = controller.get(); tab(activity, R.id.nav_mods);
            activity.findViewById(R.id.page_host).findViewWithTag(mod.id).performClick();
            androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertTrue(text(dialog.getWindow().getDecorView(), mod.version));
            findText(dialog.getWindow().getDecorView(), activity.getString(R.string.advanced)).performClick();
            assertTrue(text(dialog.getWindow().getDecorView(), mod.id));
            assertTrue(text(dialog.getWindow().getDecorView(), "arm64-v8a"));
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            selectModMenu(activity, mod, R.id.action_remove);
            dialog = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertTrue(text(dialog.getWindow().getDecorView(), activity.getString(R.string.remove_title, mod.name)));
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
            assertEquals(1, new ModStore(app().getFilesDir()).list().size());
            selectModMenu(activity, mod, R.id.action_remove);
            dialog = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick(); await(activity);
            assertTrue(text(activity.findViewById(R.id.page_host), activity.getString(R.string.mods_empty_title)));
        }
    }
    @Test @Config(qualifiers = "en-rUS-w393dp-h852dp-port-mdpi")
    public void themeAndLanguageChoicesPersistAndLeaveGuestLocaleAlone() throws Exception {
        try (ActivityController<LauncherActivity> controller = open()) {
            LauncherActivity activity = controller.get(); tab(activity, R.id.nav_settings); clickRow(activity, R.string.language);
            androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            dialog.getListView().performItemClick(dialog.getListView().getAdapter().getView(3, null, dialog.getListView()), 3, 3);
            controller.recreate(); await(controller.get()); activity = controller.get();
            assertEquals("system", UiPreferences.language(activity));
            assertEquals("Settings", activity.getString(R.string.nav_settings));
            assertEquals("en", app().getResources().getConfiguration().getLocales().get(0).getLanguage());
            tab(activity, R.id.nav_settings); clickRow(activity, R.string.theme);
            dialog = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            dialog.getListView().performItemClick(dialog.getListView().getAdapter().getView(2, null, dialog.getListView()), 2, 2);
            controller.recreate(); await(controller.get());
            assertEquals(AppCompatDelegate.MODE_NIGHT_YES, UiPreferences.theme(controller.get()));
            assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_YES,
                    controller.get().getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK);
        }
    }
    @Test @Config(qualifiers = "en-rUS-w393dp-h852dp-port-mdpi")
    public void explicitEnglishSpanishAndPortugueseChoicesUpdateScreensAndPersist() throws Exception {
        // An existing System selection survives the update to the three-language picker.
        app().getSharedPreferences(UiPreferences.FILE, 0).edit().putBoolean("system-language", true).commit();
        try (ActivityController<LauncherActivity> controller = open()) {
            assertEquals("system", UiPreferences.language(controller.get()));
            int[] choices = {1, 2, 0};
            String[] tags = {"en", "es", "pt-BR"};
            String[] settings = {"Settings", "Configuración", "Configurações"};
            String[] play = {"Play", "Jugar", "Jogar"};
            String[] missing = {"World Soccer Champs is not installed", "World Soccer Champs no está instalado", "World Soccer Champs não está instalado"};
            for (int i = 0; i < choices.length; i++) {
                LauncherActivity activity = controller.get(); tab(activity, R.id.nav_settings); clickRow(activity, R.string.language);
                androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
                assertEquals(4, dialog.getListView().getAdapter().getCount());
                int choice = choices[i];
                dialog.getListView().performItemClick(dialog.getListView().getAdapter().getView(choice, null, dialog.getListView()), choice, choice);
                controller.recreate(); await(controller.get()); activity = controller.get();
                assertEquals(tags[i], UiPreferences.language(activity));
                assertEquals(settings[i], activity.getString(R.string.nav_settings));
                assertEquals("en", app().getResources().getConfiguration().getLocales().get(0).getLanguage());
                tab(activity, R.id.nav_home); await(activity);
                assertEquals(play[i], ((TextView) activity.findViewById(R.id.play_button)).getText().toString());
                assertTrue(text(activity.findViewById(R.id.page_host), missing[i]));
                preview(activity, "locale-" + tags[i] + "-home");
                tab(activity, R.id.nav_settings); preview(activity, "locale-" + tags[i] + "-settings");
                if (tags[i].equals("es")) {
                    try (ActivityController<DocumentActivity> document = Robolectric.buildActivity(DocumentActivity.class,
                            new Intent(app(), DocumentActivity.class).putExtra("document", "logs")).setup()) {
                        long deadline = System.currentTimeMillis() + 5000;
                        while (!text(document.get().getWindow().getDecorView(), "No hay intentos registrados") && System.currentTimeMillis() < deadline) {
                            shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10);
                        }
                        assertTrue(text(document.get().getWindow().getDecorView(), "No hay intentos registrados"));
                    }
                }
            }
        }
    }
    @Test public void importButtonAndResultUseTheExistingDocumentPicker() throws Exception {
        try (ActivityController<LauncherActivity> controller = open()) {
            LauncherActivity activity = controller.get(); tab(activity, R.id.nav_mods);
            activity.findViewById(R.id.add_mod_button).performClick();
            org.robolectric.shadows.ShadowActivity.IntentForResult request = shadowOf(activity).getNextStartedActivityForResult();
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, request.intent.getAction()); assertEquals(41, request.requestCode);
            activity.onActivityResult(41, android.app.Activity.RESULT_OK,
                    new Intent().setData(Uri.fromFile(new File("../examples/player-roles/wsc-player-roles-picker-v0.4.zip").getCanonicalFile())));
            assertFalse(activity.findViewById(R.id.add_mod_button).isEnabled()); await(activity);
            assertEquals("wsc.player-roles-picker", model(activity).current().mods.get(0).id);
            assertTrue(model(activity).current().enabled.isEmpty());
        }
    }
    @Test public void renderLightAndDarkMainScreensForLocalInspection() throws Exception {
        installGame("arm64-v8a", 468); installMod(true);
        for (boolean dark : new boolean[]{false, true}) {
            app().getSharedPreferences(UiPreferences.FILE, 0).edit().putInt("theme", dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO).commit();
            try (ActivityController<LauncherActivity> controller = open()) {
                LauncherActivity activity = controller.get();
                previewIcon(activity, dark ? "launcher-icon-dark" : "launcher-icon-light");
                for (int tab : new int[]{R.id.nav_home, R.id.nav_mods, R.id.nav_settings}) {
                    tab(activity, tab); if (tab == R.id.nav_home) await(activity);
                    preview(activity, (dark ? "dark-" : "light-") +
                            (tab == R.id.nav_home ? "home" : tab == R.id.nav_mods ? "mods" : "settings"));
                }
            }
        }
    }
}
