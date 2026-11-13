package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.lan.CablePath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Игрок закончил путь провода (щелчками): точки — по порядку; рука, в которой моток. */
public record CableLayPayload(boolean offhand, List<CablePath.Point> points) implements CustomPacketPayload {

    public static final Type<CableLayPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "cable_lay"));

    public static final StreamCodec<FriendlyByteBuf, CableLayPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.offhand);
                buf.writeVarInt(Math.min(64, p.points.size()));
                for (int i = 0; i < Math.min(64, p.points.size()); i++) {
                    CablePath.Point pt = p.points.get(i);
                    buf.writeBlockPos(pt.cell());
                    buf.writeByte(pt.face().get3DDataValue());
                    buf.writeByte(pt.u());
                    buf.writeByte(pt.v());
                    buf.writeBoolean(pt.socket());
                    if (pt.socket()) {
                        buf.writeBlockPos(pt.dev());
                        buf.writeVarInt(pt.port());
                    }
                }
            },
            buf -> {
                boolean off = buf.readBoolean();
                int n = Math.min(64, buf.readVarInt());
                List<CablePath.Point> pts = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    BlockPos cell = buf.readBlockPos();
                    Direction face = Direction.from3DDataValue(buf.readByte());
                    int u = Math.max(0, Math.min(15, buf.readByte())), v = Math.max(0, Math.min(15, buf.readByte()));
                    BlockPos dev = null;
                    int port = -1;
                    if (buf.readBoolean()) {
                        dev = buf.readBlockPos();
                        port = buf.readVarInt();
                    }
                    pts.add(new CablePath.Point(cell, face, u, v, dev, port));
                }
                return new CableLayPayload(off, pts);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
