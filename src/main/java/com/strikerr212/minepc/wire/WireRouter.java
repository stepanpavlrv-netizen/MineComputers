package com.strikerr212.minepc.wire;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Поиск пути провода по сетке в 1 пиксель (1/16 блока). Провод идёт по свободным пикселям, прижимаясь к блокам:
 * «опора» — любой твёрдый пиксель по соседству (пол, стена, потолок, бок плиты, корпус устройства). Прямо идти
 * дешевле, чем поворачивать; по воздуху — дороже, только по прямой и не дальше {@link #MAX_AIR} пикселей (так провод
 * перешагивает ребро блока, спускается с полки и перекидывается через щель).
 *
 * <p>Один и тот же код считает призрак у игрока и настоящий провод на сервере — поэтому они совпадают.
 * Идея (сетка в пиксель, опора — соседний блок, прямые дешевле) — как в Create: Power Grid (Apache-2.0).
 */
public final class WireRouter {

    private WireRouter() {
    }

    /** Мир по пикселям: твёрдый ли пиксель (x, y, z). */
    public interface Voxels {
        boolean solid(int x, int y, int z);

        /** Желоб кабельного лотка: по нему тянуть провод дешевле, чем по полу. */
        default boolean lane(int x, int y, int z) {
            return false;
        }
    }

    /** Самый длинный прямой участок без опоры, пикселей (4 блока). */
    public static final int MAX_AIR = 64;
    /** Дальше за один щелчок провод не тянется, пикселей по осям в сумме (48 блоков). */
    public static final int MAX_REACH = 48 * 16;

    /** Цена шага: обычный, по воздуху (добавка), по желобу лотка; поворот. */
    private static final int STEP = 10, AIR = 14, LANE = 5, TURN = 36;
    private static final int[] DX = {1, -1, 0, 0, 0, 0}, DY = {0, 0, 1, -1, 0, 0}, DZ = {0, 0, 0, 0, 1, -1};

    /**
     * Путь: вершины (пиксели) и для каждого отрезка — висит ли он. reached — дошёл ли до цели (иначе путь до самой
     * близкой к цели точки — показать, где застрял).
     */
    public record Result(int[] xs, int[] ys, int[] zs, boolean[] hang, boolean reached) {
        public int nodes() {
            return xs.length;
        }

        /** Длина в пикселях. */
        public int pixels() {
            int n = 0;
            for (int i = 1; i < xs.length; i++) n += Math.abs(xs[i] - xs[i - 1]) + Math.abs(ys[i] - ys[i - 1]) + Math.abs(zs[i] - zs[i - 1]);
            return n;
        }
    }

    private static final class Node implements Comparable<Node> {
        int x, y, z, dir, air, g, f;
        Node parent;
        boolean closed;

        @Override
        public int compareTo(Node o) {
            return Integer.compare(f, o.f);
        }
    }

    public static boolean supported(Voxels v, int x, int y, int z) {
        return v.solid(x + 1, y, z) || v.solid(x - 1, y, z) || v.solid(x, y + 1, z) || v.solid(x, y - 1, z)
                || v.solid(x, y, z + 1) || v.solid(x, y, z - 1);
    }

    /** Направление 0..5 по вектору (один ненулевой компонент) или -1. */
    public static int dirOf(int dx, int dy, int dz) {
        if (dx > 0) return 0;
        if (dx < 0) return 1;
        if (dy > 0) return 2;
        if (dy < 0) return 3;
        if (dz > 0) return 4;
        if (dz < 0) return 5;
        return -1;
    }

    /**
     * Найти путь от пикселя s до пикселя t. startDir — куда провод шёл до этого (чтобы продолжать прямо), -1 — всё равно.
     * endSupported — цель считается опорой (гнездо, свободный конец другого провода). maxNodes — предел перебора.
     *
     * <p>Поиск идёт «прыжками»: из точки — прямо, пока не случится что-то интересное: препятствие, край опоры,
     * проём сбоку или цель на этой же линии. Только в таких местах провод и может повернуть.
     */
    public static Result route(Voxels v, int sx, int sy, int sz, int startDir, int tx, int ty, int tz, boolean endSupported, int maxNodes) {
        if (Math.abs(tx - sx) + Math.abs(ty - sy) + Math.abs(tz - sz) > MAX_REACH) return null;
        if (v.solid(tx, ty, tz) || v.solid(sx, sy, sz)) return null;
        if (sx == tx && sy == ty && sz == tz) return null;
        int x0 = Math.min(sx, tx) - MARGIN, x1 = Math.max(sx, tx) + MARGIN, y0 = Math.min(sy, ty) - MARGIN, y1 = Math.max(sy, ty) + MARGIN,
                z0 = Math.min(sz, tz) - MARGIN, z1 = Math.max(sz, tz) + MARGIN;
        Map<Long, Node> nodes = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>();
        Node start = new Node();
        start.x = sx;
        start.y = sy;
        start.z = sz;
        start.dir = startDir < 0 ? 6 : startDir;
        start.g = 0;
        start.f = h(sx, sy, sz, tx, ty, tz);
        nodes.put(key(0, 0, 0, start.dir), start);
        open.add(start);
        Node best = start;
        int bestH = start.f;
        int expanded = 0;
        Node goal = null;
        boolean[] side = new boolean[6];
        while (!open.isEmpty()) {
            Node cur = open.poll();
            if (cur.closed) continue;
            cur.closed = true;
            if (cur.x == tx && cur.y == ty && cur.z == tz) {
                goal = cur;
                break;
            }
            if (++expanded > maxNodes) break;
            int hh = cur.f - cur.g;
            if (hh < bestH && cur.air == 0) {
                bestH = hh;
                best = cur;
            }
            for (int d = 0; d < 6; d++) {
                if (cur.dir < 6 && d == (cur.dir ^ 1)) continue;          // назад не ходим
                int cost = cur.dir != 6 && d != cur.dir ? TURN : 0;
                int air = cur.air;
                boolean prevSup = cur.air == 0;
                // что по бокам от линии хода: свободно и с опорой (можно свернуть) или нет
                for (int q = 0; q < 6; q++) {
                    side[q] = (q >> 1) != (d >> 1) && prevSup && open(v, cur.x + DX[q], cur.y + DY[q], cur.z + DZ[q]);
                }
                int px = cur.x, py = cur.y, pz = cur.z, prevCost = cost, prevAir = air;
                boolean prevLane = v.lane(cur.x, cur.y, cur.z);
                boolean prevAdded = true;
                for (int k = 1; ; k++) {
                    int nx = cur.x + DX[d] * k, ny = cur.y + DY[d] * k, nz = cur.z + DZ[d] * k;
                    boolean blocked = nx < x0 || nx > x1 || ny < y0 || ny > y1 || nz < z0 || nz > z1 || v.solid(nx, ny, nz);
                    if (blocked) {
                        if (!prevAdded && prevAir == 0) add(nodes, open, cur, px, py, pz, d, prevAir, cur.g + prevCost, sx, sy, sz, tx, ty, tz);
                        break;
                    }
                    boolean target = nx == tx && ny == ty && nz == tz;
                    boolean sup = (target && endSupported) || supported(v, nx, ny, nz);
                    if (!sup) {
                        air++;
                        if (air > MAX_AIR) break;
                        // в воздухе — только прямо; свернуть с пикселя без опоры можно лишь на опору (обогнуть ребро)
                        if (k == 1 && cur.air >= 1 && d != cur.dir) break;
                    } else {
                        air = 0;
                    }
                    boolean lane = v.lane(nx, ny, nz);
                    cost += (lane ? LANE : STEP) + (sup ? 0 : AIR);
                    boolean cand = target || (d >> 1 == 0 ? nx == tx : d >> 1 == 1 ? ny == ty : nz == tz);
                    boolean edge = sup != prevSup || lane != prevLane;
                    if (sup) {
                        for (int q = 0; q < 6; q++) {
                            if ((q >> 1) == (d >> 1)) continue;
                            boolean st = open(v, nx + DX[q], ny + DY[q], nz + DZ[q]);
                            if (st != side[q]) edge = true;
                            side[q] = st;
                        }
                    } else if (prevSup) {
                        for (int q = 0; q < 6; q++) side[q] = false;
                    }
                    if (edge && !prevAdded && prevAir == 0) add(nodes, open, cur, px, py, pz, d, prevAir, cur.g + prevCost, sx, sy, sz, tx, ty, tz);
                    // первый пиксель без опоры — тоже точка поворота (обогнуть ребро блока)
                    boolean add = cand || edge;
                    if (add && (sup || air == 1 || target)) {
                        add(nodes, open, cur, nx, ny, nz, d, air, cur.g + cost, sx, sy, sz, tx, ty, tz);
                        prevAdded = true;
                    } else {
                        prevAdded = false;
                    }
                    if (target) break;
                    px = nx;
                    py = ny;
                    pz = nz;
                    prevSup = sup;
                    prevLane = lane;
                    prevCost = cost;
                    prevAir = air;
                }
            }
        }
        Node end = goal != null ? goal : best;
        if (end == start) return null;
        return trace(v, end, goal != null, tx, ty, tz, endSupported);
    }

    /** Пиксель свободен и с опорой — туда можно свернуть. */
    private static boolean open(Voxels v, int x, int y, int z) {
        return !v.solid(x, y, z) && supported(v, x, y, z);
    }

    private static void add(Map<Long, Node> nodes, PriorityQueue<Node> open, Node from, int x, int y, int z, int d, int air, int g,
                            int sx, int sy, int sz, int tx, int ty, int tz) {
        long k = key(x - sx, y - sy, z - sz, d);
        Node n = nodes.get(k);
        if (n == null) {
            n = new Node();
            n.x = x;
            n.y = y;
            n.z = z;
            n.dir = d;
            n.g = Integer.MAX_VALUE;
            nodes.put(k, n);
        }
        if (n.closed || g >= n.g) return;
        n.g = g;
        n.air = air;
        n.parent = from;
        n.f = g + h(x, y, z, tx, ty, tz);
        open.add(n);
    }

    private static int h(int x, int y, int z, int tx, int ty, int tz) {
        // оценка с запасом — поиск идёт к цели, а не вширь (путь может выйти на пару поворотов длиннее лучшего)
        return (Math.abs(tx - x) + Math.abs(ty - y) + Math.abs(tz - z)) * STEP * WEIGHT / 10;
    }

    /** Во сколько раз оценка остатка пути больше точной, в десятых. */
    public static int WEIGHT = 12;
    /** Насколько путь может отойти в сторону от коробки «начало — цель», пикселей (8 блоков). */
    private static final int MARGIN = 8 * 16;

    private static long key(int x, int y, int z, int dir) {
        return ((long) (x + 4096) << 29) | ((long) (y + 4096) << 16) | ((long) (z + 4096) << 3) | dir;
    }

    /** Собрать путь: вершины — где меняется направление или кончается/начинается участок без опоры. */
    private static Result trace(Voxels v, Node end, boolean reached, int tx, int ty, int tz, boolean endSupported) {
        List<Node> chain = new ArrayList<>();
        for (Node n = end; n != null; n = n.parent) chain.add(0, n);
        // узлы поиска стоят только в точках поворота — восстановим все пиксели между ними
        List<Node> cells = new ArrayList<>();
        cells.add(chain.get(0));
        for (int i = 1; i < chain.size(); i++) {
            Node a = chain.get(i - 1), b = chain.get(i);
            int d = b.dir;
            int steps = Math.abs(b.x - a.x) + Math.abs(b.y - a.y) + Math.abs(b.z - a.z);
            for (int k = 1; k <= steps; k++) {
                Node c = new Node();
                c.x = a.x + DX[d] * k;
                c.y = a.y + DY[d] * k;
                c.z = a.z + DZ[d] * k;
                c.dir = d;
                cells.add(c);
            }
        }
        int n = cells.size();
        boolean[] airCell = new boolean[n];
        for (int i = 1; i < n; i++) {
            Node c = cells.get(i);
            boolean target = c.x == tx && c.y == ty && c.z == tz;
            airCell[i] = !((target && endSupported) || supported(v, c.x, c.y, c.z));
        }
        // длинные участки без опоры (3 пикселя и больше) — висят
        boolean[] longAir = new boolean[n];
        for (int i = 0; i < n; ) {
            if (!airCell[i]) {
                i++;
                continue;
            }
            int j = i;
            while (j < n && airCell[j]) j++;
            if (j - i >= 3) for (int k = i; k < j; k++) longAir[k] = true;
            i = j;
        }
        boolean[] stepHang = new boolean[n];
        for (int i = 1; i < n; i++) stepHang[i] = longAir[i] || longAir[i - 1];
        List<int[]> verts = new ArrayList<>();
        List<Boolean> hangs = new ArrayList<>();
        verts.add(new int[]{cells.get(0).x, cells.get(0).y, cells.get(0).z});
        for (int i = 1; i < n; i++) {
            boolean last = i == n - 1;
            if (last || cells.get(i + 1).dir != cells.get(i).dir || stepHang[i + 1] != stepHang[i]) {
                verts.add(new int[]{cells.get(i).x, cells.get(i).y, cells.get(i).z});
                hangs.add(stepHang[i]);
            }
        }
        int m = verts.size();
        int[] xs = new int[m], ys = new int[m], zs = new int[m];
        boolean[] hang = new boolean[m - 1];
        for (int i = 0; i < m; i++) {
            xs[i] = verts.get(i)[0];
            ys[i] = verts.get(i)[1];
            zs[i] = verts.get(i)[2];
            if (i < m - 1) hang[i] = hangs.get(i);
        }
        return new Result(xs, ys, zs, hang, reached);
    }
}
