package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Команды звука компьютера (сервер → игроки рядом): пищалка и звуковой чип. Звук собирает синтезатор у игрока.
 *
 * @param pos      системный блок (оттуда и звучит)
 * @param startMs  когда начать, мс по часам компьютера (-1 — сразу)
 * @param nowMs    «сейчас» по часам компьютера в момент отправки
 * @param commands команды ({@link com.strikerr212.minepc.computer.audio.Synth})
 */
public record SoundPayload(com.strikerr212.minepc.block.HostRef host, long startMs, long nowMs, byte[] commands) implements CustomPacketPayload {

    public static final Type<SoundPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "sound"));

    public static final StreamCodec<ByteBuf, SoundPayload> STREAM_CODEC = StreamCodec.composite(
            com.strikerr212.minepc.block.HostRef.STREAM_CODEC, SoundPayload::host,
            ByteBufCodecs.VAR_LONG, SoundPayload::startMs,
            ByteBufCodecs.VAR_LONG, SoundPayload::nowMs,
            ByteBufCodecs.byteArray(256 * 1024), SoundPayload::commands,
            SoundPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
