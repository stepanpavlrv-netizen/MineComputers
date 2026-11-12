package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Порты устройства для подсказки у гнезда (сервер → клиент). data — строки «порт, связь, кто там, вкл, VLAN, длина»
 * через табуляцию; kbit — скорость устройства.
 */
public record PortsInfoPayload(BlockPos pos, int kbit, String data) implements CustomPacketPayload {

    public static final Type<PortsInfoPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "ports_info"));

    public static final StreamCodec<ByteBuf, PortsInfoPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PortsInfoPayload::pos,
            ByteBufCodecs.VAR_INT, PortsInfoPayload::kbit,
            ByteBufCodecs.stringUtf8(16384), PortsInfoPayload::data,
            PortsInfoPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
