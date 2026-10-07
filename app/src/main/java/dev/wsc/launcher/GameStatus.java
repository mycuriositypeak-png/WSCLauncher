package dev.wsc.launcher;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import java.io.File;
import java.io.FileInputStream;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipFile;

/** Read-only UI detection; binary/hash validation still belongs to the existing runtime. */
final class GameStatus {
    final boolean installed;
    final String versionName;
    final long versionCode;
    final Set<String> abis;
    final Drawable icon;
    private GameStatus(boolean installed, String versionName, long versionCode, Set<String> abis, Drawable icon) {
        this.installed = installed; this.versionName = versionName; this.versionCode = versionCode;
        this.abis = Collections.unmodifiableSet(abis); this.icon = icon;
    }
    static GameStatus missing() { return new GameStatus(false, "", 0, new TreeSet<>(), null); }
    static GameStatus detect(Context context) throws Exception {
        PackageInfo info;
        try { info = context.getPackageManager().getPackageInfo(ModSpec.PACKAGE, 0); }
        catch (PackageManager.NameNotFoundException absent) { return missing(); }
        ApplicationInfo app = info.applicationInfo;
        Set<String> abis = new TreeSet<>();
        // Read installed files/ZIP entries only. Never extract or modify the game.
        if (app != null) {
            if (app.nativeLibraryDir != null) {
                File library = new File(app.nativeLibraryDir, "libWorldSoccerChamps.so");
                if (library.isFile()) try (FileInputStream input = new FileInputStream(library)) {
                    byte[] header = new byte[20];
                    if (input.read(header) == header.length && header[0] == 127 && header[1] == 'E'
                            && header[2] == 'L' && header[3] == 'F' && header[5] == 1) {
                        int machine = (header[18] & 255) | ((header[19] & 255) << 8);
                        if (header[4] == 2 && machine == 183) abis.add("arm64-v8a");
                        if (header[4] == 1 && machine == 40) abis.add("armeabi-v7a");
                    }
                }
            }
            scanApk(app.sourceDir, abis);
            if (app.splitSourceDirs != null) for (String split : app.splitSourceDirs) scanApk(split, abis);
        }
        Drawable icon = null;
        try { icon = context.getPackageManager().getApplicationIcon(ModSpec.PACKAGE); }
        catch (PackageManager.NameNotFoundException ignored) { }
        return new GameStatus(true, info.versionName, info.versionCode, abis, icon);
    }
    private static void scanApk(String path, Set<String> abis) throws Exception {
        if (path == null || !new File(path).isFile()) return;
        try (ZipFile zip = new ZipFile(path)) {
            for (String abi : new String[]{"arm64-v8a", "armeabi-v7a"})
                if (zip.getEntry("lib/" + abi + "/libWorldSoccerChamps.so") != null) abis.add(abi);
        }
    }
    String mismatchMessage(Context context, String processAbi) {
        String available = abis.contains("armeabi-v7a") ? "ARM32" : "ARM64";
        String process = processAbi.equals("arm64-v8a") ? "ARM64" : "ARM32";
        return context.getString(R.string.abi_mismatch_description, available, process, available);
    }
    boolean supports(String abi) { return abis.isEmpty() || abis.contains(abi); }
    boolean referenceProfile() { return versionCode == 468 && (abis.contains("arm64-v8a") || abis.contains("armeabi-v7a")); }
}
