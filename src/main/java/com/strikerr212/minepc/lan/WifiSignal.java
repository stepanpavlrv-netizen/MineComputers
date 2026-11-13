package com.strikerr212.minepc.lan;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Сила сигнала Wi-Fi — одна формула для компьютера (сервер) и для карты покрытия (у игрока).
 * Дальность — меньшая из дальности адаптера и радиуса роутера; каждая сплошная стена на прямой отнимает у роутера
 * 3 блока, у потолочной точки доступа — 1 (она висит выше и стены ей мешают меньше).
 */
public final class WifiSignal {

    private WifiSignal() {
    }

    /** Сколько блоков дальности отнимает одна стена у этой точки раздачи. */
    public static int wallCost(BlockState router) {
        return router.getBlock() instanceof AccessPointBlock ? 1 : 3;
    }

    /** Сколько непрозрачных блоков на прямой между центрами двух блоков (сами они не считаются). */
    public static int walls(BlockGetter level, BlockPos a, BlockPos b) {
        Vec3 from = Vec3.atCenterOf(a), to = Vec3.atCenterOf(b);
        double len = from.distanceTo(to);
        int steps = (int) Math.ceil(len * 4);
        Set<BlockPos> seen = new HashSet<>();
        int count = 0;
        for (int i = 1; i < steps; i++) {
            BlockPos p = BlockPos.containing(from.lerp(to, i / (double) steps));
            if (p.equals(a) || p.equals(b) || !seen.add(p)) continue;
            if (level instanceof LevelReader r && !r.hasChunkAt(p)) continue;
            if (level.getBlockState(p).isSolidRender(level, p)) count++;
        }
        return count;
    }

    /** Сигнал в процентах (10..100) или 0 — не достаёт. range — дальность без стен. */
    public static int signal(double dist, int range, int walls, int wallCost) {
        int effective = range - wallCost * walls;
        if (effective <= 0 || dist > effective) return 0;
        return (int) Math.round(10 + 90 * (1 - dist / effective));
    }

    /** Скорость по сигналу: у края зоны — четверть, ближе — половина, рядом — вся. */
    public static int mbit(int signal, int adapterMbit) {
        int factor = signal >= 60 ? 4 : signal >= 35 ? 2 : 1;
        return Math.max(1, Math.min(adapterMbit, RouterBlockEntity.RADIO_MBIT) * factor / 4);
    }
}
