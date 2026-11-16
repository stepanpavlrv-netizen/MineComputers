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

import java.util.ArrayList;
import java.util.List;

/**
 * Кусачки: ПКМ по проводу — разрезать его в этом месте, с Shift — снять целиком. ПКМ по кронштейну или устройству —
 * снять висячие провода от него (воздушные линии, патч-корды). Снятый кабель возвращается в инвентарь.
 */
public class CuttersItem extends Item {

    public CuttersItem(Properties properties) {
        super(properties);
    }

    /** Смотрит ли игрок на провод (у игрока — по своей копии проводов). */
    private static boolean aimsAtWire(Player player) {
        double reach = player.blockInteractionRange() + 0.5;
        return com.strikerr212.minepc.wire.WireService.pick(com.strikerr212.minepc.wire.WireStore.all(), player.getEyePosition(),
                player.getViewVector(1), reach) != null;
    }

    /** Провод под прицелом: разрезать (shift — снять целиком). */
    private static InteractionResult cutWire(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide) return aimsAtWire(player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        if (player instanceof net.minecraft.server.level.ServerPlayer sp && com.strikerr212.minepc.wire.WireService.cut(sp, sp.isShiftKeyDown())) {
            player.getItemInHand(hand).hurtAndBreak(1, player, hand == net.minecraft.world.InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        InteractionResult r = cutWire(level, player, hand);
        return r == InteractionResult.PASS ? net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand))
                : new net.minecraft.world.InteractionResultHolder<>(r, player.getItemInHand(hand));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Player player = ctx.getPlayer();
        InteractionResult wire = cutWire(level, player, ctx.getHand());
        if (wire != InteractionResult.PASS) return wire;
        List<ItemStack> back = new ArrayList<>();
        if (!(level.getBlockState(pos).getBlock() instanceof LanDevice)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        // Устройство: снять висячие провода от него (воздушные линии, патч-корды)
        LanData data = LanData.get(server);
        for (var l : List.copyOf(data.linesAt(pos))) {
            boolean hangs = false;
            for (boolean h : l.hang) hangs |= h;
            if (!hangs) continue;
            data.removeLine(l.id);
            ItemStack s = l.meters() <= PatchCordItem.MAX_LENGTH + 1
                    ? new ItemStack(com.strikerr212.minepc.registry.ModItems.PATCH_CORD.get())
                    : new ItemStack(l.fiber ? com.strikerr212.minepc.registry.ModItems.FIBER.get() : com.strikerr212.minepc.registry.ModItems.CABLE.get(),
                    Math.min(64, l.meters()));
            back.add(CableLabels.named(s, l.label));
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
