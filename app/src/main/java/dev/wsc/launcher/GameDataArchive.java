package dev.wsc.launcher;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Streams the launcher's private data without loading saves or assets into memory. */
final class GameDataArchive {
    enum Mode { FULL, LIGHT }
    interface Progress { void update(int percent); }
    static final class Entry {
        final File source;
        final String path;
        final long size, modified;
        final boolean directory;
        Entry(File source, String path, boolean directory) {
            this.source = source; this.path = path; this.directory = directory;
            this.size = directory ? 0 : source.length(); this.modified = source.lastModified();
        }
    }
    static final class Plan {
        final File root;
        final Mode mode;
        final List<Entry> entries = new ArrayList<>();
        final List<String> skipped = new ArrayList<>();
        long bytes;
        int files;
        Plan(File root, Mode mode) { this.root = root; this.mode = mode; }
    }
    static Plan scan(File directory, Mode mode) throws IOException {
        File root = directory.getCanonicalFile();
        if (!root.isDirectory()) throw new IOException("Pasta de dados indisponível: " + root);
        Plan plan = new Plan(root, mode);
        collect(plan, root, "", 0);
        return plan;
    }
    private static void collect(Plan plan, File directory, String prefix, int depth) throws IOException {
        if (depth > 64) throw new IOException("Árvore de dados profunda demais: " + prefix);
        File[] children = directory.listFiles();
        if (children == null) throw new IOException("Não foi possível ler a pasta: " + prefix);
        Arrays.sort(children, Comparator.comparing(File::getName));
        for (File file : children) {
            String path = prefix + file.getName();
            // Do not follow lib links, directory cycles or links outside our data root.
            if (!file.getAbsoluteFile().equals(file.getCanonicalFile())) {
                plan.skipped.add(path); continue;
            }
            boolean folder = file.isDirectory();
            if ((!folder && !file.isFile()) || (plan.mode == Mode.LIGHT && !includeLight(path, folder))) {
                plan.skipped.add(path); continue;
            }
            Entry entry = new Entry(file, path, folder);
            plan.entries.add(entry);
            if (folder) collect(plan, file, path + "/", depth + 1);
            else { plan.bytes = Math.addExact(plan.bytes, entry.size); plan.files++; }
        }
    }
    static boolean includeLight(String path, boolean directory) {
        if (path.equals("files") || path.equals("shared_prefs") || path.equals("databases")) return directory;
        // Keep Android preferences and databases, including SQLite WAL/SHM/journal companions.
        if (path.startsWith("shared_prefs/") || path.startsWith("databases/")) return true;
        if (!path.startsWith("files/")) return false;
        String[] parts = path.substring(6).split("/");
        String first = parts[0];
        // Each slot's datapack.db belongs to the save. Preserve it and all backups.
        if (first.startsWith("savegame_slot_") || first.equals("saves") || first.equals("savegames")
                || first.equals("monkey_ranks")) return true;
        if (first.equals("datastore")) {
            for (int index = 1; index < parts.length; index++) {
                String name = parts[index].toLowerCase(Locale.ROOT);
                if (name.startsWith("firebase") || name.startsWith("frc_")) return false;
            }
            return true;
        }
        if (parts.length != 1 || directory) return false;
        String name = first.toLowerCase(Locale.ROOT);
        if (name.startsWith("frc_") || name.startsWith("firebase") || name.startsWith("persistedinstallation")
                || name.startsWith("sdkbox")) return false;
        return name.contains(".db") || name.contains(".bin") || name.endsWith(".csv")
                || name.endsWith(".json") || name.endsWith(".xml") || name.endsWith(".dat")
                || name.endsWith(".sav") || name.endsWith(".save") || name.endsWith(".bak");
    }
    static void write(Plan plan, OutputStream destination, String packageName, Progress progress) throws IOException {
        Reporter reporter = new Reporter(progress, Math.addExact(plan.bytes, plan.entries.size()));
        JSONArray files = new JSONArray();
        byte[] buffer = new byte[64 * 1024];
        reporter.report(0);
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(destination))) {
            for (Entry entry : plan.entries) {
                check(entry);
                // A data/ prefix preserves every original relative path without
                // colliding with this export's metadata, even on a later re-export.
                ZipEntry member = new ZipEntry("data/" + entry.path + (entry.directory ? "/" : ""));
                member.setTime(entry.modified); zip.putNextEntry(member);
                if (!entry.directory) {
                    MessageDigest digest = sha256();
                    long remaining = entry.size;
                    try (InputStream input = new FileInputStream(entry.source)) {
                        while (remaining > 0) {
                            int count = input.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                            if (count < 0) throw new EOFException("Arquivo mudou durante a exportação: " + entry.path);
                            zip.write(buffer, 0, count); digest.update(buffer, 0, count);
                            remaining -= count; reporter.advance(count);
                        }
                    }
                    check(entry);
                    JSONObject record = new JSONObject();
                    try { record.put("path", entry.path); record.put("size", entry.size); record.put("sha256", hex(digest.digest())); }
                    catch (org.json.JSONException error) { throw new IOException(error); }
                    files.put(record);
                }
                zip.closeEntry(); reporter.advance(1);
            }
            JSONObject metadata = new JSONObject();
            try {
                metadata.put("format", 2); metadata.put("package", packageName);
                metadata.put("mode", plan.mode.name().toLowerCase(Locale.ROOT));
                metadata.put("createdAt", System.currentTimeMillis()); metadata.put("dataPrefix", "data/");
                metadata.put("fileCount", plan.files); metadata.put("totalBytes", plan.bytes);
                metadata.put("files", files); metadata.put("excluded", new JSONArray(plan.skipped));
            } catch (org.json.JSONException error) { throw new IOException(error); }
            zip.putNextEntry(new ZipEntry("wsc-export.json"));
            zip.write(metadata.toString(2).getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        } catch (org.json.JSONException error) { throw new IOException(error); }
        // 100% means the ZIP's central directory and destination were closed successfully.
        reporter.report(100);
    }
    private static void check(Entry entry) throws IOException {
        if (!entry.source.getAbsoluteFile().equals(entry.source.getCanonicalFile())
                || (entry.directory ? !entry.source.isDirectory() : !entry.source.isFile()
                || entry.source.length() != entry.size || entry.source.lastModified() != entry.modified))
            throw new IOException("Arquivo mudou durante a exportação: " + entry.path);
    }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(64);
        for (byte value : bytes) text.append(Character.forDigit((value >>> 4) & 15, 16)).append(Character.forDigit(value & 15, 16));
        return text.toString();
    }
    private static final class Reporter {
        private final Progress callback;
        private final long total;
        private long done;
        private int previous = -1;
        Reporter(Progress callback, long total) { this.callback = callback; this.total = total; }
        void advance(long count) {
            done += count;
            report(total == 0 ? 0 : Math.min(99, (int)(99.0 * done / total)));
        }
        void report(int percent) {
            if (percent != previous) { previous = percent; callback.update(percent); }
        }
    }
}
