package com.strikerr212.minepc.lan;

import com.mojang.serialization.MapCodec;
import com.strikerr212.minepc.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Контроллер ввода-вывода: мост между компьютерами и механизмами. Кабель — сзади; каждая из шести сторон —
 * редстоун-вход или выход. ПКМ — окно: стороны, датчики, журнал. Переименуй на наковальне — это его имя в сети.
 */
public class IoControllerBlock extends BaseEntityBlock implements LanDevice {

    public static final MapCodec<IoControllerBlock> CODEC = simpleCodec(IoControllerBlock::new);
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Хотя бы один выход подаёт сигнал — лампы на передней панели горят. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public IoControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IoControllerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.IO_CONTROLLER.get(), IoControllerBlockEntity::serverTick);
    }

    // ======================= Редстоун =======================

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /** direction — от соседа к контроллеру, поэтому сторона контроллера — обратная. */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof IoControllerBlockEntity be ? be.signalTo(direction.getOpposite()) : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof IoControllerBlockEntity be) be.readInputs(true);
    }

    // ======================= Сеть =======================

    @Override
    public Kind lanKind() {
        return Kind.COMPUTER;
    }

    @Override
    public boolean acceptsCable(BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    public java.util.List<Socket> sockets(BlockState state) {
        return java.util.List.of(LanDevice.local(state.getValue(FACING), true, 7, 5, 0, true, false));
    }

    @Override
    public long lanBitsPerSecond(Level level, BlockPos pos, BlockState state) {
        return 100_000_000L;
    }

    // ======================= Игрок =======================

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) com.strikerr212.minepc.client.ClientAccess.openIoController(pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        var name = stack.get(DataComponents.CUSTOM_NAME);
        if (!level.isClientSide && name != null && level.getBlockEntity(pos) instanceof IoControllerBlockEntity be) be.setLabel(name.getString());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide && !old.is(this)) LanManager.updateDevice(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!level.isClientSide && !state.is(newState.getBlock())) LanManager.updateDevice(level, pos);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.minepc.io_controller").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.minepc.io_controller2").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
