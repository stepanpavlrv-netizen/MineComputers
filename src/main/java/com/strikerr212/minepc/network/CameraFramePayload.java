package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Кадр для окна камеры (сервер → игрок): w×h точек RGB, сжатые (deflate). w = 0 — снимок ещё собирается. */
public record CameraFramePayload(BlockPos pos, int w, int h, byte[] data) implements CustomPacketPayload {

    public static final Type<CameraFramePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "camera_frame"));

    public static final StreamCodec<ByteBuf, CameraFramePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CameraFramePayload::pos,
            ByteBufCodecs.VAR_INT, CameraFramePayload::w,
            ByteBufCodecs.VAR_INT, CameraFramePayload::h,
            ByteBufCodecs.BYTE_ARRAY, CameraFramePayload::data,
            CameraFramePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static byte[] pack(int[] px) {
        byte[] raw = new byte[px.length * 3];
        for (int i = 0; i < px.length; i++) {
            raw[i * 3] = (byte) (px[i] >> 16);
            raw[i * 3 + 1] = (byte) (px[i] >> 8);
            raw[i * 3 + 2] = (byte) px[i];
        }
        java.util.zip.Deflater d = new java.util.zip.Deflater(java.util.zip.Deflater.BEST_SPEED);
        d.setInput(raw);
        d.finish();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(raw.length / 3);
        byte[] buf = new byte[8192];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return out.toByteArray();
    }

    public static int[] unpack(byte[] data, int n) {
        int[] px = new int[n];
        try {
            java.util.zip.Inflater inf = new java.util.zip.Inflater();
            inf.setInput(data);
            byte[] raw = new byte[n * 3];
            int off = 0;
            while (off < raw.length && !inf.finished()) {
                int k = inf.inflate(raw, off, raw.length - off);
                if (k == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                off += k;
            }
            inf.end();
            for (int i = 0; i < n; i++) px[i] = (raw[i * 3] & 255) << 16 | (raw[i * 3 + 1] & 255) << 8 | (raw[i * 3 + 2] & 255);
        } catch (java.util.zip.DataFormatException ignored) {
        }
        return px;
    }
}
