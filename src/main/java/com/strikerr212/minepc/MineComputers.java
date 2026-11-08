package com.strikerr212.minepc;

import com.mojang.logging.LogUtils;
import com.strikerr212.minepc.computer.LuaSelfTest;
import com.strikerr212.minepc.registry.ModBlockEntities;
import com.strikerr212.minepc.registry.ModBlocks;
import com.strikerr212.minepc.registry.ModCreativeTabs;
import com.strikerr212.minepc.registry.ModDataComponents;
import com.strikerr212.minepc.registry.ModItems;
import com.strikerr212.minepc.registry.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

// Аннотация @Mod говорит NeoForge: "это главный класс мода с таким ID"
@Mod(MineComputers.MODID)
public class MineComputers {

    // ID мода. Должен совпадать с mod_id из gradle.properties!
    public static final String MODID = "minepc";

    public static final Logger LOGGER = LogUtils.getLogger();

    public MineComputers(IEventBus modEventBus, ModContainer modContainer) {
        // Порядок вызовов здесь не важен: NeoForge сам регистрирует
        // всё в правильной последовательности.
        ModDataComponents.register(modEventBus);  // данные внутри предметов
        ModBlocks.register(modEventBus);          // блоки
        ModItems.register(modEventBus);           // предметы
        ModBlockEntities.register(modEventBus);   // "умные" блоки с инвентарём
        ModMenus.register(modEventBus);           // окна с интерфейсом (сборочный стол)
        ModCreativeTabs.register(modEventBus);    // вкладка в творческом режиме

        LOGGER.info("MineComputers is loading! " + LuaSelfTest.run());
    }
}
