package dev.wsc.launcher;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Window;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import java.util.Locale;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 35})
public class GuestContextTests {
    public static class GameActivity extends Activity {
        String libraryAtCreate;
        Resources resourcesAtCreate;
        void attachFixture(Context context) { attachBaseContext(context); }
        @Override protected void onCreate(Bundle saved) {
            super.onCreate(saved);
            try { libraryAtCreate = GuestRuntime.requireCocosLibrary(this); }
            catch (Exception error) { throw new IllegalStateException(error); }
            resourcesAtCreate = getResources();
        }
    }
    private Context host() {
        Context context = RuntimeEnvironment.getApplication();
        context.getApplicationInfo().flags &= ~ApplicationInfo.FLAG_DEBUGGABLE;
        return context;
    }
    private Context game(Context host, String library) {
        ApplicationInfo original = new ApplicationInfo();
        original.packageName = GuestRuntime.TARGET;
        original.uid = host.getApplicationInfo().uid + 100;
        original.sourceDir = "/data/app/game/base.apk";
        original.dataDir = "/data/user/0/" + GuestRuntime.TARGET;
        original.metaData = new Bundle();
        if (library != null) original.metaData.putString("android.app.lib_name", library);
        PackageInfo info = new PackageInfo(); info.packageName = original.packageName; info.applicationInfo = original;
        shadowOf(host.getPackageManager()).installPackage(info);
        // Distinct Resources object: retaining the Activity's old cache fails the test.
        Resources resources = new Resources(host.getAssets(), host.getResources().getDisplayMetrics(),
                new Configuration(host.getResources().getConfiguration()));
        return new ContextWrapper(host) {
            @Override public String getPackageName() { return original.packageName; }
            @Override public String getPackageCodePath() { return original.sourceDir; }
            @Override public ApplicationInfo getApplicationInfo() { return original; }
            @Override public Resources getResources() { return resources; }
            @Override public Context createConfigurationContext(Configuration override) {
                Configuration merged = new Configuration(resources.getConfiguration()); merged.updateFrom(override);
                Resources configured = new Resources(getAssets(), resources.getDisplayMetrics(), merged);
                return new ContextWrapper(this) {
                    @Override public Resources getResources() { return configured; }
                };
            }
        };
    }
    private Object field(Object object, Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    @Test public void actualActivityRepairsHostBaseAndCachedResourcesBeforeOnCreate() throws Exception {
        Context host = host(); Context game = game(host, "WorldSoccerChamps");
        try (ActivityController<GameActivity> controller = Robolectric.buildActivity(GameActivity.class)) {
            GameActivity activity = controller.get();
            assertNotNull(activity.getBaseContext()); // The real framework attachment has already run.
            assertEquals(host.getPackageName(), activity.getPackageName());
            assertThrows(IllegalStateException.class, () -> GuestRuntime.requireCocosLibrary(activity));
            Resources oldResources = activity.getResources();
            Resources.Theme oldTheme = activity.getTheme();
            LayoutInflater oldInflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            assertSame(oldResources, field(activity, ContextThemeWrapper.class, "mResources"));
            Window window = activity.getWindow(); Application application = activity.getApplication();
            Intent intent = activity.getIntent(); Object token = field(activity, Activity.class, "mToken");
            Object info = field(activity, Activity.class, "mActivityInfo");
            assertTrue(GuestContext.bindBeforeCreate(activity, GameActivity.class, game));
            assertEquals(0, host.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE);
            assertEquals("WorldSoccerChamps", GuestRuntime.requireCocosLibrary(activity));
            assertEquals(GuestRuntime.TARGET, activity.getPackageName());
            assertNotSame(oldResources, activity.getResources());
            assertSame(game.getResources(), activity.getResources());
            assertNotSame(oldTheme, activity.getTheme());
            assertNotSame(oldInflater, activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE));
            assertSame(window, activity.getWindow()); assertSame(application, activity.getApplication());
            assertSame(intent, activity.getIntent()); assertSame(token, field(activity, Activity.class, "mToken"));
            assertSame(info, field(activity, Activity.class, "mActivityInfo"));
            assertEquals(host.getFilesDir(), activity.getFilesDir());
            controller.create();
            assertEquals("WorldSoccerChamps", activity.libraryAtCreate);
            assertSame(game.getResources(), activity.resourcesAtCreate);
        }
    }
    @Test public void repeatedRepairDoesNotNestWrappersOrResetGuestTheme() throws Exception {
        Context host = host(); Context game = game(host, "WorldSoccerChamps");
        try (ActivityController<GameActivity> controller = Robolectric.buildActivity(GameActivity.class)) {
            GameActivity activity = controller.get();
            assertTrue(GuestContext.bindBeforeCreate(activity, GameActivity.class, game));
            Context wrapped = activity.getBaseContext(); Resources.Theme theme = activity.getTheme();
            assertFalse(GuestContext.bindBeforeCreate(activity, GameActivity.class, game));
            assertSame(wrapped, activity.getBaseContext()); assertSame(theme, activity.getTheme());
        }
    }
    @Test public void cachedOverrideConfigurationKeepsGameResourcesAndLocaleAfterRepair() throws Exception {
        Context host = host(); Context game = game(host, "WorldSoccerChamps");
        GameActivity activity = new GameActivity(); activity.attachFixture(host);
        Configuration override = new Configuration(); override.setLocale(new Locale("es"));
        activity.applyOverrideConfiguration(override);
        Resources oldResources = activity.getResources();
        assertTrue(GuestContext.bindBeforeCreate(activity, GameActivity.class, game));
        assertNotSame(oldResources, activity.getResources());
        assertEquals("es", activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
        assertEquals("WorldSoccerChamps", GuestRuntime.requireCocosLibrary(activity));
        assertEquals(GuestRuntime.TARGET, ((GuestContext) activity.getBaseContext()).createConfigurationContext(override).getPackageName());
    }
    @Test public void hostActivitiesAndInactiveGuestAreUntouched() throws Exception {
        Context host = host(); Context game = game(host, "WorldSoccerChamps");
        try (ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class)) {
            Activity activity = controller.get(); Context base = activity.getBaseContext();
            Resources resources = activity.getResources(); Resources.Theme theme = activity.getTheme();
            assertFalse(GuestContext.bindBeforeCreate(activity, GameActivity.class, game));
            assertFalse(GuestContext.bindBeforeCreate(activity, null, game));
            assertSame(base, activity.getBaseContext()); assertSame(resources, activity.getResources());
            assertSame(theme, activity.getTheme());
        }
    }
    @Test public void originalPackageMetadataAndHostStorageAreRetainedWithoutMutatingGame() throws Exception {
        Context host = host(); Context game = game(host, "WorldSoccerChamps");
        GuestContext wrapped = new GuestContext(host, game);
        assertEquals(game.getPackageCodePath(), wrapped.getPackageCodePath());
        assertEquals(game.getApplicationInfo().uid, wrapped.getApplicationInfo().uid);
        assertEquals(host.getApplicationInfo().dataDir, wrapped.getApplicationInfo().dataDir);
        assertEquals(host.getFilesDir(), wrapped.getFilesDir());
        assertEquals("/data/user/0/" + GuestRuntime.TARGET, game.getApplicationInfo().dataDir);
    }
    @Test public void missingLibraryMetadataIsRejectedBeforeCocos() {
        Context host = host(); Context game = game(host, null);
        assertThrows(IllegalStateException.class, () -> GuestRuntime.requireCocosLibrary(new GuestContext(host, game)));
        game.getApplicationInfo().metaData.putString("android.app.lib_name", " ");
        assertThrows(IllegalStateException.class, () -> GuestRuntime.requireCocosLibrary(new GuestContext(host, game)));
        game.getApplicationInfo().metaData = null;
        PackageInfo replacement = new PackageInfo(); replacement.packageName = GuestRuntime.TARGET;
        replacement.applicationInfo = game.getApplicationInfo();
        shadowOf(host.getPackageManager()).installPackage(replacement);
        assertThrows(IllegalStateException.class, () -> GuestRuntime.requireCocosLibrary(new GuestContext(host, game)));
    }
    @Test public void unattachedActivityFailsWithoutChangingItsBase() {
        Context host = host(); Context game = game(host, "WorldSoccerChamps");
        GameActivity activity = new GameActivity();
        assertThrows(IllegalStateException.class, () -> GuestContext.bindBeforeCreate(activity, GameActivity.class, game));
        assertNull(activity.getBaseContext());
    }
}
