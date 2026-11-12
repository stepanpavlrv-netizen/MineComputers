package com.strikerr212.minepc.lan;

import com.mojang.serialization.MapCodec;
import com.strikerr212.minepc.block.BoxShapes;
import com.strikerr212.minepc.block.Paint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Коммутатор: соединяет все кабели, вставленные в его гнёзда, и усиливает сигнал (+1 тик к задержке).
 * Настольный — 6 гнёзд спереди. Стоечный — целый блок, два ряда по 6 гнёзд спереди (медь и оптика),
 * 10 Гбит/с; стоечные коммутаторы вплотную друг к другу работают как один.
 */
public class SwitchBlock extends HorizontalDirectionalBlock implements LanDevice, net.minecraft.world.level.block.EntityBlock {

    public static final MapCodec<SwitchBlock> CODEC = simpleCodec(p -> new SwitchBlock(p, false));
    private static final BoxShapes DESK = BoxShapes.of(1, 0, 4, 15, 4, 11);

    private final boolean rack;

    public SwitchBlock(Properties properties, boolean rack) {
        super(properties);
        this.rack = rack;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(Paint.PROPERTY, Paint.NONE));
    }

    public boolean rack() {
        return rack;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, Paint.PROPERTY);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return rack ? Shapes.block() : DESK.get(state.getValue(FACING));
    }

    @Override
    public Kind lanKind() {
        return Kind.SWITCH;
    }

    /** Гнёзда только спереди: у настольного 6 в ряд, у стоечного два ряда по 6 (медь и оптика). */
    @Override
    public boolean acceptsCable(BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    @Override
    public boolean acceptsFiber(BlockState state, Direction face) {
        return rack && face == state.getValue(FACING);
    }

    /** Порт 1 — левый, если смотреть на лицевую сторону (как подписано в программе switch). */
    @Override
    public java.util.List<Socket> sockets(BlockState state) {
        Direction f = state.getValue(FACING);
        java.util.List<Socket> out = new java.util.ArrayList<>();
        if (rack) {
            for (int row : new int[]{12, 7}) {
                for (int i = 0; i < 6; i++) out.add(LanDevice.local(f, false, 13 - 2 * i, row, 0, true, true));
            }
        } else {
            for (int i = 0; i < 6; i++) out.add(LanDevice.local(f, false, 13 - 2 * i, 1, 4, true, false));
        }
        return out;
    }

    @Override
    public long lanBitsPerSecond(Level level, BlockPos pos, BlockState state) {
        return rack ? 10_000_000_000L : 1_000_000_000L;
    }

    @Override
    public boolean joinsNeighbours() {
        return rack;
    }

    /** Цвет корпуса некрашеного коммутатора. */
    public int paintColor(BlockState state) {
        return state.getValue(Paint.PROPERTY).tint(rack ? 0x404550 : 0x535963);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        ItemInteractionResult painted = Paint.use(stack, state, level, pos, player);
        return painted != null ? painted : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LanBlockEntity(pos, state);
    }

    /** ПКМ — окно портов: включить/выключить, VLAN (цвет). */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof net.minecraft.server.level.ServerLevel server && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, SwitchPorts.openPayload(server, pos));
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide) LanManager.updateDevice(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!level.isClientSide && !state.is(newState.getBlock())) LanManager.updateDevice(level, pos);
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable(rack ? "tooltip.minepc.rack_switch" : "tooltip.minepc.network_switch").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
