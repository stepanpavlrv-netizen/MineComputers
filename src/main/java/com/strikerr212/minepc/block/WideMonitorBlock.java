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

    private final BoxShapes left, right;

    public WideMonitorBlock(Properties properties, MonitorKind kind, int paintBase, BoxShapes left, BoxShapes right) {
        super(properties, kind, paintBase);
        this.left = left;
        this.right = right;
        registerDefaultState(defaultBlockState().setValue(HALF, Half.LEFT));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HALF);
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
            level.setBlock(pos.relative(right(state)), state.setValue(HALF, Half.RIGHT), Block.UPDATE_ALL);
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
        return (state.getValue(HALF) == Half.LEFT ? left : right).get(state.getValue(FACING));
    }
}
