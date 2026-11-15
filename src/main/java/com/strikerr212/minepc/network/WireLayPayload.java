package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.wire.WirePlan;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Игрок закончил прокладку: откуда, щелчки по пути, куда (null — свободный конец там, где последний щелчок). */
public record WireLayPayload(boolean offhand, WirePlan.Anchor from, List<int[]> clicks, WirePlan.Anchor to) implements CustomPacketPayload {

    public static final int MAX_CLICKS = 64;

    public static final Type<WireLayPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "wire_lay"));

    public static final StreamCodec<FriendlyByteBuf, WireLayPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.offhand);
                p.from.write(buf);
                int n = Math.min(MAX_CLICKS, p.clicks.size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    buf.writeVarInt(p.clicks.get(i)[0]);
                    buf.writeVarInt(p.clicks.get(i)[1]);
                    buf.writeVarInt(p.clicks.get(i)[2]);
                }
                buf.writeBoolean(p.to != null);
                if (p.to != null) p.to.write(buf);
            },
            buf -> {
                boolean off = buf.readBoolean();
                WirePlan.Anchor from = WirePlan.Anchor.read(buf);
                int n = Math.min(MAX_CLICKS, buf.readVarInt());
                List<int[]> clicks = new ArrayList<>(n);
                for (int i = 0; i < n; i++) clicks.add(new int[]{buf.readVarInt(), buf.readVarInt(), buf.readVarInt()});
                WirePlan.Anchor to = buf.readBoolean() ? WirePlan.Anchor.read(buf) : null;
                return new WireLayPayload(off, from, clicks, to);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
