package com.strikerr212.minepc.lan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Путь провода по точкам излома — одна и та же логика у игрока (призрак) и на сервере (прокладка).
 *
 * <p>Точка — пиксель на грани клетки (пол, стена, потолок) или гнездо устройства. Между соседними точками путь ищется
 * по поверхностям (по полу, вверх по стене через внутренний угол, вниз через внешний), с наименьшим числом
 * поворотов. Точка излома — ровно там, куда щёлкнули (до пикселя). Две точки-гнезда без изломов между ними — это
 * прокладка «от гнезда до гнезда»: путь найдётся сам.
 */
public final class CablePath {

    private CablePath() {
    }

    /** Точка пути: клетка, грань, пиксели по осям плоскости грани (u — первая, v — вторая); dev/port — гнездо. */
    public record Point(BlockPos cell, Direction face, int u, int v, BlockPos dev, int port) {
        public boolean socket() {
            return dev != null;
        }

        public boolean same(Point o) {
            return o != null && cell.equals(o.cell) && face == o.face && u == o.u && v == o.v && port == o.port
                    && (dev == null ? o.dev == null : dev.equals(o.dev));
        }
    }

    /** Кусок провода: клетка, грань, дорожки по X, Y, Z, стороны (биты по Wire.dirs). */
    public record Piece(BlockPos cell, Direction face, int[] lanes, int ways) {
        public Wire wire(int color, boolean fiber) {
            Wire w = new Wire(color, face, lanes[0], lanes[1], lanes[2]);
            w.fiber = fiber;
            w.ways = ways;
            return w;
        }
    }

    /** Что мешает: где и почему (ключ перевода). */
    public record Problem(BlockPos cell, String reason) {
    }

    /** Итог: куски провода, сколько клеток (кабеля), что мешает. */
    public record Result(List<Piece> pieces, List<Problem> problems) {
        public boolean ok() {
            return problems.isEmpty() && !pieces.isEmpty();
        }

        public int length() {
            java.util.Set<BlockPos> cells = new java.util.HashSet<>();
            for (Piece p : pieces) cells.add(p.cell());
            return cells.size();
        }
    }

    // ======================= Поверхности =======================

    /** Клетка свободна для провода: воздух (трава, снег) или кабель без маскировки. */
    public static boolean open(Level level, BlockPos p) {
        if (!level.isLoaded(p)) return false;
        BlockState s = level.getBlockState(p);
        if (s.getBlock() instanceof CableBlock) return !(level.getBlockEntity(p) instanceof CableBlockEntity be) || be.facade() == null;
        return s.canBeReplaced() && s.getFluidState().isEmpty();
    }

    /** На грани face клетки cell можно положить провод: клетка свободна, грань держит опора. */
    public static boolean surface(Level level, BlockPos cell, Direction face) {
        return open(level, cell) && CableSupport.holds(level, cell.relative(face), face.getOpposite());
    }

    // ======================= Точки из прицела =======================

    /** Точка на поверхности под прицелом (провод ляжет на ту грань, по которой щёлкнули). */
    public static Point surfacePoint(Level level, BlockHitResult hit) {
        BlockPos clicked = hit.getBlockPos();
        BlockPos cell;
        Direction face;
        if (level.getBlockEntity(clicked) instanceof CableBlockEntity be && be.facade() == null && !be.wires().isEmpty()) {
            Vec3 at = hit.getLocation().subtract(clicked.getX(), clicked.getY(), clicked.getZ()).scale(16);
            Wire near = be.nearest(at.x, at.y, at.z);
            cell = clicked;
            face = near.face;
        } else {
            cell = clicked.relative(hit.getDirection());
            face = hit.getDirection().getOpposite();
        }
        Vec3 at = hit.getLocation().subtract(cell.getX(), cell.getY(), cell.getZ()).scale(16);
        Direction.Axis[] ax = Wire.planeAxes(face);
        return new Point(cell.immutable(), face, px(get(at, ax[0])), px(get(at, ax[1])), null, -1);
    }

    private static int px(double v) {
        return Math.max(0, Math.min(15, (int) Math.floor(v)));
    }

    private static double get(Vec3 v, Direction.Axis a) {
        return a == Direction.Axis.X ? v.x : a == Direction.Axis.Y ? v.y : v.z;
    }

    /** Свободно ли гнездо port устройства dev (в него не воткнут провод). */
    public static boolean socketFree(Level level, BlockPos dev, int port) {
        List<LanDevice.Socket> socks = LanDevice.socketsAt(level, dev);
        if (port < 0 || port >= socks.size()) return false;
        if (CordList.inSocket(level, dev, port)) return false;     // в гнезде патч-корд
        Direction f = socks.get(port).face();
        if (level.getBlockEntity(dev.relative(f)) instanceof CableBlockEntity be) {
            for (Wire w : be.wires()) {
                int k = w.index(f.getOpposite());
                if (k >= 0 && w.port(k) == port) return false;
            }
        }
        return true;
    }

    /** Гнездо под прицелом (ближайшее на той стороне, куда щёлкнули; иначе ближайшее вообще), свободное и подходящее. */
    public static int socketAt(Level level, BlockPos dev, Vec3 hit, Direction side, boolean fiber) {
        List<LanDevice.Socket> socks = LanDevice.socketsAt(level, dev);
        Vec3 at = hit.subtract(dev.getX(), dev.getY(), dev.getZ()).scale(16);
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < socks.size(); i++) {
            LanDevice.Socket s = socks.get(i);
            if (!s.accepts(fiber) || !socketFree(level, dev, i)) continue;
            Vec3 c = socketCenter(s);
            double d = c.distanceToSqr(at) + (s.face() == side ? 0 : 400);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** Середина гнезда в пикселях блока устройства. */
    public static Vec3 socketCenter(LanDevice.Socket s) {
        Direction.Axis[] fa = LanDevice.faceAxes(s.face());
        double[] c = new double[3];
        c[fa[0].ordinal()] = s.along() + 0.5;
        c[fa[1].ordinal()] = s.height() + 0.5;
        int n = s.face().getAxis().ordinal();
        c[n] = s.face().getAxisDirection() == Direction.AxisDirection.POSITIVE ? 16 - s.depth() : s.depth();
        return new Vec3(c[0], c[1], c[2]);
    }

    /**
     * Точка у гнезда: клетка перед гнездом, грань — пол (или стена, потолок — что держит провод), дорожки — напротив
     * гнезда. null — гнезда нет.
     */
    public static Point socketPoint(Level level, BlockPos dev, int port) {
        List<LanDevice.Socket> socks = LanDevice.socketsAt(level, dev);
        if (port < 0 || port >= socks.size()) return null;
        LanDevice.Socket s = socks.get(port);
        BlockPos cell = dev.relative(s.face()).immutable();
        Direction face = null;
        List<Direction> order = new ArrayList<>(List.of(Direction.DOWN));
        for (Direction d : Direction.Plane.HORIZONTAL) order.add(d);
        order.add(Direction.UP);
        for (Direction d : order) {
            if (d.getAxis() == s.face().getAxis()) continue;
            if (surface(level, cell, d)) {
                face = d;
                break;
            }
        }
        if (face == null) face = s.face().getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.DOWN;
        Direction.Axis[] ax = Wire.planeAxes(face), fa = LanDevice.faceAxes(s.face());
        int[] uv = new int[2];
        for (int i = 0; i < 2; i++) {
            Direction.Axis a = ax[i];
            if (a == fa[0]) uv[i] = px(s.along() + 0.5);
            else if (a == fa[1]) uv[i] = px(s.height() + 0.5);
            else uv[i] = 8;
        }
        return new Point(cell, face, uv[0], uv[1], dev.immutable(), port);
    }

    // ======================= Путь =======================

    private static final int STEP = 10, TURN = 16, FACE = 14, MAX_NODES = 60_000, MAX_DIST = 128;
    /** Дорожки для поворотов, которые никто не задал, — по порядку, пока не найдётся свободная. */
    private static final int[] FREE_LANES = {8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15, 0};

    /** Состояние поиска: клетка, грань, куда шли (null — стоим на месте). */
    private record State(BlockPos cell, Direction face, Direction heading) {
        long key() {
            return cell.asLong() * 64 + face.ordinal() * 8L + (heading == null ? 7 : heading.ordinal());
        }
    }

    /** Переход: из клетки в клетку, вид (FLAT, INNER, OUTER), куда ушли из прежней. */
    private record Move(State to, int type, Direction dir) {
    }

    private record Node(State s, int cost, int est) {
    }

    /** Путь по всем точкам. fiber — оптика. */
    public static Result build(Level level, List<Point> points, boolean fiber, int color) {
        List<Piece> pieces = new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        for (Point p : points) {
            if (!p.socket() && !surface(level, p.cell(), p.face())) problems.add(new Problem(p.cell(), "message.minepc.draw.no_support"));
            // Перед гнездом пусто (розетка высоко на стене) — провод повис бы в воздухе
            if (p.socket() && !surface(level, p.cell(), p.face())) problems.add(new Problem(p.cell(), "message.minepc.draw.socket_air"));
            if (p.socket() && !surface(level, p.cell(), p.face())) problems.add(new Problem(p.cell(), "message.minepc.draw.socket_blocked"));
        }
        if (!problems.isEmpty() || points.size() < 2) return new Result(pieces, problems);
        Map<String, Piece> merged = new LinkedHashMap<>();
        for (int i = 0; i + 1 < points.size(); i++) {
            Point a = points.get(i), b = points.get(i + 1);
            if (a.cell().distManhattan(b.cell()) > MAX_DIST) {
                problems.add(new Problem(b.cell(), "message.minepc.draw.far"));
                break;
            }
            List<Piece> leg = leg(level, a, b, fiber, color, new ArrayList<>(merged.values()), problems);
            if (leg == null) break;
            for (Piece p : leg) {
                String key = p.cell().asLong() + ":" + p.face() + ":" + p.lanes()[0] + ":" + p.lanes()[1] + ":" + p.lanes()[2];
                Piece old = merged.get(key);
                merged.put(key, old == null ? p : new Piece(p.cell(), p.face(), p.lanes(), old.ways() | p.ways()));
            }
        }
        pieces.addAll(merged.values());
        // Занятость: с проводами, что уже лежат, и с другими кусками этого же пути
        for (int i = 0; i < pieces.size() && problems.isEmpty(); i++) {
            Piece p = pieces.get(i);
            Wire w = p.wire(color, fiber);
            if (level.getBlockEntity(p.cell()) instanceof CableBlockEntity be && !be.fits(w)) {
                problems.add(new Problem(p.cell(), "message.minepc.draw.busy"));
            }
            for (int j = 0; j < i; j++) {
                Piece q = pieces.get(j);
                if (q.cell().equals(p.cell()) && CableBlockEntity.overlaps(q.wire(color, fiber), w)) {
                    problems.add(new Problem(p.cell(), "message.minepc.draw.self"));
                    break;
                }
            }
        }
        return new Result(pieces, problems);
    }

    /** Отрезок пути между двумя точками или null (причина — в problems). */
    private static List<Piece> leg(Level level, Point a, Point b, boolean fiber, int color, List<Piece> before, List<Problem> problems) {
        List<State> states = new ArrayList<>();
        List<Move> moves = new ArrayList<>();
        if (!search(level, a, b, states, moves, problems)) return null;
        int n = states.size();
        // Стороны каждого куска: откуда пришли и куда ушли (+ к гнезду на концах)
        int[] ways = new int[n];
        for (int k = 0; k < n - 1; k++) {
            Move m = moves.get(k);
            ways[k] |= bit(states.get(k).face(), m.dir());
            Direction entry = switch (m.type()) {
                case Wire.FLAT -> m.dir().getOpposite();
                case Wire.INNER -> states.get(k).face();
                default -> states.get(k).face().getOpposite();
            };
            ways[k + 1] |= bit(states.get(k + 1).face(), entry);
        }
        if (a.socket()) ways[0] |= bit(states.get(0).face(), toward(a));
        if (b.socket()) ways[n - 1] |= bit(states.get(n - 1).face(), toward(b));
        // Дорожки: равенства на каждом стыке (по оси ребра), концы — по точкам
        int[] parent = new int[n * 3];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        for (int k = 0; k < n - 1; k++) {
            int e = Wire.edgeAxis(states.get(k).face(), moves.get(k).dir()).ordinal();
            union(parent, k * 3 + e, (k + 1) * 3 + e);
        }
        Map<Integer, Integer> fixed = new HashMap<>();
        fix(fixed, parent, 0, states.get(0).face(), a);
        fix(fixed, parent, n - 1, states.get(n - 1).face(), b);
        for (int lane : FREE_LANES) {
            List<Piece> out = new ArrayList<>(n);
            for (int k = 0; k < n; k++) {
                int[] l = new int[3];
                for (Direction.Axis ax : Wire.planeAxes(states.get(k).face())) {
                    int root = find(parent, k * 3 + ax.ordinal());
                    l[ax.ordinal()] = fixed.getOrDefault(root, lane);
                }
                out.add(new Piece(states.get(k).cell(), states.get(k).face(), l, ways[k]));
            }
            if (free(level, out, before, color, fiber)) return out;
        }
        problems.add(new Problem(states.get(n / 2).cell(), "message.minepc.draw.busy"));
        return null;
    }

    /** Все куски ложатся: не налезают на провода в мире и на уже найденные куски пути. */
    private static boolean free(Level level, List<Piece> pieces, List<Piece> before, int color, boolean fiber) {
        for (int i = 0; i < pieces.size(); i++) {
            Piece p = pieces.get(i);
            Wire w = p.wire(color, fiber);
            if (level.getBlockEntity(p.cell()) instanceof CableBlockEntity be && !be.fits(w)) return false;
            for (Piece q : before) if (q.cell().equals(p.cell()) && !sameSpot(q, p) && CableBlockEntity.overlaps(q.wire(color, fiber), w)) return false;
            for (int j = 0; j < i; j++) {
                Piece q = pieces.get(j);
                if (q.cell().equals(p.cell()) && !sameSpot(q, p) && CableBlockEntity.overlaps(q.wire(color, fiber), w)) return false;
            }
        }
        return true;
    }

    private static boolean sameSpot(Piece a, Piece b) {
        return a.face() == b.face() && java.util.Arrays.equals(a.lanes(), b.lanes());
    }

    /** Дорожки точки: щелчок — обе (пиксель под прицелом), гнездо — только напротив гнезда (вдоль и по высоте). */
    private static void fix(Map<Integer, Integer> fixed, int[] parent, int k, Direction face, Point p) {
        Direction.Axis[] ax = Wire.planeAxes(face);
        int[] uv = {p.u(), p.v()};
        Direction.Axis toDev = p.socket() ? toward(p).getAxis() : null;
        for (int i = 0; i < 2; i++) {
            if (ax[i] == toDev) continue;
            fixed.putIfAbsent(find(parent, k * 3 + ax[i].ordinal()), uv[i]);
        }
    }

    /** Сторона от клетки перед гнездом к самому устройству. */
    private static Direction toward(Point p) {
        for (Direction d : Direction.values()) if (p.cell().relative(d).equals(p.dev())) return d;
        return Direction.NORTH;
    }

    private static int bit(Direction face, Direction d) {
        Direction[] ds = Wire.dirs(face);
        for (int i = 0; i < 4; i++) if (ds[i] == d) return 1 << i;
        return 0;
    }

    private static int find(int[] p, int i) {
        while (p[i] != i) i = p[i] = p[p[i]];
        return i;
    }

    private static void union(int[] p, int a, int b) {
        p[find(p, a)] = find(p, b);
    }

    /** Поиск пути по поверхностям (A*): меньше всего шагов, поворотов и переходов с грани на грань. */
    private static boolean search(Level level, Point a, Point b, List<State> states, List<Move> moves, List<Problem> problems) {
        State start = new State(a.cell(), a.face(), null);
        if (a.cell().equals(b.cell()) && a.face() == b.face()) {
            states.add(start);
            return true;
        }
        Map<Long, Integer> best = new HashMap<>();
        Map<Long, Object[]> came = new HashMap<>();
        PriorityQueue<Node> q = new PriorityQueue<>((x, y) -> Integer.compare(x.est, y.est));
        q.add(new Node(start, 0, h(a.cell(), b.cell())));
        best.put(start.key(), 0);
        State found = null;
        BlockPos closest = a.cell();
        int nodes = 0;
        while (!q.isEmpty() && nodes++ < MAX_NODES) {
            Node cur = q.poll();
            if (cur.cost > best.getOrDefault(cur.s.key(), Integer.MAX_VALUE)) continue;
            if (cur.s.cell().distManhattan(b.cell()) < closest.distManhattan(b.cell())) closest = cur.s.cell();
            if (cur.s.cell().equals(b.cell()) && cur.s.face() == b.face()) {
                found = cur.s;
                break;
            }
            for (Move m : neighbours(level, cur.s, b)) {
                boolean turn = cur.s.heading() != null && cur.s.heading() != m.dir();
                int c = cur.cost + STEP + (turn ? TURN : 0) + (m.type() == Wire.FLAT ? 0 : FACE);
                if (c < best.getOrDefault(m.to().key(), Integer.MAX_VALUE)) {
                    best.put(m.to().key(), c);
                    came.put(m.to().key(), new Object[]{cur.s, m});
                    q.add(new Node(m.to(), c, c + h(m.to().cell(), b.cell())));
                }
            }
        }
        if (found == null) {
            problems.add(new Problem(closest, "message.minepc.draw.no_path"));
            return false;
        }
        List<State> rs = new ArrayList<>();
        List<Move> rm = new ArrayList<>();
        for (State s = found; s != null; ) {
            rs.add(0, s);
            Object[] c = came.get(s.key());
            if (c == null) break;
            rm.add(0, (Move) c[1]);
            s = (State) c[0];
        }
        states.addAll(rs);
        moves.addAll(rm);
        return true;
    }

    private static int h(BlockPos a, BlockPos b) {
        return a.distManhattan(b) * STEP;
    }

    /** Куда можно шагнуть: по той же грани в соседнюю клетку, на соседнюю грань этой клетки, за угол опоры. */
    private static List<Move> neighbours(Level level, State s, Point goal) {
        List<Move> out = new ArrayList<>(12);
        Direction f = s.face();
        for (Direction d : Wire.dirs(f)) {
            BlockPos n = s.cell().relative(d);
            if (ok(level, n, f, goal)) out.add(new Move(new State(n.immutable(), f, d), Wire.FLAT, d));
            if (ok(level, s.cell(), d, goal)) out.add(new Move(new State(s.cell(), d, f.getOpposite()), Wire.INNER, d));
            BlockPos t = n.relative(f);
            if (ok(level, t, d.getOpposite(), goal)) out.add(new Move(new State(t.immutable(), d.getOpposite(), f), Wire.OUTER, d));
        }
        return out;
    }

    private static boolean ok(Level level, BlockPos cell, Direction face, Point goal) {
        return surface(level, cell, face) || (cell.equals(goal.cell()) && face == goal.face() && open(level, cell));
    }
}
