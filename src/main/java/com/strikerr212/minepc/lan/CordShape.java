package com.strikerr212.minepc.lan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Форма висячего провода — патч-корда или воздушной линии: откуда он выходит из устройства и как провисает.
 * Одна и та же у игрока (рисуется) и на сервере (проверка, что провод не проходит сквозь блоки).
 */
public final class CordShape {

    /** Сколько точек на провод: и для отрисовки, и для проверки блоков. */
    public static final int SEGMENTS = 24;

    private CordShape() {
    }

    /**
     * Точка, где провод выходит из устройства: у кронштейна — с верхушки изолятора; у блоков с лицом — из гнезда на
     * лицевой стороне (корд петлёй идёт спереди); у остальных — сбоку, ближе к другому концу.
     */
    public static Vec3 anchor(BlockGetter level, BlockPos pos, BlockPos toward) {
        return anchor(level, pos, -1, toward);
    }

    /** То же, но для корда в порту port: если это гнездо на модели (у коммутатора) — вилка торчит прямо из него. */
    public static Vec3 anchor(BlockGetter level, BlockPos pos, int port, BlockPos toward) {
        BlockState s = level.getBlockState(pos);
        if (port >= 0 && port < LanData.CABLE_PORTS && s.getBlock() instanceof LanDevice dev) {
            java.util.List<LanDevice.Socket> socks = dev.sockets(level, pos, s);
            if (port < socks.size()) return socketPoint(pos, socks.get(port));
        }
        if (s.getBlock() instanceof BracketBlock) {
            return new Vec3(pos.getX() + 0.5, pos.getY() + 0.62, pos.getZ() + 0.5);
        }
        VoxelShape shape = s.getShape(level, pos);
        double top = shape.isEmpty() ? 1 : shape.max(Direction.Axis.Y);
        double y = Math.min(0.5, top * 0.6);
        Vec3 c = new Vec3(pos.getX() + 0.5, pos.getY() + y, pos.getZ() + 0.5);
        if (s.hasProperty(HorizontalDirectionalBlock.FACING)) {
            Direction f = s.getValue(HorizontalDirectionalBlock.FACING);
            double front = shape.isEmpty() ? 0.5 : (f.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? shape.max(f.getAxis()) - 0.5 : 0.5 - shape.min(f.getAxis()));
            c = c.add(f.getStepX() * (front + 0.03), 0, f.getStepZ() * (front + 0.03));
            // вдоль лица — немного в сторону другого конца
            Vec3 along = new Vec3(Math.abs(f.getStepZ()), 0, Math.abs(f.getStepX()));
            double side = (toward.getX() - pos.getX()) * along.x + (toward.getZ() - pos.getZ()) * along.z;
            return c.add(along.scale(Math.signum(side) * 0.3));
        }
        Vec3 dir = new Vec3(toward.getX() - pos.getX(), 0, toward.getZ() - pos.getZ());
        if (dir.lengthSqr() > 1e-4) {
            dir = dir.normalize();
            double k = 0.48 / Math.max(Math.abs(dir.x), Math.abs(dir.z));
            c = c.add(dir.scale(Math.min(0.5, k)));
        }
        return c;
    }

    /** Насколько вилка с проводом торчит прямо из гнезда, прежде чем провод начнёт провисать, блоков. */
    public static final double LEAD = 0.25;

    /** Куда смотрит гнездо, из которого выходит корд (ZERO — корд не из гнезда, провисает сразу). */
    public static Vec3 outward(BlockGetter level, BlockPos pos, int port) {
        BlockState s = level.getBlockState(pos);
        if (port >= 0 && port < LanData.CABLE_PORTS && s.getBlock() instanceof LanDevice dev) {
            java.util.List<LanDevice.Socket> socks = dev.sockets(level, pos, s);
            if (port < socks.size()) {
                Direction f = socks.get(port).face();
                return new Vec3(f.getStepX(), f.getStepY(), f.getStepZ());
            }
        }
        return Vec3.ZERO;
    }

    /** Точка чуть впереди гнезда (в мировых координатах): туда втыкается вилка. */
    public static Vec3 socketPoint(BlockPos pos, LanDevice.Socket so) {
        Direction f = so.face();
        boolean posDir = f.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double plane = (posDir ? 16 - so.depth() + 1.0 : so.depth() - 1.0) / 16;   // на пиксель наружу от лицевой плоскости
        double along = (so.along() + 0.5) / 16, h = (so.height() + 0.5) / 16;
        double x, y, z;
        if (f.getAxis() == Direction.Axis.Y) {
            x = along;
            z = h;
            y = plane;
        } else if (f.getAxis() == Direction.Axis.Z) {
            x = along;
            y = h;
            z = plane;
        } else {
            z = along;
            y = h;
            x = plane;
        }
        return new Vec3(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
    }

    /** Провисание посередине, блоков: патч-корд висит петлёй, воздушная линия натянута. */
    public static double sag(double dist) {
        return dist > 5 ? 0.1 + 0.025 * dist : 0.2 + 0.12 * dist;
    }

    /** Точки провода от a до b (в мировых координатах), SEGMENTS + 1 штук. */
    public static List<Vec3> points(Vec3 a, Vec3 b) {
        List<Vec3> out = new ArrayList<>(SEGMENTS + 1);
        double sag = sag(a.distanceTo(b));
        for (int i = 0; i <= SEGMENTS; i++) {
            double t = i / (double) SEGMENTS;
            out.add(a.lerp(b, t).add(0, -sag * 4 * t * (1 - t), 0));
        }
        return out;
    }

    /** Точки провода между двумя устройствами. */
    public static List<Vec3> points(BlockGetter level, BlockPos a, BlockPos b) {
        return points(level, a, -1, b, -1);
    }

    /** Точки корда между портом portA устройства a и портом portB устройства b. */
    public static List<Vec3> points(BlockGetter level, BlockPos a, int portA, BlockPos b, int portB) {
        return points(anchor(level, a, portA, b), outward(level, a, portA), anchor(level, b, portB, a), outward(level, b, portB));
    }

    /**
     * Корд из гнёзд: из каждого гнезда сначала прямо наружу на {@link #LEAD} (как вилка с жёстким хвостиком), дальше
     * провисает. na, nb — куда смотрят гнёзда (ZERO — без хвостика).
     */
    public static List<Vec3> points(Vec3 a, Vec3 na, Vec3 b, Vec3 nb) {
        boolean leadA = na.lengthSqr() > 0, leadB = nb.lengthSqr() > 0;
        if (!leadA && !leadB) return points(a, b);
        List<Vec3> out = new ArrayList<>(SEGMENTS + 3);
        if (leadA) out.add(a);
        out.addAll(points(leadA ? a.add(na.scale(LEAD)) : a, leadB ? b.add(nb.scale(LEAD)) : b));
        if (leadB) out.add(b);
        return out;
    }

    /**
     * Первый блок, сквозь который прошёл бы провод между a и b (null — путь чистый). Сами устройства на концах
     * не считаются. Проверяются и точки, и отрезки между ними — тонкие блоки (плиты, панели) тоже мешают.
     */
    public static BlockPos blocker(BlockGetter level, BlockPos a, BlockPos b) {
        return blocker(level, a, -1, b, -1);
    }

    public static BlockPos blocker(BlockGetter level, BlockPos a, int portA, BlockPos b, int portB) {
        List<Vec3> pts = points(level, a, portA, b, portB);
        for (int i = 0; i + 1 < pts.size(); i++) {
            Vec3 p0 = pts.get(i), p1 = pts.get(i + 1);
            for (int k = 0; k < 4; k++) {
                Vec3 p = p0.lerp(p1, k / 4.0);
                BlockPos cell = BlockPos.containing(p);
                if (cell.equals(a) || cell.equals(b)) continue;
                BlockState s = level.getBlockState(cell);
                if (s.isAir() || s.getBlock() instanceof CableBlock && !(level.getBlockEntity(cell) instanceof CableBlockEntity be && be.facade() != null)) {
                    continue;
                }
                VoxelShape shape = s.getCollisionShape(level, cell);
                if (shape.isEmpty()) continue;
                Vec3 local = p.subtract(cell.getX(), cell.getY(), cell.getZ());
                for (AABB box : shape.toAabbs()) {
                    if (box.inflate(0.02).contains(local)) return cell.immutable();
                }
            }
        }
        return null;
    }

    /** Коробка, внутри которой лежит провод между a и b (для быстрой проверки «может ли блок ему мешать»). */
    public static AABB bounds(BlockPos a, BlockPos b) {
        double dist = Math.sqrt(a.distSqr(b));
        return new AABB(Vec3.atLowerCornerOf(a), Vec3.atLowerCornerOf(b)).expandTowards(1, 1, 1).inflate(0.5, sag(dist) + 0.5, 0.5);
    }
}
