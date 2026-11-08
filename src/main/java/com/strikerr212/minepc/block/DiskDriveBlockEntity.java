package com.strikerr212.minepc.block;

import com.strikerr212.minepc.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Дисковод: хранит вставленную дискету. Системный блок рядом подключает её как /disk. */
public class DiskDriveBlockEntity extends BlockEntity {

    private ItemStack disk = ItemStack.EMPTY;

    public DiskDriveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISK_DRIVE.get(), pos, state);
    }

    public ItemStack disk() {
        return disk;
    }

    public void setDisk(ItemStack stack) {
        disk = stack;
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState().setValue(DiskDriveBlock.HAS_DISK, !stack.isEmpty());
            level.setBlock(worldPosition, state, Block.UPDATE_ALL);
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!disk.isEmpty()) tag.put("disk", disk.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        disk = tag.contains("disk", Tag.TAG_COMPOUND)
                ? ItemStack.parse(registries, tag.getCompound("disk")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
