package com.strikerr212.minepc.computer;

import java.util.Arrays;

/**
 * Текстовый экран компьютера: сетка символов Юникода, у каждой клетки свой цвет текста и фона (RGB).
 *
 * Сколько цветов реально видно, зависит от видеокарты (глубина цвета):
 * 1 бит — чёрное и белое, 4 бита — 16 цветов палитры, 8 бит — палитра + 240 цветов (как в OpenComputers),
 * 24 бита — любой цвет. Запрошенный цвет округляется до ближайшего доступного.
 *
 * Пишет в терминал поток Lua, читает (чтобы отправить игрокам) — поток сервера,
 * поэтому все методы синхронизированы.
 */
public final class Terminal {

    /** Стартовая палитра (16 цветов, как в CC/OpenComputers). Номер = индекс. */
    public static final int[] DEFAULT_PALETTE = {
            0xF0F0F0, 0xF2B233, 0xE57FD8, 0x99B2F2, 0xDEDE6C, 0x7FCC19, 0xF2B2CC, 0x4C4C4C,
            0x999999, 0x4C99B2, 0xB266E5, 0x3366CC, 0x7F664C, 0x57A64E, 0xCC4C4C, 0x111111};
    public static final int WHITE = 0, GRAY = 8, BLACK = 15;

    private int width, height;
    private int maxWidth, maxHeight;
    private char[] chars;
    private int[] fg, bg;
    private int cursorX, cursorY;
    private boolean cursorBlink = true;
    private final int[] palette = DEFAULT_PALETTE.clone();
    private int textColor = DEFAULT_PALETTE[WHITE], backColor = DEFAULT_PALETTE[BLACK];
    /** Номер цвета палитры, если цвет задан номером (для term.getTextColor), иначе -1. */
    private int textIndex = WHITE, backIndex = BLACK;
    private int depth = 4, maxDepth = 4;
    /** Растёт при каждом изменении — по нему сервер понимает, что пора отправить экран игрокам. */
    private int version;

    public Terminal(int width, int height) {
        setMaxSize(width, height);
    }

    // ======================= Размер =======================

    /** Самый большой экран, который умеет передавать {@link TerminalCodec}. */
    public static final int LIMIT_WIDTH = 320, LIMIT_HEIGHT = 100;

    /** Максимальный размер (его даёт видеокарта); текущий размер становится максимальным. */
    public synchronized void setMaxSize(int w, int h) {
        setLimit(w, h);
        resize(maxWidth, maxHeight);
    }

    /** Размер буфера видеопамяти: он не передаётся игрокам, поэтому может быть больше экрана. */
    synchronized void setLimitUnchecked(int w, int h) {
        maxWidth = Math.max(1, w);
        maxHeight = Math.max(1, h);
        resize(maxWidth, maxHeight);
    }

    /**
     * Сменить максимальный размер, не трогая экран, если он помещается (монитор достроили или разобрали
     * на работающем компьютере). Возвращает true, если пришлось уменьшить текущий размер.
     */
    public synchronized boolean setLimit(int w, int h) {
        maxWidth = Math.max(1, Math.min(LIMIT_WIDTH, w));
        maxHeight = Math.max(1, Math.min(LIMIT_HEIGHT, h));
        if (width > maxWidth || height > maxHeight) {
            resize(Math.min(width, maxWidth), Math.min(height, maxHeight));
            return true;
        }
        return false;
    }

    /** Сменить разрешение (не больше максимального). Экран очищается. */
    public synchronized boolean resize(int w, int h) {
        w = Math.max(1, Math.min(maxWidth, w));
        h = Math.max(1, Math.min(maxHeight, h));
        boolean changed = w != width || h != height;
        width = w;
        height = h;
        chars = new char[w * h];
        fg = new int[w * h];
        bg = new int[w * h];
        clear();
        cursorX = cursorY = 0;
        return changed;
    }

    public synchronized int width() { return width; }
    public synchronized int height() { return height; }
    public synchronized int maxWidth() { return maxWidth; }
    public synchronized int maxHeight() { return maxHeight; }
    public synchronized int cursorX() { return cursorX; }
    public synchronized int cursorY() { return cursorY; }
    public synchronized boolean cursorBlink() { return cursorBlink; }
    public synchronized int version() { return version; }

    // ======================= Цвета =======================

    /** Глубина цвета видеокарты: 1, 4, 8 или 24 бита. */
    public synchronized void setMaxDepth(int bits) {
        maxDepth = bits;
        depth = bits;
        textColor = quantize(textColor);
        backColor = quantize(backColor);
    }

    public synchronized int maxDepth() { return maxDepth; }
    public synchronized int depth() { return depth; }

    public synchronized boolean setDepth(int bits) {
        if (bits != 1 && bits != 4 && bits != 8 && bits != 24 || bits > maxDepth) return false;
        depth = bits;
        return true;
    }

    public synchronized boolean isColor() {
        return depth > 1;
    }

    public synchronized int paletteColor(int index) {
        return palette[index & 15];
    }

    public synchronized void setPaletteColor(int index, int rgb) {
        palette[index & 15] = rgb & 0xFFFFFF;
    }

    /** Округлить цвет до того, что умеет показывать видеокарта. */
    public synchronized int quantize(int rgb) {
        rgb &= 0xFFFFFF;
        switch (depth) {
            case 24:
                return rgb;
            case 1:
                return luminance(rgb) >= 128 ? 0xFFFFFF : 0x000000;
            case 4:
                return palette[nearestPalette(rgb)];
            default: {
                int best = palette[nearestPalette(rgb)];
                int cube = cubeColor(rgb);
                return distance(rgb, cube) < distance(rgb, best) ? cube : best;
            }
        }
    }

    private int nearestPalette(int rgb) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < 16; i++) {
            double d = distance(rgb, palette[i]);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    /** Ближайший цвет «куба» 6×8×5 (240 фиксированных цветов, как у видеокарты OpenComputers). */
    private static int cubeColor(int rgb) {
        int r = Math.round(((rgb >> 16) & 255) * 5 / 255f), g = Math.round(((rgb >> 8) & 255) * 7 / 255f),
                b = Math.round((rgb & 255) * 4 / 255f);
        return (Math.round(r * 255 / 5f) << 16) | (Math.round(g * 255 / 7f) << 8) | Math.round(b * 255 / 4f);
    }

    private static int luminance(int rgb) {
        return (int) (((rgb >> 16) & 255) * 0.3 + ((rgb >> 8) & 255) * 0.59 + (rgb & 255) * 0.11);
    }

    private static double distance(int a, int b) {
        int dr = ((a >> 16) & 255) - ((b >> 16) & 255), dg = ((a >> 8) & 255) - ((b >> 8) & 255), db = (a & 255) - (b & 255);
        return dr * dr * 0.3 + dg * dg * 0.59 + db * db * 0.11;
    }

    /** Цвет текста номером палитры (0..15). */
    public synchronized void setTextIndex(int i) {
        textIndex = i & 15;
        textColor = depth == 1 ? quantize(palette[textIndex]) : palette[textIndex];
    }

    public synchronized void setBackIndex(int i) {
        backIndex = i & 15;
        backColor = depth == 1 ? quantize(palette[backIndex]) : palette[backIndex];
    }

    /** Цвет текста в RGB (округляется до возможностей видеокарты). */
    public synchronized void setTextRgb(int rgb) {
        textIndex = -1;
        textColor = quantize(rgb);
    }

    public synchronized void setBackRgb(int rgb) {
        backIndex = -1;
        backColor = quantize(rgb);
    }

    public synchronized int textIndex() { return textIndex; }
    public synchronized int backIndex() { return backIndex; }
    public synchronized int textRgb() { return textColor; }
    public synchronized int backRgb() { return backColor; }

    // ======================= Курсор и запись =======================

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
    public synchronized void write(String text) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            put(cursorX + n, cursorY, text.charAt(i));
            n++;
        }
        cursorX += n;
        version++;
    }

    private void put(int x, int y, char c) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        int p = y * width + x;
        chars[p] = c;
        fg[p] = textColor;
        bg[p] = backColor;
    }

    /** Написать текст в клетку (x, y) без движения курсора; vertical — сверху вниз (для gpu.set). */
    public synchronized void set(int x, int y, String text, boolean vertical) {
        for (int i = 0; i < text.length(); i++) {
            if (vertical) put(x, y + i, text.charAt(i));
            else put(x + i, y, text.charAt(i));
        }
        version++;
    }

    /**
     * Вывести картинку полублоками «▀»: каждая клетка — две точки (верх — цвет текста, низ — фон).
     * px — цвета RGB строками по pw точек, ph строк. Возвращает, сколько клеток изменилось.
     */
    public synchronized int putPixels(int x, int y, int[] px, int pw, int ph) {
        int rows = (ph + 1) / 2, changed = 0;
        java.util.Map<Integer, Integer> cache = depth < 24 ? new java.util.HashMap<>() : null;
        for (int r = 0; r < rows; r++) {
            int ty = y + r;
            if (ty < 0 || ty >= height) continue;
            int top0 = 2 * r * pw, bot0 = (2 * r + 1 < ph ? 2 * r + 1 : 2 * r) * pw;
            for (int c = 0; c < pw; c++) {
                int tx = x + c;
                if (tx < 0 || tx >= width) continue;
                int top = px[top0 + c] & 0xFFFFFF, bot = px[bot0 + c] & 0xFFFFFF;
                if (cache != null) {
                    int t0 = top, b0 = bot;
                    top = cache.computeIfAbsent(t0, this::quantize);
                    bot = cache.computeIfAbsent(b0, this::quantize);
                }
                int p = ty * width + tx;
                if (chars[p] != '\u2580' || fg[p] != top || bg[p] != bot) {
                    chars[p] = '\u2580';
                    fg[p] = top;
                    bg[p] = bot;
                    changed++;
                }
            }
        }
        if (changed > 0) version++;
        return changed;
    }

    /** Залить прямоугольник символом текущими цветами. */
    public synchronized void fill(int x, int y, int w, int h, char c) {
        for (int yy = Math.max(0, y); yy < Math.min(height, y + h); yy++) {
            for (int xx = Math.max(0, x); xx < Math.min(width, x + w); xx++) put(xx, yy, c);
        }
        version++;
    }

    /** Скопировать прямоугольник со сдвигом (tx, ty). */
    public synchronized void copy(int x, int y, int w, int h, int tx, int ty) {
        char[] c = chars.clone();
        int[] f = fg.clone(), b = bg.clone();
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                if (xx < 0 || yy < 0 || xx >= width || yy >= height) continue;
                int dx = xx + tx, dy = yy + ty;
                if (dx < 0 || dy < 0 || dx >= width || dy >= height) continue;
                int s = yy * width + xx, d = dy * width + dx;
                chars[d] = c[s];
                fg[d] = f[s];
                bg[d] = b[s];
            }
        }
        version++;
    }

    /** Символ и цвета клетки: [символ, цвет текста, цвет фона] или null вне экрана. */
    public synchronized int[] get(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return null;
        int p = y * width + x;
        return new int[]{chars[p], fg[p], bg[p]};
    }

    /** Сдвинуть содержимое вверх на n строк (n < 0 — вниз). */
    public synchronized void scroll(int n) {
        if (n == 0) return;
        char[] c = new char[chars.length];
        int[] f = new int[fg.length], b = new int[bg.length];
        Arrays.fill(c, ' ');
        Arrays.fill(f, textColor);
        Arrays.fill(b, backColor);
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
        Arrays.fill(chars, ' ');
        Arrays.fill(fg, textColor);
        Arrays.fill(bg, backColor);
        version++;
    }

    public synchronized void clearLine() {
        if (cursorY < 0 || cursorY >= height) return;
        int p = cursorY * width;
        Arrays.fill(chars, p, p + width, ' ');
        Arrays.fill(fg, p, p + width, textColor);
        Arrays.fill(bg, p, p + width, backColor);
        version++;
    }

    /** Вырезать прямоугольник: {символы, цвета текста, цвета фона, ширина, высота} (клетки вне экрана — пробелы). */
    public synchronized Object[] region(int x, int y, int w, int h) {
        w = Math.max(0, w);
        h = Math.max(0, h);
        char[] c = new char[w * h];
        int[] f = new int[w * h], b = new int[w * h];
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                int sx = x + xx, sy = y + yy, d = yy * w + xx;
                if (sx < 0 || sy < 0 || sx >= width || sy >= height) {
                    c[d] = ' ';
                    f[d] = textColor;
                    b[d] = backColor;
                } else {
                    int p = sy * width + sx;
                    c[d] = chars[p];
                    f[d] = fg[p];
                    b[d] = bg[p];
                }
            }
        }
        return new Object[]{c, f, b, w, h};
    }

    /** Вставить прямоугольник, вырезанный {@link #region}, в клетку (x, y). Возвращает число изменённых клеток. */
    public synchronized int paste(Object[] r, int x, int y) {
        char[] c = (char[]) r[0];
        int[] f = (int[]) r[1], b = (int[]) r[2];
        int w = (int) r[3], h = (int) r[4], n = 0;
        for (int yy = 0; yy < h; yy++) {
            int dy = y + yy;
            if (dy < 0 || dy >= height) continue;
            for (int xx = 0; xx < w; xx++) {
                int dx = x + xx;
                if (dx < 0 || dx >= width) continue;
                int s = yy * w + xx, p = dy * width + dx;
                chars[p] = c[s];
                fg[p] = f[s];
                bg[p] = b[s];
                n++;
            }
        }
        version++;
        return n;
    }

    /** Сколько байт памяти занимает экран (для видеопамяти): символ 2 + два цвета по 4. */
    public synchronized long cells() {
        return (long) width * height;
    }

    /** Строка экрана как текст (для проверок и отладки). */
    public synchronized String line(int y) {
        return new String(chars, y * width, width);
    }

    /**
     * Снимок экрана («кадр») для отправки игрокам, формат см. {@link TerminalCodec}:
     * ширина, высота, курсор X и Y (по 2 байта), мигание, глубина цвета, затем символы (по 2 байта),
     * цвета текста и цвета фона (по 3 байта на клетку).
     */
    public synchronized byte[] snapshot() {
        int n = width * height;
        byte[] out = new byte[TerminalCodec.HEADER + n * TerminalCodec.CELL];
        out[0] = (byte) width;
        out[1] = (byte) (width >> 8);
        out[2] = (byte) height;
        out[3] = (byte) (height >> 8);
        int cx = Math.max(-1, Math.min(32000, cursorX)), cy = Math.max(-1, Math.min(32000, cursorY));
        out[4] = (byte) cx;
        out[5] = (byte) (cx >> 8);
        out[6] = (byte) cy;
        out[7] = (byte) (cy >> 8);
        out[8] = (byte) (cursorBlink ? 1 : 0);
        out[9] = (byte) depth;
        int c = TerminalCodec.HEADER, f = c + 2 * n, b = f + 3 * n;
        for (int i = 0; i < n; i++) {
            out[c++] = (byte) (chars[i] >> 8);
            out[c++] = (byte) chars[i];
            out[f++] = (byte) (fg[i] >> 16);
            out[f++] = (byte) (fg[i] >> 8);
            out[f++] = (byte) fg[i];
            out[b++] = (byte) (bg[i] >> 16);
            out[b++] = (byte) (bg[i] >> 8);
            out[b++] = (byte) bg[i];
        }
        return out;
    }
}
