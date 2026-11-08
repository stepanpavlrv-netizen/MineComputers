package com.strikerr212.minepc.block;

import com.mojang.serialization.MapCodec;
import com.strikerr212.minepc.client.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Монитор. Ставится вплотную к системному блоку и показывает его экран.
 * ПКМ — открыть экран крупно; печатать можно, если рядом стоит клавиатура.
 */
public class MonitorBlock extends DeviceBlock {

    public static final MapCodec<MonitorBlock> CODEC = simpleCodec(MonitorBlock::new);
    /** Подставка есть только у нижнего ряда большого монитора. */
    public static final BooleanProperty STAND = BooleanProperty.create("stand");

    // Экран толщиной 3 пикселя у задней стороны блока + подставка
    private static final VoxelShape NORTH = Shapes.or(Block.box(0, 2, 11, 16, 16, 14), Block.box(4, 0, 9, 12, 2, 16));
    private static final VoxelShape SOUTH = Shapes.or(Block.box(0, 2, 2, 16, 16, 5), Block.box(4, 0, 0, 12, 2, 7));
    private static final VoxelShape WEST = Shapes.or(Block.box(11, 2, 0, 14, 16, 16), Block.box(9, 0, 4, 16, 2, 12));
    private static final VoxelShape EAST = Shapes.or(Block.box(2, 2, 0, 5, 16, 16), Block.box(0, 0, 4, 7, 2, 12));
    private static final VoxelShape NORTH_TOP = Block.box(0, 0, 11, 16, 16, 14);
    private static final VoxelShape SOUTH_TOP = Block.box(0, 0, 2, 16, 16, 5);
    private static final VoxelShape WEST_TOP = Block.box(11, 0, 0, 14, 16, 16);
    private static final VoxelShape EAST_TOP = Block.box(2, 0, 0, 5, 16, 16);

    public MonitorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(STAND, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STAND);
    }

    private static boolean standFor(BlockState state, LevelAccessor level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return !(below.getBlock() instanceof MonitorBlock && below.getValue(FACING) == state.getValue(FACING));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState s = super.getStateForPlacement(context);
        return s.setValue(STAND, standFor(s, context.getLevel(), context.getClickedPos()));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return dir == Direction.DOWN ? state.setValue(STAND, standFor(state, level, pos)) : state;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean stand = state.getValue(STAND);
        return switch (state.getValue(FACING)) {
            case SOUTH -> stand ? SOUTH : SOUTH_TOP;
            case WEST -> stand ? WEST : WEST_TOP;
            case EAST -> stand ? EAST : EAST_TOP;
            default -> stand ? NORTH : NORTH_TOP;
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MonitorBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            BlockPos casePos = level.getBlockEntity(pos) instanceof MonitorBlockEntity m ? m.casePos() : null;
            if (casePos == null) {
                player.displayClientMessage(Component.translatable("message.minepc.monitor.no_case"), true);
            } else {
                ClientAccess.openTerminal(casePos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
