package com.strikerr212.minepc.lan;

import com.strikerr212.minepc.block.BoxShapes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Настенная установка роутера и коммутатора: дно корпуса — к стене, гнёзда смотрят вниз (как у настоящих).
 * Модель та же, что на столе, повёрнутая в blockstate. У роутера гнёзда сзади — зад уходит вниз (x = 270);
 * у коммутатора гнёзда спереди — вниз уходит перед (x = 90). FACING у настенного — куда смотрит устройство от стены.
 * Координаты в пикселях: устройство задано как стоящее на столе лицом на север.
 */
public final class WallMount {

    public static final BooleanProperty WALL = BooleanProperty.create("wall");

    private WallMount() {
    }

    /** Точка со стола → на стене к северу. frontDown — коммутатор (перед вниз), иначе роутер (зад вниз). */
    private static float[] toWall(boolean frontDown, float x, float y, float z) {
        return frontDown ? new float[]{16 - x, z, y} : new float[]{x, 16 - z, y};
    }

    /** Поворот вокруг вертикали, как у {@link BoxShapes}: со стены к северу — к стене wall. */
    private static float[] rotY(Direction wall, float[] p) {
        return switch (wall) {
            case EAST -> new float[]{16 - p[2], p[1], p[0]};
            case SOUTH -> new float[]{16 - p[0], p[1], 16 - p[2]};
            case WEST -> new float[]{p[2], p[1], 16 - p[0]};
            default -> p;
        };
    }

    private static float[] transform(Direction out, boolean frontDown, float x, float y, float z) {
        return rotY(out.getOpposite(), toWall(frontDown, x, y, z));
    }

    private static Direction transform(Direction out, boolean frontDown, Direction d) {
        float[] p = transform(out, frontDown, 8 + 8 * d.getStepX(), 8 + 8 * d.getStepY(), 8 + 8 * d.getStepZ());
        return Direction.getNearest(p[0] - 8, p[1] - 8, p[2] - 8);
    }

    /** Сторона устройства на столе (лицом на север) → сторона настенного. */
    public static Direction face(Direction out, boolean frontDown, Direction local) {
        return transform(out, frontDown, local);
    }

    /** Гнездо настенного устройства: те же числа, что у {@link LanDevice#local}, для устройства на стене. */
    public static LanDevice.Socket socket(Direction out, boolean frontDown, boolean back, float x, float y, float depth,
                                          boolean copper, boolean fiber) {
        float[] p = transform(out, frontDown, x + 0.5f, y + 0.5f, back ? 16 - depth : depth);
        Direction face = transform(out, frontDown, back ? Direction.SOUTH : Direction.NORTH);
        Direction.Axis[] fa = LanDevice.faceAxes(face);
        float along = p[fa[0].ordinal()] - 0.5f, height = p[fa[1].ordinal()] - 0.5f;
        float plane = p[face.getAxis().ordinal()];
        float d = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 16 - plane : plane;
        return new LanDevice.Socket(face, along, height, d, copper, fiber);
    }

    /** Форма настенного устройства из коробки на столе (лицом на север). */
    public static BoxShapes shape(boolean frontDown, double x0, double y0, double z0, double x1, double y1, double z1) {
        return frontDown
                ? BoxShapes.of(16 - x1, z0, y0, 16 - x0, z1, y1)
                : BoxShapes.of(x0, 16 - z1, y0, x1, 16 - z0, y1);
    }
}
