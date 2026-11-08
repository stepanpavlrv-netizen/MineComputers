package com.strikerr212.minepc.computer;

import java.nio.charset.Charset;

/**
 * Кодировка компьютеров — Windows-1251: один байт на символ, есть латиница и кириллица.
 * Так строки в Lua остаются простыми: длина строки = число символов, string.sub не режет буквы пополам.
 */
public final class Cp1251 {

    public static final Charset CHARSET = Charset.forName("windows-1251");

    private Cp1251() {
    }

    public static byte[] encode(String s) {
        return s.getBytes(CHARSET);
    }

    public static String decode(byte[] b) {
        return new String(b, CHARSET);
    }

    public static String decode(byte[] b, int off, int len) {
        return new String(b, off, len, CHARSET);
    }

    /** Можно ли набрать этот символ на компьютере (есть ли он в кодировке). */
    public static boolean supports(char c) {
        return c >= 32 && c != 127 && CHARSET.newEncoder().canEncode(c);
    }
}
