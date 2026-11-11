package com.strikerr212.minepc.block;

import com.mojang.serialization.MapCodec;
import com.strikerr212.minepc.item.DriveItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Дисковод. ПКМ дискетой — вставить, ПКМ пустой рукой — вынуть.
 * Стоит вплотную к системному блоку — дискета видна в компьютере как папка /disk.
 */
public class DiskDriveBlock extends DeviceBlock {

    public static final MapCodec<DiskDriveBlock> CODEC = simpleCodec(DiskDriveBlock::new);
    public static final BooleanProperty HAS_DISK = BooleanProperty.create("disk");

    public DiskDriveBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(HAS_DISK, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HAS_DISK);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DiskDriveBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof DriveItem drive) || drive.kind() != DriveItem.Kind.FLOPPY) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof DiskDriveBlockEntity be) {
            if (!be.disk().isEmpty()) {
                player.displayClientMessage(Component.translatable("message.minepc.drive.busy").withStyle(ChatFormatting.YELLOW), true);
            } else {
                be.setDisk(stack.split(1));
                level.playSound(null, pos, com.strikerr212.minepc.registry.ModSounds.DISK_INSERT.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof DiskDriveBlockEntity be && !be.disk().isEmpty()) {
            ItemStack disk = be.disk();
            be.setDisk(ItemStack.EMPTY);
            if (!player.getInventory().add(disk)) player.drop(disk, false);
            level.playSound(null, pos, com.strikerr212.minepc.registry.ModSounds.DISK_EJECT.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof DiskDriveBlockEntity be && !be.disk().isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, be.disk());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
