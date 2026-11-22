package com.strikerr212.minepc.computer;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Как картинка экрана ({@link Terminal.Image}) едет к игроку: 256 цветов и сжатие, как zip.
 * Если цветов в кадре не больше 256 — они точные; иначе берём 256 самых частых (по 15-битным оттенкам)
 * и каждую точку красим ближайшим. Кадр 640×392 «Разлома» так весит около 90 КБ — меньше, чем тот же экран
 * символами-полублоками в 320×196.
 *
 * Пакет (до сжатия): x, y (клетки), w, h (точки) — по 2 байта; число цветов n (2 байта); n цветов по 3 байта;
 * w×h номеров цветов по байту. Пустой пакет — картинки нет.
 */
public final class ImageCodec {

    private ImageCodec() {
    }

    /** Самая большая картинка, которую примет игрок (защита от испорченных пакетов). */
    public static final int MAX_W = Terminal.LIMIT_WIDTH * Terminal.IMAGE_CX, MAX_H = Terminal.LIMIT_HEIGHT * Terminal.IMAGE_CY;

    public static byte[] encode(Terminal.Image im) {
        if (im == null) return new byte[0];
        int w = im.w(), h = im.h(), n = w * h;
        int[] rgb = im.rgb();
        int[] pal = new int[256];
        int count = 0;
        byte[] idx = new byte[n];
        // 1) точные цвета, если их не больше 256
        java.util.HashMap<Integer, Integer> exact = new java.util.HashMap<>();
        boolean fits = true;
        for (int i = 0; i < n && fits; i++) {
            int c = rgb[i] & 0xFFFFFF;
            if (!exact.containsKey(c)) {
                if (exact.size() >= 256) fits = false;
                else exact.put(c, exact.size());
            }
        }
        if (fits) {
            for (var e : exact.entrySet()) pal[e.getValue()] = e.getKey();
            count = exact.size();
            for (int i = 0; i < n; i++) idx[i] = (byte) (int) exact.get(rgb[i] & 0xFFFFFF);
        } else {
            // 2) 256 самых частых 15-битных оттенков
            int[] hist = new int[32768];
            for (int i = 0; i < n; i++) hist[key(rgb[i])]++;
            int[] order = new int[32768];
            int used = 0;
            for (int k = 0; k < 32768; k++) if (hist[k] > 0) order[used++] = k;
            Integer[] sorted = new Integer[used];
            for (int i = 0; i < used; i++) sorted[i] = order[i];
            Arrays.sort(sorted, (a, b) -> Integer.compare(hist[b], hist[a]));
            count = Math.min(256, used);
            // цвет палитры — средний настоящий цвет точек этого оттенка
            long[] sr = new long[32768], sg = new long[32768], sb = new long[32768];
            for (int i = 0; i < n; i++) {
                int c = rgb[i], k = key(c);
                sr[k] += (c >> 16) & 255; sg[k] += (c >> 8) & 255; sb[k] += c & 255;
            }
            for (int i = 0; i < count; i++) {
                int k = sorted[i], m = hist[k];
                pal[i] = (int) (sr[k] / m) << 16 | (int) (sg[k] / m) << 8 | (int) (sb[k] / m);
            }
            // каждому встреченному оттенку — ближайший цвет палитры
            short[] lut = new short[32768];
            Arrays.fill(lut, (short) -1);
            for (int i = 0; i < count; i++) lut[sorted[i]] = (short) i;
            for (int i = 0; i < used; i++) {
                int k = order[i];
                if (lut[k] >= 0) continue;
                int r = (k >> 10) << 3, g = ((k >> 5) & 31) << 3, b = (k & 31) << 3;
                int best = 0, bd = Integer.MAX_VALUE;
                for (int j = 0; j < count; j++) {
                    int p = pal[j];
                    int dr = ((p >> 16) & 255) - r, dg = ((p >> 8) & 255) - g, db = (p & 255) - b;
                    int d = dr * dr * 3 + dg * dg * 4 + db * db * 2;
                    if (d < bd) { bd = d; best = j; }
                }
                lut[k] = (short) best;
            }
            for (int i = 0; i < n; i++) idx[i] = (byte) lut[key(rgb[i])];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(12 + count * 3 + n);
        u16(out, im.x()); u16(out, im.y()); u16(out, w); u16(out, h); u16(out, count);
        for (int i = 0; i < count; i++) { out.write(pal[i] >> 16); out.write(pal[i] >> 8); out.write(pal[i]); }
        out.write(idx, 0, n);
        return deflate(out.toByteArray());
    }

    /** Разобрать пакет: картинка, null — картинки нет (пустой пакет), или бросает IllegalArgumentException. */
    public static Terminal.Image decode(byte[] data) {
        if (data.length == 0) return null;
        byte[] d = inflate(data);
        if (d == null || d.length < 10) throw new IllegalArgumentException("битая картинка");
        int x = r16(d, 0), y = r16(d, 2), w = r16(d, 4), h = r16(d, 6), count = r16(d, 8);
        if (w < 1 || h < 1 || w > MAX_W || h > MAX_H || count > 256 || d.length < 10 + count * 3 + w * h)
            throw new IllegalArgumentException("битая картинка");
        int[] pal = new int[256];
        for (int i = 0; i < count; i++) {
            int p = 10 + i * 3;
            pal[i] = (d[p] & 255) << 16 | (d[p + 1] & 255) << 8 | (d[p + 2] & 255);
        }
        int[] rgb = new int[w * h];
        int base = 10 + count * 3;
        for (int i = 0; i < rgb.length; i++) rgb[i] = pal[d[base + i] & 255];
        return new Terminal.Image(x, y, w, h, rgb, 0);
    }

    private static int key(int c) {
        return ((c >> 19) & 31) << 10 | ((c >> 11) & 31) << 5 | ((c >> 3) & 31);
    }

    private static void u16(ByteArrayOutputStream o, int v) {
        o.write(v & 255);
        o.write((v >> 8) & 255);
    }

    private static int r16(byte[] b, int at) {
        return (b[at] & 255) | (b[at + 1] & 255) << 8;
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 3 + 16);
        byte[] buf = new byte[8192];
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf));
        deflater.end();
        return out.toByteArray();
    }

    private static byte[] inflate(byte[] data) {
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 3 + 16);
        byte[] buf = new byte[8192];
        int limit = 10 + 256 * 3 + MAX_W * MAX_H;
        try {
            while (!inflater.finished()) {
                int k = inflater.inflate(buf);
                if (k == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                out.write(buf, 0, k);
                if (out.size() > limit) return null;     // защита от «zip-бомбы»
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
    }
}
