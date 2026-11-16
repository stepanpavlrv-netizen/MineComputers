package com.strikerr212.minepc.wire;

import com.strikerr212.minepc.MineComputers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Бирка на уже проложенный провод: ПКМ по проводу биркой Minecraft. Переименованная на наковальне — провод получает
 * её имя, безымянная — подпись снимается. Бирка при этом не тратится.
 */
@EventBusSubscriber(modid = MineComputers.MODID)
public final class WireTags {

    private WireTags() {
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getItemStack().is(Items.NAME_TAG)) return;
        // у игрока ничего не отменяем: запрос и так уходит на сервер, а там решается, был ли под прицелом провод
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Component name = event.getItemStack().get(DataComponents.CUSTOM_NAME);
        if (WireService.relabel(player, name == null ? "" : name.getString())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
