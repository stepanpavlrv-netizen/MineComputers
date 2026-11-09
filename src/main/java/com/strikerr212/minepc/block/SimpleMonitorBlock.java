package com.strikerr212.minepc.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Монитор в один блок: кинескоп, телевизор, терминал, настенная панель. */
public class SimpleMonitorBlock extends BaseMonitorBlock {

    public static final MapCodec<SimpleMonitorBlock> CODEC = simpleCodec(p -> new SimpleMonitorBlock(p, MonitorKind.CRT, -1, BoxShapes.of(0, 0, 0, 16, 16, 16)));

    private final BoxShapes shape;

    public SimpleMonitorBlock(Properties properties, MonitorKind kind, int paintBase, BoxShapes shape) {
        super(properties, kind, paintBase);
        this.shape = shape;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape.get(state.getValue(FACING));
    }
}
