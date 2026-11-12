package com.strikerr212.minepc.client;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.hardware.HardwareType;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Свои шаблоны сборочного стола. Хранятся у игрока на компьютере (config/minepc_templates.txt),
 * поэтому работают на любом сервере. Строка файла: ВИД ⇥ имя ⇥ надпись ⇥ 1,2,3…
 */
public final class AssemblyTemplates {

    /** Больше этого на одну вкладку не держим — список должен помещаться на экране. */
    public static final int MAX_PER_TYPE = 8;

    public record Saved(HardwareType type, String name, String label, List<Integer> values) {
    }

    private static List<Saved> all;

    private AssemblyTemplates() {
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("minepc_templates.txt");
    }

    private static List<Saved> all() {
        if (all != null) return all;
        all = new ArrayList<>();
        try {
            Path f = file();
            if (Files.exists(f)) {
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    String[] p = line.split("\t", -1);
                    if (p.length != 4) continue;
                    try {
                        List<Integer> v = new ArrayList<>();
                        for (String n : p[3].split(",")) if (!n.isBlank()) v.add(Integer.parseInt(n.trim()));
                        all.add(new Saved(HardwareType.valueOf(p[0]), p[1], p[2], v));
                    } catch (IllegalArgumentException ignored) {
                        // испорченная строка — пропускаем
                    }
                }
            }
        } catch (IOException e) {
            MineComputers.LOGGER.warn("Не удалось прочитать шаблоны сборочного стола", e);
        }
        return all;
    }

    public static List<Saved> of(HardwareType type) {
        List<Saved> out = new ArrayList<>();
        for (Saved s : all()) if (s.type() == type) out.add(s);
        return out;
    }

    /** Сохранить шаблон; с тем же именем — заменить. false — места нет. */
    public static boolean add(Saved saved) {
        List<Saved> list = all();
        list.removeIf(s -> s.type() == saved.type() && s.name().equals(saved.name()));
        if (of(saved.type()).size() >= MAX_PER_TYPE) return false;
        list.add(saved);
        save();
        return true;
    }

    public static void remove(Saved saved) {
        if (all().remove(saved)) save();
    }

    private static void save() {
        StringBuilder sb = new StringBuilder();
        for (Saved s : all()) {
            StringBuilder v = new StringBuilder();
            for (int i = 0; i < s.values().size(); i++) {
                if (i > 0) v.append(',');
                v.append(s.values().get(i));
            }
            sb.append(s.type().name()).append('\t').append(clean(s.name())).append('\t').append(clean(s.label()))
                    .append('\t').append(v).append('\n');
        }
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MineComputers.LOGGER.warn("Не удалось сохранить шаблоны сборочного стола", e);
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ');
    }
}
