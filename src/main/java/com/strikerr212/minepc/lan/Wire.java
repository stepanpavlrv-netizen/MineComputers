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

    public Wire(int color, int xi, int zi) {
        this.color = color;
        this.xi = xi;
        this.zi = zi;
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
        return t;
    }

    public static Wire load(CompoundTag t) {
        Wire w = new Wire(t.getByte("c"), t.getByte("x"), t.getByte("z"));
        w.dirs = t.getByte("d");
        w.plugs = t.getByte("p");
        return w;
    }

    /** Совпадает с проводом в другом блоке (для отрисовки и сравнения при синхронизации). */
    public int packed() {
        return color | xi << 4 | zi << 6 | dirs << 8 | plugs << 12;
    }
}
