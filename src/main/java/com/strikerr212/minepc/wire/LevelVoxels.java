package com.strikerr212.minepc.wire;

import com.strikerr212.minepc.lan.TrayBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Мир по пикселям для поиска пути: твёрдый пиксель — тот, чей центр внутри формы столкновений блока (целый блок,
 * плита, ступенька, забор, корпус устройства). Блоки без столкновений (трава, факел, вода) проводу не мешают.
 * Формы блоков запоминаются на время одного поиска.
 *
 * <p>В кабельном лотке ({@link TrayBlock}) пиксели, где уже лежат другие провода, тоже твёрдые — так каждый новый
 * провод ложится на свою дорожку; а желоб лотка — «дорожка», по ней путь дешевле ({@link #lane}).
 */
public final class LevelVoxels implements WireRouter.Voxels {

    private static final long[] EMPTY = new long[0], FULL = new long[1];

    private final BlockGetter level;
    private final Map<Long, long[]> cache = new HashMap<>();
    /** Желоба лотков: клетка → пиксели дорожек (только у клеток с лотком). */
    private final Map<Long, long[]> lanes = new HashMap<>();
    private long lastKey = Long.MIN_VALUE;
    private long[] last;
    private WirePlan.Lines wires;
    private Set<Long> exclude = Set.of();

    public LevelVoxels(BlockGetter level) {
        this.level = level;
    }

    /** Учитывать провода в лотках (кроме проводов exclude — например, того, который сейчас продолжают). */
    public LevelVoxels wires(WirePlan.Lines lines, Set<Long> exclude) {
        this.wires = lines;
        this.exclude = exclude;
        return this;
    }

    private long[] bits(int x, int y, int z) {
        long key = BlockPos.asLong(x >> 4, y >> 4, z >> 4);
        if (key == lastKey) return last;
        long[] bits = cache.get(key);
        if (bits == null) {
            bits = build(new BlockPos(x >> 4, y >> 4, z >> 4));
            cache.put(key, bits);
        }
        lastKey = key;
        last = bits;
        return bits;
    }

    private static int index(int x, int y, int z) {
        return (((y & 15) << 4) | (z & 15)) << 4 | (x & 15);
    }

    private static boolean get(long[] bits, int x, int y, int z) {
        if (bits == EMPTY) return false;
        if (bits == FULL) return true;
        int i = index(x, y, z);
        return (bits[i >> 6] >>> (i & 63) & 1) != 0;
    }

    @Override
    public boolean solid(int x, int y, int z) {
        return get(bits(x, y, z), x, y, z);
    }

    @Override
    public boolean lane(int x, int y, int z) {
        bits(x, y, z);                       // клетка разобрана — желоб (если это лоток) уже известен
        long[] l = lanes.get(BlockPos.asLong(x >> 4, y >> 4, z >> 4));
        return l != null && get(l, x, y, z) && !solid(x, y, z);
    }

    private long[] build(BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (st.isAir()) return EMPTY;
        VoxelShape shape = st.getCollisionShape(level, pos);
        if (shape.isEmpty()) return EMPTY;
        if (Block.isShapeFullBlock(shape)) return FULL;
        long[] bits = new long[64];
        for (AABB b : shape.toAabbs()) fill(bits, b.minX * 16, b.minY * 16, b.minZ * 16, b.maxX * 16, b.maxY * 16, b.maxZ * 16);
        if (st.getBlock() instanceof TrayBlock) {
            long[] ch = new long[64];
            for (double[] b : TrayBlock.channel(st)) fill(ch, b[0], b[1], b[2], b[3], b[4], b[5]);
            lanes.put(pos.asLong(), ch);
            if (wires != null) occupy(bits, pos);
        }
        return bits;
    }

    private static void fill(long[] bits, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        int x0 = lo(minX), x1 = hi(maxX), y0 = lo(minY), y1 = hi(maxY), z0 = lo(minZ), z1 = hi(maxZ);
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    int i = ((y << 4) | z) << 4 | x;
                    bits[i >> 6] |= 1L << (i & 63);
                }
            }
        }
    }

    /** Пиксели клетки pos, по которым уже лежат провода, — твёрдые. */
    private void occupy(long[] bits, BlockPos pos) {
        int cx = pos.getX(), cy = pos.getY(), cz = pos.getZ();
        for (WireLine l : wires.near(pos)) {
            if (exclude.contains(l.id)) continue;
            for (int i = 0; i + 1 < l.nodes(); i++) {
                if (l.hang[i]) continue;
                int ax = (l.xs[i] - 1) >> 1, ay = (l.ys[i] - 1) >> 1, az = (l.zs[i] - 1) >> 1;
                int bx = (l.xs[i + 1] - 1) >> 1, by = (l.ys[i + 1] - 1) >> 1, bz = (l.zs[i + 1] - 1) >> 1;
                int n = Math.abs(bx - ax) + Math.abs(by - ay) + Math.abs(bz - az);
                if (n > 4096 || Integer.bitCount((ax != bx ? 1 : 0) | (ay != by ? 2 : 0) | (az != bz ? 4 : 0)) > 1) continue;
                int sx = Integer.signum(bx - ax), sy = Integer.signum(by - ay), sz = Integer.signum(bz - az);
                for (int k = 0; k <= n; k++) {
                    int x = ax + sx * k, y = ay + sy * k, z = az + sz * k;
                    if (x >> 4 != cx || y >> 4 != cy || z >> 4 != cz) continue;
                    int idx = index(x, y, z);
                    bits[idx >> 6] |= 1L << (idx & 63);
                }
            }
        }
    }

    /** Первый пиксель, чей центр не меньше границы (граница — в пикселях). */
    private static int lo(double v) {
        return Math.max(0, (int) Math.ceil(v - 0.5 - 1e-6));
    }

    /** Последний пиксель, чей центр меньше границы. */
    private static int hi(double v) {
        return Math.min(15, (int) Math.ceil(v - 0.5 - 1e-6) - 1);
    }
}
