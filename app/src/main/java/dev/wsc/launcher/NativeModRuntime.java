package dev.wsc.launcher;

import android.content.Context;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;

final class NativeModRuntime {
    private final List<File> modules = new ArrayList<>();
    private final List<String> ids = new ArrayList<>();
    private boolean loaded;
    NativeModRuntime(Context host, List<ModSpec> mods, String gameLibrary) throws Exception {
        String abi = android.os.Process.is64Bit() ? "arm64-v8a" : "armeabi-v7a";
        ModStore store = new ModStore(host.getFilesDir());
        String gameHash = null;
        for (ModSpec mod : mods) {
            ModSpec.NativeLibrary spec = mod.libraryFor(abi);
            if (spec == null) throw new IOException("Mod " + mod.name + " não suporta " + abi + ". Desative o mod.");
            if (gameHash == null) gameHash = gameHash(gameLibrary);
            GuestRuntime.log("NATIVE MOD: perfil " + mod.id + "; SHA256 original=" + gameHash
                    + "; SHA256 esperado=" + spec.gameSha256, null);
            if (!gameHash.equals(spec.gameSha256)) throw new IOException("Biblioteca do jogo incompatível com " + mod.name + ". Desative o mod.");
            File file = store.nativeFile(mod, abi);
            byte[] bytes;
            try (InputStream input = new FileInputStream(file)) { bytes = ModStore.read(input, 4 * 1024 * 1024); }
            ModStore.validateElf(bytes, abi);
            if (!ModSpec.sha256(bytes).equals(spec.sha256)) throw new IOException("Biblioteca instalada do mod foi alterada.");
            modules.add(file); ids.add(mod.id);
            GuestRuntime.log("NATIVE MOD: preparado " + mod.id + "; ABI=" + abi + "; SHA256 original=" + gameHash, null);
        }
    }
    boolean hasModules() { return !modules.isEmpty(); }
    void load() {
        if (loaded) return;
        for (int i = 0; i < modules.size(); i++) {
            NativeModBridge.load(modules.get(i).getAbsolutePath());
            GuestRuntime.log("NATIVE MOD: inicializado " + ids.get(i) + " pela Native Mod ABI v1.", null);
        }
        loaded = true;
    }
    private static String gameHash(String path) throws Exception {
        int split = path.indexOf("!/");
        if (split < 0) try (InputStream input = new FileInputStream(path)) { return digest(input); }
        try (ZipFile zip = new ZipFile(path.substring(0, split))) {
            java.util.zip.ZipEntry entry = zip.getEntry(path.substring(split + 2));
            if (entry == null) throw new IOException("Biblioteca original ausente no split: " + path);
            try (InputStream input = zip.getInputStream(entry)) { return digest(input); }
        }
    }
    private static String digest(InputStream input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[65536]; int count; long total = 0;
        while ((count = input.read(buffer)) != -1) {
            total += count; if (total > 128 * 1024 * 1024) throw new IOException("Biblioteca excede o limite de leitura.");
            digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", value & 255));
        return hex.toString();
    }
}
