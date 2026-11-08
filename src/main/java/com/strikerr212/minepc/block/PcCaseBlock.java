package com.strikerr212.minepc.block;

import com.mojang.serialization.MapCodec;
import com.strikerr212.minepc.item.HardwareItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Системный блок.
 *
 * Управление (пока нет интерфейса):
 *   ПКМ деталью              — установить её в свободный слот
 *   ПКМ пустой рукой         — показать состав и проверку охлаждения
 *   Shift + ПКМ пустой рукой — вынуть последнюю деталь
 */
public class PcCaseBlock extends BaseEntityBlock {

    public static final MapCodec<PcCaseBlock> CODEC = simpleCodec(PcCaseBlock::new);

    // В какую сторону смотрит передняя панель
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // Форма блока: корпус уже полного блока (10 пикселей в ширину)
    private static final VoxelShape SHAPE_NORTH_SOUTH = Block.box(3, 0, 0, 13, 16, 16);
    private static final VoxelShape SHAPE_EAST_WEST = Block.box(0, 0, 3, 16, 16, 13);

    public PcCaseBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** При установке корпус поворачивается передней панелью к игроку. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NORTH_SOUTH : SHAPE_EAST_WEST;
    }

    /** Блоки с BlockEntity по умолчанию невидимы — включаем обычную модель. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PcCaseBlockEntity(pos, state);
    }

    /** ПКМ предметом в руке. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof HardwareItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PcCaseBlockEntity pc) {
            player.displayClientMessage(pc.tryInstall(stack, player), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** ПКМ пустой рукой. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PcCaseBlockEntity pc) {
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(pc.removeLast(player), true);
            } else {
                pc.sendStatus(player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Когда корпус ломают, детали выпадают на землю. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PcCaseBlockEntity pc) {
            pc.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
