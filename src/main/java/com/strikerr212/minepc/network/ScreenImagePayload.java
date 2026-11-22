package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Картинка поверх экрана компьютера (сервер → игроки рядом): кадр игры в высоком разрешении.
 * Пустые данные — картинки больше нет.
 *
 * @param host компьютер, чей это экран
 * @param data сжатая картинка ({@link com.strikerr212.minepc.computer.ImageCodec})
 */
public record ScreenImagePayload(com.strikerr212.minepc.block.HostRef host, byte[] data) implements CustomPacketPayload {

    public static final Type<ScreenImagePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "screen_image"));

    public static final StreamCodec<ByteBuf, ScreenImagePayload> STREAM_CODEC = StreamCodec.composite(
            com.strikerr212.minepc.block.HostRef.STREAM_CODEC, ScreenImagePayload::host,
            ByteBufCodecs.byteArray(2_000_000), ScreenImagePayload::data,
            ScreenImagePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
