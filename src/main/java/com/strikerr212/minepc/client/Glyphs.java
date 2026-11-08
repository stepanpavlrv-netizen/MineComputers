package com.strikerr212.minepc.client;

import net.minecraft.client.gui.Font;

/** Кэш символов экрана: строка и ширина глифа для каждого символа Юникода. */
final class Glyphs {

    private static final String[] TEXT = new String[65536];
    private static final short[] WIDTH = new short[65536];
    private static Font font;

    private Glyphs() {
    }

    static String text(char c) {
        String s = TEXT[c];
        if (s == null) TEXT[c] = s = String.valueOf(c);
        return s;
    }

    static int width(Font f, char c) {
        if (f != font) {
            font = f;
            java.util.Arrays.fill(WIDTH, (short) -1);
        }
        if (WIDTH[c] < 0) WIDTH[c] = (short) f.width(text(c));
        return WIDTH[c];
    }
}
