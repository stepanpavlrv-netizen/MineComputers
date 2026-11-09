package com.strikerr212.minepc.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Блочный монитор — целый блок-экран. Соседние блоки того же вида, смотрящие в ту же сторону,
 * стыкуются без швов: рамка рисуется только с тех сторон, где соседа нет.
 * LEFT/RIGHT — как видит зритель, стоя перед экраном.
 */
public class BlockMonitorBlock extends BaseMonitorBlock {

    public static final MapCodec<BlockMonitorBlock> CODEC = simpleCodec(BlockMonitorBlock::new);
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    public BlockMonitorBlock(Properties properties) {
        super(properties, MonitorKind.BLOCK);
        registerDefaultState(defaultBlockState().setValue(LEFT, false).setValue(RIGHT, false).setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LEFT, RIGHT, UP, DOWN);
    }

    private boolean joins(LevelAccessor level, BlockPos pos, Direction facing) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() == this && s.getValue(FACING) == facing;
    }

    private BlockState connect(BlockState state, LevelAccessor level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        Direction left = facing.getClockWise();          // зритель смотрит на экран: его лево — по часовой от «лица» экрана
        return state.setValue(LEFT, joins(level, pos.relative(left), facing))
                .setValue(RIGHT, joins(level, pos.relative(left.getOpposite()), facing))
                .setValue(UP, joins(level, pos.above(), facing))
                .setValue(DOWN, joins(level, pos.below(), facing));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connect(super.getStateForPlacement(context), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return connect(state, level, pos);
    }
}
