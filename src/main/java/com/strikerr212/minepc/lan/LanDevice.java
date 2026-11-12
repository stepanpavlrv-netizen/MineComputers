package com.strikerr212.minepc.lan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/** Блок, в который втыкается сетевой кабель: системный блок, коммутатор, роутер, патч-панель, радиомодем. */
public interface LanDevice {

    enum Kind { COMPUTER, SWITCH }

    /**
     * Гнездо на модели устройства. Координаты — в пикселях самого блока, как он стоит в мире:
     * face — сторона блока; along — где гнездо вдоль этой стороны (x у северной и южной стороны, z у западной и
     * восточной; начало полоски шириной 1); height — высота (начало полоски); depth — насколько лицевая плоскость
     * устройства утоплена от края блока (у настольного коммутатора 4: он не во весь блок).
     */
    record Socket(Direction face, float along, float height, float depth, boolean copper, boolean fiber) {
        public boolean accepts(boolean fiberWire) {
            return fiberWire ? fiber : copper;
        }
    }

    Kind lanKind();

    /** Есть ли гнездо на этой стороне блока (face — сторона устройства, к которой подходит кабель). */
    boolean acceptsCable(BlockState state, Direction face);

    /** Гнездо для оптоволокна на этой стороне (есть только у стоечного коммутатора и патч-панели). */
    default boolean acceptsFiber(BlockState state, Direction face) {
        return false;
    }

    /**
     * Гнёзда устройства по порядку — это и номера портов (порт 1 — первое). По умолчанию: по одному гнезду
     * посередине каждой стороны, к которой можно подвести кабель, на высоте чуть ниже середины корпуса.
     */
    default List<Socket> sockets(BlockState state) {
        List<Socket> out = new ArrayList<>();
        AABB box = bounds(state);
        float h = (float) (box.minY * 16 + (box.maxY - box.minY) * 16 * 0.4);
        for (Direction f : Direction.Plane.HORIZONTAL) {
            boolean copper = acceptsCable(state, f), fiber = acceptsFiber(state, f);
            if (!copper && !fiber) continue;
            out.add(onBox(box, f, h, copper, fiber));
        }
        return out;
    }

    /** Гнездо посередине стороны f коробки box на высоте h. */
    static Socket onBox(AABB box, Direction f, float h, boolean copper, boolean fiber) {
        float along = (float) (f.getAxis() == Direction.Axis.Z ? (box.minX + box.maxX) * 8 : (box.minZ + box.maxZ) * 8) - 0.5f;
        float depth = (float) switch (f) {
            case NORTH -> box.minZ * 16;
            case SOUTH -> 16 - box.maxZ * 16;
            case WEST -> box.minX * 16;
            default -> 16 - box.maxX * 16;
        };
        return new Socket(f, along, (float) Math.floor(h), depth, copper, fiber);
    }

    /** Габарит модели (по форме блока), в долях блока. */
    static AABB bounds(BlockState state) {
        VoxelShape s = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return s.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : s.bounds();
    }

    /**
     * Гнездо, заданное на модели «лицом на север» (как в редакторе моделей): x и y — левый нижний пиксель гнезда,
     * back — на задней стороне (иначе на лицевой), depth — утопленность от края. Поворачивается вслед за блоком.
     */
    static Socket local(Direction facing, boolean back, float x, float y, float depth, boolean copper, boolean fiber) {
        float cx = x + 0.5f, cz = back ? 16 - depth : depth;
        float wx, wz;
        switch (facing) {
            case EAST -> { wx = 16 - cz; wz = cx; }
            case SOUTH -> { wx = 16 - cx; wz = 16 - cz; }
            case WEST -> { wx = cz; wz = 16 - cx; }
            default -> { wx = cx; wz = cz; }
        }
        Direction face = back ? facing.getOpposite() : facing;
        float along = (face.getAxis() == Direction.Axis.Z ? wx : wz) - 0.5f;
        return new Socket(face, along, y, depth, copper, fiber);
    }

    /** Скорость, бит/с (как в жизни); в игре делится на 800 → байт/с. */
    long lanBitsPerSecond(Level level, BlockPos pos, BlockState state);

    /** Стоечные коммутаторы рядом друг с другом работают как один. */
    default boolean joinsNeighbours() {
        return false;
    }

    /** Дополнительно для схемы (у радиомодема — дальность и канал). */
    default int lanExtra(Level level, BlockPos pos, BlockState state) {
        return 0;
    }

    /** Вид для схемы сети ({@link LanData#COMPUTER} и т.д.). */
    default int lanType(BlockState state) {
        if (lanKind() == Kind.COMPUTER) return LanData.COMPUTER;
        return joinsNeighbours() ? LanData.RACK_SWITCH : LanData.DESK_SWITCH;
    }

    /** Гнёзда устройства, стоящего в pos (пустой список — это не устройство). */
    static List<Socket> socketsAt(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof LanDevice d ? d.sockets(s) : List.of();
    }
}
