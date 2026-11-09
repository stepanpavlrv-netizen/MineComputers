package com.strikerr212.minepc.block;

import com.strikerr212.minepc.client.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
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
    /** Цвет корпуса некрашеного монитора (им красится модель через tintindex 0); -1 — не красится. */
    private final int paintBase;

    protected BaseMonitorBlock(Properties properties, MonitorKind kind, int paintBase) {
        super(properties);
        this.kind = kind;
        this.paintBase = paintBase;
        registerDefaultState(defaultBlockState().setValue(Paint.PROPERTY, Paint.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(Paint.PROPERTY);
    }

    /** Цвет граней с краской для этого состояния; -1 — монитор не красится. */
    public int paintColor(BlockState state) {
        return paintBase < 0 ? -1 : state.getValue(Paint.PROPERTY).tint(paintBase);
    }

    /** ПКМ красителем — покрасить корпус монитора, ведром воды — смыть. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (paintBase >= 0) {
            ItemInteractionResult painted = Paint.use(stack, state, level, pos, player);
            if (painted != null) return painted;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
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
