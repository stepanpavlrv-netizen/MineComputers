package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Как гудит работающий компьютер (сервер → игроки рядом, раз в пару секунд и при изменениях).
 *
 * @param pos  системный блок
 * @param fan  обороты вентиляторов, 0..100 (0 — выключен)
 * @param hdd  есть ли жёсткий диск (он тоже шумит)
 * @param start компьютер только что включили — звук раскручивается (иначе игрок просто подошёл)
 */
public record HumPayload(com.strikerr212.minepc.block.HostRef host, int fan, boolean hdd, boolean start) implements CustomPacketPayload {

    public static final Type<HumPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "hum"));

    public static final StreamCodec<ByteBuf, HumPayload> STREAM_CODEC = StreamCodec.composite(
            com.strikerr212.minepc.block.HostRef.STREAM_CODEC, HumPayload::host,
            ByteBufCodecs.VAR_INT, HumPayload::fan,
            ByteBufCodecs.BOOL, HumPayload::hdd,
            ByteBufCodecs.BOOL, HumPayload::start,
            HumPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
