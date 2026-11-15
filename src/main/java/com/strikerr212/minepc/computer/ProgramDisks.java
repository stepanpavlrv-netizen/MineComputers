package com.strikerr212.minepc.computer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Дискеты с программами: лежат в сундуках деревень. Файлы программы — в моде (data/minepc/computer/disks/&lt;id&gt;/,
 * список — disks.list); при первом подключении дискеты они копируются в её папку, дальше это обычная дискета.
 */
public final class ProgramDisks {

    /** Программа: номер, название, версия, цвет наклейки, вес в сундуках (чем больше — тем чаще). */
    public record Program(String id, String name, String version, int color, int weight) {
    }

    public static final List<Program> ALL = List.of(
            new Program("wired", "Wired", "1.0", 0xFF4F5E, 10),
            new Program("mail", "Почта", "1.0", 0xECE8F4, 8),
            new Program("office", "Офис", "1.0", 0x6B95FF, 5),
            new Program("cameras", "Камеры", "1.0", 0xFF8A3D, 5),
            new Program("browser", "Браузер", "1.0", 0x4FD1E8, 8),
            new Program("site", "Сайтостроитель", "1.0", 0xFFC857, 3));

    private static final String BASE = "/data/minepc/computer/";

    private ProgramDisks() {
    }

    public static Program get(String id) {
        if (id == null) return null;
        for (Program p : ALL) if (p.id().equals(id)) return p;
        return null;
    }

    public static int index(String id) {
        for (int i = 0; i < ALL.size(); i++) if (ALL.get(i).id().equals(id)) return i;
        return -1;
    }

    /** Файлы программы (пути относительно её папки). */
    public static List<String> files(String id) throws IOException {
        List<String> out = new ArrayList<>();
        try (InputStream in = ProgramDisks.class.getResourceAsStream(BASE + "disks.list")) {
            if (in == null) throw new IOException("нет disks.list");
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String f = line.trim();
                if (f.isEmpty() || f.startsWith("#")) continue;
                if (f.startsWith(id + "/")) out.add(f.substring(id.length() + 1));
            }
        }
        return out;
    }

    /** Записать файлы программы id в папку дискеты. */
    public static void populate(Path folder, String id) throws IOException {
        if (get(id) == null) return;
        Files.createDirectories(folder);
        for (String f : files(id)) {
            Path dst = folder.resolve(f).normalize();
            if (!dst.startsWith(folder)) continue;
            try (InputStream in = ProgramDisks.class.getResourceAsStream(BASE + "disks/" + id + "/" + f)) {
                if (in == null) continue;
                Files.createDirectories(dst.getParent());
                Files.write(dst, in.readAllBytes());
            }
        }
    }
}
