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
    public static final java.util.Map<com.strikerr212.minepc.block.CaseVariant, DeferredItem<BlockItem>> CASES = new java.util.EnumMap<>(com.strikerr212.minepc.block.CaseVariant.class);
    static {
        for (var e : ModBlocks.CASES.entrySet()) CASES.put(e.getKey(), ITEMS.registerSimpleBlockItem(e.getKey().id, e.getValue()));
    }
    public static final DeferredItem<BlockItem> ASSEMBLY_TABLE =
            ITEMS.registerSimpleBlockItem("assembly_table", ModBlocks.ASSEMBLY_TABLE);

    public static final DeferredItem<BlockItem> MONITOR =
            ITEMS.registerSimpleBlockItem("monitor", ModBlocks.MONITOR);
    public static final DeferredItem<BlockItem> MONITOR_CRT = ITEMS.registerSimpleBlockItem("monitor_crt", ModBlocks.MONITOR_CRT);
    public static final DeferredItem<BlockItem> MONITOR_TV = ITEMS.registerSimpleBlockItem("monitor_tv", ModBlocks.MONITOR_TV);
    public static final DeferredItem<BlockItem> MONITOR_TERMINAL = ITEMS.registerSimpleBlockItem("monitor_terminal", ModBlocks.MONITOR_TERMINAL);
    public static final DeferredItem<BlockItem> MONITOR_PANEL = ITEMS.registerSimpleBlockItem("monitor_panel", ModBlocks.MONITOR_PANEL);
    public static final DeferredItem<BlockItem> MONITOR_BLOCK = ITEMS.registerSimpleBlockItem("monitor_block", ModBlocks.MONITOR_BLOCK);
    public static final DeferredItem<BlockItem> MONITOR_BLOCK_WHITE = ITEMS.registerSimpleBlockItem("monitor_block_white", ModBlocks.MONITOR_BLOCK_WHITE);
    public static final DeferredItem<BlockItem> MONITOR_BLOCK_RETRO = ITEMS.registerSimpleBlockItem("monitor_block_retro", ModBlocks.MONITOR_BLOCK_RETRO);
    public static final DeferredItem<BlockItem> MONITOR_WIDE = ITEMS.registerSimpleBlockItem("monitor_wide", ModBlocks.MONITOR_WIDE);
    public static final DeferredItem<BlockItem> MONITOR_CURVED = ITEMS.registerSimpleBlockItem("monitor_curved", ModBlocks.MONITOR_CURVED);
    public static final DeferredItem<com.strikerr212.minepc.lan.CableItem> CABLE = ITEMS.register("network_cable",
            () -> new com.strikerr212.minepc.lan.CableItem(new Item.Properties()));
    public static final DeferredItem<com.strikerr212.minepc.lan.CableItem> FIBER = ITEMS.register("fiber_cable",
            () -> new com.strikerr212.minepc.lan.CableItem(new Item.Properties(), true));
    public static final DeferredItem<BlockItem> NETWORK_SWITCH = ITEMS.registerSimpleBlockItem("network_switch", ModBlocks.NETWORK_SWITCH);
    public static final DeferredItem<BlockItem> RACK_SWITCH = ITEMS.registerSimpleBlockItem("rack_switch", ModBlocks.RACK_SWITCH);
    public static final DeferredItem<BlockItem> ROUTER = ITEMS.registerSimpleBlockItem("wifi_router", ModBlocks.ROUTER);
    public static final DeferredItem<BlockItem> TRAY = ITEMS.registerSimpleBlockItem("cable_tray", ModBlocks.TRAY);
    public static final DeferredItem<BlockItem> PATCH_PANEL = ITEMS.registerSimpleBlockItem("patch_panel", ModBlocks.PATCH_PANEL);
    public static final DeferredItem<BlockItem> BRACKET = ITEMS.registerSimpleBlockItem("cable_bracket", ModBlocks.BRACKET);
    public static final DeferredItem<com.strikerr212.minepc.lan.CuttersItem> CUTTERS = ITEMS.register("wire_cutters",
            () -> new com.strikerr212.minepc.lan.CuttersItem(new Item.Properties().stacksTo(1).durability(512)));
    public static final DeferredItem<BlockItem> RADIO = ITEMS.registerSimpleBlockItem("radio_modem", ModBlocks.RADIO);
    public static final DeferredItem<BlockItem> MAST = ITEMS.registerSimpleBlockItem("radio_mast", ModBlocks.MAST);
    public static final DeferredItem<com.strikerr212.minepc.lan.PatchCordItem> PATCH_CORD = ITEMS.register("patch_cord",
            () -> new com.strikerr212.minepc.lan.PatchCordItem(new Item.Properties()));
    public static final DeferredItem<com.strikerr212.minepc.lan.WifiAdapterItem> WIFI_G = ITEMS.register("wifi_adapter_g",
            () -> new com.strikerr212.minepc.lan.WifiAdapterItem("802.11g", 16, 54, new Item.Properties()));
    public static final DeferredItem<com.strikerr212.minepc.lan.WifiAdapterItem> WIFI_N = ITEMS.register("wifi_adapter_n",
            () -> new com.strikerr212.minepc.lan.WifiAdapterItem("802.11n", 32, 300, new Item.Properties()));
    public static final DeferredItem<com.strikerr212.minepc.lan.WifiAdapterItem> WIFI_AC = ITEMS.register("wifi_adapter_ac",
            () -> new com.strikerr212.minepc.lan.WifiAdapterItem("802.11ac", 48, 1200, new Item.Properties()));
    public static final DeferredItem<BlockItem> KEYBOARD_STAND = ITEMS.registerSimpleBlockItem("keyboard_stand", ModBlocks.KEYBOARD_STAND);
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
