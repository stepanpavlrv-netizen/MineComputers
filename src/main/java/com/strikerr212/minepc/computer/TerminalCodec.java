package com.strikerr212.minepc.computer;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Как экран компьютера передаётся игрокам. Экран 160×50 — это 16 КБ, а меняется обычно пара строк,
 * поэтому сервер отправляет только изменившиеся строки и сжимает их (как zip).
 *
 * Кадр (снимок {@link Terminal#snapshot()}): 8 байт заголовка (ширина, высота, курсор X и Y по 2 байта,
 * мигание, глубина цвета), затем для всех клеток: символы (2 байта), цвета текста (3 байта), цвета фона (3 байта).
 *
 * Пакет (до сжатия): тип (0 — весь кадр, 1 — изменения), 8 байт заголовка, затем
 * весь кадр целиком или число строк (2 байта) и для каждой: номер строки, символы, цвета текста, цвета фона.
 */
public final class TerminalCodec {

    public static final int HEADER = 8;
    /** Байтов на клетку: символ 2 + цвет текста 3 + цвет фона 3. */
    public static final int CELL = 8;
    private static final int FULL = 0, DIFF = 1;

    private TerminalCodec() {
    }

    public static int width(byte[] frame) {
        return frame[0] & 0xFF;
    }

    public static int height(byte[] frame) {
        return frame[1] & 0xFF;
    }

    public static int cursorX(byte[] frame) {
        return (short) ((frame[2] & 0xFF) | (frame[3] << 8));
    }

    public static int cursorY(byte[] frame) {
        return (short) ((frame[4] & 0xFF) | (frame[5] << 8));
    }

    /** Закодировать кадр: целиком (prev == null или другой размер) или только изменившиеся строки. */
    public static byte[] encode(byte[] prev, byte[] cur) {
        int w = width(cur), h = height(cur), n = w * h;
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 + n);
        boolean full = prev == null || prev.length != cur.length || width(prev) != w || height(prev) != h;
        out.write(full ? FULL : DIFF);
        out.write(cur, 0, HEADER);
        if (full) {
            out.write(cur, HEADER, n * CELL);
        } else {
            ByteArrayOutputStream rows = new ByteArrayOutputStream();
            int count = 0;
            for (int y = 0; y < h; y++) {
                if (rowChanged(prev, cur, w, h, y)) {
                    rows.write(y);
                    rows.write(cur, HEADER + 2 * y * w, 2 * w);
                    rows.write(cur, HEADER + 2 * n + 3 * y * w, 3 * w);
                    rows.write(cur, HEADER + 5 * n + 3 * y * w, 3 * w);
                    count++;
                }
            }
            out.write(count & 0xFF);
            out.write(count >> 8);
            byte[] r = rows.toByteArray();
            out.write(r, 0, r.length);
        }
        return deflate(out.toByteArray());
    }

    private static boolean rowChanged(byte[] a, byte[] b, int w, int h, int y) {
        int n = w * h;
        return !java.util.Arrays.equals(a, HEADER + 2 * y * w, HEADER + 2 * (y + 1) * w, b, HEADER + 2 * y * w, HEADER + 2 * (y + 1) * w)
                || !java.util.Arrays.equals(a, HEADER + 2 * n + 3 * y * w, HEADER + 2 * n + 3 * (y + 1) * w,
                b, HEADER + 2 * n + 3 * y * w, HEADER + 2 * n + 3 * (y + 1) * w)
                || !java.util.Arrays.equals(a, HEADER + 5 * n + 3 * y * w, HEADER + 5 * n + 3 * (y + 1) * w,
                b, HEADER + 5 * n + 3 * y * w, HEADER + 5 * n + 3 * (y + 1) * w);
    }

    /** Есть ли в кадре изменения по сравнению с предыдущим (включая курсор). */
    public static boolean changed(byte[] prev, byte[] cur) {
        return !java.util.Arrays.equals(prev, cur);
    }

    /**
     * Применить пакет к кадру, который уже есть у игрока. Возвращает новый кадр или null,
     * если это изменения, а полного кадра у игрока ещё нет (тогда нужно попросить весь кадр).
     */
    public static byte[] apply(byte[] base, byte[] packet) {
        byte[] d = inflate(packet);
        if (d == null || d.length < 1 + HEADER) return null;
        int w = d[1] & 0xFF, h = d[2] & 0xFF, n = w * h;
        if (d[0] == FULL) {
            if (d.length < 1 + HEADER + CELL * n) return null;
            byte[] frame = new byte[HEADER + CELL * n];
            System.arraycopy(d, 1, frame, 0, frame.length);
            return frame;
        }
        if (base == null || base.length != HEADER + CELL * n || width(base) != w || height(base) != h) return null;
        byte[] frame = base.clone();
        System.arraycopy(d, 1, frame, 0, HEADER);
        int p = 1 + HEADER;
        int count = (d[p] & 0xFF) | (d[p + 1] & 0xFF) << 8;
        p += 2;
        for (int i = 0; i < count; i++) {
            if (p + 1 + CELL * w > d.length) return null;
            int y = d[p++] & 0xFF;
            if (y >= h) return null;
            System.arraycopy(d, p, frame, HEADER + 2 * y * w, 2 * w);
            p += 2 * w;
            System.arraycopy(d, p, frame, HEADER + 2 * n + 3 * y * w, 3 * w);
            p += 3 * w;
            System.arraycopy(d, p, frame, HEADER + 5 * n + 3 * y * w, 3 * w);
            p += 3 * w;
        }
        return frame;
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 4 + 16);
        byte[] buf = new byte[4096];
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf));
        deflater.end();
        return out.toByteArray();
    }

    private static byte[] inflate(byte[] data) {
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 4 + 16);
        byte[] buf = new byte[4096];
        try {
            while (!inflater.finished()) {
                int k = inflater.inflate(buf);
                if (k == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                out.write(buf, 0, k);
                if (out.size() > 1 << 21) return null;   // защита от «zip-бомбы»
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
    }
}
