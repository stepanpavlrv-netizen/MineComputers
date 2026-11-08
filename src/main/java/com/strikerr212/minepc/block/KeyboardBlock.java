package com.strikerr212.minepc.block;

import com.mojang.serialization.MapCodec;
import com.strikerr212.minepc.client.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.shapes.VoxelShape;

/** Клавиатура: без неё на экране можно только смотреть. Ставится рядом с монитором или системным блоком. */
public class KeyboardBlock extends DeviceBlock {

    public static final MapCodec<KeyboardBlock> CODEC = simpleCodec(KeyboardBlock::new);

    private static final VoxelShape NS = Block.box(1, 0, 4, 15, 2, 12);
    private static final VoxelShape EW = Block.box(4, 0, 1, 12, 2, 15);

    public KeyboardBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NS : EW;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null;   // клавиатуре не нужно хранить данные
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            BlockPos casePos = findCase(level, pos);
            if (casePos == null) {
                player.displayClientMessage(Component.translatable("message.minepc.keyboard.no_case"), true);
            } else {
                ClientAccess.openTerminal(casePos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
