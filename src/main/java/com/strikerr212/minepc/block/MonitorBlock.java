package com.strikerr212.minepc.block;

import com.mojang.serialization.MapCodec;
import com.strikerr212.minepc.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Плоский монитор на подставке. Складывается в большой экран; у нижнего ряда — подставка.
 * ПКМ клавиатурой — клавиатура встаёт в тот же блок перед экраном (Shift + ПКМ пустой рукой — вынуть).
 */
public class MonitorBlock extends BaseMonitorBlock {

    public static final MapCodec<MonitorBlock> CODEC = simpleCodec(MonitorBlock::new);
    /** Подставка есть только у нижнего ряда большого монитора. */
    public static final BooleanProperty STAND = BooleanProperty.create("stand");
    /** Клавиатура стоит в этом же блоке перед экраном. */
    public static final BooleanProperty KEYBOARD = BooleanProperty.create("keyboard");

    // Экран толщиной 3 пикселя у задней стороны блока + подставка (+ клавиатура перед ней)
    private static final BoxShapes WITH_STAND = BoxShapes.of(0, 2, 11, 16, 16, 14).add(4, 0, 9, 12, 2, 16);
    private static final BoxShapes TOP = BoxShapes.of(0, 0, 11, 16, 16, 14);
    private static final BoxShapes WITH_KEYBOARD = BoxShapes.of(0, 2, 11, 16, 16, 14).add(4, 0, 9, 12, 2, 16).add(1, 0, 1, 15, 1.5, 9);

    public MonitorBlock(Properties properties) {
        super(properties, MonitorKind.LCD, 0x303036);
        registerDefaultState(defaultBlockState().setValue(STAND, true).setValue(KEYBOARD, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STAND, KEYBOARD);
    }

    @Override
    public boolean hasKeyboard(BlockState state) {
        return state.getValue(KEYBOARD);
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
        Direction facing = state.getValue(FACING);
        if (!state.getValue(STAND)) return TOP.get(facing);
        return (state.getValue(KEYBOARD) ? WITH_KEYBOARD : WITH_STAND).get(facing);
    }

    /** ПКМ клавиатурой: поставить её в блок монитора, перед подставкой. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.KEYBOARD.get()) || state.getValue(KEYBOARD) || player.isShiftKeyDown()) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (!state.getValue(STAND)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.minepc.monitor.keyboard_bottom"), true);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, state.setValue(KEYBOARD, true));
            level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1f, 1.2f);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Shift + ПКМ пустой рукой — забрать клавиатуру; иначе — открыть экран. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown() && state.getValue(KEYBOARD)) {
            if (!level.isClientSide) {
                level.setBlockAndUpdate(pos, state.setValue(KEYBOARD, false));
                ItemStack kb = new ItemStack(ModItems.KEYBOARD.get());
                if (!player.getInventory().add(kb)) player.drop(kb, false);
                level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1f, 1.2f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }
}
