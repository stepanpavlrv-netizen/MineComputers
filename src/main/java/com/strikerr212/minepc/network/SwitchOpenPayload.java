package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Окно коммутатора (сервер → игрок): вид, номер и порты (строка на порт, поля через табуляцию). */
public record SwitchOpenPayload(BlockPos pos, String kind, String id, String ports) implements CustomPacketPayload {

    public static final Type<SwitchOpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "switch_open"));

    public static final StreamCodec<ByteBuf, SwitchOpenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SwitchOpenPayload::pos,
            ByteBufCodecs.stringUtf8(16), SwitchOpenPayload::kind,
            ByteBufCodecs.stringUtf8(16), SwitchOpenPayload::id,
            ByteBufCodecs.stringUtf8(16384), SwitchOpenPayload::ports,
            SwitchOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
