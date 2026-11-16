package com.strikerr212.minepc.wire;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.lan.LanData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** ПКМ красителем по проводу — перекрасить его (разные цвета — для порядка; сеть от цвета не зависит). */
@EventBusSubscriber(modid = MineComputers.MODID)
public final class WireDye {

    private WireDye() {
    }

    @SubscribeEvent
    public static void onBlock(PlayerInteractEvent.RightClickBlock event) {
        handle(event, event.getItemStack(), event.getEntity());
    }

    @SubscribeEvent
    public static void onItem(PlayerInteractEvent.RightClickItem event) {
        handle(event, event.getItemStack(), event.getEntity());
    }

    private static <E extends PlayerInteractEvent & ICancellableEvent> void handle(E event, ItemStack stack,
                                                                                   net.minecraft.world.entity.player.Player player) {
        if (!(stack.getItem() instanceof DyeItem dye) || !(player instanceof ServerPlayer sp)) return;
        WireService.Hit hit = WireService.aimed(sp);
        if (hit == null) return;
        WireLine l = hit.line();
        int color = dye.getDyeColor().getId();
        event.setCanceled(true);
        if (event instanceof PlayerInteractEvent.RightClickBlock rb) rb.setCancellationResult(InteractionResult.SUCCESS);
        if (event instanceof PlayerInteractEvent.RightClickItem ri) ri.setCancellationResult(InteractionResult.SUCCESS);
        if (l.color == color) return;
        LanData.get(sp.serverLevel()).putLine(l.withColor(color));
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        sp.serverLevel().playSound(null, BlockPos.containing(hit.point()), SoundEvents.DYE_USE, SoundSource.PLAYERS, 1f, 1f);
    }
}
