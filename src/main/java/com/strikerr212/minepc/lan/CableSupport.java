package com.strikerr212.minepc.lan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Держит ли блок провод на своей грани: крепкая грань, а сверху — ещё и почти полный блок (тропинка, грядка). */
public final class CableSupport {

    private CableSupport() {
    }

    /** support — блок-опора, face — его грань, к которой прижат провод (у провода на полу — UP). */
    public static boolean holds(BlockGetter level, BlockPos support, Direction face) {
        BlockState s = level.getBlockState(support);
        if (s.isFaceSturdy(level, support, face)) return true;
        if (face != Direction.UP) return false;
        VoxelShape c = s.getCollisionShape(level, support);
        return !c.isEmpty() && c.max(Direction.Axis.Y) >= 0.875 && c.min(Direction.Axis.X) <= 0.01 && c.max(Direction.Axis.X) >= 0.99
                && c.min(Direction.Axis.Z) <= 0.01 && c.max(Direction.Axis.Z) >= 0.99;
    }
}
