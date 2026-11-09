package com.strikerr212.minepc.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Форма блока для четырёх сторон: задаётся для смотрящего на север, остальные — поворотом, как в blockstate. */
public final class BoxShapes {

    private final VoxelShape[] shapes = {Shapes.empty(), Shapes.empty(), Shapes.empty(), Shapes.empty()};

    public BoxShapes add(double x0, double y0, double z0, double x1, double y1, double z1) {
        shapes[0] = Shapes.or(shapes[0], Block.box(x0, y0, z0, x1, y1, z1));
        shapes[1] = Shapes.or(shapes[1], Block.box(16 - z1, y0, x0, 16 - z0, y1, x1));
        shapes[2] = Shapes.or(shapes[2], Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0));
        shapes[3] = Shapes.or(shapes[3], Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0));
        return this;
    }

    public static BoxShapes of(double x0, double y0, double z0, double x1, double y1, double z1) {
        return new BoxShapes().add(x0, y0, z0, x1, y1, z1);
    }

    public VoxelShape get(Direction facing) {
        return shapes[switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        }];
    }
}
