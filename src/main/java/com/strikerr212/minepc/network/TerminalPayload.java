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
 * @param casePos  системный блок, чей это экран
 * @param data     снимок из {@link com.strikerr212.minepc.computer.Terminal#snapshot()}
 * @param keyboard подключена ли клавиатура (можно ли печатать)
 */
public record TerminalPayload(BlockPos casePos, byte[] data, boolean keyboard) implements CustomPacketPayload {

    public static final Type<TerminalPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "terminal"));

    public static final StreamCodec<ByteBuf, TerminalPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TerminalPayload::casePos,
            ByteBufCodecs.byteArray(16384), TerminalPayload::data,
            ByteBufCodecs.BOOL, TerminalPayload::keyboard,
            TerminalPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
