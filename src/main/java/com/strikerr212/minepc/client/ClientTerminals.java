package com.strikerr212.minepc.client;

import com.strikerr212.minepc.computer.TerminalCodec;
import com.strikerr212.minepc.network.TerminalInputPayload;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/** Последние кадры экранов компьютеров, пришедшие с сервера (только у игрока). */
public final class ClientTerminals {

    /** Разобранный кадр: символы и цвета каждой клетки. */
    public record Snapshot(int width, int height, int cursorX, int cursorY, boolean blink,
                           byte[] chars, byte[] fg, byte[] bg) {

        static Snapshot parse(byte[] f) {
            int w = TerminalCodec.width(f), h = TerminalCodec.height(f), n = w * h;
            if (w == 0 || h == 0 || f.length < TerminalCodec.HEADER + 2 * n) return null;
            byte[] c = new byte[n], fg = new byte[n], bg = new byte[n];
            System.arraycopy(f, TerminalCodec.HEADER, c, 0, n);
            for (int i = 0; i < n; i++) {
                int col = f[TerminalCodec.HEADER + n + i];
                fg[i] = (byte) ((col >> 4) & 15);
                bg[i] = (byte) (col & 15);
            }
            return new Snapshot(w, h, TerminalCodec.cursorX(f), TerminalCodec.cursorY(f), f[6] != 0, c, fg, bg);
        }
    }

    private static final Map<BlockPos, byte[]> FRAMES = new HashMap<>();
    private static final Map<BlockPos, Snapshot> SCREENS = new HashMap<>();
    private static final Map<BlockPos, Boolean> KEYBOARD = new HashMap<>();
    private static final Map<BlockPos, Long> LAST_REQUEST = new HashMap<>();

    private ClientTerminals() {
    }

    public static void update(BlockPos pos, byte[] data, boolean keyboard) {
        KEYBOARD.put(pos, keyboard);
        if (data.length == 0) {          // компьютер выключен
            FRAMES.remove(pos);
            SCREENS.remove(pos);
            return;
        }
        byte[] frame = TerminalCodec.apply(FRAMES.get(pos), data);
        if (frame == null) {
            // Пришли изменения, а основы нет (игрок только подошёл) — просим весь кадр
            requestFull(pos);
            return;
        }
        Snapshot s = Snapshot.parse(frame);
        if (s == null) return;
        FRAMES.put(pos, frame);
        SCREENS.put(pos, s);
    }

    /** Попросить сервер прислать экран целиком (не чаще раза в секунду). */
    public static void requestFull(BlockPos pos) {
        long now = System.currentTimeMillis();
        Long last = LAST_REQUEST.get(pos);
        if (last != null && now - last < 1000) return;
        LAST_REQUEST.put(pos, now);
        PacketDistributor.sendToServer(new TerminalInputPayload(pos, TerminalInputPayload.REFRESH, "", 0));
    }

    /** Экран компьютера или null, если он выключен. */
    public static Snapshot get(BlockPos pos) {
        return pos == null ? null : SCREENS.get(pos);
    }

    public static boolean keyboard(BlockPos pos) {
        return KEYBOARD.getOrDefault(pos, false);
    }

    public static void clear() {
        FRAMES.clear();
        SCREENS.clear();
        KEYBOARD.clear();
        LAST_REQUEST.clear();
    }
}
