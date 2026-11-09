package com.strikerr212.minepc.client;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.item.HardwareItem;
import com.strikerr212.minepc.registry.ModBlockEntities;
import com.strikerr212.minepc.registry.ModItems;
import com.strikerr212.minepc.registry.ModMenus;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Всё, что относится только к отображению (работает лишь у игрока, не на сервере).
 */
@EventBusSubscriber(modid = MineComputers.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    /**
     * Свойства v0, v1, v2 — по ним JSON-модель выбирает вариант геометрии
     * (тип крышки, число слотов, длина видеокарты...). Вариант N → значение N*0.1 + 0.05.
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (var item : ModItems.HARDWARE) {
                for (int dim = 0; dim < 3; dim++) {
                    final int d = dim;
                    ItemProperties.register(item.get(), ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "v" + dim),
                            (stack, level, entity, seed) -> {
                                if (!(stack.getItem() instanceof HardwareItem hardware)) return 0.05f;
                                return hardware.spec().visual(hardware.values(stack), d) * 0.1f + 0.05f;
                            });
                }
            }
        });
    }

    /**
     * Перекраска слоёв модели:
     *   слой 1 — основной цвет (крышки, радиаторы, кожухи),
     *   слой 2 — подсветка или наклейка,
     *   слой 3 — цвет текстолита.
     */
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        ItemColor colors = (stack, tintIndex) -> {
            if (tintIndex < 1 || tintIndex > 3 || !(stack.getItem() instanceof HardwareItem hardware)) {
                return 0xFFFFFFFF;
            }
            return 0xFF000000 | hardware.spec().tint(hardware.values(stack), tintIndex);
        };
        for (var item : ModItems.HARDWARE) {
            event.register(colors, item.get());
        }
        // Корпуса и мониторы: у предмета — некрашеный цвет основы
        for (var block : paintable()) {
            ItemColor paint = (stack, tintIndex) -> tintIndex == 0 ? 0xFF000000 | paintColor(block.defaultBlockState()) : 0xFFFFFFFF;
            event.register(paint, block.asItem());
        }
    }

    /** Блоки, которые красятся красителем. */
    private static java.util.List<net.minecraft.world.level.block.Block> paintable() {
        java.util.List<net.minecraft.world.level.block.Block> out = new java.util.ArrayList<>();
        for (var b : com.strikerr212.minepc.registry.ModBlocks.CASES.values()) out.add(b.get());
        out.add(com.strikerr212.minepc.registry.ModBlocks.NETWORK_SWITCH.get());
        out.add(com.strikerr212.minepc.registry.ModBlocks.RACK_SWITCH.get());
        out.add(com.strikerr212.minepc.registry.ModBlocks.ROUTER.get());
        out.add(com.strikerr212.minepc.registry.ModBlocks.RADIO.get());
        for (var b : com.strikerr212.minepc.registry.ModBlocks.allMonitors()) {
            if (b instanceof com.strikerr212.minepc.block.BaseMonitorBlock m && m.paintColor(b.defaultBlockState()) >= 0) out.add(b);
        }
        return out;
    }

    private static int paintColor(net.minecraft.world.level.block.state.BlockState state) {
        if (state.getBlock() instanceof com.strikerr212.minepc.block.PcCaseBlock c) return Math.max(0, c.paintColor(state));
        if (state.getBlock() instanceof com.strikerr212.minepc.block.BaseMonitorBlock m) return Math.max(0, m.paintColor(state));
        if (state.getBlock() instanceof com.strikerr212.minepc.lan.SwitchBlock sw) return sw.paintColor(state);
        if (state.getBlock() instanceof com.strikerr212.minepc.lan.RouterBlock r) return r.paintColor(state);
        if (state.getBlock() instanceof com.strikerr212.minepc.lan.RadioBlock r) return r.paintColor(state);
        return 0xFFFFFF;
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> tintIndex == 0 ? paintColor(state) : -1,
                paintable().toArray(new net.minecraft.world.level.block.Block[0]));
        // Провода кабеля: tintindex — номер красителя
        event.register((state, level, pos, tintIndex) -> tintIndex >= 0 && tintIndex < 16
                        ? net.minecraft.world.item.DyeColor.byId(tintIndex).getTextureDiffuseColor() & 0xFFFFFF : -1,
                com.strikerr212.minepc.registry.ModBlocks.CABLE.get(), com.strikerr212.minepc.registry.ModBlocks.TRAY.get());
    }

    /** Модель кабеля собирается из проводов в блоке. */
    @SubscribeEvent
    public static void modifyModels(net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event) {
        for (var state : com.strikerr212.minepc.registry.ModBlocks.CABLE.get().getStateDefinition().getPossibleStates()) {
            var loc = net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state);
            var base = event.getModels().get(loc);
            if (base != null) event.getModels().put(loc, new CableModel(base));
        }
        for (var state : com.strikerr212.minepc.registry.ModBlocks.TRAY.get().getStateDefinition().getPossibleStates()) {
            var loc = net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state);
            var base = event.getModels().get(loc);
            if (base != null) event.getModels().put(loc, new TrayModel(base));
        }
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.PC_CASE.get(), PcCaseRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MONITOR.get(), MonitorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LAN.get(), CordRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ROUTER.get(), CordRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ASSEMBLY.get(), AssemblyScreen::new);
        event.register(ModMenus.PC_CASE.get(), PcCaseScreen::new);
    }
}
