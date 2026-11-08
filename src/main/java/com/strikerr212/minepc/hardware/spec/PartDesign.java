package com.strikerr212.minepc.hardware.spec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * "Чертёж" детали, который хранится внутри предмета.
 *
 * @param values номера выбранных вариантов для каждого параметра (порядок — как в спецификации)
 * @param label  надпись: гравировка на крышке процессора или название модели
 */
public record PartDesign(List<Integer> values, String label) {

    public static final int MAX_LABEL = 24;

    public static final Codec<PartDesign> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.listOf().fieldOf("values").forGetter(PartDesign::values),
            Codec.STRING.optionalFieldOf("label", "").forGetter(PartDesign::label)
    ).apply(i, PartDesign::new));

    public static final StreamCodec<ByteBuf, PartDesign> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public PartDesign {
        values = List.copyOf(values);
        label = label == null ? "" : (label.length() > MAX_LABEL ? label.substring(0, MAX_LABEL) : label);
    }

    public int get(int param) {
        return param >= 0 && param < values.size() ? values.get(param) : 0;
    }
}
