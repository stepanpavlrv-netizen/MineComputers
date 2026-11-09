package com.strikerr212.minepc.lan;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Патч-панель: пассивный блок для стойки. 4 гнезда спереди и 4 сзади (медь или оптика), гнездо спереди соединено
 * с гнездом сзади на той же дорожке и с патч-кордом в этом гнезде. Сигнал проходит насквозь — без задержки и переходов.
 * Удобно: длинные кабели заходят сзади, а спереди короткими патч-кордами перекидываешь, куда нужно.
 */
public class PatchPanelBlock extends HorizontalDirectionalBlock implements LanDevice, EntityBlock {

    public static final MapCodec<PatchPanelBlock> CODEC = simpleCodec(PatchPanelBlock::new);

    public PatchPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LanBlockEntity(pos, state);
    }

    @Override
    public Kind lanKind() {
        return Kind.SWITCH;
    }

    @Override
    public int lanType(BlockState state) {
        return LanData.PANEL;
    }

    private static boolean frontOrBack(BlockState state, Direction face) {
        Direction f = state.getValue(FACING);
        return face == f || face == f.getOpposite();
    }

    @Override
    public boolean acceptsCable(BlockState state, Direction face) {
        return frontOrBack(state, face);
    }

    @Override
    public boolean acceptsFiber(BlockState state, Direction face) {
        return frontOrBack(state, face);
    }

    @Override
    public long lanBitsPerSecond(Level level, BlockPos pos, BlockState state) {
        return 10_000_000_000L;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide) LanManager.updateDevice(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!level.isClientSide && !state.is(newState.getBlock())) LanManager.updateDevice(level, pos);
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.minepc.patch_panel").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
