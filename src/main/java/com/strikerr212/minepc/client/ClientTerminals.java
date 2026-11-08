package com.strikerr212.minepc.client;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

/** Последние снимки экранов компьютеров, пришедшие с сервера (только у игрока). */
public final class ClientTerminals {

    /** Разобранный снимок экрана. */
    public record Snapshot(int width, int height, int cursorX, int cursorY, boolean blink,
                           byte[] chars, byte[] fg, byte[] bg, boolean keyboard) {

        public static Snapshot parse(byte[] d, boolean keyboard) {
            if (d.length < 6) return null;
            int w = d[0] & 0xFF, h = d[1] & 0xFF, n = w * h;
            if (w == 0 || h == 0 || d.length < 6 + 3 * n) return null;
            byte[] c = new byte[n], f = new byte[n], b = new byte[n];
            System.arraycopy(d, 6, c, 0, n);
            System.arraycopy(d, 6 + n, f, 0, n);
            System.arraycopy(d, 6 + 2 * n, b, 0, n);
            return new Snapshot(w, h, d[2], d[3], d[4] != 0, c, f, b, keyboard);
        }
    }

    private static final Map<BlockPos, Snapshot> SCREENS = new HashMap<>();
    private static final Map<BlockPos, Boolean> KEYBOARD = new HashMap<>();

    private ClientTerminals() {
    }

    public static void update(BlockPos pos, byte[] data, boolean keyboard) {
        KEYBOARD.put(pos, keyboard);
        Snapshot s = Snapshot.parse(data, keyboard);
        if (s == null) SCREENS.remove(pos);
        else SCREENS.put(pos, s);
    }

    /** Экран компьютера или null, если он выключен. */
    public static Snapshot get(BlockPos pos) {
        return pos == null ? null : SCREENS.get(pos);
    }

    public static boolean keyboard(BlockPos pos) {
        return KEYBOARD.getOrDefault(pos, false);
    }

    public static void clear() {
        SCREENS.clear();
        KEYBOARD.clear();
    }
}
