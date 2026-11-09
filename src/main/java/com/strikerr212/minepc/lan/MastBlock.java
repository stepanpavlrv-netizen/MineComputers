package com.strikerr212.minepc.lan;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Секция мачты: ставится над радиомодемом (до трёх), каждая добавляет 250 блоков дальности. Верхняя — с антенной. */
public class MastBlock extends Block {

    public static final MapCodec<MastBlock> CODEC = simpleCodec(MastBlock::new);
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    private static final VoxelShape SHAPE = Block.box(5.5, 0, 5.5, 10.5, 16, 10.5);

    public MastBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TOP, true));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockPos above = context.getClickedPos().above();
        return defaultBlockState().setValue(TOP, !context.getLevel().getBlockState(above).is(this));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (dir == Direction.UP) return state.setValue(TOP, !neighbor.is(this));
        return state;
    }

    /** Модем внизу (под всеми секциями) пересчитывает дальность. */
    private static void notifyRadio(Level level, BlockPos pos) {
        BlockPos p = pos.below();
        for (int i = 0; i < 16; i++) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof RadioBlock) {
                LanManager.updateDevice(level, p);
                return;
            }
            if (!(s.getBlock() instanceof MastBlock)) return;
            p = p.below();
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide && !old.is(this)) notifyRadio(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!level.isClientSide && !newState.is(this)) notifyRadio(level, pos);
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.minepc.radio_mast").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
