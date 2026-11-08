package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Ввод с клавиатуры (игрок → сервер).
 *
 * @param kind  {@link #CHAR} — набранный текст, {@link #KEY} — нажатая клавиша (имя: "enter", "up", "s"...),
 *              {@link #KEY_UP} — отпущенная клавиша, {@link #PASTE} — вставка, {@link #TERMINATE} — Ctrl+T,
 *              {@link #POWER} — кнопка питания
 * @param flags 1 — зажат Ctrl, 2 — зажат Shift
 */
public record TerminalInputPayload(BlockPos casePos, int kind, String text, int flags) implements CustomPacketPayload {

    public static final int CHAR = 0, KEY = 1, KEY_UP = 2, PASTE = 3, TERMINATE = 4, POWER = 5;
    public static final int MAX_TEXT = 4096;

    public static final Type<TerminalInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "terminal_input"));

    public static final StreamCodec<ByteBuf, TerminalInputPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TerminalInputPayload::casePos,
            ByteBufCodecs.VAR_INT, TerminalInputPayload::kind,
            ByteBufCodecs.stringUtf8(MAX_TEXT), TerminalInputPayload::text,
            ByteBufCodecs.VAR_INT, TerminalInputPayload::flags,
            TerminalInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
