package com.strikerr212.minepc.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Выдвинутая часть модуля стойки — невидимый блок перед стойкой (сам модуль рисует стойка). Нужен, чтобы по
 * выдвинутому модулю можно было щёлкнуть и чтобы сквозь него нельзя было пройти. FACING — куда смотрит стойка,
 * SLOT — какой модуль выдвинут. Щелчки передаёт стойке.
 */
public class RackDrawerBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<RackDrawerBlock> CODEC = simpleCodec(RackDrawerBlock::new);
    public static final IntegerProperty SLOT = IntegerProperty.create("slot", 0, RackBlockEntity.SLOTS - 1);
    private static final BoxShapes[] SHAPES = new BoxShapes[RackBlockEntity.SLOTS];

    static {
        for (int i = 0; i < SHAPES.length; i++) {
            // Модуль выходит из стойки (она с юга, у «смотрящего на север») на TRAVEL пикселей
            SHAPES[i] = BoxShapes.of(0, i * 4, 16 - RackBlock.TRAVEL, 16, i * 4 + 4, 16);
        }
    }

    public RackDrawerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SLOT, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SLOT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(SLOT)].get(state.getValue(FACING));
    }

    private static BlockPos rackPos(BlockState state, BlockPos pos) {
        return pos.relative(state.getValue(FACING).getOpposite());
    }

    /** Стойки позади нет или модуль задвинули — выдвинутая часть пропадает. */
    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockPos rack = rackPos(state, pos);
        if (!(level.getBlockEntity(rack) instanceof RackBlockEntity r) || r.extended() != state.getValue(SLOT)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos rack = rackPos(state, pos);
        if (!(level.getBlockEntity(rack) instanceof RackBlockEntity r)) return InteractionResult.PASS;
        return RackBlock.use(level, rack, level.getBlockState(rack), r, state.getValue(SLOT), player);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        BlockPos rack = rackPos(state, pos);
        BlockState rs = level.getBlockState(rack);
        if (!(rs.getBlock() instanceof RackBlock block)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        // Щелчок по выдвинутой части — как по самому модулю в стойке
        BlockHitResult moved = new BlockHitResult(
                new net.minecraft.world.phys.Vec3(hit.getLocation().x, rack.getY() + state.getValue(SLOT) * 0.25 + 0.125, hit.getLocation().z),
                hit.getDirection(), rack, hit.isInside());
        return block.useItemOn(stack, rs, level, rack, player, hand, moved);
    }

    /** Выдвинутую часть сломали — модуль задвигается. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            BlockPos rack = rackPos(state, pos);
            if (level.getBlockEntity(rack) instanceof RackBlockEntity r && r.extended() == state.getValue(SLOT)) r.setExtended(-1);
        }
    }
}
