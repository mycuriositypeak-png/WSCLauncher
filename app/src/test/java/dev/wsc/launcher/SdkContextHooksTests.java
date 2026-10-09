package dev.wsc.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.internal.client.zzaz;
import com.google.android.gms.ads.internal.client.zzex;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 35})
public class SdkContextHooksTests {
    private Context host, game;
    private SdkContext service;
    private List<Method> hooks;

    @Before public void fixture() {
        host = RuntimeEnvironment.getApplication();
        ApplicationInfo original = new ApplicationInfo(host.getApplicationInfo());
        original.packageName = GuestRuntime.TARGET;
        original.uid += 100;
        Resources resources = new Resources(host.getAssets(), host.getResources().getDisplayMetrics(),
                new Configuration(host.getResources().getConfiguration()));
        game = new ContextWrapper(host) {
            @Override public String getPackageName() { return GuestRuntime.TARGET; }
            @Override public ApplicationInfo getApplicationInfo() { return original; }
            @Override public Resources getResources() { return resources; }
            @Override public Context getApplicationContext() { return this; }
        };
        service = new SdkContext(host, game);
        hooks = new ArrayList<>();
        SdkContextHooks.installAds(getClass().getClassLoader(), hooks::add);
    }

    @Test public void initializationAndBrokerAreAdaptedBeforeTheyCaptureTheGuest() throws Exception {
        assertTrue(hooks.contains(MobileAds.class.getDeclaredMethod("initialize", Context.class)));
        assertTrue(hooks.contains(MobileAds.class.getDeclaredMethod("initialize", Context.class, Object.class)));
        assertTrue(hooks.contains(zzex.class.getDeclaredMethod("zzC", Context.class)));
        Method initialize = zzex.class.getDeclaredMethod("zzc", Context.class, String.class, Object.class);
        Method create = zzaz.class.getDeclaredMethod("zzc", Context.class, String.class, Object.class);
        assertTrue(hooks.contains(initialize));
        assertTrue(hooks.contains(create));
        Object listener = new Object();
        Object[] initialization = {game, null, listener};
        assertTrue(SdkContextHooks.adaptArguments(initialization, service));
        zzex settings = new zzex(); initialize.invoke(settings, initialization);
        assertSame(service, settings.captured);
        assertSame(listener, settings.listener);
        Object adapter = new Object();
        Object[] request = {game, "unit", adapter};
        assertTrue(SdkContextHooks.adaptArguments(request, service));
        zzaz broker = new zzaz(); assertSame(adapter, create.invoke(broker, request));
        assertEquals("unit", broker.unit); assertSame(adapter, broker.adapter);
        // Both the captured context and the app context must name the process owner.
        assertEquals(host.getPackageName(), broker.captured.getPackageName());
        assertEquals(host.getApplicationInfo().uid, broker.captured.getApplicationInfo().uid);
        assertEquals(host.getPackageName(), broker.captured.getApplicationContext().getPackageName());
        assertSame(game.getResources(), broker.captured.getResources());
        assertSame(game.getAssets(), broker.captured.getAssets());
        assertSame(game.getClassLoader(), broker.captured.getClassLoader());
        assertEquals(host.getFilesDir(), broker.captured.getFilesDir());
        assertEquals(GuestRuntime.TARGET, game.getPackageName());
        assertNotEquals(host.getApplicationInfo().uid, game.getApplicationInfo().uid);
    }

    @Test public void overlaysAndOtherMobileAdsUiEntriesAreNotRegistered() throws Exception {
        assertFalse(hooks.contains(zzaz.class.getDeclaredMethod("zzh", Activity.class)));
        assertFalse(hooks.contains(zzaz.class.getDeclaredMethod("zzf", android.view.View.class, Object.class, Object.class)));
        assertFalse(hooks.contains(MobileAds.class.getDeclaredMethod("openDebugMenu", Context.class, String.class)));
    }

    @Test public void unrelatedNullAndAlreadyAdaptedContextsAreUnchanged() {
        Object[] hostArguments = {host, "unit"};
        assertFalse(SdkContextHooks.adaptArguments(hostArguments, service));
        assertSame(host, hostArguments[0]);
        Object[] adapted = {service};
        assertFalse(SdkContextHooks.adaptArguments(adapted, service)); assertSame(service, adapted[0]);
        Context unrelated = new ContextWrapper(host) {
            @Override public String getPackageName() { return "other.game"; }
        };
        Object[] other = {unrelated};
        assertFalse(SdkContextHooks.adaptArguments(other, service)); assertSame(unrelated, other[0]);
        Object[] missing = {null};
        assertFalse(SdkContextHooks.adaptArguments(missing, service)); assertNull(missing[0]);
        assertFalse(SdkContextHooks.adaptArguments(new Object[0], service));
    }

    @Test public void missingInternalSdkClassesDoNotPreventPublicInitializationHooks() {
        ClassLoader changedSdk = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.google.android.gms.ads.internal.client.")) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
        };
        List<Method> available = new ArrayList<>();
        assertEquals(2, SdkContextHooks.installAds(changedSdk, available::add));
        assertTrue(available.stream().allMatch(method -> method.getDeclaringClass() == MobileAds.class));
    }
}
