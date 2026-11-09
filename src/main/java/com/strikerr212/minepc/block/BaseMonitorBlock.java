package com.strikerr212.minepc.block;

import com.strikerr212.minepc.client.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Любой монитор: ставится вплотную к системному блоку (или к другому монитору того же экрана) и показывает его экран.
 * ПКМ — открыть экран крупно; печатать можно, если рядом есть клавиатура.
 */
public abstract class BaseMonitorBlock extends DeviceBlock {

    private final MonitorKind kind;

    protected BaseMonitorBlock(Properties properties, MonitorKind kind) {
        super(properties);
        this.kind = kind;
    }

    public MonitorKind kind() {
        return kind;
    }

    /** Есть ли в этом блоке клавиатура (встроенная или вставленная). */
    public boolean hasKeyboard(BlockState state) {
        return kind.keyboard;
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
