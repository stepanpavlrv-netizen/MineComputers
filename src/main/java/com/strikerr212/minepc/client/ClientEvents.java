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
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.PC_CASE.get(), PcCaseRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ASSEMBLY.get(), AssemblyScreen::new);
    }
}
