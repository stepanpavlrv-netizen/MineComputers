package com.strikerr212.minepc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Большой монитор: мониторы, смотрящие в одну сторону и сложенные ровным прямоугольником
 * (до 8×8 блоков), показывают одну общую картинку.
 *
 * @param cols   ширина в блоках
 * @param rows   высота в блоках
 * @param col    этот блок: номер столбца слева (если смотреть на экран)
 * @param row    этот блок: номер строки сверху
 * @param blocks все блоки монитора
 */
public record MonitorGroup(int cols, int rows, int col, int row, Set<BlockPos> blocks) {

    public static final int MAX = 8;

    public static MonitorGroup single(BlockPos pos) {
        return new MonitorGroup(1, 1, 0, 0, Set.of(pos));
    }

    private static boolean sameMonitor(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof MonitorBlock && s.getValue(DeviceBlock.FACING) == facing;
    }

    /** Найти монитор, в который входит блок pos. Если мониторы стоят не прямоугольником — каждый сам по себе. */
    public static MonitorGroup of(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof MonitorBlock)) return single(pos);
        Direction facing = state.getValue(DeviceBlock.FACING);
        Direction right = facing.getCounterClockWise();     // «вправо», если смотреть на экран
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(pos);
        seen.add(pos);
        int minR = 0, maxR = 0, minU = 0, maxU = 0;
        while (!queue.isEmpty() && seen.size() <= MAX * MAX) {
            BlockPos p = queue.poll();
            for (Direction d : List.of(right, right.getOpposite(), Direction.UP, Direction.DOWN)) {
                BlockPos n = p.relative(d);
                if (seen.contains(n) || !sameMonitor(level, n, facing)) continue;
                seen.add(n);
                queue.add(n);
            }
        }
        if (seen.size() > MAX * MAX) return single(pos);
        for (BlockPos p : seen) {
            BlockPos rel = p.subtract(pos);
            int r = rel.getX() * right.getStepX() + rel.getZ() * right.getStepZ();
            int u = rel.getY();
            minR = Math.min(minR, r);
            maxR = Math.max(maxR, r);
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
        }
        int cols = maxR - minR + 1, rows = maxU - minU + 1;
        if (cols * rows != seen.size() || cols > MAX || rows > MAX) return single(pos);
        return new MonitorGroup(cols, rows, -minR, maxU, seen);
    }
}
