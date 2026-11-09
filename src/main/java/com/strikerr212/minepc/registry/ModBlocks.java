package com.strikerr212.minepc.registry;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.block.AssemblyTableBlock;
import com.strikerr212.minepc.block.PcCaseBlock;
import com.strikerr212.minepc.block.MonitorBlock;
import com.strikerr212.minepc.block.KeyboardBlock;
import com.strikerr212.minepc.block.DiskDriveBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MineComputers.MODID);

    public static final DeferredBlock<PcCaseBlock> PC_CASE = BLOCKS.register("pc_case",
            () -> new PcCaseBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()));   // блок не полный, сквозь стекло видно соседей

    /** Корпуса-варианты: другой облик и тема окна, внутри — тот же системный блок. */
    public static final java.util.Map<com.strikerr212.minepc.block.CaseVariant, DeferredBlock<PcCaseBlock>> CASES = new java.util.EnumMap<>(com.strikerr212.minepc.block.CaseVariant.class);
    static {
        for (com.strikerr212.minepc.block.CaseVariant v : com.strikerr212.minepc.block.CaseVariant.values()) {
            CASES.put(v, BLOCKS.register(v.id, () -> new PcCaseBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion(), v)));
        }
    }

    /** Все системные блоки: обычный и варианты. */
    public static java.util.List<PcCaseBlock> allCases() {
        java.util.List<PcCaseBlock> out = new java.util.ArrayList<>();
        out.add(PC_CASE.get());
        for (DeferredBlock<PcCaseBlock> b : CASES.values()) out.add(b.get());
        return out;
    }

    public static final DeferredBlock<AssemblyTableBlock> ASSEMBLY_TABLE = BLOCKS.register("assembly_table",
            () -> new AssemblyTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    public static final DeferredBlock<MonitorBlock> MONITOR = BLOCKS.register("monitor",
            () -> new MonitorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 3)
                    .noOcclusion()));

    private static BlockBehaviour.Properties monitorProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5f, 6.0f)
                .sound(SoundType.METAL).lightLevel(state -> 3).noOcclusion();
    }

    // Мониторы других видов: в один блок и блочные (стыкуются без швов)
    public static final DeferredBlock<com.strikerr212.minepc.block.SimpleMonitorBlock> MONITOR_CRT = BLOCKS.register("monitor_crt",
            () -> new com.strikerr212.minepc.block.SimpleMonitorBlock(monitorProps(), com.strikerr212.minepc.block.MonitorKind.CRT,
                    com.strikerr212.minepc.block.BoxShapes.of(1, 0, 1, 15, 15, 14)));
    public static final DeferredBlock<com.strikerr212.minepc.block.SimpleMonitorBlock> MONITOR_TV = BLOCKS.register("monitor_tv",
            () -> new com.strikerr212.minepc.block.SimpleMonitorBlock(monitorProps(), com.strikerr212.minepc.block.MonitorKind.TV,
                    com.strikerr212.minepc.block.BoxShapes.of(1, 0, 2, 15, 16, 12)));
    public static final DeferredBlock<com.strikerr212.minepc.block.SimpleMonitorBlock> MONITOR_TERMINAL = BLOCKS.register("monitor_terminal",
            () -> new com.strikerr212.minepc.block.SimpleMonitorBlock(monitorProps(), com.strikerr212.minepc.block.MonitorKind.TERMINAL,
                    com.strikerr212.minepc.block.BoxShapes.of(1, 0, 3, 15, 14, 13).add(1, 0, 0, 15, 2, 5)));
    public static final DeferredBlock<com.strikerr212.minepc.block.SimpleMonitorBlock> MONITOR_PANEL = BLOCKS.register("monitor_panel",
            () -> new com.strikerr212.minepc.block.SimpleMonitorBlock(monitorProps(), com.strikerr212.minepc.block.MonitorKind.PANEL,
                    com.strikerr212.minepc.block.BoxShapes.of(0, 0, 13, 16, 16, 16)));
    public static final DeferredBlock<com.strikerr212.minepc.block.BlockMonitorBlock> MONITOR_BLOCK = BLOCKS.register("monitor_block",
            () -> new com.strikerr212.minepc.block.BlockMonitorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                    .strength(1.5f, 6.0f).sound(SoundType.METAL).lightLevel(state -> 3)));
    public static final DeferredBlock<com.strikerr212.minepc.block.BlockMonitorBlock> MONITOR_BLOCK_WHITE = BLOCKS.register("monitor_block_white",
            () -> new com.strikerr212.minepc.block.BlockMonitorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW)
                    .strength(1.5f, 6.0f).sound(SoundType.METAL).lightLevel(state -> 3)));
    public static final DeferredBlock<com.strikerr212.minepc.block.BlockMonitorBlock> MONITOR_BLOCK_RETRO = BLOCKS.register("monitor_block_retro",
            () -> new com.strikerr212.minepc.block.BlockMonitorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
                    .strength(1.5f, 6.0f).sound(SoundType.METAL).lightLevel(state -> 3)));

    /** Все мониторы (у всех один BlockEntity). */
    public static java.util.List<net.minecraft.world.level.block.Block> allMonitors() {
        return java.util.List.of(MONITOR.get(), MONITOR_CRT.get(), MONITOR_TV.get(), MONITOR_TERMINAL.get(), MONITOR_PANEL.get(),
                MONITOR_BLOCK.get(), MONITOR_BLOCK_WHITE.get(), MONITOR_BLOCK_RETRO.get());
    }

    public static final DeferredBlock<KeyboardBlock> KEYBOARD_STAND = BLOCKS.register("keyboard_stand",
            () -> new KeyboardBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(1.2f)
                    .sound(SoundType.METAL)
                    .noOcclusion(), true));

    public static final DeferredBlock<KeyboardBlock> KEYBOARD = BLOCKS.register("keyboard",
            () -> new KeyboardBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(0.8f)
                    .sound(SoundType.STONE)
                    .noOcclusion()));

    public static final DeferredBlock<DiskDriveBlock> DISK_DRIVE = BLOCKS.register("disk_drive",
            () -> new DiskDriveBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
