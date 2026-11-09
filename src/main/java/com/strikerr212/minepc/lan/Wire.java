package com.strikerr212.minepc.lan;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

/**
 * Один провод в блоке кабеля. В блоке до 4 проводов, у каждого своя «дорожка» по обеим осям:
 * xi — где он идёт поперёк оси Z (север–юг), zi — поперёк оси X (запад–восток). Дорожки 0..3 = пиксели 1–3, 5–7, 9–11, 13–15.
 * Провод соединяется с проводом соседнего блока того же цвета на той же дорожке, а с устройством — вилкой в гнездо этой дорожки.
 */
public final class Wire {

    /** Центры дорожек в пикселях блока. */
    public static final int[] LANE = {2, 6, 10, 14};
    /** Биты сторон: север, юг, запад, восток. */
    public static final int N = 1, S = 2, W = 4, E = 8;

    public int color;     // номер красителя 0..15
    public final int xi, zi;
    public int dirs;      // куда идёт (соседний провод или устройство)
    public int plugs;     // где вилка в устройство
    public int ups;       // где поднимается по стенке соседнего блока на блок выше
    public int downs;     // где спускается к проводу на блок ниже (он поднимается к нам)
    public boolean fiber; // оптоволокно: 10 Гбит/с, до 2000 блоков, только в стоечный коммутатор

    public Wire(int color, int xi, int zi) {
        this.color = color;
        this.xi = xi;
        this.zi = zi;
    }

    /** Соединяется ли с другим проводом: тот же цвет и тот же вид (медь или оптика). */
    public boolean sameKind(Wire o) {
        return o.color == color && o.fiber == fiber;
    }

    public static int bit(Direction d) {
        return switch (d) {
            case NORTH -> N;
            case SOUTH -> S;
            case WEST -> W;
            case EAST -> E;
            default -> 0;
        };
    }

    /** Дорожка, по которой провод выходит в эту сторону: на восток/запад — zi, на север/юг — xi. */
    public int laneToward(Direction d) {
        return d.getAxis() == Direction.Axis.X ? zi : xi;
    }

    /** Стороны, куда провод нарисован: если соединений меньше двух — идёт прямо насквозь. */
    public int drawnDirs() {
        int d = dirs;
        if (d == 0) return W | E;
        if (Integer.bitCount(d) == 1) {
            if ((d & (N | S)) != 0) return N | S;
            return W | E;
        }
        return d;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putByte("c", (byte) color);
        t.putByte("x", (byte) xi);
        t.putByte("z", (byte) zi);
        t.putByte("d", (byte) dirs);
        t.putByte("p", (byte) plugs);
        t.putByte("u", (byte) ups);
        t.putByte("dn", (byte) downs);
        if (fiber) t.putBoolean("f", true);
        return t;
    }

    public static Wire load(CompoundTag t) {
        Wire w = new Wire(t.getByte("c"), t.getByte("x"), t.getByte("z"));
        w.dirs = t.getByte("d");
        w.plugs = t.getByte("p");
        w.ups = t.getByte("u");
        w.downs = t.getByte("dn");
        w.fiber = t.getBoolean("f");
        return w;
    }

    /** Провод одним числом: для модели и для схемы сети (LanData). */
    public int packed() {
        return color | xi << 4 | zi << 6 | dirs << 8 | plugs << 12 | ups << 16 | downs << 20 | (fiber ? 1 << 24 : 0);
    }

    public static Wire unpack(int p) {
        Wire w = new Wire(p & 15, (p >> 4) & 3, (p >> 6) & 3);
        w.dirs = (p >> 8) & 15;
        w.plugs = (p >> 12) & 15;
        w.ups = (p >> 16) & 15;
        w.downs = (p >> 20) & 15;
        w.fiber = (p & 1 << 24) != 0;
        return w;
    }

    public static Direction dir(int bit) {
        return switch (bit) {
            case N -> Direction.NORTH;
            case S -> Direction.SOUTH;
            case W -> Direction.WEST;
            default -> Direction.EAST;
        };
    }
}
