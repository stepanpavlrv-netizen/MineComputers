package com.strikerr212.minepc.lan;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Антенна на боку секции вышки: секторная панель (белая, на свою четверть круга, быстрее штыря) или тарелка
 * (только на другую тарелку, повёрнутую навстречу: вдвое дальше и намного быстрее). Смотрит от вышки наружу —
 * ставится щелчком по боку секции.
 */
public class TowerAntennaBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<TowerAntennaBlock> CODEC = simpleCodec(p -> new TowerAntennaBlock(p, false));
    private final boolean dish;
    private final com.strikerr212.minepc.block.BoxShapes shape;

    public TowerAntennaBlock(Properties properties, boolean dish) {
        super(properties);
        this.dish = dish;
        this.shape = dish ? com.strikerr212.minepc.block.BoxShapes.of(1, 1, 3, 15, 15, 16)
                : com.strikerr212.minepc.block.BoxShapes.of(4, 0, 11, 12, 16, 16);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public boolean dish() {
        return dish;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Ставится на бок секции: смотрит от неё наружу. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis().isHorizontal()) {
            BlockState s = defaultBlockState().setValue(FACING, face);
            if (s.canSurvive(context.getLevel(), context.getClickedPos())) return s;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState s = defaultBlockState().setValue(FACING, d);
            if (s.canSurvive(context.getLevel(), context.getClickedPos())) return s;
        }
        return null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.relative(state.getValue(FACING).getOpposite())).getBlock() instanceof TowerSectionBlock;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!state.canSurvive(level, pos)) level.scheduleTick(pos, this, 1);
        return state;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) level.destroyBlock(pos, true);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape.get(state.getValue(FACING));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide && !old.is(this)) Tower.notifyModems(level, pos.relative(state.getValue(FACING).getOpposite()));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!level.isClientSide && !newState.is(this)) Tower.notifyModems(level, pos.relative(state.getValue(FACING).getOpposite()));
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable(dish ? "tooltip.minepc.tower_dish" : "tooltip.minepc.tower_sector")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
