package launcher;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Game {
    public interface Progress {
        void step(String message, int percent);
        void log(String line);
    }

    private static final Gson GSON = new Gson();
    public static final String MINECRAFT_VERSION = "1.21.11";
    public static final String FABRIC_LOADER = "0.19.3";
    private static final String VERSION_MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String FABRIC_META = "https://meta.fabricmc.net/v2/versions/loader/%s/%s";
    private static final String FABRIC_MAVEN = "https://maven.fabricmc.net/";
    private static final String MC_MAVEN = "https://libraries.minecraft.net/";
    private static final String ASSET_BASE = "https://resources.download.minecraft.net/";
    private static final String VERSION_JSON_FALLBACK = "https://piston-meta.mojang.com/v1/packages/67d771169eafe895ee156ec74e64cceee2069c5d/1.21.11.json";

    public final Path base;
    public final Path gameDir;
    public final Path nativesDir;

    private final Progress progress;
    private final String username;
    private int ramMb = 2048;

    private String versionMeta;
    private String clientJar;
    private String clientJarPath;

    public Game(Path base, String username, Progress progress) {
        this.base = base;
        this.gameDir = base.resolve("game");
        this.nativesDir = base.resolve("natives");
        this.username = username;
        this.progress = progress;
    }

    public void setRamMb(int ramMb) {
        this.ramMb = Math.max(512, Math.min(16384, ramMb));
    }

    public void installAndLaunch() throws Exception {
        String versionUrl = resolveVersionUrl();
        progress.step("Загрузка описания версии...", 2);
        versionMeta = Net.get(versionUrl);
        JsonObject ver = GSON.fromJson(versionMeta, JsonObject.class);

        String jarName = MINECRAFT_VERSION + ".jar";
        clientJarPath = ver.get("downloads").getAsJsonObject().get("client").getAsJsonObject().get("path") == null
                ? null : ver.get("downloads").getAsJsonObject().get("client").getAsJsonObject().get("path").getAsString();
        clientJar = clientJarPath != null ? clientJarPath : "unknown-client-path";

        Path versionsDir = base.resolve("versions").resolve(MINECRAFT_VERSION);
        Path versionJson = versionsDir.resolve(MINECRAFT_VERSION + ".json");
        Path clientFile = versionsDir.resolve(jarName);

        Files.createDirectories(versionsDir);
        if (!Files.exists(versionJson))
            Files.writeString(versionJson, versionMeta);

        if (!Files.exists(clientFile) || Files.size(clientFile) < 1_000_000L) {
            progress.step("Скачивание клиента 1.21.11...", 5);
            String clientUrl = ver.get("downloads").getAsJsonObject().get("client").getAsJsonObject().get("url").getAsString();
            Net.download(clientUrl, clientFile);
        }

        progress.step("Библиотеки Minecraft...", 10);
        List<String> classpath = new ArrayList<>();
        classpath.add(clientFile.toString());
        installLibraries(ver, classpath);

        progress.step("Нативные библиотеки...", 45);
        extractNatives(ver);

        progress.step("Ресурсы (assets)...", 55);
        installAssets(ver);

        progress.step("Fabric Loader...", 80);
        String fabricMeta = Net.get(String.format(FABRIC_META, MINECRAFT_VERSION, FABRIC_LOADER));
        installFabric(fabricMeta, classpath);

        installMods();

        progress.step("Запуск Minecraft...", 95);
        launch(classpath);
    }

    private String resolveVersionUrl() throws Exception {
        try {
            String manifest = Net.get(VERSION_MANIFEST);
            JsonObject root = GSON.fromJson(manifest, JsonObject.class);
            for (JsonElement e : root.getAsJsonArray("versions")) {
                JsonObject v = e.getAsJsonObject();
                if (v.get("id").getAsString().equals(MINECRAFT_VERSION)) {
                    return v.get("url").getAsString();
                }
            }
        } catch (Exception ignored) {
        }
        return VERSION_JSON_FALLBACK;
    }

    // ---- Libraries ----

    private void installLibraries(JsonObject ver, List<String> classpath) throws Exception {
        JsonArray libs = ver.getAsJsonArray("libraries");
        List<String> ordered = new ArrayList<>();
        collectAllowed(libs, ordered);
        for (String s : ordered) {
            JsonObject l = GSON.fromJson(s, JsonObject.class);
            if (l.has("downloads")) {
                JsonObject downloads = l.getAsJsonObject("downloads");
                if (downloads.has("artifact")) {
                    JsonObject art = downloads.getAsJsonObject("artifact");
                    Path file = base.resolve("libraries").resolve(art.get("path").getAsString());
                    if (!Files.exists(file))
                        Net.download(art.get("url").getAsString(), file);
                    classpath.add(file.toString());
                }
            } else {
                String name = l.get("name").getAsString();
                String mavenUrl = l.has("url") ? l.get("url").getAsString() : MC_MAVEN;
                String[] parts = splitMaven(name);
                String path = parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/" + parts[1] + "-" + parts[2] + ".jar";
                Path file = base.resolve("libraries").resolve(path);
                if (!Files.exists(file))
                    Net.download(mavenUrl + path, file);
                classpath.add(file.toString());
            }
        }
    }

    private void collectAllowed(JsonArray libs, List<String> out) {
        for (JsonElement e : libs) {
            JsonObject l = e.getAsJsonObject();
            if (l.has("rules") && !rulesAllow(l.getAsJsonArray("rules")))
                continue;
            if (isNativesEntry(l))
                continue;
            out.add(l.toString());
        }
    }

    private boolean isNativesEntry(JsonObject l) {
        if (l.has("natives"))
            return l.getAsJsonObject("natives").has("windows");
        String name = l.has("name") ? l.get("name").getAsString() : "";
        String[] parts = splitMaven(name);
        return parts.length > 3 && parts[3] != null && parts[3].startsWith("natives");
    }

    private boolean nativesMatchesPlatform(JsonObject l) {
        String name = l.has("name") ? l.get("name").getAsString() : "";
        String[] parts = splitMaven(name);
        String classifier = parts.length > 3 ? parts[3] : "";
        if (classifier == null || !classifier.startsWith("natives-windows"))
            return false;
        String arch = System.getProperty("os.arch", "amd64").toLowerCase();
        boolean arm64 = arch.contains("aarch64") || arch.contains("arm64");
        boolean x86 = arch.contains("x86") && !arch.contains("amd64") && !arch.contains("x86_64");
        if (classifier.equals("natives-windows"))
            return !arm64 && !x86;
        if (classifier.equals("natives-windows-arm64"))
            return arm64;
        if (classifier.equals("natives-windows-x86"))
            return x86;
        return false;
    }

    private boolean rulesAllow(JsonArray rules) {
        boolean allow = false;
        for (JsonElement e : rules) {
            JsonObject r = e.getAsJsonObject();
            boolean pass = true;
            if (r.has("os")) {
                JsonObject os = r.getAsJsonObject("os");
                String name = os.has("name") ? os.get("name").getAsString() : "";
                if (!name.equals("windows"))
                    pass = false;
            }
            if (pass)
                allow = r.get("action").getAsString().equals("allow");
        }
        return allow;
    }

    private void extractNatives(JsonObject ver) throws Exception {
        JsonArray libs = ver.getAsJsonArray("libraries");
        Files.createDirectories(nativesDir);
        for (JsonElement e : libs) {
            JsonObject l = e.getAsJsonObject();
            if (l.has("rules") && !rulesAllow(l.getAsJsonArray("rules")))
                continue;
            if (!isNativesEntry(l) || !nativesMatchesPlatform(l))
                continue;
            String path;
            if (l.has("natives")) {
                JsonObject downloads = l.getAsJsonObject("downloads");
                JsonObject cls = downloads.getAsJsonObject("classifiers");
                JsonObject n = cls.getAsJsonObject("natives-windows");
                path = n.get("path").getAsString();
            } else {
                path = l.getAsJsonObject("downloads").getAsJsonObject("artifact").get("path").getAsString();
            }
            Path jar = base.resolve("libraries").resolve(path);
            if (!Files.exists(jar)) {
                String url = l.get("downloads").getAsJsonObject().get("artifact").getAsJsonObject().get("url").getAsString();
                Net.download(url, jar);
            }
            extractZip(jar, nativesDir, true);
        }
    }

    // ---- Assets ----

    private void installAssets(JsonObject ver) throws Exception {
        JsonObject ai = ver.getAsJsonObject("assetIndex");
        String id = ai.get("id").getAsString();
        String url = ai.get("url").getAsString();

        Path idxDir = base.resolve("assets").resolve("indexes");
        Path objects = base.resolve("assets").resolve("objects");
        Files.createDirectories(idxDir);
        Files.createDirectories(objects);

        Path indexFile = idxDir.resolve(id + ".json");
        if (!Files.exists(indexFile))
            Net.download(url, indexFile);

        JsonObject idx = GSON.fromJson(Files.readString(indexFile), JsonObject.class);
        JsonObject objs = idx.getAsJsonObject("objects");
        List<String> keys = new ArrayList<>(objs.keySet());

        AtomicInteger done = new AtomicInteger(0);
        int total = Math.max(1, keys.size());
        int threads = Math.min(8, Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        List<Thread> workers = new ArrayList<>();
        java.util.concurrent.ArrayBlockingQueue<String> queue = new java.util.concurrent.ArrayBlockingQueue<>(keys.size());
        queue.addAll(keys);

        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(() -> {
                while (true) {
                    String key;
                    try {
                        key = queue.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (InterruptedException ie) {
                        return;
                    }
                    if (key == null)
                        return;
                    try {
                        JsonObject obj = objs.getAsJsonObject(key);
                        String hash = obj.get("hash").getAsString();
                        String sub = hash.substring(0, 2);
                        Path dest = objects.resolve(sub).resolve(hash);
                        if (!Files.exists(dest)) {
                            Net.download(ASSET_BASE + sub + "/" + hash, dest);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        int d = done.incrementAndGet();
                        if (d % 200 == 0 || d == total)
                            progress.step("Ресурсы (assets)...", 55 + (int) (25f * d / total));
                    }
                }
            });
            t.setDaemon(true);
            workers.add(t);
            t.start();
        }
        for (Thread t : workers)
            t.join();
    }

    // ---- Fabric ----

    private void installFabric(String metaJson, List<String> classpath) throws Exception {
        JsonObject meta = GSON.fromJson(metaJson, JsonObject.class);
        String loaderMaven = meta.get("loader").getAsJsonObject().get("maven").getAsString();
        String intermediaryMaven = meta.get("intermediary").getAsJsonObject().get("maven").getAsString();

        String loaderPath = maven(loaderMaven);
        String intermediaryPath = maven(intermediaryMaven);

        Path loaderJar = base.resolve("libraries").resolve(loaderPath);
        Path intermediaryJar = base.resolve("libraries").resolve(intermediaryPath);
        if (!Files.exists(loaderJar))
            Net.download(FABRIC_MAVEN + loaderPath, loaderJar);
        if (!Files.exists(intermediaryJar))
            Net.download(FABRIC_MAVEN + intermediaryPath, intermediaryJar);

        classpath.add(loaderJar.toString());
        classpath.add(intermediaryJar.toString());

        JsonObject lm = meta.getAsJsonObject("launcherMeta");
        JsonObject libs = lm.getAsJsonObject("libraries");
        for (String section : new String[]{"common", "client"}) {
            JsonArray arr = libs.getAsJsonArray(section);
            if (arr == null)
                continue;
            for (JsonElement e : arr) {
                JsonObject l = e.getAsJsonObject();
                String name = l.get("name").getAsString();
                String url = l.has("url") ? l.get("url").getAsString() : FABRIC_MAVEN;
                String p = maven(name);
                Path f = base.resolve("libraries").resolve(p);
                if (!Files.exists(f))
                    Net.download(url + p, f);
                classpath.add(f.toString());
            }
        }
    }

    private String maven(String coords) {
        String[] parts = splitMaven(coords);
        String filename = parts[1] + "-" + parts[2];
        if (parts.length > 3 && parts[3] != null && !parts[3].isEmpty())
            filename += "-" + parts[3];
        if (parts.length > 4 && parts[4] != null && !parts[4].isEmpty())
            filename += "-" + parts[4];
        return parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/" + filename + ".jar";
    }

    private String[] splitMaven(String coords) {
        String[] basic = coords.split(":");
        String[] out = new String[5];
        for (int i = 0; i < basic.length && i < 5; i++)
            out[i] = basic[i];
        return out;
    }

    // ---- Mods ----

    private void installMods() throws Exception {
        Path modsDir = gameDir.resolve("mods");
        Files.createDirectories(modsDir);
        String[] embedded = {"workup-1.6.1.jar", "fabric-api-0.141.6+1.21.11.jar"};
        for (String name : embedded) {
            InputStream in = getClass().getResourceAsStream("/mods/" + name);
            if (in == null)
                throw new FileNotFoundException("Встроенный мод не найден: " + name);
            Path dest = modsDir.resolve(name);
            if (!Files.exists(dest)) {
                try (in) {
                    Files.copy(in, dest);
                }
                progress.log("Мод установлен: " + name);
            }
        }
    }

    // ---- Launch ----

    private void launch(List<String> classpath) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe());
        cmd.add("-Xmx" + ramMb + "m");
        cmd.add("-Djava.library.path=" + nativesDir.toAbsolutePath());
        cmd.add("-Djna.tmpdir=" + nativesDir.toAbsolutePath());
        cmd.add("-Dorg.lwjgl.system.SharedLibraryExtractPath=" + nativesDir.toAbsolutePath());
        cmd.add("-Dio.netty.native.workdir=" + nativesDir.toAbsolutePath());
        cmd.add("-Dminecraft.launcher.brand=workup");
        cmd.add("-Dminecraft.launcher.version=1.6.1");
        cmd.add("-cp");
        cmd.add(String.join(File.pathSeparator, classpath));
        cmd.add("net.fabricmc.loader.impl.launch.knot.KnotClient");
        cmd.add("--gameDir"); cmd.add(gameDir.toAbsolutePath().toString());
        cmd.add("--assetsDir"); cmd.add(base.resolve("assets").toAbsolutePath().toString());
        cmd.add("--assetIndex"); cmd.add("1.21.11");
        cmd.add("--uuid"); cmd.add(UUID.randomUUID().toString());
        cmd.add("--username"); cmd.add(username);
        cmd.add("--userType"); cmd.add("legacy");
        cmd.add("--accessToken"); cmd.add("0");
        cmd.add("--version"); cmd.add(MINECRAFT_VERSION);

        progress.log("Запуск: " + String.join(" ", cmd.subList(0, 4)) + " ...");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(gameDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = br.readLine()) != null)
            progress.log(line);
        p.waitFor();
    }

    private String javaExe() {
        String javaHome = System.getProperty("java.home");
        Path exe = Paths.get(javaHome, "bin", "java.exe");
        if (Files.exists(exe))
            return exe.toString();
        return "java";
    }

    private void extractZip(Path zip, Path dest, boolean overwrite) throws Exception {
        Files.createDirectories(dest);
        java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip.toFile());
        java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            java.util.zip.ZipEntry entry = en.nextElement();
            if (entry.isDirectory())
                continue;
            Path out = dest.resolve(entry.getName());
            if (!overwrite && Files.exists(out))
                continue;
            Files.createDirectories(out.getParent());
            try (InputStream is = zf.getInputStream(entry)) {
                Files.copy(is, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        zf.close();
    }
}