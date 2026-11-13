package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Результат прозвонки кабельным тестером (сервер → игрок): что подсветить и что показать на экранчике.
 * wires — пары {клетка, провод (Wire.packed)}; cords — пары {конец, конец}; ends — устройства на концах;
 * passives — патч-панели и розетки по дороге; open — клетки, где провод обрывается.
 * info — строка через табуляцию: длина, предел, оптика (0/1), скорость (кбит/с), бирка, концы (через \u001e,
 * у конца поля через \u001f: ключ названия, порт, имя компьютера).
 */
public record TesterPayload(long[] wires, long[] trays, long[] cords, long[] passives, long[] ends, long[] open, String info)
        implements CustomPacketPayload {

    public static final Type<TesterPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "tester"));

    public static final StreamCodec<FriendlyByteBuf, TesterPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeLongArray(p.wires);
                buf.writeLongArray(p.trays);
                buf.writeLongArray(p.cords);
                buf.writeLongArray(p.passives);
                buf.writeLongArray(p.ends);
                buf.writeLongArray(p.open);
                buf.writeUtf(p.info, 4096);
            },
            buf -> new TesterPayload(buf.readLongArray(), buf.readLongArray(), buf.readLongArray(), buf.readLongArray(),
                    buf.readLongArray(), buf.readLongArray(), buf.readUtf(4096)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
