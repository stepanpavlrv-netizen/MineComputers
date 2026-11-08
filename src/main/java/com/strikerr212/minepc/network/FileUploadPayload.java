package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Кусок файла, который игрок перетащил со своего компьютера в окно экрана (игрок → сервер).
 * Большие файлы приходят несколькими кусками: offset — откуда этот кусок, total — размер файла.
 */
public record FileUploadPayload(BlockPos casePos, String name, int offset, int total, byte[] data)
        implements CustomPacketPayload {

    public static final int CHUNK = 30000;
    public static final int MAX_FILE = 4 * 1024 * 1024;

    public static final Type<FileUploadPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "file_upload"));

    public static final StreamCodec<ByteBuf, FileUploadPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, FileUploadPayload::casePos,
            ByteBufCodecs.stringUtf8(255), FileUploadPayload::name,
            ByteBufCodecs.VAR_INT, FileUploadPayload::offset,
            ByteBufCodecs.VAR_INT, FileUploadPayload::total,
            ByteBufCodecs.byteArray(CHUNK), FileUploadPayload::data,
            FileUploadPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
