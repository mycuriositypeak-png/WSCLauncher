package dev.wsc.launcher;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ProviderInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import com.google.firebase.provider.FirebaseInitProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 35})
public class GuestProvidersTests {
    public static class GameApplication extends Application {
        boolean initializedAtCreate;
        @Override public void onCreate() {
            initializedAtCreate = FirebaseInitProvider.initializedContext == this;
        }
    }
    private Context host;
    private Context game;
    private ProviderInfo provider;
    private GameApplication application;

    @Before public void fixture() throws Exception {
        FirebaseInitProvider.calls = 0;
        FirebaseInitProvider.initializedContext = null;
        FirebaseInitProvider.failure = null;
        host = RuntimeEnvironment.getApplication();
        ApplicationInfo original = new ApplicationInfo();
        original.packageName = GuestRuntime.TARGET;
        original.uid = host.getApplicationInfo().uid + 100;
        original.dataDir = "/data/user/0/" + original.packageName;
        provider = new ProviderInfo();
        provider.name = FirebaseInitProvider.class.getName();
        provider.packageName = original.packageName;
        provider.applicationInfo = original;
        provider.authority = original.packageName + ".firebaseinitprovider";
        provider.enabled = true;
        install(provider);
        Resources resources = new Resources(host.getAssets(), host.getResources().getDisplayMetrics(),
                new Configuration(host.getResources().getConfiguration()));
        game = new ContextWrapper(host) {
            @Override public String getPackageName() { return original.packageName; }
            @Override public Resources getResources() { return resources; }
        };
        application = (GameApplication) new Instrumentation().newApplication(getClass().getClassLoader(),
                GameApplication.class.getName(), new GuestContext(host, game));
        GuestRuntime.gameApplication = application;
    }
    @After public void reset() { GuestRuntime.gameApplication = null; }
    private void install(ProviderInfo... providers) {
        PackageInfo info = new PackageInfo(); info.packageName = GuestRuntime.TARGET;
        info.applicationInfo = provider.applicationInfo; info.providers = providers;
        shadowOf(host.getPackageManager()).installPackage(info);
    }
    @Test public void realProviderLifecycleUsesGameApplicationBeforeApplicationOnCreate() throws Exception {
        assertTrue(GuestProviders.initializeFirebase(application, getClass().getClassLoader()));
        assertEquals(1, FirebaseInitProvider.calls);
        assertSame(application, FirebaseInitProvider.initializedContext);
        assertSame(application, FirebaseInitProvider.initializedContext.getApplicationContext());
        assertEquals(GuestRuntime.TARGET, FirebaseInitProvider.initializedContext.getPackageName());
        assertSame(game.getResources(), FirebaseInitProvider.initializedContext.getResources());
        assertEquals(host.getFilesDir(), FirebaseInitProvider.initializedContext.getFilesDir());
        assertEquals(GuestRuntime.TARGET + ".firebaseinitprovider", provider.authority);
        application.onCreate();
        assertTrue(application.initializedAtCreate);
    }
    @Test public void absentProvidersDoNotInitializeSdk() throws Exception {
        install((ProviderInfo[]) null);
        assertFalse(GuestProviders.initializeFirebase(application, getClass().getClassLoader()));
        assertEquals(0, FirebaseInitProvider.calls);
    }
    @Test public void disabledOrUnrelatedProvidersAreNotStarted() throws Exception {
        provider.enabled = false; install(provider);
        assertFalse(GuestProviders.initializeFirebase(application, getClass().getClassLoader()));
        provider.enabled = true; provider.name = "other.Provider"; install(provider);
        assertFalse(GuestProviders.initializeFirebase(application, getClass().getClassLoader()));
        assertEquals(0, FirebaseInitProvider.calls);
    }
    @Test public void gameClassLoaderIsRequiredAndMissingDeclaredClassFails() {
        ClassLoader unavailable = new ClassLoader(null) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                throw new ClassNotFoundException(name);
            }
        };
        assertThrows(ClassNotFoundException.class, () -> GuestProviders.initializeFirebase(application, unavailable));
        assertEquals(0, FirebaseInitProvider.calls);
    }
    @Test public void sdkFailuresPropagateToBootstrapInsteadOfPretendingSuccess() {
        RuntimeException problem = new IllegalStateException("SDK initialization failed");
        FirebaseInitProvider.failure = problem;
        assertSame(problem, assertThrows(RuntimeException.class,
                () -> GuestProviders.initializeFirebase(application, getClass().getClassLoader())));
    }
    @Test public void backgroundInitializationIsRejectedBeforeStartingProvider() throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { GuestProviders.initializeFirebase(application, getClass().getClassLoader()); }
            catch (Throwable failure) { error.set(failure); }
        });
        worker.start(); worker.join(5000);
        assertFalse(worker.isAlive());
        assertTrue(error.get() instanceof IllegalStateException);
        assertEquals(0, FirebaseInitProvider.calls);
    }
}
