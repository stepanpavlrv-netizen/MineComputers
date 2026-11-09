package com.strikerr212.minepc.registry;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.hardware.HardwarePresets;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MineComputers.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.minepc"))
                    .icon(() -> HardwarePresets.cpu(5))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.ASSEMBLY_TABLE.get());
                        output.accept(ModItems.PC_CASE.get());
                        for (var item : ModItems.CASES.values()) output.accept(item.get());
                        output.accept(ModItems.MONITOR.get());
                        output.accept(ModItems.MONITOR_CRT.get());
                        output.accept(ModItems.MONITOR_TV.get());
                        output.accept(ModItems.MONITOR_TERMINAL.get());
                        output.accept(ModItems.MONITOR_PANEL.get());
                        output.accept(ModItems.MONITOR_BLOCK.get());
                        output.accept(ModItems.MONITOR_BLOCK_WHITE.get());
                        output.accept(ModItems.MONITOR_BLOCK_RETRO.get());
                        output.accept(ModItems.KEYBOARD.get());
                        output.accept(ModItems.KEYBOARD_STAND.get());
                        output.accept(ModItems.DISK_DRIVE.get());
                        output.accept(ModItems.FLOPPY_DISK.get());
                        output.accept(ModItems.FLASH_DRIVE.get());
                        // стоковые детали: 5 уровней каждого вида
                        for (int tier = 1; tier <= HardwarePresets.MAX_TIER; tier++) output.accept(HardwarePresets.motherboard(tier));
                        for (int tier = 1; tier <= HardwarePresets.MAX_TIER; tier++) output.accept(HardwarePresets.cpu(tier));
                        for (int tier = 1; tier <= HardwarePresets.MAX_TIER; tier++) output.accept(HardwarePresets.cooler(tier));
                        for (int tier = 1; tier <= HardwarePresets.MAX_TIER; tier++) output.accept(HardwarePresets.ram(tier));
                        for (int tier = 1; tier <= HardwarePresets.MAX_TIER; tier++) output.accept(HardwarePresets.gpu(tier));
                        for (int tier = 1; tier <= HardwarePresets.MAX_TIER; tier++) output.accept(HardwarePresets.psu(tier));
                        for (int tier = 1; tier <= HardwarePresets.MAX_TIER; tier++) output.accept(HardwarePresets.hdd(tier));
                        for (int tier = 1; tier <= HardwarePresets.MAX_TIER; tier++) output.accept(HardwarePresets.ssd(tier));
                        // примеры "самосборных" деталей с другим видом и характеристиками
                        for (ItemStack example : HardwarePresets.customExamples()) output.accept(example);
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_TABS.register(eventBus);
    }
}
