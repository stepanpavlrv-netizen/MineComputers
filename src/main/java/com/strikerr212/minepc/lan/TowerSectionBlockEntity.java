package com.strikerr212.minepc.lan;

import com.strikerr212.minepc.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Секция вышки: только чтобы рисовать мигающие огни (данных нет). */
public class TowerSectionBlockEntity extends BlockEntity {

    public TowerSectionBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOWER_SECTION.get(), pos, state);
    }

    /** Есть ли огни у этой секции: на макушке и каждые 6 секций. */
    public static boolean lamps(BlockState state) {
        return state.getValue(TowerSectionBlock.ABOVE) == 0 || state.getValue(TowerSectionBlock.HEIGHT) % 6 == 5;
    }
}
