package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Текстуры блоков для камер. Сервер → игрок: «пришли таблицу» (index = -1). Игрок → сервер: кусок сжатой таблицы
 * index из total (не больше {@link #CHUNK} байт — пакеты к серверу короткие).
 */
public record CameraTexturesPayload(int index, int total, byte[] data) implements CustomPacketPayload {

    public static final int CHUNK = 30_000;

    public static final Type<CameraTexturesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "camera_textures"));

    public static final StreamCodec<ByteBuf, CameraTexturesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CameraTexturesPayload::index,
            ByteBufCodecs.VAR_INT, CameraTexturesPayload::total,
            ByteBufCodecs.byteArray(CHUNK + 64), CameraTexturesPayload::data,
            CameraTexturesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
