package dev.wsc.launcher;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.util.*;

/** Manifest for independent executable native modules (format 2 / Native Mod ABI v1). */
final class ModSpec {
    static final String PACKAGE = "com.monkeyibrow.worldsoccerchamps";
    final String id, name, version, description, digest;
    final long gameVersion;
    final byte[] document;
    final List<NativeLibrary> nativeLibraries;
    final List<String> claims;

    static final class NativeLibrary {
        final String abi, path, sha256, gameSha256;
        NativeLibrary(String abi, String path, String sha256, String gameSha256) {
            this.abi = abi; this.path = path; this.sha256 = sha256; this.gameSha256 = gameSha256;
        }
    }
    NativeLibrary libraryFor(String abi) {
        for (NativeLibrary library : nativeLibraries) if (library.abi.equals(abi)) return library;
        return null;
    }

    private ModSpec(JSONObject json, byte[] bytes) throws Exception {
        if (json.getInt("format") != 2 || !json.getString("type").equals("native"))
            throw new IOException("Formato de mod não suportado. Use native / formato 2.");
        id = string(json, "id", 64);
        if (!id.matches("[a-z0-9][a-z0-9._-]{1,63}")) throw new IOException("ID de mod inválido.");
        name = string(json, "name", 80);
        version = string(json, "version", 32);
        description = json.optString("description", "");
        if (description.length() > 400) throw new IOException("Descrição muito longa.");
        JSONObject game = json.getJSONObject("game");
        if (!PACKAGE.equals(game.getString("package"))) throw new IOException("Este mod não é para World Soccer Champs.");
        gameVersion = game.getLong("versionCode");
        if (gameVersion <= 0) throw new IOException("Versão do jogo inválida.");
        document = bytes.clone(); digest = sha256(document);
        if (json.getInt("api") != 1) throw new IOException("Este mod requer outra versão da Native Mod ABI.");
        JSONObject libraries = json.getJSONObject("libraries");
        List<NativeLibrary> parsedLibraries = new ArrayList<>();
        Iterator<String> architectures = libraries.keys();
        while (architectures.hasNext()) {
            String abi = architectures.next();
            if (!abi.equals("arm64-v8a") && !abi.equals("armeabi-v7a")) throw new IOException("ABI não suportada.");
            JSONObject lib = libraries.getJSONObject(abi);
            String path = string(lib, "path", 160);
            if (!path.matches("lib/" + abi + "/lib[a-z0-9_]+\\.so")) throw new IOException("Caminho de biblioteca inválido.");
            parsedLibraries.add(new NativeLibrary(abi, path, hash(lib, "sha256"), hash(lib, "gameLibrarySha256")));
        }
        if (parsedLibraries.isEmpty()) throw new IOException("Mod nativo sem biblioteca.");
        nativeLibraries = Collections.unmodifiableList(parsedLibraries);
        JSONArray hooks = json.getJSONArray("claims");
        if (hooks.length() == 0 || hooks.length() > 32) throw new IOException("Declare de 1 a 32 pontos de integração.");
        List<String> names = new ArrayList<>();
        for (int i = 0; i < hooks.length(); i++) {
            String claim = hooks.getString(i);
            if (!claim.matches("[A-Za-z0-9_.-]{1,100}") || names.contains(claim)) throw new IOException("Ponto de integração inválido/duplicado.");
            names.add(claim);
        }
        claims = Collections.unmodifiableList(names);
    }

    static ModSpec parse(byte[] bytes) throws IOException {
        if (bytes.length > 64 * 1024) throw new IOException("mod.json excede 64 KiB.");
        try { return new ModSpec(new JSONObject(utf8(bytes)), bytes); }
        catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("mod.json inválido: " + e.getMessage(), e); }
    }
    static String utf8(byte[] bytes) throws IOException {
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (Exception e) { throw new IOException("Texto não está em UTF-8.", e); }
    }
    static String sha256(byte[] bytes) {
        try {
            StringBuilder value = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
                value.append(String.format(Locale.ROOT, "%02x", b & 255));
            return value.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static String string(JSONObject json, String key, int max) throws Exception {
        String value = json.getString(key);
        if (value.isEmpty() || value.length() > max || value.chars().anyMatch(c -> c < 32))
            throw new IOException("Campo inválido: " + key);
        return value;
    }
    private static String hash(JSONObject json, String key) throws Exception {
        String value = string(json, key, 64);
        if (!value.matches("[0-9a-f]{64}")) throw new IOException("SHA256 inválido: " + key);
        return value;
    }
}
