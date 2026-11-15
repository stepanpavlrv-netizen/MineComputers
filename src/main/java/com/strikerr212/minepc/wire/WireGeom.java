package com.strikerr212.minepc.wire;

import com.strikerr212.minepc.lan.LanDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Где у гнезда вилка и с какого пикселя провод уходит от него; пиксель по щелчку на поверхность. */
public final class WireGeom {

    private WireGeom() {
    }

    /** Пиксели мира: блок × 16 + пиксель в блоке. */
    public static int[] pixelOf(Vec3 v) {
        return new int[]{(int) Math.floor(v.x * 16), (int) Math.floor(v.y * 16), (int) Math.floor(v.z * 16)};
    }

    /** Точка провода в единицах 1/32 блока — центр пикселя. */
    public static int unit(int pixel) {
        return pixel * 2 + 1;
    }

    /** Пиксель снаружи стороны блока, куда щёлкнули: туда ляжет провод. */
    public static int[] surfacePixel(Vec3 hit, Direction side) {
        return pixelOf(hit.add(side.getStepX() / 32.0, side.getStepY() / 32.0, side.getStepZ() / 32.0));
    }

    public static LanDevice.Socket socket(BlockGetter level, BlockPos dev, int port) {
        List<LanDevice.Socket> s = LanDevice.socketsAt(level, dev);
        return port >= 0 && port < s.size() ? s.get(port) : null;
    }

    /** Координаты центра гнезда в пикселях блока (вдоль, высота) → в пикселях мира на плоскости лицевой стороны. */
    private static double[] center(BlockPos dev, LanDevice.Socket so) {
        Direction f = so.face();
        boolean pos = f.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double plane = pos ? 16 - so.depth() : so.depth();
        Direction.Axis[] ax = LanDevice.faceAxes(f);
        double[] c = new double[3];
        c[ax[0].ordinal()] = so.along() + 0.5;
        c[ax[1].ordinal()] = so.height() + 0.5;
        c[f.getAxis().ordinal()] = plane;
        return new double[]{dev.getX() * 16 + c[0], dev.getY() * 16 + c[1], dev.getZ() * 16 + c[2]};
    }

    /** Пиксель прямо перед гнездом — отсюда провод уходит (или сюда приходит). */
    public static int[] socketPixel(BlockPos dev, LanDevice.Socket so) {
        double[] c = center(dev, so);
        Direction f = so.face();
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) out[i] = (int) Math.floor(c[i] - 0.5 + 1e-4);
        int n = f.getAxis().ordinal();
        out[n] = f.getAxisDirection() == Direction.AxisDirection.POSITIVE ? (int) Math.round(c[n]) : (int) Math.round(c[n]) - 1;
        return out;
    }

    /** Точка вилки (в единицах 1/32): на лицевой плоскости, в центре гнезда. */
    public static int[] socketNode(BlockPos dev, LanDevice.Socket so) {
        double[] c = center(dev, so);
        int[] px = socketPixel(dev, so);
        int n = so.face().getAxis().ordinal();
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) out[i] = WireGeom.unit(px[i]);
        out[n] = (int) Math.round(c[n] * 2);       // сама вилка — на плоскости гнезда
        return out;
    }

    /** Направление поиска пути из гнезда: прочь от устройства. */
    public static int socketDir(LanDevice.Socket so) {
        Direction f = so.face();
        return WireRouter.dirOf(f.getStepX(), f.getStepY(), f.getStepZ());
    }
}
