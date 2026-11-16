package com.strikerr212.minepc.wire;

import com.strikerr212.minepc.lan.LanDevice;
import com.strikerr212.minepc.lan.TrayBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Маскировка «провод в стене»: ПКМ обычным блоком по проводу — блок встаёт на его место (как на любое место у
 * поверхности), провод проходит сквозь него. Внутри блока провод не рисуется (только там, где блок), сломал блок —
 * провод снова виден; связь не рвётся. Прицел кусачек, тестера и бирки видит провод и внутри блока.
 */
public final class WireMask {

    private WireMask() {
    }

    /** Точка провода p спрятана в блоке: внутри его формы столкновений (устройства и лотки — не маскировка). */
    public static boolean inside(BlockGetter level, Vec3 p) {
        BlockPos cell = BlockPos.containing(p);
        BlockState s = level.getBlockState(cell);
        if (s.isAir() || s.getBlock() instanceof LanDevice || s.getBlock() instanceof TrayBlock) return false;
        VoxelShape shape = s.getCollisionShape(level, cell);
        if (shape.isEmpty()) return false;
        Vec3 local = p.subtract(cell.getX(), cell.getY(), cell.getZ());
        for (AABB b : shape.toAabbs()) if (b.inflate(0.01).contains(local)) return true;
        return false;
    }
}
