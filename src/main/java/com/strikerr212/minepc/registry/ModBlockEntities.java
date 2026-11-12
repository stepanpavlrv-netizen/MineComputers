package com.strikerr212.minepc.registry;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.block.PcCaseBlockEntity;
import com.strikerr212.minepc.block.MonitorBlockEntity;
import com.strikerr212.minepc.block.DiskDriveBlockEntity;
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
                    () -> BlockEntityType.Builder.of(PcCaseBlockEntity::new,
                            ModBlocks.allCases().toArray(new net.minecraft.world.level.block.Block[0])).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.strikerr212.minepc.lan.TrayBlockEntity>> TRAY =
            BLOCK_ENTITIES.register("cable_tray", () ->
                    BlockEntityType.Builder.of(com.strikerr212.minepc.lan.TrayBlockEntity::new, ModBlocks.TRAY.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.strikerr212.minepc.lan.LanBlockEntity>> LAN =
            BLOCK_ENTITIES.register("lan_device", () ->
                    BlockEntityType.Builder.of(com.strikerr212.minepc.lan.LanBlockEntity::new, ModBlocks.NETWORK_SWITCH.get(),
                            ModBlocks.RACK_SWITCH.get(), ModBlocks.PATCH_PANEL.get(), ModBlocks.RADIO.get(), ModBlocks.BRACKET.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.strikerr212.minepc.lan.RouterBlockEntity>> ROUTER =
            BLOCK_ENTITIES.register("wifi_router", () ->
                    BlockEntityType.Builder.of(com.strikerr212.minepc.lan.RouterBlockEntity::new, ModBlocks.ROUTER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.strikerr212.minepc.lan.CableBlockEntity>> CABLE =
            BLOCK_ENTITIES.register("network_cable", () ->
                    BlockEntityType.Builder.of(com.strikerr212.minepc.lan.CableBlockEntity::new, ModBlocks.CABLE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MonitorBlockEntity>> MONITOR =
            BLOCK_ENTITIES.register("monitor",
                    () -> BlockEntityType.Builder.of(MonitorBlockEntity::new,
                            ModBlocks.allMonitors().toArray(new net.minecraft.world.level.block.Block[0])).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DiskDriveBlockEntity>> DISK_DRIVE =
            BLOCK_ENTITIES.register("disk_drive",
                    () -> BlockEntityType.Builder.of(DiskDriveBlockEntity::new, ModBlocks.DISK_DRIVE.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
