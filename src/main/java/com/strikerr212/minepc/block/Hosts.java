package com.strikerr212.minepc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/** Найти компьютер по его {@link HostRef}: системный блок или модуль серверной стойки. */
public final class Hosts {

    private Hosts() {
    }

    /** Компьютер или null (блока нет, чанк не загружен, модуля в этом месте нет). */
    public static PcCaseBlockEntity get(BlockGetter level, HostRef ref) {
        return get(level, ref.pos(), ref.slot());
    }

    public static PcCaseBlockEntity get(BlockGetter level, BlockPos pos, int slot) {
        if (level instanceof net.minecraft.world.level.LevelReader r && !r.hasChunkAt(pos)) return null;
        var be = level.getBlockEntity(pos);
        if (be instanceof PcCaseBlockEntity pc) return pc;
        if (be instanceof RackBlockEntity rack) return rack.server(slot);
        return null;
    }
}
