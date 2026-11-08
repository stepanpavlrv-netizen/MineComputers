package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Пакет "игрок ввёл надпись в сборочном столе" (клиент → сервер). */
public record LabelPayload(String text) implements CustomPacketPayload {

    public static final Type<LabelPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "label"));

    public static final StreamCodec<ByteBuf, LabelPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(64), LabelPayload::text, LabelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
