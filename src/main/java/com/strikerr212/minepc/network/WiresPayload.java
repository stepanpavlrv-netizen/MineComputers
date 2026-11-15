package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.wire.WireLine;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Сервер → игрок: провода (новые или изменённые целиком) и номера убранных. */
public record WiresPayload(List<WireLine> lines, long[] removed) implements CustomPacketPayload {

    public static final Type<WiresPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "wires"));

    public static final StreamCodec<FriendlyByteBuf, WiresPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.lines.size());
                for (WireLine l : p.lines) l.write(buf);
                buf.writeLongArray(p.removed);
            },
            buf -> {
                int n = Math.min(4096, buf.readVarInt());
                List<WireLine> lines = new ArrayList<>(n);
                for (int i = 0; i < n; i++) lines.add(WireLine.read(buf));
                return new WiresPayload(lines, buf.readLongArray());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
