package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Изменить порт коммутатора (игрок → сервер); port = -1 — только обновить окно. */
public record SwitchPortPayload(BlockPos pos, int port, boolean enabled, int vlan) implements CustomPacketPayload {

    public static final Type<SwitchPortPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "switch_port"));

    public static final StreamCodec<ByteBuf, SwitchPortPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SwitchPortPayload::pos,
            ByteBufCodecs.VAR_INT, SwitchPortPayload::port,
            ByteBufCodecs.BOOL, SwitchPortPayload::enabled,
            ByteBufCodecs.VAR_INT, SwitchPortPayload::vlan,
            SwitchPortPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
