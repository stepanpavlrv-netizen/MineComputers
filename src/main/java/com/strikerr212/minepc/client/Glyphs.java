package com.strikerr212.minepc.client;

import com.strikerr212.minepc.computer.Cp1251;
import net.minecraft.client.gui.Font;

/** Кэш символов кодировки компьютера: строка и ширина для каждого из 256 байтов. */
final class Glyphs {

    private static final String[] TEXT = new String[256];
    private static final int[] WIDTH = new int[256];
    private static Font font;

    private Glyphs() {
    }

    static String text(byte b) {
        int i = b & 0xFF;
        if (TEXT[i] == null) TEXT[i] = Cp1251.decode(new byte[]{b});
        return TEXT[i];
    }

    static int width(Font f, byte b) {
        if (f != font) {
            font = f;
            java.util.Arrays.fill(WIDTH, -1);
        }
        int i = b & 0xFF;
        if (WIDTH[i] < 0) WIDTH[i] = f.width(text(b));
        return WIDTH[i];
    }
}
