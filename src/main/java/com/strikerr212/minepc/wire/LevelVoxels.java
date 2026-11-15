package com.strikerr212.minepc.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

/**
 * Мир по пикселям для поиска пути: твёрдый пиксель — тот, чей центр внутри формы столкновений блока (целый блок,
 * плита, ступенька, забор, корпус устройства). Блоки без столкновений (трава, факел, вода) проводу не мешают.
 * Формы блоков запоминаются на время одного поиска.
 */
public final class LevelVoxels implements WireRouter.Voxels {

    private static final long[] EMPTY = new long[0], FULL = new long[1];

    private final BlockGetter level;
    private final Map<Long, long[]> cache = new HashMap<>();
    private long lastKey = Long.MIN_VALUE;
    private long[] last;

    public LevelVoxels(BlockGetter level) {
        this.level = level;
    }

    @Override
    public boolean solid(int x, int y, int z) {
        long key = BlockPos.asLong(x >> 4, y >> 4, z >> 4);
        long[] bits;
        if (key == lastKey) {
            bits = last;
        } else {
            bits = cache.get(key);
            if (bits == null) {
                bits = build(new BlockPos(x >> 4, y >> 4, z >> 4));
                cache.put(key, bits);
            }
            lastKey = key;
            last = bits;
        }
        if (bits == EMPTY) return false;
        if (bits == FULL) return true;
        int i = (((y & 15) << 4) | (z & 15)) << 4 | (x & 15);
        return (bits[i >> 6] >>> (i & 63) & 1) != 0;
    }

    private long[] build(BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (st.isAir()) return EMPTY;
        VoxelShape shape = st.getCollisionShape(level, pos);
        if (shape.isEmpty()) return EMPTY;
        if (Block.isShapeFullBlock(shape)) return FULL;
        long[] bits = new long[64];
        for (AABB b : shape.toAabbs()) {
            int x0 = lo(b.minX), x1 = hi(b.maxX), y0 = lo(b.minY), y1 = hi(b.maxY), z0 = lo(b.minZ), z1 = hi(b.maxZ);
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    for (int x = x0; x <= x1; x++) {
                        int i = ((y << 4) | z) << 4 | x;
                        bits[i >> 6] |= 1L << (i & 63);
                    }
                }
            }
        }
        return bits;
    }

    /** Первый пиксель, чей центр не меньше границы. */
    private static int lo(double v) {
        return Math.max(0, (int) Math.ceil(v * 16 - 0.5 - 1e-6));
    }

    /** Последний пиксель, чей центр меньше границы. */
    private static int hi(double v) {
        return Math.min(15, (int) Math.ceil(v * 16 - 0.5 - 1e-6) - 1);
    }
}
