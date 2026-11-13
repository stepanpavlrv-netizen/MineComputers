package com.strikerr212.minepc.lan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Прокладка кабеля «от порта до порта»: ищет путь по полу от одного устройства до другого — с поворотами, подъёмами
 * на блок вверх и спусками (провод идёт по стенке ступеньки) — и выбирает одну дорожку, свободную по всему пути.
 * Одна и та же логика у игрока (призрак маршрута) и на сервере (настоящая прокладка).
 */
public final class RoutePlanner {

    /** Дальше этого (по прямой от первого устройства) маршрут не ищем, блоков. */
    public static final int MAX_RADIUS = 48;
    private static final int MAX_NODES = 40_000;
    /** Цена шага, поворота (дорого: лучше ровная «Г», чем лесенка), подъёма; рядом с проводами — чуть дешевле. */
    private static final int STEP = 10, TURN = 14, CLIMB = 25, NEAR_CABLE = -2;

    private RoutePlanner() {
    }

    /** Провод маршрута: клетка, грань, дорожки по осям плоскости грани. */
    public record Piece(BlockPos cell, Direction face, int a, int b) {
    }

    /** Найденный маршрут: провода, длина (клеток), дорожка. */
    public record Route(List<Piece> pieces, int length, int lane) {
    }

    /** Можно ли положить провод на пол этой клетки: она свободна (или там уже кабель), под ней — опора. */
    static boolean walkable(Level level, BlockPos p) {
        if (!level.isLoaded(p)) return false;
        BlockState s = level.getBlockState(p);
        boolean free = s.getBlock() instanceof CableBlock
                ? !(level.getBlockEntity(p) instanceof CableBlockEntity be) || be.facade() == null
                : s.canBeReplaced() && s.getFluidState().isEmpty();
        if (!free) return false;
        return CableSupport.holds(level, p.below(), Direction.UP);
    }

    /** Через клетку можно протянуть провод по стенке (воздух или кабель). */
    static boolean open(Level level, BlockPos p) {
        if (!level.isLoaded(p)) return false;
        BlockState s = level.getBlockState(p);
        return s.getBlock() instanceof CableBlock || (s.canBeReplaced() && s.getFluidState().isEmpty());
    }

    /** Клетки у гнёзд устройства, откуда может начинаться провод этого вида. */
    static List<BlockPos> ends(Level level, BlockPos dev, boolean fiber) {
        List<BlockPos> out = new ArrayList<>();
        for (LanDevice.Socket s : LanDevice.socketsAt(level, dev)) {
            if (!s.accepts(fiber) || !s.face().getAxis().isHorizontal()) continue;
            BlockPos c = dev.relative(s.face());
            if (!out.contains(c) && walkable(level, c)) out.add(c.immutable());
        }
        return out;
    }

    private record Node(BlockPos pos, Direction from, int cost) {
    }

    /** Как попали в клетку: откуда и подъём/спуск ли это. */
    private record Step(BlockPos prev, Direction dir, int climb) {
    }

    /** Маршрут от устройства a к устройству b или null. */
    public static Route plan(Level level, BlockPos a, BlockPos b, boolean fiber) {
        return find(level, a, b, fiber).route();
    }

    /**
     * Итог поиска: маршрут или причина, почему его нет (ключ перевода), и где застряли (клетка, ближе всего
     * подошедшая к цели, или устройство, у которого нет места для провода).
     */
    public record Result(Route route, String reason, BlockPos stuck) {
    }

    public static Result find(Level level, BlockPos a, BlockPos b, boolean fiber) {
        List<BlockPos> starts = ends(level, a, fiber), goals = ends(level, b, fiber);
        if (a.equals(b)) return new Result(null, "message.minepc.route.none", null);
        if (starts.isEmpty()) return new Result(null, sockets(level, a, fiber) ? "message.minepc.route.no_start" : "message.minepc.route.no_socket", a);
        if (goals.isEmpty()) return new Result(null, sockets(level, b, fiber) ? "message.minepc.route.no_goal" : "message.minepc.route.no_socket", b);
        if (a.distManhattan(b) > MAX_RADIUS * 2) return new Result(null, "message.minepc.route.far", b);
        Set<BlockPos> goalSet = new HashSet<>(goals);
        Map<BlockPos, Integer> best = new HashMap<>();
        Map<BlockPos, Step> came = new HashMap<>();
        PriorityQueue<Node> q = new PriorityQueue<>((x, y) -> Integer.compare(x.cost, y.cost));
        for (BlockPos s : starts) {
            best.put(s, 0);
            q.add(new Node(s, null, 0));
        }
        BlockPos found = null, closest = null;
        int nodes = 0;
        while (!q.isEmpty() && nodes++ < MAX_NODES) {
            Node cur = q.poll();
            if (cur.cost > best.getOrDefault(cur.pos, Integer.MAX_VALUE)) continue;
            if (closest == null || cur.pos.distManhattan(b) < closest.distManhattan(b)) closest = cur.pos;
            if (goalSet.contains(cur.pos)) {
                found = cur.pos;
                break;
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = cur.pos.relative(d);
                int turn = cur.from != null && cur.from != d ? TURN : 0;
                if (walkable(level, n)) {
                    push(level, q, best, came, cur, n, d, 0, STEP + turn);
                } else if (!open(level, n) && !(level.getBlockState(n).getBlock() instanceof LanDevice)
                        && level.getBlockState(n).isFaceSturdy(level, n, d.getOpposite()) && walkable(level, n.above())) {
                    push(level, q, best, came, cur, n.above(), d, 1, CLIMB + turn);           // на ступеньку
                } else if (open(level, n) && walkable(level, n.below())) {
                    push(level, q, best, came, cur, n.below(), d, -1, CLIMB + turn);          // со ступеньки
                }
            }
        }
        if (found == null) return new Result(null, "message.minepc.route.none", closest);
        // Путь от конца к началу
        List<BlockPos> cells = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        for (BlockPos p = found; p != null; ) {
            cells.add(0, p);
            Step st = came.get(p);
            steps.add(0, st);
            p = st == null ? null : st.prev;
        }
        // Дорожка, свободная везде: на полу каждой клетки и на стенках подъёмов
        for (int lane = 0; lane < Wire.LANES; lane++) {
            List<Piece> pieces = new ArrayList<>();
            boolean ok = true;
            for (int i = 0; i < cells.size() && ok; i++) {
                BlockPos c = cells.get(i);
                ok = add(level, pieces, new Piece(c, Direction.DOWN, lane, lane));
                Step st = steps.get(i);
                if (ok && st != null && st.climb == 1) {
                    ok = add(level, pieces, new Piece(st.prev, st.dir, lane, lane));                     // стенка ступеньки
                } else if (ok && st != null && st.climb == -1) {
                    ok = add(level, pieces, new Piece(c, st.dir.getOpposite(), lane, lane));
                }
            }
            if (ok) return new Result(new Route(pieces, cells.size(), lane), null, null);
        }
        return new Result(null, "message.minepc.route.no_lane", found);
    }

    /** Есть ли у устройства вообще гнёзда для этого провода (сбоку — для маршрута по полу). */
    private static boolean sockets(Level level, BlockPos dev, boolean fiber) {
        for (LanDevice.Socket s : LanDevice.socketsAt(level, dev)) if (s.accepts(fiber) && s.face().getAxis().isHorizontal()) return true;
        return false;
    }

    private static void push(Level level, PriorityQueue<Node> q, Map<BlockPos, Integer> best, Map<BlockPos, Step> came,
                             Node cur, BlockPos n, Direction d, int climb, int cost) {
        if (n.distManhattan(cur.pos) > 3) return;
        // Рядом с уже лежащими проводами — чуть дешевле: новые ложатся жгутом
        int c = cur.cost + cost + (level.getBlockEntity(n) instanceof CableBlockEntity ? NEAR_CABLE : 0);
        if (c < best.getOrDefault(n, Integer.MAX_VALUE)) {
            best.put(n.immutable(), c);
            came.put(n.immutable(), new Step(cur.pos, d, climb));
            q.add(new Node(n.immutable(), d, c));
        }
    }

    /** Добавить провод, если дорожки на этой грани свободны. */
    private static boolean add(Level level, List<Piece> pieces, Piece p) {
        for (Piece o : pieces) if (o.cell.equals(p.cell) && o.face == p.face) return true;   // уже есть
        if (level.getBlockEntity(p.cell) instanceof CableBlockEntity be && !be.free(p.face, p.a, p.b)) return false;
        pieces.add(p);
        return true;
    }

    /** Проложить маршрут: поставить клетки кабеля и провода, пересчитать соединения. */
    public static void lay(Level level, Route route, int color, boolean fiber) {
        lay(level, route, color, fiber, 0);
    }

    /** Проложить маршрут; label — бирка проводов (0 — без бирки). */
    public static void lay(Level level, Route route, int color, boolean fiber, int label) {
        for (Piece p : route.pieces) {
            if (!(level.getBlockState(p.cell).getBlock() instanceof CableBlock)) {
                level.setBlockAndUpdate(p.cell, com.strikerr212.minepc.registry.ModBlocks.CABLE.get().defaultBlockState());
            }
            if (level.getBlockEntity(p.cell) instanceof CableBlockEntity be && be.free(p.face, p.a, p.b)) {
                Wire w = CableBlockEntity.make(color, fiber, p.face, p.a, p.b);
                w.label = label;
                be.add(w);
            }
        }
        for (int pass = 0; pass < 2; pass++) {
            for (Piece p : route.pieces) {
                if (level.getBlockEntity(p.cell) instanceof CableBlockEntity be) {
                    be.reconnect();
                    be.reconnectNeighbours();
                }
            }
        }
    }
}
