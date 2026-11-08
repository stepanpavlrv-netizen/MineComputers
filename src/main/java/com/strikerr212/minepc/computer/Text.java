package com.strikerr212.minepc.computer;

import java.nio.charset.StandardCharsets;

/**
 * Текст в компьютере — UTF-8, как в OpenComputers: в строках Lua лежат байты UTF-8, а на экране
 * каждый символ Юникода (кириллица, рамки, стрелки, полублоки) занимает одну клетку.
 */
public final class Text {

    private Text() {
    }

    public static byte[] encode(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public static String decode(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }

    public static String decode(byte[] b, int off, int len) {
        return new String(b, off, len, StandardCharsets.UTF_8);
    }

    /** Можно ли набрать символ (печатный, не половинка суррогатной пары). */
    public static boolean supports(char c) {
        return c >= 32 && c != 127 && !Character.isSurrogate(c);
    }

    /** Убрать символы за пределами основной плоскости Юникода (эмодзи и т. п.): экран их не покажет. */
    public static String bmp(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            b.append(cp < 0x10000 ? (char) cp : '?');
            i += Character.charCount(cp);
        }
        return b.toString();
    }
}
