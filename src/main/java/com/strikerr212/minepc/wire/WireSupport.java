package com.strikerr212.minepc.wire;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.lan.LanData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Опора провода. Убрали блок, вдоль которого лежал провод, — участок без опоры (от 3 пикселей) становится висящим:
 * провисает дугой между ближайшими опорами, а не висит палкой в воздухе и не рвётся.
 */
@EventBusSubscriber(modid = MineComputers.MODID)
public final class WireSupport {

    private WireSupport() {
    }

    /** Клетки, где что-то поменялось за этот тик (по мирам). */
    private static final Map<ServerLevel, Set<Long>> changed = new HashMap<>();

    @SubscribeEvent
    public static void onNeighbor(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Set<Long> set = changed.computeIfAbsent(level, k -> new HashSet<>());
            if (set.size() < 4096) set.add(event.getPos().asLong());
        }
    }

    @SubscribeEvent
    public static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Set<Long> set = changed.remove(level);
        if (set == null || set.isEmpty()) return;
        LanData data = LanData.get(level);
        if (data.lines().isEmpty()) return;
        Set<Long> seen = new HashSet<>();
        for (long p : set) {
            BlockPos pos = BlockPos.of(p);
            for (WireLine l : data.linesInChunk(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4))) {
                if (!l.bounds().inflate(1).contains(pos.getCenter()) || !seen.add(l.id)) continue;
                WireLine fixed = fix(new LevelVoxels(level), l);
                if (fixed != null) data.putLine(fixed);
            }
        }
    }

    /** Точка пути: узел (1/32 блока), держит ли его что-то, и вид связи с предыдущей точкой (висит ли). */
    private record Pt(int x, int y, int z, boolean anchor, boolean hang) {
    }

    /** Провод с поправленной опорой или null, если менять нечего. */
    public static WireLine fix(WireRouter.Voxels v, WireLine l) {
        List<Pt> pts = new ArrayList<>();
        pts.add(new Pt(l.xs[0], l.ys[0], l.zs[0], true, false));
        for (int i = 0; i + 1 < l.nodes(); i++) {
            int ax = l.xs[i], ay = l.ys[i], az = l.zs[i], bx = l.xs[i + 1], by = l.ys[i + 1], bz = l.zs[i + 1];
            boolean axis = Integer.bitCount((ax != bx ? 1 : 0) | (ay != by ? 2 : 0) | (az != bz ? 4 : 0)) == 1;
            boolean centers = (ax & 1) == 1 && (ay & 1) == 1 && (az & 1) == 1 && (bx & 1) == 1 && (by & 1) == 1 && (bz & 1) == 1;
            if (l.hang[i] || !axis || !centers) {
                // висящий отрезок, вилка у гнезда или наклонный кусок — не трогаем
                boolean anchor = i + 1 == l.nodes() - 1 || !centers || l.hang[i];
                pts.add(new Pt(bx, by, bz, anchor || !l.hang[i], l.hang[i]));
                continue;
            }
            int sx = Integer.signum(bx - ax) * 2, sy = Integer.signum(by - ay) * 2, sz = Integer.signum(bz - az) * 2;
            int n = (Math.abs(bx - ax) + Math.abs(by - ay) + Math.abs(bz - az)) / 2;
            for (int k = 1; k <= n; k++) {
                int x = ax + sx * k, y = ay + sy * k, z = az + sz * k;
                boolean last = i + 1 == l.nodes() - 1 && k == n;
                boolean sup = last || WireRouter.supported(v, (x - 1) >> 1, (y - 1) >> 1, (z - 1) >> 1);
                pts.add(new Pt(x, y, z, sup, false));
            }
        }
        // участки без опоры от 3 точек подряд — висят между соседними опорами
        List<Pt> out = new ArrayList<>();
        boolean changedAny = false;
        int i = 0;
        while (i < pts.size()) {
            Pt p = pts.get(i);
            if (p.anchor() || p.hang()) {
                out.add(p);
                i++;
                continue;
            }
            int j = i;
            while (j < pts.size() && !pts.get(j).anchor() && !pts.get(j).hang()) j++;
            if (j - i >= 3) {
                // висим от последней опоры до следующей (или до самого конца провода)
                Pt to = j < pts.size() ? pts.get(j) : pts.get(pts.size() - 1);
                Pt from = out.get(out.size() - 1);
                List<Pt> lying = j < pts.size() ? relay(v, from, to) : null;
                if (lying != null) out.addAll(lying);         // рядом есть опора — провод просто ложится по ней
                else out.add(new Pt(to.x(), to.y(), to.z(), true, true));
                changedAny = true;
                i = j < pts.size() ? j + 1 : j;
            } else {
                for (int k = i; k < j; k++) out.add(pts.get(k));
                i = j;
            }
        }
        if (!changedAny) return null;
        // собрать узлы, склеив прямые отрезки одного направления
        List<int[]> nodes = new ArrayList<>();
        List<Boolean> hang = new ArrayList<>();
        for (Pt p : out) {
            int[] c = {p.x(), p.y(), p.z()};
            if (!nodes.isEmpty()) {
                int[] q = nodes.get(nodes.size() - 1);
                if (q[0] == c[0] && q[1] == c[1] && q[2] == c[2]) continue;
            }
            if (nodes.size() >= 2 && !p.hang() && !hang.get(hang.size() - 1)) {
                int[] a = nodes.get(nodes.size() - 2), b = nodes.get(nodes.size() - 1);
                if (sameDir(a, b, c)) {
                    nodes.set(nodes.size() - 1, c);
                    continue;
                }
            }
            if (!nodes.isEmpty()) hang.add(p.hang());
            nodes.add(c);
        }
        if (nodes.size() < 2) return null;
        int n = nodes.size();
        int[] xs = new int[n], ys = new int[n], zs = new int[n];
        boolean[] hh = new boolean[n - 1];
        for (int k = 0; k < n; k++) {
            xs[k] = nodes.get(k)[0];
            ys[k] = nodes.get(k)[1];
            zs[k] = nodes.get(k)[2];
            if (k < n - 1) hh[k] = hang.get(k);
        }
        return new WireLine(l.id, l.fiber, l.color, l.label, xs, ys, zs, hh, l.devA, l.portA, l.devB, l.portB);
    }

    /**
     * Провод потерял опору, но рядом есть другая (стену убрали — под ней пол): короткий путь по опорам между двумя
     * точками провода, не сильно длиннее прямого. null — такого нет, провод провисает.
     */
    private static List<Pt> relay(WireRouter.Voxels v, Pt a, Pt b) {
        if (((a.x() & a.y() & a.z() & b.x() & b.y() & b.z()) & 1) == 0) return null;   // точки не в центрах пикселей
        int ax = (a.x() - 1) >> 1, ay = (a.y() - 1) >> 1, az = (a.z() - 1) >> 1, bx = (b.x() - 1) >> 1, by = (b.y() - 1) >> 1, bz = (b.z() - 1) >> 1;
        int straight = Math.abs(ax - bx) + Math.abs(ay - by) + Math.abs(az - bz);
        if (straight > 256) return null;
        WireRouter.Result r = WireRouter.route(v, ax, ay, az, -1, bx, by, bz, true, 6000);
        if (r == null || !r.reached()) return null;
        int len = 0;
        for (int k = 1; k < r.nodes(); k++) {
            if (r.hang()[k - 1]) return null;
            len += Math.abs(r.xs()[k] - r.xs()[k - 1]) + Math.abs(r.ys()[k] - r.ys()[k - 1]) + Math.abs(r.zs()[k] - r.zs()[k - 1]);
        }
        if (len > straight * 2 + 16) return null;
        List<Pt> out = new ArrayList<>();
        for (int k = 1; k < r.nodes(); k++) out.add(new Pt(WireGeom.unit(r.xs()[k]), WireGeom.unit(r.ys()[k]), WireGeom.unit(r.zs()[k]), true, false));
        return out;
    }

    private static boolean sameDir(int[] a, int[] b, int[] c) {
        int x1 = Integer.signum(b[0] - a[0]), y1 = Integer.signum(b[1] - a[1]), z1 = Integer.signum(b[2] - a[2]);
        int x2 = Integer.signum(c[0] - b[0]), y2 = Integer.signum(c[1] - b[1]), z2 = Integer.signum(c[2] - b[2]);
        boolean axis1 = Math.abs(x1) + Math.abs(y1) + Math.abs(z1) == 1;
        return axis1 && x1 == x2 && y1 == y2 && z1 == z2;
    }
}
