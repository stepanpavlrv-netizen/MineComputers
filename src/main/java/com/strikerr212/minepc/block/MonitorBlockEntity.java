package com.strikerr212.minepc.block;

import com.strikerr212.minepc.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Монитор помнит, к какому системному блоку подключён: оттуда берётся картинка. */
public class MonitorBlockEntity extends BlockEntity {

    private BlockPos casePos;

    public MonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MONITOR.get(), pos, state);
    }

    public BlockPos casePos() {
        return casePos;
    }

    private MonitorGroup group;
    private long groupTime = Long.MIN_VALUE;

    /** Большой монитор, в который входит этот блок (пересчитывается раз в полсекунды). */
    public MonitorGroup group() {
        long now = level == null ? 0 : level.getGameTime();
        if (group == null || now - groupTime >= 10 || now < groupTime) {
            group = level == null ? MonitorGroup.single(worldPosition) : MonitorGroup.of(level, worldPosition);
            groupTime = now;
        }
        return group;
    }

    /** Вызывает системный блок, когда находит монитор рядом с собой (или теряет его). */
    public void link(BlockPos pos) {
        if (pos == null ? casePos == null : pos.equals(casePos)) return;
        casePos = pos;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (casePos != null) tag.putLong("case", casePos.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        casePos = tag.contains("case") ? BlockPos.of(tag.getLong("case")) : null;
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
