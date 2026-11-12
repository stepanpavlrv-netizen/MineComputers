package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Через устройство прошло сообщение — мигнуть лампочками гнёзд (сервер → игроки рядом). */
public record LanActivityPayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<LanActivityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "lan_activity"));

    public static final StreamCodec<ByteBuf, LanActivityPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, LanActivityPayload::pos, LanActivityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
