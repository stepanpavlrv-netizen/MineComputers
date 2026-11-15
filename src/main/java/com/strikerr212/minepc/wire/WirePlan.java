package com.strikerr212.minepc.wire;

import com.strikerr212.minepc.lan.LanDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.BlockGetter;

import java.util.ArrayList;
import java.util.List;

/**
 * Прокладка провода: откуда (гнездо, свободный конец провода или точка на поверхности), через какие щелчки и куда.
 * {@link #build} строит путь — одинаково у игрока (призрак) и на сервере (настоящий провод).
 */
public final class WirePlan {

    private WirePlan() {
    }

    public static final int SOCKET = 0, END = 1, PIXEL = 2;

    /** Конец прокладки: гнездо (устройство, порт), свободный конец провода (номер, конец b) или пиксель. */
    public record Anchor(int kind, BlockPos dev, int port, long line, boolean b, int x, int y, int z) {
        public static Anchor socket(BlockPos dev, int port) {
            return new Anchor(SOCKET, dev, port, 0, false, 0, 0, 0);
        }

        public static Anchor end(long line, boolean b) {
            return new Anchor(END, null, -1, line, b, 0, 0, 0);
        }

        public static Anchor pixel(int[] p) {
            return new Anchor(PIXEL, null, -1, 0, false, p[0], p[1], p[2]);
        }

        public boolean same(Anchor o) {
            return o != null && kind == o.kind && port == o.port && line == o.line && b == o.b && x == o.x && y == o.y && z == o.z
                    && (dev == null ? o.dev == null : dev.equals(o.dev));
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeByte(kind);
            switch (kind) {
                case SOCKET -> {
                    buf.writeBlockPos(dev);
                    buf.writeVarInt(port);
                }
                case END -> {
                    buf.writeLong(line);
                    buf.writeBoolean(b);
                }
                default -> {
                    buf.writeVarInt(x);
                    buf.writeVarInt(y);
                    buf.writeVarInt(z);
                }
            }
        }

        public static Anchor read(FriendlyByteBuf buf) {
            int k = buf.readByte();
            return switch (k) {
                case SOCKET -> socket(buf.readBlockPos(), buf.readVarInt());
                case END -> end(buf.readLong(), buf.readBoolean());
                default -> new Anchor(PIXEL, null, -1, 0, false, buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
            };
        }
    }

    /** Откуда брать провода (для свободных концов): у игрока — его копия, на сервере — схема мира. */
    public interface Lines {
        WireLine get(long id);
    }

    /**
     * Готовый путь: узлы (1/32 блока), висит ли отрезок, сколько нового кабеля (м); ok — дошли до конца;
     * stuck — пиксель, где застряли, и почему (ключ перевода).
     */
    public record Result(int[] xs, int[] ys, int[] zs, boolean[] hang, int meters, boolean ok, int[] stuck, String problem) {
    }

    /** Начальная точка: пиксель, направление и узлы перед ним (вилка). null — не годится (и почему — в err). */
    private record Start(int[] px, int dir, List<int[]> prefix) {
    }

    private static Start start(BlockGetter level, Lines lines, Anchor a, boolean fiber, String[] err) {
        switch (a.kind()) {
            case SOCKET -> {
                LanDevice.Socket so = WireGeom.socket(level, a.dev(), a.port());
                if (so == null) {
                    err[0] = "message.minepc.wire.no_socket";
                    return null;
                }
                if (!so.accepts(fiber)) {
                    err[0] = "message.minepc.draw.socket_kind";
                    return null;
                }
                List<int[]> pre = new ArrayList<>();
                pre.add(WireGeom.socketNode(a.dev(), so));
                return new Start(WireGeom.socketPixel(a.dev(), so), WireGeom.socketDir(so), pre);
            }
            case END -> {
                WireLine l = lines.get(a.line());
                if (l == null || !l.free(a.b())) {
                    err[0] = "message.minepc.wire.end_gone";
                    return null;
                }
                if (l.fiber != fiber) {
                    err[0] = "message.minepc.wire.kind_mismatch";
                    return null;
                }
                int n = l.nodes() - 1, i = a.b() ? n : 0, j = a.b() ? n - 1 : 1;
                int[] px = {(l.xs[i] - 1) >> 1, (l.ys[i] - 1) >> 1, (l.zs[i] - 1) >> 1};
                int dir = WireRouter.dirOf(Integer.signum(l.xs[i] - l.xs[j]), Integer.signum(l.ys[i] - l.ys[j]), Integer.signum(l.zs[i] - l.zs[j]));
                // у наклонного (висящего) последнего отрезка направления нет — всё равно
                if (Integer.bitCount((l.xs[i] != l.xs[j] ? 1 : 0) | (l.ys[i] != l.ys[j] ? 2 : 0) | (l.zs[i] != l.zs[j] ? 4 : 0)) != 1) dir = -1;
                return new Start(px, dir, new ArrayList<>());
            }
            default -> {
                return new Start(new int[]{a.x(), a.y(), a.z()}, -1, new ArrayList<>());
            }
        }
    }

    /** Пиксель-цель конца прокладки (гнездо или свободный конец) и узел вилки после него (или null). */
    private static int[][] target(BlockGetter level, Lines lines, Anchor a, boolean fiber, String[] err) {
        switch (a.kind()) {
            case SOCKET -> {
                LanDevice.Socket so = WireGeom.socket(level, a.dev(), a.port());
                if (so == null) {
                    err[0] = "message.minepc.wire.no_socket";
                    return null;
                }
                if (!so.accepts(fiber)) {
                    err[0] = "message.minepc.draw.socket_kind";
                    return null;
                }
                return new int[][]{WireGeom.socketPixel(a.dev(), so), WireGeom.socketNode(a.dev(), so)};
            }
            case END -> {
                WireLine l = lines.get(a.line());
                if (l == null || !l.free(a.b())) {
                    err[0] = "message.minepc.wire.end_gone";
                    return null;
                }
                if (l.fiber != fiber) {
                    err[0] = "message.minepc.wire.kind_mismatch";
                    return null;
                }
                int i = a.b() ? l.nodes() - 1 : 0;
                return new int[][]{{(l.xs[i] - 1) >> 1, (l.ys[i] - 1) >> 1, (l.zs[i] - 1) >> 1}, null};
            }
            default -> {
                return new int[][]{{a.x(), a.y(), a.z()}, null};
            }
        }
    }

    /**
     * Построить путь от from через clicks к to (to может быть null — конец там, где последний щелчок).
     * maxNodes — предел перебора на каждый участок.
     */
    public static Result build(BlockGetter level, Lines lines, boolean fiber, Anchor from, List<int[]> clicks, Anchor to, int maxNodes) {
        String[] err = {null};
        Start st = start(level, lines, from, fiber, err);
        if (st == null) return fail(err[0]);
        WireRouter.Voxels v = new LevelVoxels(level);
        List<int[]> nodes = new ArrayList<>(st.prefix());
        List<Boolean> hang = new ArrayList<>();
        int[] cur = st.px();
        int dir = st.dir();
        nodes.add(units(cur));
        if (!st.prefix().isEmpty()) hang.add(false);
        List<int[]> goals = new ArrayList<>(clicks);
        int[][] end = null;
        if (to != null) {
            end = target(level, lines, to, fiber, err);
            if (end == null) return partial(nodes, hang, cur, err[0]);
            goals.add(end[0]);
        }
        for (int g = 0; g < goals.size(); g++) {
            int[] t = goals.get(g);
            if (t[0] == cur[0] && t[1] == cur[1] && t[2] == cur[2]) continue;
            boolean last = g == goals.size() - 1 && end != null;
            WireRouter.Result r = WireRouter.route(v, cur[0], cur[1], cur[2], dir, t[0], t[1], t[2], last, maxNodes);
            if (r == null) return partial(nodes, hang, cur, "message.minepc.wire.no_path");
            for (int i = 1; i < r.nodes(); i++) {
                nodes.add(new int[]{WireGeom.unit(r.xs()[i]), WireGeom.unit(r.ys()[i]), WireGeom.unit(r.zs()[i])});
                hang.add(r.hang()[i - 1]);
            }
            int n = r.nodes() - 1;
            cur = new int[]{r.xs()[n], r.ys()[n], r.zs()[n]};
            dir = WireRouter.dirOf(Integer.signum(r.xs()[n] - r.xs()[n - 1]), Integer.signum(r.ys()[n] - r.ys()[n - 1]), Integer.signum(r.zs()[n] - r.zs()[n - 1]));
            if (!r.reached()) return partial(nodes, hang, cur, "message.minepc.wire.no_path");
        }
        if (end != null && end[1] != null) {
            nodes.add(end[1]);
            hang.add(false);
        }
        if (nodes.size() < 2) return fail("message.minepc.wire.too_short");
        return result(nodes, hang, true, null, null);
    }

    private static int[] units(int[] px) {
        return new int[]{WireGeom.unit(px[0]), WireGeom.unit(px[1]), WireGeom.unit(px[2])};
    }

    private static Result fail(String why) {
        return new Result(new int[0], new int[0], new int[0], new boolean[0], 0, false, null, why);
    }

    private static Result partial(List<int[]> nodes, List<Boolean> hang, int[] stuck, String why) {
        return result(nodes, hang, false, stuck, why);
    }

    /** Собрать результат, склеив соседние отрезки одного направления. */
    private static Result result(List<int[]> nodes, List<Boolean> hang, boolean ok, int[] stuck, String why) {
        List<int[]> n2 = new ArrayList<>();
        List<Boolean> h2 = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            int[] p = nodes.get(i);
            if (!n2.isEmpty()) {
                int[] q = n2.get(n2.size() - 1);
                if (q[0] == p[0] && q[1] == p[1] && q[2] == p[2]) continue;
            }
            if (n2.size() >= 2) {
                int[] a = n2.get(n2.size() - 2), b = n2.get(n2.size() - 1);
                boolean hb = h2.get(h2.size() - 1), hp = hang.get(i - 1);
                if (hb == hp && colinear(a, b, p)) {
                    n2.set(n2.size() - 1, p);
                    continue;
                }
            }
            if (!n2.isEmpty()) h2.add(i == 0 ? false : hang.get(i - 1));
            n2.add(p);
        }
        int m = n2.size();
        int[] xs = new int[m], ys = new int[m], zs = new int[m];
        boolean[] hh = new boolean[Math.max(0, m - 1)];
        for (int i = 0; i < m; i++) {
            xs[i] = n2.get(i)[0];
            ys[i] = n2.get(i)[1];
            zs[i] = n2.get(i)[2];
            if (i < m - 1) hh[i] = h2.get(i);
        }
        double len = 0;
        for (int i = 1; i < m; i++) {
            double dx = xs[i] - xs[i - 1], dy = ys[i] - ys[i - 1], dz = zs[i] - zs[i - 1];
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz) / WireLine.UNITS;
            len += hh[i - 1] ? d * 1.04 : d;
        }
        return new Result(xs, ys, zs, hh, Math.max(1, (int) Math.ceil(len - 0.01)), ok, stuck, why);
    }

    private static boolean colinear(int[] a, int[] b, int[] c) {
        long x1 = b[0] - a[0], y1 = b[1] - a[1], z1 = b[2] - a[2], x2 = c[0] - b[0], y2 = c[1] - b[1], z2 = c[2] - b[2];
        boolean zero = y1 * z2 - z1 * y2 == 0 && z1 * x2 - x1 * z2 == 0 && x1 * y2 - y1 * x2 == 0;
        return zero && x1 * x2 + y1 * y2 + z1 * z2 > 0;
    }
}
