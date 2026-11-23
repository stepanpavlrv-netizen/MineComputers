package com.strikerr212.minepc.lan;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Опора широкой радиовышки: вышка выше 17 секций внизу шире 3×3 (до 5×5), а модель одного блока не может выходить
 * за соседние, поэтому ноги, пояса и раскосы за пределами квадрата 3×3 рисуют опоры в кольце 5×5 вокруг секции.
 * Ставит и убирает их сама секция ({@link TowerSectionBlock#syncLegs}); у опоры нет предмета, её нельзя выбрать
 * прицелом и сломать рукой, а убранная (взрывом) встаёт обратно. Сквозная, как и вся вышка.
 */
public class TowerLegBlock extends Block {

    public static final MapCodec<TowerLegBlock> CODEC = simpleCodec(TowerLegBlock::new);
    /** Сколько секций над секцией-хозяйкой (опоры нужны только с {@link TowerSectionBlock#WIDE_FROM}). */
    public static final IntegerProperty ABOVE = IntegerProperty.create("above", TowerSectionBlock.WIDE_FROM, TowerSectionBlock.MAX_SECTIONS - 1);
    /** Место в кольце 5×5 — номер в {@link #CELLS}. */
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, 15);
    /** Опора нижней секции — рисует ещё и бетонные основания ног. */
    public static final BooleanProperty FOOT = BooleanProperty.create("foot");
    /** Кольцо 5×5 вокруг секции (dx, dz): по рядам с севера на юг, в ряду с запада на восток — как в tools/assetgen/tower.py. */
    public static final int[][] CELLS = cells();

    public TowerLegBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ABOVE, TowerSectionBlock.WIDE_FROM).setValue(CELL, 0)
                .setValue(TowerSectionBlock.BAND, false).setValue(TowerSectionBlock.GALVANIZED, false).setValue(FOOT, false));
    }

    private static int[][] cells() {
        int[][] out = new int[16][];
        int n = 0;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 2) out[n++] = new int[]{dx, dz};
            }
        }
        return out;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ABOVE, CELL, TowerSectionBlock.BAND, TowerSectionBlock.GALVANIZED, FOOT);
    }

    /** Секция, которой принадлежит опора. */
    public static BlockPos owner(BlockState state, BlockPos pos) {
        int[] c = CELLS[state.getValue(CELL)];
        return pos.offset(-c[0], 0, -c[1]);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }


    /** Убрали не секция (взрыв, команда) — секция поставит опору обратно, а если она уже не нужна, ничего не сделает. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (level instanceof ServerLevel sl && !newState.is(this)) {
            BlockPos o = owner(state, pos);
            BlockState os = sl.getBlockState(o);
            if (os.getBlock() instanceof TowerSectionBlock) sl.scheduleTick(o, os.getBlock(), 1);
        }
    }
}
