package com.strikerr212.minepc.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Монитор в два блока (широкий, изогнутый). Ставится сразу двумя половинами, как кровать:
 * левая — где кликнули, правая — справа от неё (как видит зритель). Сломали одну половину — пропадает и вторая.
 */
public class WideMonitorBlock extends BaseMonitorBlock {

    public static final MapCodec<WideMonitorBlock> CODEC = simpleCodec(p -> new WideMonitorBlock(p, MonitorKind.WIDE, -1,
            BoxShapes.of(0, 0, 0, 16, 16, 16), BoxShapes.of(0, 0, 0, 16, 16, 16)));

    public enum Half implements StringRepresentable {
        LEFT("left"), RIGHT("right");

        private final String name;

        Half(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Half> HALF = EnumProperty.create("half", Half.class);

    private final BoxShapes left, right, leftKb, rightKb;

    public WideMonitorBlock(Properties properties, MonitorKind kind, int paintBase, BoxShapes left, BoxShapes right) {
        super(properties, kind, paintBase);
        this.left = left;
        this.right = right;
        // Клавиатура перед экраном, посередине пары: половина в левом блоке, половина в правом
        this.leftKb = left.copy().add(0, 0, 0.5, 7, 1.5, 4.5);
        this.rightKb = right.copy().add(9, 0, 0.5, 16, 1.5, 4.5);
        registerDefaultState(defaultBlockState().setValue(HALF, Half.LEFT).setValue(MonitorBlock.KEYBOARD, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HALF, MonitorBlock.KEYBOARD);
    }

    @Override
    public boolean hasKeyboard(BlockState state) {
        return state.getValue(MonitorBlock.KEYBOARD);
    }

    /** Клавиатура — на обе половины сразу. */
    private static void setKeyboard(Level level, BlockPos pos, BlockState state, boolean on) {
        level.setBlockAndUpdate(pos, state.setValue(MonitorBlock.KEYBOARD, on));
        BlockPos other = partner(state, pos);
        BlockState os = level.getBlockState(other);
        if (os.getBlock() == state.getBlock()) level.setBlockAndUpdate(other, os.setValue(MonitorBlock.KEYBOARD, on));
    }

    /** ПКМ клавиатурой — клавиатура встаёт перед экраном (посередине двух блоков). */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level,
                                                                  BlockPos pos, Player player, net.minecraft.world.InteractionHand hand,
                                                                  net.minecraft.world.phys.BlockHitResult hit) {
        if (!stack.is(com.strikerr212.minepc.registry.ModItems.KEYBOARD.get()) || state.getValue(MonitorBlock.KEYBOARD) || player.isShiftKeyDown()) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (!level.isClientSide) {
            setKeyboard(level, pos, state, true);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.STONE_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1.2f);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Shift + ПКМ пустой рукой — забрать клавиатуру; иначе — открыть экран. */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                                   net.minecraft.world.phys.BlockHitResult hit) {
        if (player.isShiftKeyDown() && state.getValue(MonitorBlock.KEYBOARD)) {
            if (!level.isClientSide) {
                setKeyboard(level, pos, state, false);
                net.minecraft.world.item.ItemStack kb = new net.minecraft.world.item.ItemStack(com.strikerr212.minepc.registry.ModItems.KEYBOARD.get());
                if (!player.getInventory().add(kb)) player.drop(kb, false);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.STONE_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1.2f);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    /** Вправо, если смотреть на экран. */
    public static Direction right(BlockState state) {
        return state.getValue(FACING).getCounterClockWise();
    }

    /** Где вторая половина. */
    public static BlockPos partner(BlockState state, BlockPos pos) {
        Direction r = right(state);
        return state.getValue(HALF) == Half.LEFT ? pos.relative(r) : pos.relative(r.getOpposite());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState s = super.getStateForPlacement(context);
        BlockPos other = context.getClickedPos().relative(right(s));
        Level level = context.getLevel();
        if (!level.getWorldBorder().isWithinBounds(other) || !level.getBlockState(other).canBeReplaced(context)) return null;
        return s.setValue(HALF, Half.LEFT);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            level.setBlock(pos.relative(right(state)), state.setValue(HALF, Half.RIGHT).setValue(MonitorBlock.KEYBOARD, false), Block.UPDATE_ALL);
            state.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (neighborPos.equals(partner(state, pos))) {
            boolean ok = neighbor.getBlock() == this && neighbor.getValue(FACING) == state.getValue(FACING)
                    && neighbor.getValue(HALF) != state.getValue(HALF);
            if (!ok) return Blocks.AIR.defaultBlockState();
            if (neighbor.getValue(Paint.PROPERTY) != state.getValue(Paint.PROPERTY)) {
                return state.setValue(Paint.PROPERTY, neighbor.getValue(Paint.PROPERTY));   // краска — на обе половины
            }
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    /** Ломают правую половину в выживании — предмет выпадает из левой (у правой добычи нет). */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative() && state.getValue(HALF) == Half.RIGHT) {
            BlockPos other = partner(state, pos);
            if (level.getBlockState(other).getBlock() == this) level.destroyBlock(other, true, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean kb = state.getValue(MonitorBlock.KEYBOARD);
        return (state.getValue(HALF) == Half.LEFT ? (kb ? leftKb : left) : (kb ? rightKb : right)).get(state.getValue(FACING));
    }
}
