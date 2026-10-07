package dev.wsc.launcher;

import android.app.Application;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowVMRuntime;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

/** Exercises preparation and per-process gates. Never dlopens the synthetic ELF fixtures. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class NativeModRuntimeTests {
    private final String[] abis = {"arm64-v8a", "armeabi-v7a"};
    private Application app() { return RuntimeEnvironment.getApplication(); }
    private byte[] game(int index) { return ("independent-game-fixture-" + abis[index]).getBytes(StandardCharsets.UTF_8); }
    private byte[] elf(int index) {
        byte[] bytes = new byte[64]; bytes[0]=127; bytes[1]='E'; bytes[2]='L'; bytes[3]='F';
        bytes[4]=(byte)(index==0?2:1); bytes[5]=1; bytes[6]=1; bytes[16]=3; bytes[18]=(byte)(index==0?183:40); return bytes;
    }
    private ModSpec install(ModStore store, boolean dual, boolean corruptArm32) throws Exception {
        JSONObject libraries = new JSONObject();
        for (int index=0; index<(dual?2:1); index++) libraries.put(abis[index], new JSONObject()
                .put("path", "lib/"+abis[index]+"/libtest.so").put("sha256", ModSpec.sha256(elf(index)))
                .put("gameLibrarySha256", ModSpec.sha256(game(index))));
        JSONObject manifest = new JSONObject().put("format",2).put("type","native").put("api",1)
                .put("id","test.runtime").put("name","Runtime fixture").put("version","1")
                .put("game",new JSONObject().put("package",ModSpec.PACKAGE).put("versionCode",468))
                .put("claims",new JSONArray().put("test.runtime.hook")).put("libraries",libraries);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip=new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("mod.json")); zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            for (int index=0;index<(dual?2:1);index++) {
                zip.putNextEntry(new ZipEntry("lib/"+abis[index]+"/libtest.so"));
                byte[] binary=elf(index); if (corruptArm32 && index==1) binary[63]=42;
                zip.write(binary); zip.closeEntry();
            }
        }
        return store.install(new ByteArrayInputStream(bytes.toByteArray()));
    }
    private File gameFile(int index) throws Exception {
        File file=new File(app().getCacheDir(), "game-fixture"); Files.write(file.toPath(),game(index)); return file;
    }
    private interface Action { void run() throws Exception; }
    private void rejected(String message, Action action) throws Exception {
        try { action.run(); fail("Expected incompatibility"); }
        catch (IOException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains(message)); }
    }
    @Test public void eachProcessSelectsItsOwnLibraryAndGameHashIncludingApkEntries() throws Exception {
        ModStore store=new ModStore(app().getFilesDir()); ModSpec mod=install(store,true,false);
        for (int index=0;index<2;index++) {
            ShadowVMRuntime.setIs64Bit(index==0); File correct=gameFile(index);
            assertTrue(new NativeModRuntime(app(),Collections.singletonList(mod),correct.getPath()).hasModules());
            File split=new File(app().getCacheDir(),"game-split.zip");
            String entry="lib/"+abis[index]+"/libWorldSoccerChamps.so";
            try (ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(split))) {
                zip.putNextEntry(new ZipEntry(entry)); zip.write(game(index)); zip.closeEntry();
            }
            assertTrue(new NativeModRuntime(app(),Collections.singletonList(mod),split.getPath()+"!/"+entry).hasModules());
            File wrong=gameFile(1-index);
            rejected("Biblioteca do jogo incompatível",()->new NativeModRuntime(app(),Collections.singletonList(mod),wrong.getPath()));
        }
    }
    @Test public void singleAbiThirdPartyModsStillWorkAndMissingAbiIsRejected() throws Exception {
        ModStore store=new ModStore(app().getFilesDir()); ModSpec mod=install(store,false,false); File game=gameFile(0);
        ShadowVMRuntime.setIs64Bit(true);
        assertTrue(new NativeModRuntime(app(),Collections.singletonList(mod),game.getPath()).hasModules());
        ShadowVMRuntime.setIs64Bit(false);
        rejected("não suporta armeabi-v7a",()->new NativeModRuntime(app(),Collections.singletonList(mod),game.getPath()));
    }
    @Test public void tamperedArm32ArchiveIsRejectedAtomically() throws Exception {
        ModStore store=new ModStore(app().getFilesDir());
        rejected("Hash da biblioteca",()->install(store,true,true)); assertTrue(store.list().isEmpty());
    }
    @Test public void alteredInstalledPayloadIsRejectedForEitherProcess() throws Exception {
        ModStore store=new ModStore(app().getFilesDir()); ModSpec mod=install(store,true,false);
        for (int index=0;index<2;index++) {
            ShadowVMRuntime.setIs64Bit(index==0); File game=gameFile(index); File library=store.nativeFile(mod,abis[index]);
            assertTrue(library.setWritable(true)); byte[] binary=elf(index); binary[63]=9; Files.write(library.toPath(),binary);
            rejected("foi alterada",()->new NativeModRuntime(app(),Collections.singletonList(mod),game.getPath()));
        }
    }
}
