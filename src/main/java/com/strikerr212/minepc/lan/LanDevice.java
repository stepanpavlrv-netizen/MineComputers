package com.strikerr212.minepc.lan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Блок, в который втыкается сетевой кабель: системный блок, коммутатор. */
public interface LanDevice {

    enum Kind { COMPUTER, SWITCH }

    Kind lanKind();

    /** Есть ли гнездо на этой стороне блока (face — сторона устройства, к которой подходит кабель). */
    boolean acceptsCable(BlockState state, Direction face);

    /** Скорость, бит/с (как в жизни); в игре делится на 800 → байт/с. */
    long lanBitsPerSecond(Level level, BlockPos pos, BlockState state);

    /** Стоечные коммутаторы рядом друг с другом работают как один. */
    default boolean joinsNeighbours() {
        return false;
    }

    /** Вид для схемы сети ({@link LanData#COMPUTER} и т.д.). */
    default int lanType(BlockState state) {
        if (lanKind() == Kind.COMPUTER) return LanData.COMPUTER;
        return joinsNeighbours() ? LanData.RACK_SWITCH : LanData.DESK_SWITCH;
    }
}
