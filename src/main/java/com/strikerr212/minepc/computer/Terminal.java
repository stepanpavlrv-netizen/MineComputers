package com.strikerr212.minepc.computer;

import java.util.Arrays;

/**
 * Текстовый экран компьютера: сетка символов, у каждого свой цвет текста и фона.
 *
 * Символы хранятся байтами в кодировке Windows-1251 (латиница + кириллица), цвета — номерами
 * палитры 0..15. Пишет в терминал поток Lua, читает (чтобы отправить игрокам) — поток сервера,
 * поэтому все методы синхронизированы.
 */
public final class Terminal {

    /** 16 цветов, как в старых терминалах. Номер = индекс. */
    public static final int[] PALETTE = {
            0xF0F0F0, 0xF2B233, 0xE57FD8, 0x99B2F2, 0xDEDE6C, 0x7FCC19, 0xF2B2CC, 0x4C4C4C,
            0x999999, 0x4C99B2, 0xB266E5, 0x3366CC, 0x7F664C, 0x57A64E, 0xCC4C4C, 0x111111};
    public static final int WHITE = 0, GRAY = 8, BLACK = 15;

    private int width, height;
    private byte[] chars, fg, bg;
    private int cursorX, cursorY;
    private boolean cursorBlink = true;
    private int textColor = WHITE, backColor = BLACK;
    /** Без видеокарты экран монохромный: доступны только белый, серые и чёрный. */
    private boolean color = true;
    /** Растёт при каждом изменении — по нему сервер понимает, что пора отправить экран игрокам. */
    private int version;

    public Terminal(int width, int height) {
        resize(width, height);
    }

    public synchronized void resize(int w, int h) {
        width = Math.max(1, w);
        height = Math.max(1, h);
        chars = new byte[width * height];
        fg = new byte[width * height];
        bg = new byte[width * height];
        clear();
        cursorX = cursorY = 0;
    }

    public synchronized void setColorSupported(boolean supported) {
        color = supported;
        if (!supported) {
            textColor = WHITE;
            backColor = BLACK;
        }
    }

    public synchronized boolean isColor() {
        return color;
    }

    public synchronized int width() { return width; }
    public synchronized int height() { return height; }
    public synchronized int cursorX() { return cursorX; }
    public synchronized int cursorY() { return cursorY; }
    public synchronized int version() { return version; }
    public synchronized int textColor() { return textColor; }
    public synchronized int backColor() { return backColor; }

    private int allowed(int c) {
        c &= 15;
        if (color) return c;
        return c == WHITE || c == GRAY || c == 7 || c == BLACK ? c : WHITE;
    }

    public synchronized void setTextColor(int c) { textColor = allowed(c); }
    public synchronized void setBackColor(int c) { backColor = allowed(c); }

    public synchronized void setCursor(int x, int y) {
        cursorX = x;
        cursorY = y;
        version++;
    }

    public synchronized void setCursorBlink(boolean blink) {
        cursorBlink = blink;
        version++;
    }

    /** Написать текст с позиции курсора (без переноса строк — как в CC и OpenComputers). */
    public synchronized void write(byte[] text) {
        if (cursorY >= 0 && cursorY < height) {
            for (int i = 0; i < text.length; i++) {
                int x = cursorX + i;
                if (x < 0) continue;
                if (x >= width) break;
                int p = cursorY * width + x;
                chars[p] = text[i];
                fg[p] = (byte) textColor;
                bg[p] = (byte) backColor;
            }
        }
        cursorX += text.length;
        version++;
    }

    /** Сдвинуть содержимое вверх на n строк (n < 0 — вниз). */
    public synchronized void scroll(int n) {
        if (n == 0) return;
        byte[] c = new byte[chars.length], f = new byte[fg.length], b = new byte[bg.length];
        Arrays.fill(c, (byte) ' ');
        Arrays.fill(f, (byte) textColor);
        Arrays.fill(b, (byte) backColor);
        for (int y = 0; y < height; y++) {
            int from = y + n;
            if (from < 0 || from >= height) continue;
            System.arraycopy(chars, from * width, c, y * width, width);
            System.arraycopy(fg, from * width, f, y * width, width);
            System.arraycopy(bg, from * width, b, y * width, width);
        }
        chars = c;
        fg = f;
        bg = b;
        version++;
    }

    public synchronized void clear() {
        Arrays.fill(chars, (byte) ' ');
        Arrays.fill(fg, (byte) textColor);
        Arrays.fill(bg, (byte) backColor);
        version++;
    }

    public synchronized void clearLine() {
        if (cursorY < 0 || cursorY >= height) return;
        int p = cursorY * width;
        Arrays.fill(chars, p, p + width, (byte) ' ');
        Arrays.fill(fg, p, p + width, (byte) textColor);
        Arrays.fill(bg, p, p + width, (byte) backColor);
        version++;
    }

    /** Строка экрана как текст (для проверок и отладки). */
    public synchronized String line(int y) {
        return Cp1251.decode(Arrays.copyOfRange(chars, y * width, (y + 1) * width));
    }

    /**
     * Снимок экрана («кадр») для отправки игрокам, формат см. {@link TerminalCodec}:
     * ширина, высота, курсор X и Y (по 2 байта), мигание, цвет текста, затем символы
     * и цвета клеток (старшие 4 бита — цвет текста, младшие — фон).
     */
    public synchronized byte[] snapshot() {
        int n = width * height;
        byte[] out = new byte[TerminalCodec.HEADER + n * 2];
        out[0] = (byte) width;
        out[1] = (byte) height;
        int cx = Math.max(-1, Math.min(32000, cursorX)), cy = Math.max(-1, Math.min(32000, cursorY));
        out[2] = (byte) cx;
        out[3] = (byte) (cx >> 8);
        out[4] = (byte) cy;
        out[5] = (byte) (cy >> 8);
        out[6] = (byte) (cursorBlink ? 1 : 0);
        out[7] = (byte) textColor;
        System.arraycopy(chars, 0, out, TerminalCodec.HEADER, n);
        for (int i = 0; i < n; i++) out[TerminalCodec.HEADER + n + i] = (byte) ((fg[i] & 15) << 4 | (bg[i] & 15));
        return out;
    }
}
