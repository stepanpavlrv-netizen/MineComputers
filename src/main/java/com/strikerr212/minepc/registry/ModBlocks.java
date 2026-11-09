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
