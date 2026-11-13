package com.strikerr212.minepc.lan;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Потолочная точка доступа Wi-Fi: белая «шайба» с синим кольцом, висит под потолком. Для сети это тот же роутер
 * (имя сети, пароль, радиус — в том же окне), но радиус поменьше (до {@link #MAX_RADIUS}), зато каждая стена отнимает
 * у сигнала только 1 блок вместо 3. Кабель подходит к ней по потолку — гнёзда с четырёх сторон.
 */
public class AccessPointBlock extends BaseEntityBlock implements LanDevice {

    public static final MapCodec<AccessPointBlock> CODEC = simpleCodec(AccessPointBlock::new);
    public static final int MAX_RADIUS = 24, DEFAULT_RADIUS = 16;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 14, 3, 13, 16, 13), Block.box(4, 13.5, 4, 12, 14, 12));

    public AccessPointBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Вешается только под потолок. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos up = pos.above();
        return level.getBlockState(up).isFaceSturdy(level, up, Direction.DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState s = defaultBlockState();
        return s.canSurvive(context.getLevel(), context.getClickedPos()) ? s : null;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return dir == Direction.UP && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RouterBlockEntity(pos, state);
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

    @Override
    public boolean acceptsCable(BlockState state, Direction face) {
        return face.getAxis().isHorizontal();
    }

    /** По гнезду на каждой стороне — у самого потолка, где лежит провод на потолке соседней клетки. */
    @Override
    public List<Socket> sockets(BlockState state) {
        List<Socket> out = new ArrayList<>();
        for (Direction f : Direction.Plane.HORIZONTAL) out.add(new Socket(f, 7, 15, 3, true, false));
        return out;
    }

    @Override
    public long lanBitsPerSecond(Level level, BlockPos pos, BlockState state) {
        return 1_000_000_000L;
    }

    // ======================= Игрок =======================

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof RouterBlockEntity be) {
            PacketDistributor.sendToPlayer(sp, RouterBlock.openPayload(server, pos, be));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide) {
            LanManager.updateDevice(level, pos);
            CableBlockEntity.reconnectAround(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            LanManager.updateDevice(level, pos);
            CableBlockEntity.reconnectAround(level, pos);
        }
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.minepc.access_point", MAX_RADIUS)
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
