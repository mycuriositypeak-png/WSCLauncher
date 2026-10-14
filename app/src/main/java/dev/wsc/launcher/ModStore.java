package dev.wsc.launcher;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Persistent settings read from disk in both host and guest; no cross-process preference cache. */
final class ModStore {
    private final File root, installed, state;
    ModStore(File files) throws IOException {
        root = new File(files, "mods"); installed = new File(root, "installed"); state = new File(root, "state.json");
        if (!installed.isDirectory() && !installed.mkdirs()) throw new IOException("Não foi possível criar a pasta de mods.");
    }
    synchronized ModSpec install(InputStream input) throws IOException {
        File incoming = File.createTempFile("incoming-", ".zip", root);
        File staging = null;
        try {
            try (FileOutputStream output = new FileOutputStream(incoming)) { copy(input, output, 16 * 1024 * 1024); }
            ModSpec mod;
            try (ZipFile zip = new ZipFile(incoming)) {
                Map<String, ZipEntry> entries = new HashMap<>();
                Enumeration<? extends ZipEntry> iterator = zip.entries();
                while (iterator.hasMoreElements()) {
                    ZipEntry entry = iterator.nextElement();
                    if (entry.isDirectory() || entries.put(entry.getName(), entry) != null || entries.size() > 3)
                        throw new IOException("ZIP contém entradas inválidas ou duplicadas.");
                }
                ZipEntry manifest = entries.remove("mod.json");
                if (manifest == null) throw new IOException("mod.json não encontrado na raiz.");
                mod = ModSpec.parse(readEntry(zip, manifest, 64 * 1024));
                staging = new File(root, "stage-" + UUID.randomUUID());
                if (!staging.mkdir()) throw new IOException("Não foi possível preparar a instalação.");
                for (ModSpec.NativeLibrary library : mod.nativeLibraries) {
                    ZipEntry entry = entries.remove(library.path);
                    if (entry == null) throw new IOException("Biblioteca ausente: " + library.path);
                    byte[] bytes = readEntry(zip, entry, 4 * 1024 * 1024);
                    validateElf(bytes, library.abi);
                    if (!ModSpec.sha256(bytes).equals(library.sha256)) throw new IOException("Hash da biblioteca do mod não confere.");
                    File output = new File(staging, library.abi + ".so");
                    atomic(output, bytes);
                    if (!output.setReadOnly()) throw new IOException("Não foi possível proteger a biblioteca instalada contra escrita.");
                }
                if (!entries.isEmpty()) throw new IOException("ZIP contém arquivos não declarados pelo mod.");
            }
            synchronized (ModStore.class) {
                if (list().size() >= 128) throw new IOException("Limite de 128 mods instalados.");
                File destination = new File(installed, mod.id + ".json");
                if (destination.exists()) throw new IOException("Mod já instalado. Remova a versão anterior para substituir.");
                File nativeFolder = new File(new File(root, "native"), mod.id);
                if (!nativeFolder.getParentFile().isDirectory() && !nativeFolder.getParentFile().mkdir()) throw new IOException("Pasta nativa indisponível.");
                if (nativeFolder.exists()) deleteTree(nativeFolder); // Leftover of an interrupted, unpublished install.
                if (!staging.renameTo(nativeFolder)) throw new IOException("Falha ao instalar biblioteca nativa.");
                try { atomic(destination, mod.document); }
                catch (IOException failure) { deleteTree(nativeFolder); throw failure; }
            }
            return mod;
        } finally {
            incoming.delete();
            if (staging != null && staging.exists()) deleteTree(staging);
        }
    }
    File nativeFile(ModSpec mod, String abi) throws IOException {
        if (mod.libraryFor(abi) == null) throw new IOException("Mod " + mod.name + " não possui biblioteca " + abi + ".");
        return new File(new File(new File(root, "native"), mod.id), abi + ".so");
    }
    private static byte[] readEntry(ZipFile zip, ZipEntry entry, int max) throws IOException {
        byte[] bytes;
        try (InputStream input = zip.getInputStream(entry)) { bytes = read(input, max); }
        CRC32 crc = new CRC32(); crc.update(bytes);
        if (crc.getValue() != entry.getCrc()) throw new IOException("CRC inválido: " + entry.getName());
        return bytes;
    }
    static void validateElf(byte[] bytes, String abi) throws IOException {
        int elfClass = abi.equals("arm64-v8a") ? 2 : 1, machine = elfClass == 2 ? 183 : 40;
        int minimum = elfClass == 2 ? 64 : 52;
        if (bytes.length < minimum || bytes[0] != 127 || bytes[1] != 'E' || bytes[2] != 'L' || bytes[3] != 'F'
                || bytes[4] != elfClass || bytes[5] != 1 || bytes[6] != 1 || bytes[16] != 3 || bytes[17] != 0
                || (bytes[18] & 255) != machine || bytes[19] != 0)
            throw new IOException("Biblioteca ELF incompatível com " + abi + ".");
    }
    private static void deleteTree(File file) throws IOException {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete() && file.exists()) throw new IOException("Não foi possível remover " + file.getName());
    }
    synchronized List<ModSpec> list() throws IOException {
        List<ModSpec> mods = new ArrayList<>();
        File[] files = installed.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) throw new IOException("Não foi possível listar os mods.");
        if (files.length > 128) throw new IOException("Limite de 128 mods instalados.");
        for (File file : files) {
            try (InputStream input = new FileInputStream(file)) {
                ModSpec mod = ModSpec.parse(read(input, 64 * 1024));
                if (!file.getName().equals(mod.id + ".json")) throw new IOException("Nome do mod inconsistente.");
                mods.add(mod);
            }
        }
        mods.sort(Comparator.comparing(m -> m.id));
        return mods;
    }
    synchronized Set<String> enabledIds() throws IOException {
        Set<String> ids = new TreeSet<>();
        if (!state.exists()) return ids;
        try (InputStream input = new FileInputStream(state)) {
            JSONArray array = new JSONObject(ModSpec.utf8(read(input, 64 * 1024))).getJSONArray("enabled");
            if (array.length() > 128) throw new IOException("Configuração de mods excede o limite.");
            for (int i = 0; i < array.length(); i++) ids.add(array.getString(i));
        } catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("Configuração de mods inválida.", e); }
        return ids;
    }
    synchronized List<ModSpec> active(long gameVersion) throws IOException {
        List<ModSpec> selected = new ArrayList<>();
        Set<String> ids = enabledIds();
        for (ModSpec mod : list()) if (ids.remove(mod.id)) {
            if (mod.gameVersion != gameVersion) throw new IOException("Mod " + mod.name
                    + " requer jogo " + mod.gameVersion + "; instalado: " + gameVersion + ". Desative o mod.");
            selected.add(mod);
        }
        if (!ids.isEmpty()) throw new IOException("Mods ativos não encontrados: " + ids);
        validateConflicts(selected);
        return selected;
    }
    synchronized void setEnabled(String id, boolean enabled, long gameVersion) throws IOException {
        Set<String> ids = enabledIds();
        List<ModSpec> selected = new ArrayList<>();
        boolean found = false;
        for (ModSpec mod : list()) {
            if (mod.id.equals(id)) {
                found = true;
                if (enabled && mod.gameVersion != gameVersion) throw new IOException("Mod requer jogo " + mod.gameVersion
                        + "; instalado: " + gameVersion + ".");
            }
            if ((mod.id.equals(id) && enabled) || (!mod.id.equals(id) && ids.contains(mod.id))) selected.add(mod);
        }
        if (!found) throw new IOException("Mod não encontrado.");
        validateConflicts(selected);
        if (enabled) ids.add(id); else ids.remove(id);
        save(ids);
    }
    synchronized void remove(String id) throws IOException {
        for (ModSpec mod : list()) if (mod.id.equals(id)) {
            Set<String> ids = enabledIds(); ids.remove(id); save(ids);
            File file = new File(installed, id + ".json");
            if (!file.delete()) throw new IOException("Não foi possível remover o mod.");
            File nativeFolder = new File(new File(root, "native"), id);
            if (nativeFolder.exists()) deleteTree(nativeFolder);
            return;
        }
        throw new IOException("Mod não encontrado.");
    }
    synchronized String fingerprint() throws IOException {
        Set<String> ids = enabledIds();
        List<ModSpec> selected = new ArrayList<>();
        for (ModSpec mod : list()) if (ids.remove(mod.id)) selected.add(mod);
        if (!ids.isEmpty()) throw new IOException("Mods ativos não encontrados: " + ids);
        return fingerprintOf(selected);
    }
    static String fingerprintOf(List<ModSpec> selected) {
        List<ModSpec> sorted = new ArrayList<>(selected);
        sorted.sort(Comparator.comparing(mod -> mod.id));
        StringBuilder contents = new StringBuilder("wsc-native-2-abi-1\n");
        for (ModSpec mod : sorted) contents.append(mod.id).append(':').append(mod.digest).append('\n');
        return ModSpec.sha256(contents.toString().getBytes(StandardCharsets.UTF_8));
    }
    synchronized String describe() throws IOException {
        Set<String> enabled = enabledIds();
        StringBuilder value = new StringBuilder("Formato: native v2 / Native Mod ABI v1\nFingerprint: " + fingerprint() + "\n");
        for (ModSpec mod : list()) value.append(enabled.contains(mod.id) ? "ATIVO " : "DESATIVADO ")
                .append(mod.id).append(" v").append(mod.version).append("; jogo ").append(mod.gameVersion)
                .append("; bibliotecas ").append(mod.nativeLibraries.size()).append("; sha256 ").append(mod.digest).append('\n');
        return value.toString();
    }
    private static void validateConflicts(List<ModSpec> mods) throws IOException {
        Map<String, String> used = new HashMap<>();
        for (ModSpec mod : mods) {
            for (String claim : mod.claims) {
                String prior = used.put("native:" + claim, mod.name);
                if (prior != null) throw new IOException("Conflito entre " + prior + " e " + mod.name + ": " + claim);
            }
        }
    }
    private void save(Set<String> ids) throws IOException {
        try { atomic(state, new JSONObject().put("enabled", new JSONArray(ids)).toString().getBytes(StandardCharsets.UTF_8)); }
        catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException(e); }
    }
    static void atomic(File destination, byte[] bytes) throws IOException {
        File temp = File.createTempFile("write-", ".tmp", destination.getParentFile());
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) { out.write(bytes); out.getFD().sync(); }
            // POSIX rename replaces atomically on Android, within the same directory/filesystem.
            if (!temp.renameTo(destination)) throw new IOException("Não foi possível gravar " + destination.getName());
        } finally { if (temp.exists()) temp.delete(); }
    }
    static byte[] read(InputStream input, int max) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); copy(input, output, max); return output.toByteArray();
    }
    private static void copy(InputStream input, OutputStream output, int max) throws IOException {
        byte[] buffer = new byte[8192]; int count, total = 0;
        while ((count = input.read(buffer)) != -1) {
            if (count > max - total) throw new IOException("Arquivo excede o limite de " + max + " bytes.");
            total += count; output.write(buffer, 0, count);
        }
    }
}
