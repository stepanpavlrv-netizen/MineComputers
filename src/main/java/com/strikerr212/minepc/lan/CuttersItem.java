package com.strikerr212.minepc.lan;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Кусачки: ПКМ по проводу — снять именно его (тот, на который смотришь), с Shift — весь жгут в этой клетке.
 * ПКМ по кронштейну или устройству — снять висячие провода от него (воздушные линии, патч-корды). Снятый кабель возвращается в инвентарь.
 */
public class CuttersItem extends Item {

    public CuttersItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Player player = ctx.getPlayer();
        List<ItemStack> back = new ArrayList<>();
        if (level.getBlockEntity(pos) instanceof CableBlockEntity be && be.facade() == null && !be.wires().isEmpty()) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            List<Wire> cut = new ArrayList<>();
            if (player != null && player.isShiftKeyDown()) {
                cut.addAll(be.wires());
            } else {
                Vec3 at = ctx.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()).scale(16);
                Wire w = be.nearest(at.x, at.y, at.z);
                if (w != null) cut.add(w);
            }
            for (Wire w : cut) {
                be.remove(w);
                back.add(CableBlockEntity.item(w));
            }
            if (be.wires().isEmpty()) {
                level.removeBlock(pos, false);
            } else {
                be.reconnect();
                be.reconnectNeighbours();
            }
        } else if (level.getBlockState(pos).getBlock() instanceof LanDevice && level instanceof ServerLevel server) {
            LanData data = LanData.get(server);
            List<LanData.Cord> removed = data.removeCords(pos);
            for (LanData.Cord c : removed) {
                back.add(c.length() > 4 ? new ItemStack(com.strikerr212.minepc.registry.ModItems.CABLE.get(), c.length())
                        : new ItemStack(com.strikerr212.minepc.registry.ModItems.PATCH_CORD.get()));
                CordList.refresh(server, c.a().equals(pos) ? c.b() : c.a());
            }
            CordList.refresh(server, pos);
            if (removed.isEmpty()) return InteractionResult.PASS;
        } else {
            return level.getBlockState(pos).getBlock() instanceof LanDevice ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (back.isEmpty()) return InteractionResult.PASS;
        level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 0.8f, 1.4f);
        if (player != null) {
            if (!player.getAbilities().instabuild) {
                for (ItemStack s : back) {
                    if (!player.getInventory().add(s)) player.drop(s, false);
                }
            }
            ctx.getItemInHand().hurtAndBreak(1, player, ctx.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        return InteractionResult.CONSUME;
    }

    /** По устройству — раньше самого устройства (иначе оно откроет своё окно). */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        if (ctx.getLevel().getBlockState(ctx.getClickedPos()).getBlock() instanceof LanDevice) return useOn(ctx);
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.minepc.cutters").withStyle(ChatFormatting.GRAY));
    }
}
