package com.strikerr212.minepc.client;

import com.strikerr212.minepc.computer.TerminalCodec;
import com.strikerr212.minepc.network.TerminalInputPayload;
import com.strikerr212.minepc.block.HostRef;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/** Последние кадры экранов компьютеров, пришедшие с сервера (только у игрока). */
public final class ClientTerminals {

    /** Разобранный кадр: символы и цвета (RGB) каждой клетки. */
    public record Snapshot(HostRef host, int width, int height, int cursorX, int cursorY, boolean blink, int depth,
                           char[] chars, int[] fg, int[] bg) {

        static Snapshot parse(HostRef pos, byte[] f) {
            int w = TerminalCodec.width(f), h = TerminalCodec.height(f), n = w * h;
            if (w == 0 || h == 0 || f.length < TerminalCodec.HEADER + TerminalCodec.CELL * n) return null;
            char[] c = new char[n];
            int[] fg = new int[n], bg = new int[n];
            int pc = TerminalCodec.HEADER, pf = pc + 2 * n, pb = pf + 3 * n;
            for (int i = 0; i < n; i++) {
                c[i] = (char) ((f[pc++] & 0xFF) << 8 | (f[pc++] & 0xFF));
                fg[i] = (f[pf++] & 0xFF) << 16 | (f[pf++] & 0xFF) << 8 | (f[pf++] & 0xFF);
                bg[i] = (f[pb++] & 0xFF) << 16 | (f[pb++] & 0xFF) << 8 | (f[pb++] & 0xFF);
            }
            return new Snapshot(pos, w, h, TerminalCodec.cursorX(f), TerminalCodec.cursorY(f), TerminalCodec.blink(f), TerminalCodec.depth(f), c, fg, bg);
        }
    }

    private static final Map<HostRef, byte[]> FRAMES = new HashMap<>();
    private static final Map<HostRef, Snapshot> SCREENS = new HashMap<>();
    private static final Map<HostRef, Boolean> KEYBOARD = new HashMap<>();
    private static final Map<HostRef, Long> LAST_REQUEST = new HashMap<>();

    private ClientTerminals() {
    }

    /** Сколько кадров пришло (для проверки скорости в CI). */
    public static long received, receivedAt;
    public static BlockPos countAt;

    public static void update(HostRef host, byte[] data, boolean keyboard) {
        received++;
        if (host.pos().equals(countAt)) receivedAt++;
        KEYBOARD.put(host, keyboard);
        if (data.length == 0) {          // компьютер выключен
            FRAMES.remove(host);
            SCREENS.remove(host);
            PixelLayer.release(host);
            return;
        }
        byte[] frame = TerminalCodec.apply(FRAMES.get(host), data);
        if (frame == null) {
            // Пришли изменения, а основы нет (игрок только подошёл) — просим весь кадр
            requestFull(host);
            return;
        }
        Snapshot s = Snapshot.parse(host, frame);
        if (s == null) return;
        FRAMES.put(host, frame);
        SCREENS.put(host, s);
    }

    /** Попросить сервер прислать экран целиком (не чаще раза в секунду). */
    public static void requestFull(HostRef host) {
        long now = System.currentTimeMillis();
        Long last = LAST_REQUEST.get(host);
        if (last != null && now - last < 1000) return;
        LAST_REQUEST.put(host, now);
        PacketDistributor.sendToServer(new TerminalInputPayload(host, TerminalInputPayload.REFRESH, "", 0, 0, 0));
    }

    /** Экран компьютера в системном блоке pos или null, если он выключен. */
    public static Snapshot get(BlockPos pos) {
        return pos == null ? null : SCREENS.get(HostRef.of(pos));
    }

    /** Экран любого компьютера (и модуля стойки) или null. */
    public static Snapshot get(HostRef host) {
        return host == null ? null : SCREENS.get(host);
    }

    public static boolean keyboard(HostRef host) {
        return KEYBOARD.getOrDefault(host, false);
    }

    public static void clear() {
        PixelLayer.releaseAll();
        ComputerAudio.clear();
        HumSounds.clear();
        FRAMES.clear();
        SCREENS.clear();
        KEYBOARD.clear();
        LAST_REQUEST.clear();
    }
}
