package com.strikerr212.minepc.lan;

import com.mojang.serialization.MapCodec;
import com.strikerr212.minepc.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Кабельный лоток. Ставится где угодно (на стену, под потолок, в воздух), соединяется с соседними лотками во все
 * стороны. ПКМ мотком кабеля или оптики — положить провод (до 8, цвета по очереди; краситель во второй руке — свой цвет).
 * ПКМ красителем — перекрасить последний провод. Ломается сначала по проводу, последним — сам лоток.
 */
public class TrayBlock extends BaseEntityBlock {

    public static final MapCodec<TrayBlock> CODEC = simpleCodec(TrayBlock::new);
    private static final VoxelShape CORE = Block.box(4, 4, 4, 12, 12, 12);
    private static final VoxelShape[] ARMS = new VoxelShape[6];

    static {
        for (Direction d : Direction.values()) {
            double x0 = 4, y0 = 4, z0 = 4, x1 = 12, y1 = 12, z1 = 12;
            switch (d) {
                case DOWN -> y0 = 0;
                case UP -> y1 = 16;
                case NORTH -> z0 = 0;
                case SOUTH -> z1 = 16;
                case WEST -> x0 = 0;
                case EAST -> x1 = 16;
            }
            ARMS[d.ordinal()] = Block.box(x0, y0, z0, x1, y1, z1);
        }
    }

    public TrayBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrayBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape s = CORE;
        if (level.getBlockEntity(pos) instanceof TrayBlockEntity be) {
            int conn = be.conn();
            for (Direction d : Direction.values()) if ((conn & 1 << d.ordinal()) != 0) s = Shapes.or(s, ARMS[d.ordinal()]);
        }
        return s;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TrayBlockEntity be) be.reconnect();
        return state;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TrayBlockEntity be) be.reconnect();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof TrayBlockEntity be) {
            int copper = 0, fiber = 0;
            for (int w : be.wires()) {
                if (TrayBlockEntity.fiber(w)) fiber++;
                else copper++;
            }
            if (copper > 0) popResource(level, pos, new ItemStack(ModItems.CABLE.get(), copper));
            if (fiber > 0) popResource(level, pos, new ItemStack(ModItems.FIBER.get(), fiber));
        }
        super.onRemove(state, level, pos, newState, moved);
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            LanData.get(server).removeTray(pos);
            for (Direction d : Direction.values()) {
                BlockPos n = pos.relative(d);
                if (level.getBlockEntity(n) instanceof TrayBlockEntity t) t.reconnect();
                if (level.getBlockEntity(n) instanceof CableBlockEntity c) c.reconnect();
            }
        }
    }

    /** Сначала вынимается последний провод, пустой лоток ломается целиком. */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (level.getBlockEntity(pos) instanceof TrayBlockEntity be && !be.wires().isEmpty()) {
            if (!level.isClientSide) {
                int w = be.removeLast();
                if (!player.isCreative()) popResource(level, pos, new ItemStack(TrayBlockEntity.fiber(w) ? ModItems.FIBER.get() : ModItems.CABLE.get()));
                level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1f, 1f);
            }
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TrayBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (stack.getItem() instanceof CableItem cable) {
            if (!level.isClientSide) {
                ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
                int wanted = other.getItem() instanceof DyeItem dye ? dye.getDyeColor().getId() : -1;
                int color = be.pickColor(wanted, cable.fiber());
                if (color < 0) {
                    player.displayClientMessage(Component.translatable("message.minepc.tray.full"), true);
                    return ItemInteractionResult.FAIL;
                }
                be.add(TrayBlockEntity.make(color, cable.fiber()));
                level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1f, 1.1f);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.getItem() instanceof DyeItem dye) {
            if (!level.isClientSide) {
                if (!be.recolorLast(dye.getDyeColor().getId())) return ItemInteractionResult.FAIL;
                level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1f, 1f);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.minepc.cable_tray").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
