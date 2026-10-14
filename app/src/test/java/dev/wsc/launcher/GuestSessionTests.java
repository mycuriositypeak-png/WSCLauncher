package dev.wsc.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Process;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 35})
public class GuestSessionTests {
    private ActivityManager.RunningAppProcessInfo process(String name, int uid, int pid) {
        ActivityManager.RunningAppProcessInfo info = new ActivityManager.RunningAppProcessInfo();
        info.processName = name; info.uid = uid; info.pid = pid; return info;
    }
    @Test public void pidOnlySelectsTheGuestOwnedByThisInstallation() {
        Context context = RuntimeEnvironment.getApplication();
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        String guest = context.getPackageName() + ":guest";
        shadowOf(manager).setProcesses(Arrays.asList(
                process(guest, Process.myUid()+1, 801), process(context.getPackageName(), Process.myUid(), Process.myPid()),
                process("other.launcher:guest", Process.myUid(), 802), process(guest, Process.myUid(), 803)));
        assertEquals(803, GuestSession.pid(context));
    }
    @Test public void exportDoesNotStopAnotherUidOrTheCurrentProcess() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        String guest = context.getPackageName()+":guest";
        shadowOf(manager).setProcesses(Arrays.asList(
                process(guest, Process.myUid()+1, 901), process(guest, Process.myUid(), Process.myPid())));
        assertEquals(-1, GuestSession.pid(context));
        GuestSession.stopForExport(context);
        assertFalse(org.robolectric.shadows.ShadowProcess.wasKilled(901));
        assertFalse(org.robolectric.shadows.ShadowProcess.wasKilled(Process.myPid()));
    }
}
