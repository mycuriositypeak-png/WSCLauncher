package dev.wsc.launcher;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import java.util.Locale;

/** UI-only preferences: never change the guest context, ABI or mod settings. */
final class UiPreferences {
    static final String FILE = "launcher-ui";

    // ---- Hooking backend selection ----
    // Persisted under "hook_backend" in the launcher-ui SharedPreferences.
    // Although this affects the guest runtime (which Java hook library is used),
    // we keep it in launcher-ui for simplicity — it's a UI-driven choice.
    static final String KEY_HOOK_BACKEND = "hook_backend";
    static final int HOOK_AUTO = 0;
    static final int HOOK_PINE = 1;
    static final int HOOK_LSPLANT = 2;

    static int hookBackend(Context context) {
        return context.getSharedPreferences(FILE, 0).getInt(KEY_HOOK_BACKEND, HOOK_AUTO);
    }

    static int hookBackendLabel(int value) {
        switch (value) {
            case HOOK_PINE: return R.string.hook_backend_pine;
            case HOOK_LSPLANT: return R.string.hook_backend_lsplant;
            default: return R.string.hook_backend_auto;
        }
    }

    static String language(Context context) {
        android.content.SharedPreferences preferences = context.getSharedPreferences(FILE, 0);
        // Retain selections made with the previous Portuguese/System picker.
        String selected = preferences.getString("language", null);
        if (selected == null) return preferences.getBoolean("system-language", false) ? "system" : "pt-BR";
        switch (selected) {
            case "en": case "es": case "system": return selected;
            default: return "pt-BR";
        }
    }
    static int languageLabel(Context context) {
        switch (language(context)) {
            case "en": return R.string.language_en;
            case "es": return R.string.language_es;
            case "system": return R.string.follow_system;
            default: return R.string.language_pt;
        }
    }
    static Context localized(Context base) {
        String language = language(base);
        if (language.equals("system")) return base;
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocales(new LocaleList(Locale.forLanguageTag(language)));
        return base.createConfigurationContext(config);
    }
    static int theme(Context context) {
        return context.getSharedPreferences(FILE, 0).getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
    static boolean dynamic(Context context) {
        return context.getSharedPreferences(FILE, 0).getBoolean("dynamic", false);
    }
    static void prepare(AppCompatActivity activity) {
        activity.getDelegate().setLocalNightMode(theme(activity));
        if (dynamic(activity)) DynamicColors.applyToActivityIfAvailable(activity);
    }
    static void systemBars(AppCompatActivity activity) {
        boolean light = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                != Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView())
                .setAppearanceLightStatusBars(light);
        WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView())
                .setAppearanceLightNavigationBars(light);
        int surface = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorSurface, 0);
        activity.getWindow().setStatusBarColor(surface);
        activity.getWindow().setNavigationBarColor(surface);
    }
}
