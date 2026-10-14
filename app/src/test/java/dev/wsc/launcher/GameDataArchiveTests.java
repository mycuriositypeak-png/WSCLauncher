package dev.wsc.launcher;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import static org.junit.Assert.*;

public class GameDataArchiveTests {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private File root() throws IOException { return temporary.newFolder(); }
    private void file(File root, String path, String contents) throws IOException {
        File file = new File(root, path);
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        Files.write(file.toPath(), contents.getBytes(StandardCharsets.UTF_8));
    }
    private void fixture(File root) throws IOException {
        for (String path : Arrays.asList("files/savegame_slot_1/data.bin", "files/savegame_slot_1/data.bin.old",
                "files/savegame_slot_1/datapack.db", "files/savegame_slot_2/data.bin", "files/configuration.bin",
                "files/configuration.bin_backup", "files/main.db", "files/main.db-wal", "files/main.db-shm",
                "files/main.db.version", "files/datapack.db", "files/settings.csv", "files/players.csv",
                "files/monkey_ranks/outbox.bin", "files/datastore/game.preferences_pb", "shared_prefs/game.xml",
                "databases/game.db", "databases/game.db-journal")) file(root, path, "important: " + path);
        for (String path : Arrays.asList("files/club_logos/100.webp", "files/competition_logos/20.webp",
                "files/adboards/example.png", "files/datapacks/pro/pro_datapack_v86.zip", "files/guest-diagnostic.log",
                "files/mods/native/example/arm64-v8a.so", "files/.crashlytics.v3/report", "files/frc_settings.json",
                "files/datastore/firebaseSessions/session.preferences_pb", "cache/network.bin", "code_cache/code.dex",
                "app_webview/Default/Cookies", "wsc-export.json")) file(root, path, "extra: " + path);
        assertTrue(new File(root, "empty-directory").mkdir());
    }
    private Map<String, byte[]> contents(byte[] archive) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                assertNull("Duplicate ZIP path", entries.put(entry.getName(), input.readAllBytes()));
            }
        }
        return entries;
    }
    private Map<String, byte[]> export(File root, GameDataArchive.Mode mode) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        GameDataArchive.write(GameDataArchive.scan(root, mode), bytes, "test.launcher", percent -> { });
        return contents(bytes.toByteArray());
    }
    @Test public void completeArchivePreservesTreeAndOriginalMetadataWithoutCollisions() throws Exception {
        File root = root(); fixture(root);
        Map<String, byte[]> entries = export(root, GameDataArchive.Mode.FULL);
        assertTrue(entries.containsKey("data/empty-directory/"));
        assertTrue(entries.containsKey("data/cache/network.bin"));
        assertTrue(entries.containsKey("data/files/datapacks/pro/pro_datapack_v86.zip"));
        assertArrayEquals(Files.readAllBytes(new File(root, "wsc-export.json").toPath()), entries.get("data/wsc-export.json"));
        JSONObject metadata = new JSONObject(new String(entries.get("wsc-export.json"), StandardCharsets.UTF_8));
        assertEquals(2, metadata.getInt("format"));
        assertEquals("test.launcher", metadata.getString("package")); assertEquals("full", metadata.getString("mode"));
        assertEquals("data/", metadata.getString("dataPrefix"));
        long total = 0;
        for (int index = 0; index < metadata.getJSONArray("files").length(); index++) {
            JSONObject record = metadata.getJSONArray("files").getJSONObject(index);
            byte[] expected = Files.readAllBytes(new File(root, record.getString("path")).toPath());
            assertArrayEquals(expected, entries.get("data/" + record.getString("path")));
            assertEquals(expected.length, record.getLong("size")); total += expected.length;
            StringBuilder digest = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(expected)) digest.append(String.format(Locale.ROOT, "%02x", b));
            assertEquals(digest.toString(), record.getString("sha256"));
        }
        assertEquals(metadata.getJSONArray("files").length(), metadata.getInt("fileCount"));
        assertEquals(total, metadata.getLong("totalBytes"));
    }
    @Test public void lightArchiveKeepsSaveDatabasesBackupsAndConfigurationButDropsResources() throws Exception {
        File root = root(); fixture(root);
        Map<String, byte[]> entries = export(root, GameDataArchive.Mode.LIGHT);
        for (String path : Arrays.asList("files/savegame_slot_1/data.bin", "files/savegame_slot_1/data.bin.old",
                "files/savegame_slot_1/datapack.db", "files/savegame_slot_2/data.bin", "files/configuration.bin_backup",
                "files/main.db-wal", "files/main.db-shm", "files/main.db.version", "files/datapack.db",
                "files/players.csv", "files/datastore/game.preferences_pb", "shared_prefs/game.xml", "databases/game.db-journal"))
            assertTrue("Missing essential file: " + path, entries.containsKey("data/" + path));
        for (String path : entries.keySet())
            assertFalse(path, path.contains("club_logos") || path.contains("competition_logos") || path.contains("adboards")
                    || path.contains("/datapacks/") || path.contains("/mods/") || path.contains("crashlytics")
                    || path.contains("firebaseSessions") || path.contains("frc_settings") || path.contains("cache/")
                    || path.contains("app_webview") || path.contains("guest-diagnostic"));
        assertEquals("light", new JSONObject(new String(entries.get("wsc-export.json"), StandardCharsets.UTF_8)).getString("mode"));
    }
    @Test public void symbolicLinksAndDirectoryCyclesAreSkippedWithoutReadingTheirTargets() throws Exception {
        File root = root(); file(root, "files/savegame_slot_1/data.bin", "save");
        File outside = temporary.newFile(); Files.write(outside.toPath(), "outside".getBytes(StandardCharsets.UTF_8));
        Files.createSymbolicLink(new File(root, "external-file").toPath(), outside.toPath());
        Files.createSymbolicLink(new File(root, "cycle").toPath(), root.toPath());
        Files.createSymbolicLink(new File(root, "internal-link").toPath(), new File(root, "files").toPath());
        GameDataArchive.Plan plan = GameDataArchive.scan(root, GameDataArchive.Mode.FULL);
        assertEquals(1, plan.files);
        assertTrue(plan.skipped.containsAll(Arrays.asList("external-file", "cycle", "internal-link")));
    }
    @Test public void changedOrMissingFilesFailRatherThanProduceAnApparentlyValidBackup() throws Exception {
        File root = root(); file(root, "files/configuration.bin", "before");
        GameDataArchive.Plan plan = GameDataArchive.scan(root, GameDataArchive.Mode.FULL);
        file(root, "files/configuration.bin", "changed size");
        List<Integer> progress = new ArrayList<>();
        assertThrows(IOException.class, () -> GameDataArchive.write(plan, new ByteArrayOutputStream(), "test", progress::add));
        assertFalse(progress.contains(100));
        assertTrue(new File(root, "files/configuration.bin").delete());
        assertThrows(IOException.class, () -> GameDataArchive.write(plan, new ByteArrayOutputStream(), "test", percent -> { }));
    }
    @Test public void progressIsMonotonicAndOnlyReaches100AfterSuccessfulClose() throws Exception {
        File root = root(); File big = new File(root, "files/savegame_slot_1/data.bin");
        assertTrue(big.getParentFile().mkdirs()); byte[] data = new byte[1024 * 1024]; new Random(7).nextBytes(data); Files.write(big.toPath(), data);
        List<Integer> progress = new ArrayList<>(); boolean[] closed = {false};
        ByteArrayOutputStream output = new ByteArrayOutputStream() {
            @Override public void close() { closed[0] = true; }
        };
        GameDataArchive.write(GameDataArchive.scan(root, GameDataArchive.Mode.LIGHT), output, "test", percent -> {
            if (percent == 100) assertTrue(closed[0]); else assertFalse(closed[0]);
            progress.add(percent);
        });
        assertEquals(Integer.valueOf(0), progress.get(0)); assertEquals(Integer.valueOf(100), progress.get(progress.size()-1));
        assertTrue(progress.size() > 5);
        for (int i = 1; i < progress.size(); i++) assertTrue(progress.get(i) > progress.get(i-1));
        assertArrayEquals(data, contents(output.toByteArray()).get("data/files/savegame_slot_1/data.bin"));
    }
    @Test public void writeAndCloseFailuresNeverReportSuccess() throws Exception {
        File root = root(); fixture(root);
        List<Integer> progress = new ArrayList<>();
        OutputStream unavailable = new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("Destination full"); }
        };
        assertThrows(IOException.class, () -> GameDataArchive.write(GameDataArchive.scan(root, GameDataArchive.Mode.LIGHT), unavailable, "test", progress::add));
        assertFalse(progress.contains(100));
        progress.clear();
        OutputStream closeFailure = new ByteArrayOutputStream() {
            @Override public void close() throws IOException { throw new IOException("Close failed"); }
        };
        assertThrows(IOException.class, () -> GameDataArchive.write(GameDataArchive.scan(root, GameDataArchive.Mode.LIGHT), closeFailure, "test", progress::add));
        assertFalse(progress.contains(100));
    }
    @Test public void emptyDataFolderStillProducesMetadataAndFinishesWithoutDividingByZero() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); List<Integer> progress = new ArrayList<>();
        GameDataArchive.write(GameDataArchive.scan(root(), GameDataArchive.Mode.LIGHT), output, "test", progress::add);
        assertEquals(Arrays.asList(0, 100), progress);
        JSONObject info = new JSONObject(new String(contents(output.toByteArray()).get("wsc-export.json"), StandardCharsets.UTF_8));
        assertEquals(0, info.getInt("fileCount")); assertEquals(0, info.getLong("totalBytes"));
    }
}
