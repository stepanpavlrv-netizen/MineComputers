package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Окно камеры (игрок → сервер): поворот, зум, настройки, запрос кадра для превью. */
public record CameraActionPayload(BlockPos pos, int action, float a, float b) implements CustomPacketPayload {

    public static final int TURN = 0, LOOK = 1, ZOOM = 2, RES = 3, FPS = 4, NIGHT = 5, PATROL = 6, FRAME = 7;

    public static final Type<CameraActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "camera_action"));

    public static final StreamCodec<ByteBuf, CameraActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CameraActionPayload::pos,
            ByteBufCodecs.VAR_INT, CameraActionPayload::action,
            ByteBufCodecs.FLOAT, CameraActionPayload::a,
            ByteBufCodecs.FLOAT, CameraActionPayload::b,
            CameraActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
