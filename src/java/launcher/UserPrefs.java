package launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class UserPrefs {
    private static final Path PREFS = Paths.get(System.getenv("APPDATA"), ".workuplauncher", "prefs.properties");

    public static String getNick() {
        return read("nick");
    }

    public static void saveNick(String nick) {
        write("nick", nick);
    }

    public static int getRamMb() {
        String v = read("ramMb");
        if (v == null) return 4096;
        try {
            int n = Integer.parseInt(v.trim());
            return Math.max(1024, Math.min(16384, n));
        } catch (Exception e) {
            return 4096;
        }
    }

    public static void saveRamMb(int ramMb) {
        write("ramMb", String.valueOf(Math.max(1024, Math.min(16384, ramMb))));
    }

    public static Path gameDir() {
        return Paths.get(System.getenv("APPDATA"), "Workup Launcher");
    }

    private static String read(String key) {
        try {
            if (!Files.exists(PREFS))
                return null;
            for (String line : Files.readAllLines(PREFS, StandardCharsets.UTF_8)) {
                int i = line.indexOf('=');
                if (i > 0 && line.substring(0, i).equals(key))
                    return line.substring(i + 1);
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static void write(String key, String value) {
        try {
            Files.createDirectories(PREFS.getParent());
            StringBuilder sb = new StringBuilder();
            if (Files.exists(PREFS)) {
                boolean found = false;
                for (String line : Files.readAllLines(PREFS, StandardCharsets.UTF_8)) {
                    int i = line.indexOf('=');
                    if (i > 0 && line.substring(0, i).equals(key)) {
                        sb.append(key).append('=').append(value).append('\n');
                        found = true;
                    } else {
                        sb.append(line).append('\n');
                    }
                }
                if (!found)
                    sb.append(key).append('=').append(value).append('\n');
            } else {
                sb.append(key).append('=').append(value).append('\n');
            }
            Files.writeString(PREFS, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}