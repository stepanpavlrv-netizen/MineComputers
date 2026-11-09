package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Открыть окно роутера (сервер → игрок): настройки и подключённые компьютеры.
 *
 * @param clients по строке на компьютер: имя, адрес, сигнал %, Мбит/с через табуляцию
 */
public record RouterOpenPayload(BlockPos pos, String ssid, String password, int radius, String clients) implements CustomPacketPayload {

    public static final Type<RouterOpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "router_open"));

    public static final StreamCodec<ByteBuf, RouterOpenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RouterOpenPayload::pos,
            ByteBufCodecs.stringUtf8(64), RouterOpenPayload::ssid,
            ByteBufCodecs.stringUtf8(64), RouterOpenPayload::password,
            ByteBufCodecs.VAR_INT, RouterOpenPayload::radius,
            ByteBufCodecs.stringUtf8(16384), RouterOpenPayload::clients,
            RouterOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
