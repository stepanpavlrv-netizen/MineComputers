package com.strikerr212.minepc.registry;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.block.PcCaseBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * BlockEntity — это "мозг" блока: хранит инвентарь и любые данные,
 * которых нет у обычных блоков вроде камня.
 */
public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MineComputers.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PcCaseBlockEntity>> PC_CASE =
            BLOCK_ENTITIES.register("pc_case",
                    () -> BlockEntityType.Builder.of(PcCaseBlockEntity::new, ModBlocks.PC_CASE.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
