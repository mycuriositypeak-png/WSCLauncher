package dev.wsc.launcher;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public class NativeModTests {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    // Header fixture for the importer's architecture gate; not an executable library.
    private static byte[] elf() {
        byte[] bytes = new byte[64]; bytes[0] = 127; bytes[1] = 'E'; bytes[2] = 'L'; bytes[3] = 'F';
        bytes[4] = 2; bytes[5] = 1; bytes[6] = 1; bytes[16] = 3; bytes[18] = (byte)183; return bytes;
    }
    private static JSONObject manifest(String id, byte[] binary) throws Exception {
        return new JSONObject().put("format", 2).put("type", "native").put("api", 1)
                .put("id", id).put("name", id).put("version", "1")
                .put("game", new JSONObject().put("package", ModSpec.PACKAGE).put("versionCode", 468))
                .put("claims", new JSONArray().put("PlayerRolesScene.roleButton"))
                .put("libraries", new JSONObject().put("arm64-v8a", new JSONObject()
                    .put("path", "lib/arm64-v8a/libtest.so").put("sha256", ModSpec.sha256(binary))
                    .put("gameLibrarySha256", "0".repeat(64))));
    }
    private static byte[] archive(JSONObject json, Map<String, byte[]> payloads) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("mod.json")); zip.write(json.toString().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            for (Map.Entry<String, byte[]> entry : payloads.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry();
            }
        }
        return buffer.toByteArray();
    }
    private static ModSpec install(ModStore store, JSONObject json, byte[] bytes) throws Exception {
        return store.install(new ByteArrayInputStream(archive(json, Collections.singletonMap("lib/arm64-v8a/libtest.so", bytes))));
    }
    private interface Action { void run() throws Exception; }
    private static void rejected(Action action) throws Exception {
        try { action.run(); fail("Expected rejection"); } catch (IOException expected) { assertNotNull(expected.getMessage()); }
    }
    @Test public void externalPayloadPersistsDisabledThenActivatesAndRemoves() throws Exception {
        File files = folder.newFolder(); ModStore store = new ModStore(files);
        ModSpec mod = install(store, manifest("test.native", elf()), elf());
        assertTrue(store.active(468).isEmpty()); assertEquals(1, mod.nativeLibraries.size());
        assertArrayEquals(elf(), java.nio.file.Files.readAllBytes(store.nativeFile(mod, "arm64-v8a").toPath()));
        store.setEnabled(mod.id, true, 468);
        ModStore guest = new ModStore(files);
        assertEquals(mod.digest, guest.active(468).get(0).digest);
        assertNull(mod.libraryFor("armeabi-v7a"));
        rejected(() -> store.nativeFile(mod, "armeabi-v7a"));
        File library = store.nativeFile(mod, "arm64-v8a");
        store.remove(mod.id); assertFalse(library.exists()); assertTrue(guest.list().isEmpty()); assertTrue(guest.enabledIds().isEmpty());
    }
    @Test public void tamperedOrWrongArchitecturePayloadLeavesNoInstall() throws Exception {
        ModStore store = new ModStore(folder.newFolder());
        JSONObject json = manifest("test.native", elf());
        byte[] tampered = elf(); tampered[63] = 42;
        rejected(() -> install(store, json, tampered));
        byte[] wrongAbi = elf(); wrongAbi[4] = 1; wrongAbi[18] = 40;
        rejected(() -> install(store, manifest("test.native", wrongAbi), wrongAbi));
        assertTrue(store.list().isEmpty());
    }
    @Test public void undeclaredMissingAndTraversalPayloadsAreRejected() throws Exception {
        ModStore store = new ModStore(folder.newFolder()); JSONObject json = manifest("test.native", elf());
        rejected(() -> store.install(new ByteArrayInputStream(archive(json, Collections.emptyMap()))));
        Map<String, byte[]> extra = new HashMap<>(); extra.put("lib/arm64-v8a/libtest.so", elf()); extra.put("../escaped", elf());
        rejected(() -> store.install(new ByteArrayInputStream(archive(json, extra))));
        json.getJSONObject("libraries").getJSONObject("arm64-v8a").put("path", "../libtest.so");
        rejected(() -> ModSpec.parse(json.toString().getBytes(StandardCharsets.UTF_8)));
        assertTrue(store.list().isEmpty());
    }
    @Test public void hookClaimsConflictWithoutChangingEnabledSelection() throws Exception {
        ModStore store = new ModStore(folder.newFolder());
        install(store, manifest("test.one", elf()), elf()); install(store, manifest("test.two", elf()), elf());
        store.setEnabled("test.one", true, 468); String fingerprint = store.fingerprint();
        rejected(() -> store.setEnabled("test.two", true, 468));
        assertEquals(Collections.singleton("test.one"), store.enabledIds()); assertEquals(fingerprint, store.fingerprint());
    }
    @Test public void duplicateCannotOverwriteExecutable() throws Exception {
        ModStore store = new ModStore(folder.newFolder()); ModSpec mod = install(store, manifest("test.native", elf()), elf());
        byte[] changed = elf(); changed[63] = 7;
        rejected(() -> install(store, manifest("test.native", changed), changed));
        assertArrayEquals(elf(), java.nio.file.Files.readAllBytes(store.nativeFile(mod, "arm64-v8a").toPath()));
        assertEquals(mod.digest, store.list().get(0).digest);
    }
    @Test public void wrongApiOrMissingHashAndDuplicateClaimsAreRejected() throws Exception {
        JSONObject json = manifest("test.native", elf()).put("api", 2);
        rejected(() -> ModSpec.parse(json.toString().getBytes(StandardCharsets.UTF_8)));
        json.put("api", 1); json.getJSONObject("libraries").getJSONObject("arm64-v8a").remove("gameLibrarySha256");
        rejected(() -> ModSpec.parse(json.toString().getBytes(StandardCharsets.UTF_8)));
        JSONObject duplicate = manifest("test.native", elf()); duplicate.getJSONArray("claims").put("PlayerRolesScene.roleButton");
        rejected(() -> ModSpec.parse(duplicate.toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Test public void legacyDeclarativeManifestIsRejectedWithoutInstalling() throws Exception {
        JSONObject legacy = new JSONObject().put("format", 1).put("type", "plist-patch")
                .put("id", "test.legacy").put("name", "Legacy").put("version", "1")
                .put("game", new JSONObject().put("package", ModSpec.PACKAGE).put("versionCode", 468))
                .put("patches", new JSONArray().put(new JSONObject().put("asset", "strings/main/en.plist")
                    .put("sourceSha256", "0".repeat(64)).put("key", "MENU_OPTION_1").put("value", "Legacy")));
        rejected(() -> ModSpec.parse(legacy.toString().getBytes(StandardCharsets.UTF_8)));
        ModStore store = new ModStore(folder.newFolder());
        String fingerprint = store.fingerprint();
        rejected(() -> store.install(new ByteArrayInputStream(archive(legacy, Collections.emptyMap()))));
        assertTrue(store.list().isEmpty()); assertEquals(fingerprint, store.fingerprint());
        legacy.put("format", 2);
        rejected(() -> ModSpec.parse(legacy.toString().getBytes(StandardCharsets.UTF_8)));
    }
    @Test public void wrongGameVersionCannotActivateOrRun() throws Exception {
        ModStore store = new ModStore(folder.newFolder());
        install(store, manifest("test.native", elf()), elf());
        rejected(() -> store.setEnabled("test.native", true, 469)); assertTrue(store.enabledIds().isEmpty());
        store.setEnabled("test.native", true, 468);
        rejected(() -> store.active(469));
        store.setEnabled("test.native", false, -1);
    }
    @Test public void fingerprintTracksSelectionButPreservesLoadedSnapshot() throws Exception {
        ModStore store = new ModStore(folder.newFolder());
        String original = store.fingerprint();
        install(store, manifest("test.native", elf()), elf());
        assertEquals(original, store.fingerprint());
        store.setEnabled("test.native", true, 468);
        List<ModSpec> loaded = store.active(468);
        String fingerprint = store.fingerprint();
        store.setEnabled("test.native", false, -1);
        assertEquals(fingerprint, ModStore.fingerprintOf(loaded));
        assertNotEquals(store.fingerprint(), ModStore.fingerprintOf(loaded));
        store.setEnabled("test.native", true, 468); store.remove("test.native");
        assertEquals(original, store.fingerprint());
    }
    @Test public void invalidMetadataAndMalformedArchivesLeaveNoInstall() throws Exception {
        JSONObject json = manifest("test.native", elf()); json.getJSONObject("game").put("package", "other.game");
        rejected(() -> ModSpec.parse(json.toString().getBytes(StandardCharsets.UTF_8)));
        JSONObject badId = manifest("../escape", elf());
        rejected(() -> ModSpec.parse(badId.toString().getBytes(StandardCharsets.UTF_8)));
        JSONObject wrongFormat = manifest("test.native", elf()).put("format", 1);
        rejected(() -> ModSpec.parse(wrongFormat.toString().getBytes(StandardCharsets.UTF_8)));
        rejected(() -> ModSpec.parse(new byte[]{(byte) 0xc3, 0x28}));
        ModStore store = new ModStore(folder.newFolder());
        byte[] good = archive(manifest("test.native", elf()), Collections.singletonMap("lib/arm64-v8a/libtest.so", elf()));
        rejected(() -> store.install(new ByteArrayInputStream(Arrays.copyOf(good, good.length / 2))));
        JSONObject oversized = manifest("test.native", elf()).put("extra", "x".repeat(64 * 1024));
        rejected(() -> store.install(new ByteArrayInputStream(archive(oversized, Collections.emptyMap()))));
        rejected(() -> store.install(new ByteArrayInputStream(new byte[16 * 1024 * 1024 + 1])));
        assertTrue(store.list().isEmpty());
    }
    @Test public void prebuiltPlayerRolesPackageInstallsThroughRealImporter() throws Exception {
        File zip = new File("../examples/player-roles/wsc-player-roles-picker-v0.4.zip");
        assertTrue("Versioned Player Roles package must exist", zip.isFile());
        ModStore store = new ModStore(folder.newFolder());
        ModSpec mod;
        try (InputStream input = new FileInputStream(zip)) { mod = store.install(input); }
        assertEquals("wsc.player-roles-picker", mod.id); assertEquals("0.4.0", mod.version);
        assertEquals(468, mod.gameVersion); assertEquals(4, mod.claims.size());
        assertEquals(2, mod.nativeLibraries.size()); assertTrue(store.enabledIds().isEmpty());
        store.setEnabled(mod.id, true, 468); assertEquals(mod.digest, store.active(468).get(0).digest);
        for (String abi : new String[]{"arm64-v8a", "armeabi-v7a"}) {
            assertEquals(mod.libraryFor(abi).sha256,
                    ModSpec.sha256(java.nio.file.Files.readAllBytes(store.nativeFile(mod, abi).toPath())));
        }
        assertNotEquals(mod.libraryFor("arm64-v8a").gameSha256, mod.libraryFor("armeabi-v7a").gameSha256);
        try (ZipFile archive = new ZipFile(zip)) {
            assertEquals(3, archive.size());
            assertNotNull(archive.getEntry("mod.json"));
            for (ModSpec.NativeLibrary library : mod.nativeLibraries) assertNotNull(archive.getEntry(library.path));
        }
    }
}
