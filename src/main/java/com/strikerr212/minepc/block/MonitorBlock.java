package com.strikerr212.minepc.block;

import com.mojang.serialization.MapCodec;
import com.strikerr212.minepc.client.ClientAccess;
import net.minecraft.core.BlockPos;
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

    // Экран толщиной 3 пикселя у задней стороны блока + подставка
    private static final VoxelShape NORTH = Shapes.or(Block.box(0, 2, 11, 16, 16, 14), Block.box(4, 0, 9, 12, 2, 16));
    private static final VoxelShape SOUTH = Shapes.or(Block.box(0, 2, 2, 16, 16, 5), Block.box(4, 0, 0, 12, 2, 7));
    private static final VoxelShape WEST = Shapes.or(Block.box(11, 2, 0, 14, 16, 16), Block.box(9, 0, 4, 16, 2, 12));
    private static final VoxelShape EAST = Shapes.or(Block.box(2, 2, 0, 5, 16, 16), Block.box(0, 0, 4, 7, 2, 12));

    public MonitorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
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
