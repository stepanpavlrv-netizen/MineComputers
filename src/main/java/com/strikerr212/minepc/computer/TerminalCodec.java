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
 * мигание, цвет текста), затем ширина×высота символов и столько же байтов цвета.
 *
 * Пакет (до сжатия): тип (0 — весь кадр, 1 — изменения), 8 байт заголовка, затем
 * весь кадр целиком или число строк (2 байта) и для каждой: номер строки, символы, цвета.
 */
public final class TerminalCodec {

    public static final int HEADER = 8;
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
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 + n / 4);
        boolean full = prev == null || prev.length != cur.length || width(prev) != w || height(prev) != h;
        out.write(full ? FULL : DIFF);
        out.write(cur, 0, HEADER);
        if (full) {
            out.write(cur, HEADER, n * 2);
        } else {
            ByteArrayOutputStream rows = new ByteArrayOutputStream();
            int count = 0;
            for (int y = 0; y < h; y++) {
                if (rowChanged(prev, cur, w, h, y)) {
                    rows.write(y);
                    rows.write(cur, HEADER + y * w, w);
                    rows.write(cur, HEADER + n + y * w, w);
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
        for (int x = 0; x < w; x++) {
            int c = HEADER + y * w + x;
            if (a[c] != b[c] || a[c + n] != b[c + n]) return true;
        }
        return false;
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
            if (d.length < 1 + HEADER + 2 * n) return null;
            byte[] frame = new byte[HEADER + 2 * n];
            System.arraycopy(d, 1, frame, 0, frame.length);
            return frame;
        }
        if (base == null || base.length != HEADER + 2 * n || width(base) != w || height(base) != h) return null;
        byte[] frame = base.clone();
        System.arraycopy(d, 1, frame, 0, HEADER);
        int p = 1 + HEADER;
        int count = (d[p] & 0xFF) | (d[p + 1] & 0xFF) << 8;
        p += 2;
        for (int i = 0; i < count; i++) {
            if (p + 1 + 2 * w > d.length) return null;
            int y = d[p++] & 0xFF;
            if (y >= h) return null;
            System.arraycopy(d, p, frame, HEADER + y * w, w);
            p += w;
            System.arraycopy(d, p, frame, HEADER + n + y * w, w);
            p += w;
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
                if (out.size() > 1 << 20) return null;   // защита от «zip-бомбы»
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
    }
}
