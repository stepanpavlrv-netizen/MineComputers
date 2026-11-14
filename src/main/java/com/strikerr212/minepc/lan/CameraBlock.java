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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Камера наблюдения: крепление (на стену, пол или потолок) и поворотная голова. Кабель подходит к пластине
 * крепления по стене. ПКМ — окно камеры: живое изображение, поворот, зум, разрешение и частота.
 * Переименуй на наковальне — это её имя в сети.
 */
public class CameraBlock extends FaceAttachedHorizontalDirectionalBlock implements LanDevice, EntityBlock {

    public static final MapCodec<CameraBlock> CODEC = simpleCodec(CameraBlock::new);

    public CameraBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FACE, AttachFace.WALL));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE);
    }

    /** Сторона, к которой прикручено крепление. */
    public static Direction attached(BlockState state) {
        return getConnectedDirection(state).getOpposite();
    }

    /** Шарнир головы в долях блока (x, y, z). */
    public static double[] joint(BlockState state) {
        AttachFace face = state.getValue(FACE);
        if (face == AttachFace.FLOOR) return new double[]{0.5, 7 / 16.0, 0.5};
        if (face == AttachFace.CEILING) return new double[]{0.5, 9 / 16.0, 0.5};
        Direction f = state.getValue(FACING);
        return new double[]{0.5 - f.getStepX() * 0.125, 10 / 16.0, 0.5 - f.getStepZ() * 0.125};
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        AttachFace face = state.getValue(FACE);
        if (face == AttachFace.FLOOR) return Block.box(4, 0, 4, 12, 11, 12);
        if (face == AttachFace.CEILING) return Block.box(4, 5, 4, 12, 16, 12);
        return switch (state.getValue(FACING)) {
            case SOUTH -> Block.box(4, 6, 0, 12, 13, 11);
            case EAST -> Block.box(0, 6, 4, 11, 13, 12);
            case WEST -> Block.box(5, 6, 4, 16, 13, 12);
            default -> Block.box(4, 6, 5, 12, 13, 16);
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CameraBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.CAMERA.get()) return null;
        return (l, p, s, be) -> CameraBlockEntity.serverTick(l, p, s, (CameraBlockEntity) be);
    }

    // ======================= Сеть =======================

    @Override
    public Kind lanKind() {
        return Kind.COMPUTER;
    }

    @Override
    public boolean acceptsCable(BlockState state, Direction face) {
        return face.getAxis() != attached(state).getAxis();
    }

    /** Гнёзда — на кромках пластины крепления, у опоры: кабель подходит по стене (полу, потолку) с любой стороны. */
    @Override
    public List<Socket> sockets(BlockState state) {
        Direction a = attached(state);
        int t = a.getAxis().ordinal();
        boolean pos = a.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        float layer = pos ? 15 : 0;
        List<Socket> out = new ArrayList<>();
        for (Direction f : Direction.values()) {
            if (f.getAxis() == a.getAxis()) continue;
            Direction.Axis[] fa = LanDevice.faceAxes(f);
            if (fa[0].ordinal() == t) out.add(new Socket(f, layer, 7.5f, 5, true, false));
            else out.add(new Socket(f, 7.5f, layer, 5, true, false));
        }
        return out;
    }

    @Override
    public long lanBitsPerSecond(Level level, BlockPos pos, BlockState state) {
        return 1_000_000_000L;
    }

    // ======================= Игрок =======================

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) com.strikerr212.minepc.client.ClientAccess.openCamera(pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        var name = stack.get(DataComponents.CUSTOM_NAME);
        if (!level.isClientSide && name != null && level.getBlockEntity(pos) instanceof CameraBlockEntity be) be.setLabel(name.getString());
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
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.minepc.camera").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.minepc.camera2").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
