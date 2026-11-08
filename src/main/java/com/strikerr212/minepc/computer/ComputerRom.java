package com.strikerr212.minepc.computer;

import com.strikerr212.minepc.computer.fs.RomMount;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Встроенная система: BIOS и файлы ПЗУ (/rom). Лежат в моде в data/minepc/computer/.
 * Список файлов ПЗУ — в rom.list (его обновляет tools/computer/update_rom_list.py).
 */
public final class ComputerRom {

    private static final String BASE = "/data/minepc/computer/";
    private static byte[] bios;
    private static Map<String, byte[]> rom;

    private ComputerRom() {
    }

    private static byte[] load(String path) throws IOException {
        try (InputStream in = ComputerRom.class.getResourceAsStream(BASE + path)) {
            if (in == null) throw new IOException("нет файла " + path);
            return in.readAllBytes();
        }
    }

    /** Исходники лежат в UTF-8 (так их удобно править), а компьютер работает в Windows-1251. */
    private static byte[] toCp1251(byte[] utf8) {
        return Cp1251.encode(new String(utf8, StandardCharsets.UTF_8));
    }

    public static synchronized byte[] bios() {
        if (bios == null) {
            try {
                bios = toCp1251(load("bios.lua"));
            } catch (IOException e) {
                bios = Cp1251.encode("error('Нет BIOS: " + e.getMessage() + "')");
            }
        }
        return bios;
    }

    public static synchronized RomMount mount() {
        if (rom == null) {
            rom = new HashMap<>();
            try {
                String list = new String(load("rom.list"), StandardCharsets.UTF_8);
                for (String line : list.split("\n")) {
                    String f = line.trim();
                    if (f.isEmpty() || f.startsWith("#")) continue;
                    byte[] data = load("rom/" + f);
                    rom.put(f, f.endsWith(".lua") || f.endsWith(".txt") || !f.contains(".") ? toCp1251(data) : data);
                }
            } catch (IOException e) {
                rom.put("ERROR.txt", Cp1251.encode("ПЗУ не загрузилось: " + e.getMessage()));
            }
        }
        return new RomMount(rom);
    }
}
