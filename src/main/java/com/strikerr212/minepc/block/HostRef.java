package com.strikerr212.minepc.block;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Какой это компьютер: блок и место в нём. У системного блока место всегда 0, в серверной стойке — номер модуля
 * (0 — нижний). По этой паре компьютер находят сеть, экран, меню и звук.
 */
public record HostRef(BlockPos pos, int slot) {

    public static final StreamCodec<ByteBuf, HostRef> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HostRef::pos,
            ByteBufCodecs.VAR_INT, HostRef::slot,
            HostRef::new);

    public HostRef {
        pos = pos.immutable();
    }

    /** Компьютер в обычном системном блоке. */
    public static HostRef of(BlockPos pos) {
        return new HostRef(pos, 0);
    }
}
