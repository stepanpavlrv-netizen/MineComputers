package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Доступ к камере из её окна. Игрок → сервер: поставить пароль (пустой — убрать) или, если password == null-маркер
 * {@link #ASK}, попросить журнал. Сервер → игрок: журнал входов (data: список T/A/C/Ok) и закрыта ли камера.
 */
public record CameraAccessPayload(BlockPos pos, String password, CompoundTag data) implements CustomPacketPayload {

    /** Не пароль, а просьба прислать журнал (такой пароль поставить нельзя: в нём управляющий символ). */
    public static final String ASK = "\u0001ask";

    public static final Type<CameraAccessPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "camera_access"));

    public static final StreamCodec<ByteBuf, CameraAccessPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CameraAccessPayload::pos,
            ByteBufCodecs.stringUtf8(256), CameraAccessPayload::password,
            ByteBufCodecs.COMPOUND_TAG, CameraAccessPayload::data,
            CameraAccessPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
