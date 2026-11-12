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
 *              {@link #POWER} — кнопка питания, {@link #REFRESH} — прислать экран целиком
 * @param flags 1 — зажат Ctrl, 2 — зажат Shift; для мыши — номер кнопки (1 левая, 2 правая, 3 средняя)
 *              или направление прокрутки (-1 вверх, 1 вниз)
 * @param x     клетка экрана по горизонтали (для мыши, с 1)
 * @param y     клетка экрана по вертикали (для мыши, с 1)
 */
public record TerminalInputPayload(com.strikerr212.minepc.block.HostRef host, int kind, String text, int flags, int x, int y) implements CustomPacketPayload {

    public static final int CHAR = 0, KEY = 1, KEY_UP = 2, PASTE = 3, TERMINATE = 4, POWER = 5, REFRESH = 6,
            MOUSE_DOWN = 7, MOUSE_DRAG = 8, MOUSE_UP = 9, MOUSE_SCROLL = 10;
    public static final int MAX_TEXT = 32000;
    /** В flags: клавишу нажали (не автоповтор) — пусть другие услышат стук. */
    public static final int FLAG_CLICK = 8;

    public static final Type<TerminalInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "terminal_input"));

    public static final StreamCodec<ByteBuf, TerminalInputPayload> STREAM_CODEC = StreamCodec.composite(
            com.strikerr212.minepc.block.HostRef.STREAM_CODEC, TerminalInputPayload::host,
            ByteBufCodecs.VAR_INT, TerminalInputPayload::kind,
            ByteBufCodecs.stringUtf8(MAX_TEXT), TerminalInputPayload::text,
            ByteBufCodecs.VAR_INT, TerminalInputPayload::flags,
            ByteBufCodecs.VAR_INT, TerminalInputPayload::x,
            ByteBufCodecs.VAR_INT, TerminalInputPayload::y,
            TerminalInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
