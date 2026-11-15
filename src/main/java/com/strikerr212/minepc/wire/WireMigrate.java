package com.strikerr212.minepc.wire;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.lan.CableBlockEntity;
import com.strikerr212.minepc.lan.CordList;
import com.strikerr212.minepc.lan.LanData;
import com.strikerr212.minepc.lan.LanManager;
import com.strikerr212.minepc.lan.Wire;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Перевод старых построек на провода 3.0. Идёт сам, понемногу, там, где мир загружен:
 * <ul>
 *     <li>патч-корды и воздушные линии между кронштейнами — в провода-шнуры (форма та же, что у нового патч-корда);</li>
 *     <li>провод-блоки, которые просто соединяют два гнезда (без лотков, развилок и обрывов), — в один провод по тому
 *     же маршруту (точки маршрута берутся со старого провода, путь между ними строится заново).</li>
 * </ul>
 * Остальное (лотки, развилки, провода под маскировкой) остаётся по-старому и продолжает работать.
 */
@EventBusSubscriber(modid = MineComputers.MODID)
public final class WireMigrate {

    private WireMigrate() {
    }

    /** Раз в сколько тиков смотреть. */
    private static final int PERIOD = 20;
    /** Сколько старых кусков за раз. */
    private static final int CORDS_PER_RUN = 64, CABLES_PER_RUN = 4;
    /** Точка маршрута — каждая N-я клетка старого провода. */
    private static final int STEP = 8;
    private static final int SEARCH = 60000;

    /** Клетки, которые переводить не стали (не подходят или пути нет) — до перезапуска не пробуем. */
    private static final Map<ServerLevel, Set<Long>> skip = new WeakHashMap<>();

    @SubscribeEvent
    public static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % PERIOD != 7) return;
        run(level);
    }

    /** Один проход: сколько переведено (корды + провода). */
    public static int run(ServerLevel level) {
        LanData data = LanData.get(level);
        int done = cords(level, data);
        if (data.cableCount() > 0) done += cables(level, data);
        return done;
    }

    // ======================= Корды =======================

    private static int cords(ServerLevel level, LanData data) {
        if (data.allCords().isEmpty()) return 0;
        int n = 0;
        for (LanData.Cord c : new ArrayList<>(data.allCords())) {
            if (n >= CORDS_PER_RUN) break;
            if (!level.isLoaded(c.a()) || !level.isLoaded(c.b())) continue;
            WireLine line = WireLines.cord(level, data.newLineId(), c.a(), c.portA(), c.b(), c.portB(), c.color(), data.labelName(c.label()));
            data.removeCord(c);
            data.putLine(line);
            CordList.refresh(level, c.a());
            CordList.refresh(level, c.b());
            n++;
        }
        return n;
    }

    // ======================= Старые провода =======================

    /** Провод старого вида: клетка и номер провода в ней. */
    private record Piece(BlockPos cell, long packed, Wire w) {
    }

    private static int cables(ServerLevel level, LanData data) {
        Set<Long> skipped = skip.computeIfAbsent(level, k -> new HashSet<>());
        Set<Long> seen = new HashSet<>();
        int n = 0;
        for (long cell : data.cableCells()) {
            if (n >= CABLES_PER_RUN) break;
            if (skipped.contains(cell) || seen.contains(cell)) continue;
            BlockPos pos = BlockPos.of(cell);
            if (!level.isLoaded(pos)) continue;
            long[] here = data.cable(cell);
            if (here == null) continue;
            for (int j = 0; j < here.length; j++) {
                Component comp = component(data, pos, j);
                for (Piece p : comp.pieces) seen.add(p.cell.asLong());
                int r = convert(level, data, comp);
                if (r > 0) n++;
                else if (r < 0) for (Piece p : comp.pieces) skipped.add(p.cell.asLong());
                if (r != 0) break;   // клетку поменяли — остальные её провода посмотрим в следующий раз
            }
        }
        return n;
    }

    /** Кусок старой сети: провода, кто с кем соединён, вилки в гнёзда, и годится ли он для перевода. */
    private static final class Component {
        final List<Piece> pieces = new ArrayList<>();
        final List<List<Integer>> links = new ArrayList<>();
        final List<LanData.Plug> plugs = new ArrayList<>();
        /** Номер провода, воткнутого в каждую вилку. */
        final List<Integer> plugPiece = new ArrayList<>();
        boolean odd;   // лоток, обрыв или слишком большой
    }

    private static Component component(LanData data, BlockPos start, int startWire) {
        Component c = new Component();
        Map<Long, Integer> index = new HashMap<>();
        ArrayDeque<long[]> q = new ArrayDeque<>();
        q.add(new long[]{start.asLong(), startWire});
        index.put(start.asLong() * 64 + startWire, 0);
        long[] first = data.cable(start.asLong());
        c.pieces.add(new Piece(start, first[startWire], Wire.unpack(first[startWire])));
        c.links.add(new ArrayList<>());
        while (!q.isEmpty()) {
            long[] cur = q.poll();
            int me = index.get(cur[0] * 64 + cur[1]);
            Piece piece = c.pieces.get(me);
            Wire w = piece.w;
            BlockPos p = piece.cell;
            if (c.pieces.size() > 600) {
                c.odd = true;
                break;
            }
            int links = 0;
            for (int i = 0; i < 4; i++) {
                Direction d = w.dirs()[i];
                if (w.port(i) >= 0) {
                    links++;
                    c.plugs.add(new LanData.Plug(p.relative(d).immutable(), w.port(i)));
                    c.plugPiece.add(me);
                    continue;
                }
                Object[] t = LanManager.target(p, w, i);
                if (t == null) continue;
                links++;
                BlockPos nb = (BlockPos) t[0];
                Direction face = (Direction) t[1];
                if (w.conn[i] == Wire.FLAT && data.tray(nb.asLong()) != null) {
                    c.odd = true;
                    continue;
                }
                long[] other = data.cable(nb.asLong());
                if (other == null) {
                    c.odd = true;   // провод уходит в никуда
                    continue;
                }
                int edge = Wire.edgeAxis(w.face, d).ordinal();
                for (int j = 0; j < other.length; j++) {
                    Wire o = Wire.unpack(other[j]);
                    if (o.face != face || !o.sameKind(w) || o.lane[edge] != w.lane[edge]) continue;
                    long key = nb.asLong() * 64 + j;
                    Integer k = index.get(key);
                    if (k == null) {
                        k = c.pieces.size();
                        index.put(key, k);
                        c.pieces.add(new Piece(nb.immutable(), other[j], o));
                        c.links.add(new ArrayList<>());
                        q.add(new long[]{nb.asLong(), j});
                    }
                    if (!c.links.get(me).contains(k)) c.links.get(me).add(k);
                    if (!c.links.get(k).contains(me)) c.links.get(k).add(me);
                }
            }
            if (links < 2) c.odd = true;   // обрыв
        }
        return c;
    }

    /** 1 — переведено, 0 — пока нельзя (мир не загружен), -1 — не подходит. */
    private static int convert(ServerLevel level, LanData data, Component c) {
        if (c.odd || c.plugs.size() != 2) return -1;
        for (int i = 0; i < c.links.size(); i++) if (c.links.get(i).size() > 2) return -1;   // развилка
        // всё вокруг должно быть загружено
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (Piece p : c.pieces) {
            x0 = Math.min(x0, p.cell.getX());
            z0 = Math.min(z0, p.cell.getZ());
            x1 = Math.max(x1, p.cell.getX());
            z1 = Math.max(z1, p.cell.getZ());
        }
        for (LanData.Plug pl : c.plugs) {
            x0 = Math.min(x0, pl.pos().getX());
            z0 = Math.min(z0, pl.pos().getZ());
            x1 = Math.max(x1, pl.pos().getX());
            z1 = Math.max(z1, pl.pos().getZ());
        }
        for (int cx = (x0 - 8) >> 4; cx <= (x1 + 8) >> 4; cx++) {
            for (int cz = (z0 - 8) >> 4; cz <= (z1 + 8) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) return 0;
            }
        }
        for (Piece p : c.pieces) {
            if (level.getBlockEntity(p.cell) instanceof CableBlockEntity be) {
                if (be.facade() != null) return -1;   // под маскировкой — в стене, новым проводом не пройти
            } else {
                return -1;
            }
        }
        // старый маршрут по порядку: от провода у первой вилки к проводу у второй
        int from = c.plugPiece.get(0), to = c.plugPiece.get(1);
        int[] parent = new int[c.pieces.size()];
        java.util.Arrays.fill(parent, -2);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(from);
        parent[from] = -1;
        while (!q.isEmpty()) {
            int k = q.poll();
            for (int nb : c.links.get(k)) {
                if (parent[nb] == -2) {
                    parent[nb] = k;
                    q.add(nb);
                }
            }
        }
        if (parent[to] == -2) return -1;
        List<Integer> path = new ArrayList<>();
        for (int k = to; k != -1; k = parent[k]) path.add(0, k);
        List<int[]> clicks = new ArrayList<>();
        for (int i = STEP; i < path.size() - 2; i += STEP) clicks.add(pixel(c.pieces.get(path.get(i))));
        Wire w0 = c.pieces.get(from).w;
        LanData.Plug a = c.plugs.get(0), b = c.plugs.get(1);
        WirePlan.Result r = WirePlan.build(level, data::line, w0.fiber, WirePlan.Anchor.socket(a.pos(), a.port()), clicks,
                WirePlan.Anchor.socket(b.pos(), b.port()), SEARCH);
        if (!r.ok() || r.xs().length > WireLine.MAX_NODES) return -1;
        String label = "";
        if (level.getBlockEntity(c.pieces.get(from).cell) instanceof CableBlockEntity be) label = be.label(w0);
        // убрать старые провода
        Set<BlockPos> touched = new HashSet<>();
        for (Piece p : c.pieces) {
            if (!(level.getBlockEntity(p.cell) instanceof CableBlockEntity be)) continue;
            for (Wire w : new ArrayList<>(be.wires())) {
                if (w.packed() == p.packed || (w.sameKind(p.w) && w.face == p.w.face && java.util.Arrays.equals(w.lane, p.w.lane))) {
                    be.remove(w);
                    break;
                }
            }
            touched.add(p.cell);
        }
        // новый провод — сразу в гнёзда, чтобы оставшиеся старые провода не воткнулись туда при пересчёте
        data.putLine(new WireLine(data.newLineId(), w0.fiber, w0.color, label, r.xs(), r.ys(), r.zs(), r.hang(),
                a.pos().immutable(), a.port(), b.pos().immutable(), b.port()));
        for (BlockPos p : touched) {
            if (level.getBlockEntity(p) instanceof CableBlockEntity be) {
                if (be.wires().isEmpty()) level.removeBlock(p, false);
                else {
                    be.reconnect();
                    be.reconnectNeighbours();
                }
            }
        }
        return 1;
    }

    /** Пиксель, где лежал старый провод: прижат к своей грани клетки, по дорожкам. */
    private static int[] pixel(Piece p) {
        int[] base = {p.cell.getX() * 16, p.cell.getY() * 16, p.cell.getZ() * 16};
        int[] out = new int[3];
        Direction f = p.w.face;
        for (int i = 0; i < 3; i++) out[i] = base[i] + Math.max(0, Math.min(15, p.w.lane[i]));
        int n = f.getAxis().ordinal();
        out[n] = base[n] + (f.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 15 : 0);
        return out;
    }
}
