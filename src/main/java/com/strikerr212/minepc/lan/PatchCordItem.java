package com.strikerr212.minepc.lan;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Патч-корд: короткий провод между двумя устройствами (до 4 блоков) — коммутатор, корпус, роутер, патч-панель.
 * ПКМ по первому устройству, затем по второму. Shift+ПКМ — снять все корды с устройства.
 * Краситель во второй руке — цвет корда (просто для красоты).
 */
public class PatchCordItem extends Item {

    public static final double MAX_LENGTH = 4.5;

    public PatchCordItem(Properties properties) {
        super(properties);
    }

    private static CompoundTag pending(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag t = data.copyTag();
        return t.contains("cord_pos") ? t : null;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return pending(stack) != null;
    }

    /** Порт для нового корда: у патч-панели — дорожка, куда кликнули; у остальных — первый свободный. -1 — нет мест. */
    private static int pickPort(LanData data, BlockPos pos, BlockState state, LanData.Device d, Vec3 hit) {
        if (d.passive()) {
            Direction f = state.getValue(PatchPanelBlock.FACING);
            double local = (f.getAxis() == Direction.Axis.Z ? hit.x - pos.getX() : hit.z - pos.getZ()) * 16;
            int ch = Math.max(0, Math.min(3, (int) (local / 4)));
            int port = LanData.CABLE_PORTS + ch;
            return data.cordAt(pos, port) ? -1 : port;
        }
        for (int port = LanData.CABLE_PORTS; port < LanData.PORTS; port++) if (!data.cordAt(pos, port)) return port;
        return -1;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LanDevice)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        Player player = ctx.getPlayer();
        LanData data = LanData.get(server);
        LanManager.updateDevice(level, pos);
        LanData.Device d = data.device(pos);
        if (d == null) return InteractionResult.FAIL;
        CompoundTag first = pending(stack);

        if (player != null && player.isShiftKeyDown() && first == null) {
            List<LanData.Cord> removed = data.removeCords(pos);
            for (LanData.Cord c : removed) {
                Block.popResource(level, pos, new ItemStack(this));
                CordList.refresh(server, c.a().equals(pos) ? c.b() : c.a());
            }
            CordList.refresh(server, pos);
            if (player != null) player.displayClientMessage(Component.translatable("message.minepc.cord.removed", removed.size()), true);
            return InteractionResult.CONSUME;
        }

        int port = pickPort(data, pos, state, d, ctx.getClickLocation());
        if (port < 0) {
            if (player != null) player.displayClientMessage(Component.translatable("message.minepc.cord.busy"), true);
            return InteractionResult.FAIL;
        }
        if (first == null) {
            CompoundTag t = new CompoundTag();
            t.putLong("cord_pos", pos.asLong());
            t.putInt("cord_port", port);
            t.putString("cord_dim", level.dimension().location().toString());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
            if (player != null) player.displayClientMessage(Component.translatable("message.minepc.cord.first"), true);
            level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.6f, 1.6f);
            return InteractionResult.CONSUME;
        }
        BlockPos a = BlockPos.of(first.getLong("cord_pos"));
        int portA = first.getInt("cord_port");
        stack.remove(DataComponents.CUSTOM_DATA);
        if (a.equals(pos) || !first.getString("cord_dim").equals(level.dimension().location().toString())) {
            if (player != null) player.displayClientMessage(Component.translatable("message.minepc.cord.cancel"), true);
            return InteractionResult.CONSUME;
        }
        if (Math.sqrt(a.distSqr(pos)) > MAX_LENGTH) {
            if (player != null) player.displayClientMessage(Component.translatable("message.minepc.cord.far"), true);
            return InteractionResult.FAIL;
        }
        if (data.device(a) == null || data.cordAt(a, portA)) {
            if (player != null) player.displayClientMessage(Component.translatable("message.minepc.cord.gone"), true);
            return InteractionResult.FAIL;
        }
        int color = DyeColor.BLUE.getId();
        if (player != null && player.getItemInHand(ctx.getHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND)
                .getItem() instanceof DyeItem dye) {
            color = dye.getDyeColor().getId();
        }
        data.addCord(new LanData.Cord(a.immutable(), portA, pos.immutable(), port, color));
        CordList.refresh(server, a);
        CordList.refresh(server, pos);
        level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.8f, 1.3f);
        if (player == null || !player.getAbilities().instabuild) stack.shrink(1);
        if (player != null) player.displayClientMessage(Component.translatable("message.minepc.cord.done"), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.minepc.cord").withStyle(ChatFormatting.GRAY));
        if (pending(stack) != null) tooltip.add(Component.translatable("tooltip.minepc.cord.pending").withStyle(ChatFormatting.YELLOW));
    }
}
