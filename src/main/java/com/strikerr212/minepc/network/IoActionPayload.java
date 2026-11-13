package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Окно контроллера ввода-вывода (игрок → сервер): переключить сторону вход/выход или задать уровень выхода. */
public record IoActionPayload(BlockPos pos, int side, int action, int value) implements CustomPacketPayload {

    public static final int MODE = 0, LEVEL = 1;

    public static final Type<IoActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "io_action"));

    public static final StreamCodec<ByteBuf, IoActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, IoActionPayload::pos,
            ByteBufCodecs.VAR_INT, IoActionPayload::side,
            ByteBufCodecs.VAR_INT, IoActionPayload::action,
            ByteBufCodecs.VAR_INT, IoActionPayload::value,
            IoActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
