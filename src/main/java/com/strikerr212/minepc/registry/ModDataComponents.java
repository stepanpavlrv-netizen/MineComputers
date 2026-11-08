package com.strikerr212.minepc.registry;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.hardware.spec.PartDesign;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Данные внутри предметов. Теперь у всех деталей один компонент —
 * "чертёж" (какие варианты выбраны в сборочном столе).
 */
public class ModDataComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MineComputers.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PartDesign>> DESIGN =
            COMPONENTS.register("design", () -> DataComponentType.<PartDesign>builder()
                    .persistent(PartDesign.CODEC)                 // сохранять в мир
                    .networkSynchronized(PartDesign.STREAM_CODEC)  // передавать игрокам
                    .build());

    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
    }
}
