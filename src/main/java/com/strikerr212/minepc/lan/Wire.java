package com.strikerr212.minepc.lan;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

/**
 * Один провод в блоке кабеля — полоска 1×1 пиксель, прижатая к одной грани клетки: к полу (face = DOWN),
 * к потолку (UP) или к стене (NORTH/SOUTH/WEST/EAST — в сторону стены).
 *
 * <p>У провода своя «дорожка» по каждой оси в плоскости грани ({@link #lane} — пиксель 0..15; в мирах до 0.31 было
 * 6 дорожек — пиксели 3, 5, … 13, они переводятся сами). Провод, проложенный щелчками, помнит, куда его вели
 * ({@link #ways}): соединяется только в эти стороны. У старых проводов — во все (как раньше). Идёт он
 * по одной из четырёх сторон грани ({@link #dirs}): на пол — север/юг/запад/восток, на стену — ещё вверх/вниз.
 * В каждую сторону провод соединяется:
 * <ul>
 *     <li>{@link #FLAT} — с таким же проводом на той же грани соседней клетки (или с лотком);</li>
 *     <li>{@link #INNER} — с проводом на соседней грани этой же клетки (пол → стена во внутреннем углу);</li>
 *     <li>{@link #OUTER} — через ребро опоры с проводом на её боку (верх блока → его стенка);</li>
 *     <li>вилкой — в гнездо устройства ({@link #plug}: номер порта + 1).</li>
 * </ul>
 * Соединяются провода одного цвета и вида на одной дорожке по оси ребра, через которое они встречаются.
 */
public final class Wire {

    /** Дорожек (пикселей) по оси. */
    public static final int LANES = 16;
    /** Дорожки старых миров (номер 0..5) → пиксель. */
    public static final int[] LEGACY_PX = {3, 5, 7, 9, 11, 13};
    /** Все четыре стороны: провод соединяется со всем, что подходит (старые провода и выложенные по одному). */
    public static final int ALL_WAYS = 15;
    /** Виды соединения в сторону. */
    public static final int NONE = 0, FLAT = 1, INNER = 2, OUTER = 3;
    /** Вилка есть, а порт ещё не выбран (провод из старого мира — порт выберется при загрузке). */
    public static final int PLUG_PENDING = 31;

    public int color;     // номер красителя 0..15
    public boolean fiber; // оптоволокно
    public final Direction face;
    /** Дорожка по осям X, Y, Z (по оси нормали грани не используется). */
    public final int[] lane = new int[3];
    /** Соединение в каждую из четырёх сторон {@link #dirs(Direction)}. */
    public final int[] conn = new int[4];
    /** Вилка в каждую сторону: 0 — нет, 1..30 — порт устройства + 1, {@link #PLUG_PENDING} — порт не выбран. */
    public final int[] plug = new int[4];
    /** Бирка (имя мотка с наковальни): номер в словаре схемы сети ({@link LanData#labelName}), 0 — без бирки (до 2047). */
    public int label;
    /** Стороны (биты по {@link #dirs()}), куда провод проложен; {@link #ALL_WAYS} — во все. */
    public int ways = ALL_WAYS;

    /** Можно ли проводу идти в сторону i. */
    public boolean way(int i) {
        return (ways & (1 << i)) != 0;
    }

    public Wire(int color, Direction face, int laneX, int laneY, int laneZ) {
        this.color = color;
        this.face = face;
        lane[0] = laneX;
        lane[1] = laneY;
        lane[2] = laneZ;
        lane[face.getAxis().ordinal()] = 0;
    }

    // ======================= Геометрия грани =======================

    private static final Direction[][] DIRS = new Direction[3][];

    static {
        for (Direction.Axis a : Direction.Axis.values()) {
            List<Direction> l = new ArrayList<>();
            for (Direction d : Direction.values()) if (d.getAxis() != a) l.add(d);
            DIRS[a.ordinal()] = l.toArray(new Direction[0]);
        }
    }

    /** Четыре стороны, куда может идти провод на этой грани (порядок постоянный — по нему хранятся conn и plug). */
    public static Direction[] dirs(Direction face) {
        return DIRS[face.getAxis().ordinal()];
    }

    public Direction[] dirs() {
        return dirs(face);
    }

    /** Номер стороны d среди {@link #dirs()} или -1, если провод в эту сторону идти не может. */
    public int index(Direction d) {
        Direction[] ds = dirs();
        for (int i = 0; i < 4; i++) if (ds[i] == d) return i;
        return -1;
    }

    /** Ось ребра, через которое провод уходит в сторону d (третья ось, не нормаль и не направление). */
    public static Direction.Axis edgeAxis(Direction face, Direction d) {
        for (Direction.Axis a : Direction.Axis.values()) if (a != face.getAxis() && a != d.getAxis()) return a;
        return Direction.Axis.Y;
    }

    /** Дорожка, по которой провод выходит в сторону d. */
    public int laneToward(Direction d) {
        return lane[edgeAxis(face, d).ordinal()];
    }

    /** Две оси в плоскости грани (в порядке X, Y, Z). */
    public static Direction.Axis[] planeAxes(Direction face) {
        Direction.Axis[] out = new Direction.Axis[2];
        int k = 0;
        for (Direction.Axis a : Direction.Axis.values()) if (a != face.getAxis()) out[k++] = a;
        return out;
    }

    /** Соединяется ли с другим проводом: тот же цвет и тот же вид (медь или оптика). */
    public boolean sameKind(Wire o) {
        return o.color == color && o.fiber == fiber;
    }

    public boolean connected(int i) {
        return conn[i] != NONE || plug[i] != 0;
    }

    /** Порт устройства, в которое провод воткнут в сторону i (или -1). */
    public int port(int i) {
        return plug[i] == 0 || plug[i] == PLUG_PENDING ? -1 : plug[i] - 1;
    }

    /**
     * Стороны, куда провод нарисован (номера в {@link #dirs()}): все соединения; если соединение одно — ещё и
     * противоположная сторона (провод идёт насквозь), если нет ни одного — прямо по второй оси грани.
     */
    public boolean[] drawn() {
        boolean[] out = new boolean[4];
        if (ways != ALL_WAYS) {
            // Проложен щелчками: рисуется туда, куда вели (свободный конец — тоже)
            for (int i = 0; i < 4; i++) out[i] = way(i);
            return out;
        }
        int n = 0;
        for (int i = 0; i < 4; i++) if (connected(i)) {
            out[i] = true;
            n++;
        }
        if (n == 0) {
            out[2] = out[3] = true;
        } else if (n == 1) {
            for (int i = 0; i < 4; i++) if (out[i]) out[i ^ 1] = true;   // стороны идут парами: 0-1, 2-3
        }
        return out;
    }

    // ======================= Хранение =======================

    /** Самый большой номер бирки, который помещается в провод. */
    public static final int MAX_LABEL_ID = 2047;

    /**
     * Провод одним числом: для модели и для схемы сети (LanData). С 0.31: цвет 0–3, оптика 4, грань 5–7,
     * дорожки по 4 бита 8–19, соединения 20–27, вилки 28–47, стороны 48–51, бирка 52–62, бит 63 — новый формат.
     */
    public long packed() {
        long p = color & 15;
        if (fiber) p |= 1L << 4;
        p |= (long) face.get3DDataValue() << 5;
        p |= (long) (lane[0] & 15) << 8 | (long) (lane[1] & 15) << 12 | (long) (lane[2] & 15) << 16;
        for (int i = 0; i < 4; i++) p |= (long) (conn[i] & 3) << (20 + 2 * i);
        for (int i = 0; i < 4; i++) p |= (long) (plug[i] & 31) << (28 + 5 * i);
        p |= (long) (ways & 15) << 48;
        p |= (long) (label & MAX_LABEL_ID) << 52;
        return p | 1L << 63;
    }

    public static Wire unpack(long p) {
        if (p >= 0) return unpackLegacy(p);
        Wire w = new Wire((int) (p & 15), Direction.from3DDataValue((int) (p >> 5) & 7),
                (int) (p >> 8) & 15, (int) (p >> 12) & 15, (int) (p >> 16) & 15);
        w.fiber = (p & (1L << 4)) != 0;
        for (int i = 0; i < 4; i++) w.conn[i] = (int) (p >> (20 + 2 * i)) & 3;
        for (int i = 0; i < 4; i++) w.plug[i] = (int) (p >> (28 + 5 * i)) & 31;
        w.ways = (int) (p >>> 48) & 15;
        w.label = (int) (p >>> 52) & MAX_LABEL_ID;
        return w;
    }

    /** Формат 0.22–0.30: дорожки 0..5 (по 3 бита) → пиксели, бирка 16 бит. */
    private static Wire unpackLegacy(long p) {
        Wire w = new Wire((int) (p & 15), Direction.from3DDataValue((int) (p >> 5) & 7),
                LEGACY_PX[Math.min(5, (int) (p >> 8) & 7)], LEGACY_PX[Math.min(5, (int) (p >> 11) & 7)],
                LEGACY_PX[Math.min(5, (int) (p >> 14) & 7)]);
        w.lane[w.face.getAxis().ordinal()] = 0;
        w.fiber = (p & (1L << 4)) != 0;
        for (int i = 0; i < 4; i++) w.conn[i] = (int) (p >> (17 + 2 * i)) & 3;
        for (int i = 0; i < 4; i++) w.plug[i] = (int) (p >> (25 + 5 * i)) & 31;
        int label = (int) (p >>> 45) & 0xFFFF;
        w.label = label <= MAX_LABEL_ID ? label : 0;
        return w;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("v", packed());
        return t;
    }

    /** Провода из сохранения; старый формат (до 0.22, провода только на полу, 4 дорожки) переводится в новый. */
    public static List<Wire> load(CompoundTag t) {
        if (t.contains("v")) return List.of(unpack(t.getLong("v")));
        int p = (t.getByte("c") & 15) | (t.getByte("x") & 3) << 4 | (t.getByte("z") & 3) << 6 | (t.getByte("d") & 15) << 8
                | (t.getByte("p") & 15) << 12 | (t.getByte("u") & 15) << 16 | (t.getByte("dn") & 15) << 20
                | (t.getBoolean("f") ? 1 << 24 : 0);
        return fromLegacy(p);
    }

    /** Старая дорожка (0..3, пиксели 2, 6, 10, 14) → новая (0..5). */
    private static final int[] LEGACY_LANE = {0, 1, 3, 5};
    /** Старые биты сторон: север, юг, запад, восток — в том же порядке, что {@link #dirs(Direction)} у пола. */
    private static final Direction[] LEGACY_DIRS = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    /**
     * Провод в старом формате (одно число) → провода нового. Подъём по стенке становится отдельным проводом
     * на этой стенке: пол → внутренний угол → стенка → внешний угол → верх стенки, как и раньше.
     */
    public static List<Wire> fromLegacy(int p) {
        int color = p & 15, xi = (p >> 4) & 3, zi = (p >> 6) & 3;
        int dirs = (p >> 8) & 15, plugs = (p >> 12) & 15, ups = (p >> 16) & 15, downs = (p >> 20) & 15;
        boolean fiber = (p & 1 << 24) != 0;
        List<Wire> out = new ArrayList<>();
        Wire w = new Wire(color, Direction.DOWN, LEGACY_PX[LEGACY_LANE[xi]], 0, LEGACY_PX[LEGACY_LANE[zi]]);
        w.fiber = fiber;
        out.add(w);
        int riserY = 0;
        for (int i = 0; i < 4; i++) {
            int bit = 1 << i;
            Direction d = LEGACY_DIRS[i];
            int k = w.index(d);
            if ((plugs & bit) != 0) w.plug[k] = PLUG_PENDING;
            else if ((ups & bit) != 0) {
                w.conn[k] = INNER;
                // Подъём: провод на стенке d, по ребру — та же дорожка, что у провода на полу
                Wire r = new Wire(color, d, 0, 0, 0);
                r.fiber = fiber;
                r.lane[edgeAxis(Direction.DOWN, d).ordinal()] = w.laneToward(d);
                r.lane[1] = LEGACY_PX[Math.min(5, riserY++)];
                r.conn[r.index(Direction.DOWN)] = INNER;
                r.conn[r.index(Direction.UP)] = OUTER;
                out.add(r);
            } else if ((downs & bit) != 0) w.conn[k] = OUTER;
            else if ((dirs & bit) != 0) w.conn[k] = FLAT;
        }
        return out;
    }
}
