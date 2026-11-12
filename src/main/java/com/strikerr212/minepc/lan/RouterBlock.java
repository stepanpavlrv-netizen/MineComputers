package com.strikerr212.minepc.lan;

import com.mojang.serialization.MapCodec;
import com.strikerr212.minepc.block.BoxShapes;
import com.strikerr212.minepc.block.Paint;
import com.strikerr212.minepc.network.RouterOpenPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Wi-Fi роутер: раздаёт сеть компьютерам с USB Wi-Fi адаптером в своём радиусе и соединяет их с кабельной сетью
 * (кабель втыкается сзади или сбоку). Для сети это ещё один коммутатор: +1 переход. ПКМ — окно настроек.
 */
public class RouterBlock extends BaseEntityBlock implements LanDevice {

    public static final MapCodec<RouterBlock> CODEC = simpleCodec(RouterBlock::new);
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final BoxShapes SHAPE = BoxShapes.of(3, 0, 4, 13, 3, 11);
    /** Цвет корпуса некрашеного роутера (белый пластик). */
    public static final int PAINT_BASE = 0xF9FBFF;

    public RouterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(Paint.PROPERTY, Paint.NONE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.get(state.getValue(FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RouterBlockEntity(pos, state);
    }

    public int paintColor(BlockState state) {
        return state.getValue(Paint.PROPERTY).tint(PAINT_BASE);
    }

    // ======================= Сеть =======================

    @Override
    public Kind lanKind() {
        return Kind.SWITCH;
    }

    @Override
    public int lanType(BlockState state) {
        return LanData.ROUTER;
    }

    /** Гнёзда сзади (синий WAN и 4 жёлтых LAN), спереди лампочки. */
    @Override
    public boolean acceptsCable(BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    public java.util.List<Socket> sockets(BlockState state) {
        Direction f = state.getValue(FACING);
        java.util.List<Socket> out = new java.util.ArrayList<>();
        for (int x : new int[]{11, 9, 7, 5, 3}) out.add(LanDevice.local(f, true, x, 0, 5, true, false));
        return out;
    }

    @Override
    public long lanBitsPerSecond(Level level, BlockPos pos, BlockState state) {
        return 1_000_000_000L;
    }

    // ======================= Игрок =======================

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        ItemInteractionResult painted = Paint.use(stack, state, level, pos, player);
        return painted != null ? painted : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp
                && level.getBlockEntity(pos) instanceof RouterBlockEntity be) {
            PacketDistributor.sendToPlayer(sp, openPayload(server, pos, be));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Настройки и список подключённых для окна роутера. */
    public static RouterOpenPayload openPayload(ServerLevel level, BlockPos pos, RouterBlockEntity be) {
        List<String> clients = new ArrayList<>();
        for (var c : LanData.get(level).wifiClients(pos)) {
            String name = "", address = "";
            if (level.isLoaded(c.computer()) && level.getBlockEntity(c.computer()) instanceof com.strikerr212.minepc.block.PcCaseBlockEntity pc) {
                name = pc.label();
                address = pc.address();
            }
            clients.add(name + "\t" + address + "\t" + c.signal() + "\t" + c.mbit());
        }
        return new RouterOpenPayload(pos, be.ssid(), be.password(), be.radius(), String.join("\n", clients));
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
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.minepc.wifi_router").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
