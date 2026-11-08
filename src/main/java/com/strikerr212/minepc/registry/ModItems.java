package com.strikerr212.minepc.registry;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.item.CoolerItem;
import com.strikerr212.minepc.item.CpuItem;
import com.strikerr212.minepc.item.DriveItem;
import com.strikerr212.minepc.item.GpuItem;
import com.strikerr212.minepc.item.HardwareItem;
import com.strikerr212.minepc.item.HddItem;
import com.strikerr212.minepc.item.MotherboardItem;
import com.strikerr212.minepc.item.PsuItem;
import com.strikerr212.minepc.item.RamItem;
import com.strikerr212.minepc.item.SsdItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Все предметы мода.
 *
 * Каждый вид детали — ОДИН предмет. Его характеристики и внешний вид
 * хранятся в данных конкретного экземпляра (см. ModDataComponents и HardwarePresets).
 */
public class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MineComputers.MODID);

    public static final DeferredItem<MotherboardItem> MOTHERBOARD =
            ITEMS.register("motherboard", () -> new MotherboardItem(new Item.Properties()));
    public static final DeferredItem<CpuItem> CPU =
            ITEMS.register("cpu", () -> new CpuItem(new Item.Properties()));
    public static final DeferredItem<CoolerItem> COOLER =
            ITEMS.register("cooler", () -> new CoolerItem(new Item.Properties()));
    public static final DeferredItem<RamItem> RAM =
            ITEMS.register("ram", () -> new RamItem(new Item.Properties()));
    public static final DeferredItem<GpuItem> GPU =
            ITEMS.register("gpu", () -> new GpuItem(new Item.Properties()));
    public static final DeferredItem<PsuItem> PSU =
            ITEMS.register("psu", () -> new PsuItem(new Item.Properties()));
    public static final DeferredItem<HddItem> HDD =
            ITEMS.register("hdd", () -> new HddItem(new Item.Properties()));
    public static final DeferredItem<SsdItem> SSD =
            ITEMS.register("ssd", () -> new SsdItem(new Item.Properties()));

    // Предметы-блоки (то, что лежит в инвентаре)
    public static final DeferredItem<BlockItem> PC_CASE =
            ITEMS.registerSimpleBlockItem("pc_case", ModBlocks.PC_CASE);
    public static final DeferredItem<BlockItem> ASSEMBLY_TABLE =
            ITEMS.registerSimpleBlockItem("assembly_table", ModBlocks.ASSEMBLY_TABLE);

    public static final DeferredItem<BlockItem> MONITOR =
            ITEMS.registerSimpleBlockItem("monitor", ModBlocks.MONITOR);
    public static final DeferredItem<BlockItem> KEYBOARD =
            ITEMS.registerSimpleBlockItem("keyboard", ModBlocks.KEYBOARD);
    public static final DeferredItem<BlockItem> DISK_DRIVE =
            ITEMS.registerSimpleBlockItem("disk_drive", ModBlocks.DISK_DRIVE);

    // Съёмные накопители: файлы хранятся в папке мира, в предмете — только номер
    public static final DeferredItem<DriveItem> FLOPPY_DISK =
            ITEMS.register("floppy_disk", () -> new DriveItem(DriveItem.Kind.FLOPPY, 512 * 1024, new Item.Properties()));
    public static final DeferredItem<DriveItem> FLASH_DRIVE =
            ITEMS.register("flash_drive", () -> new DriveItem(DriveItem.Kind.FLASH, 4 * 1024 * 1024, new Item.Properties()));

    /** Все комплектующие одним списком — удобно перебирать в цикле. */
    public static final List<DeferredItem<? extends HardwareItem>> HARDWARE =
            List.of(MOTHERBOARD, CPU, COOLER, RAM, GPU, PSU, HDD, SSD);

    /** Предмет детали по её виду. */
    public static DeferredItem<? extends HardwareItem> forType(HardwareType type) {
        return switch (type) {
            case MOTHERBOARD -> MOTHERBOARD;
            case CPU -> CPU;
            case COOLER -> COOLER;
            case RAM -> RAM;
            case GPU -> GPU;
            case PSU -> PSU;
            case HDD -> HDD;
            case SSD -> SSD;
        };
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
