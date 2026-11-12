package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Пакет «применить шаблон на сборочном столе»: все варианты вкладки разом (клиент → сервер). */
public record AssemblyValuesPayload(int tab, List<Integer> values) implements CustomPacketPayload {

    public static final Type<AssemblyValuesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "assembly_values"));

    public static final StreamCodec<ByteBuf, AssemblyValuesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AssemblyValuesPayload::tab,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(32)), AssemblyValuesPayload::values,
            AssemblyValuesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
