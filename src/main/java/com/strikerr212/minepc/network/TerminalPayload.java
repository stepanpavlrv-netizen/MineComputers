package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Снимок экрана компьютера (сервер → игроки рядом). Пустые данные — компьютер выключен.
 *
 * @param host     компьютер, чей это экран (блок и место в нём)
 * @param data     сжатый кадр или изменения ({@link com.strikerr212.minepc.computer.TerminalCodec})
 * @param keyboard подключена ли клавиатура (можно ли печатать)
 */
public record TerminalPayload(com.strikerr212.minepc.block.HostRef host, byte[] data, boolean keyboard) implements CustomPacketPayload {

    public static final Type<TerminalPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "terminal"));

    public static final StreamCodec<ByteBuf, TerminalPayload> STREAM_CODEC = StreamCodec.composite(
            com.strikerr212.minepc.block.HostRef.STREAM_CODEC, TerminalPayload::host,
            ByteBufCodecs.byteArray(1_000_000), TerminalPayload::data,
            ByteBufCodecs.BOOL, TerminalPayload::keyboard,
            TerminalPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
