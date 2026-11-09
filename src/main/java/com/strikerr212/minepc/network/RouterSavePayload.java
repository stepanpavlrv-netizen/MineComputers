package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Сохранить настройки роутера (игрок → сервер). refresh — только прислать окно заново (обновить список). */
public record RouterSavePayload(BlockPos pos, String ssid, String password, int radius, boolean refresh) implements CustomPacketPayload {

    public static final Type<RouterSavePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "router_save"));

    public static final StreamCodec<ByteBuf, RouterSavePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RouterSavePayload::pos,
            ByteBufCodecs.stringUtf8(64), RouterSavePayload::ssid,
            ByteBufCodecs.stringUtf8(64), RouterSavePayload::password,
            ByteBufCodecs.VAR_INT, RouterSavePayload::radius,
            ByteBufCodecs.BOOL, RouterSavePayload::refresh,
            RouterSavePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
