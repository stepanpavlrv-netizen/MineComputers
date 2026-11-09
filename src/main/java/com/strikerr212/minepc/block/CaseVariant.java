package com.strikerr212.minepc.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Корпуса-варианты: тот же системный блок, но в другом облике и с окном своей темы.
 * Модели собраны из воксельных эскизов (tools/assetgen/cases.py), габариты — для корпуса, смотрящего на север.
 */
public enum CaseVariant {
    RETRO_TOWER("case_retro_tower", CaseStyle.RETRO, 3, 0, 1, 13, 16, 15),
    RETRO_DESKTOP("case_retro_desktop", CaseStyle.RETRO, 1, 0, 1, 15, 5, 14),
    HOME80("case_home80", CaseStyle.RETRO, 1, 0, 3, 15, 3, 12),
    OFFICE_TOWER("case_office_tower", CaseStyle.OFFICE, 4, 0, 1, 12, 14, 15),
    MINI("case_mini", CaseStyle.OFFICE, 4, 0, 4, 12, 5, 12),
    WORKSTATION("case_workstation", CaseStyle.OFFICE, 3, 0, 1, 13, 16, 15),
    GAMING("case_gaming", CaseStyle.GAMING, 2, 0, 1, 14, 16, 15),
    GAMING_WHITE("case_gaming_white", CaseStyle.GAMING, 2, 0, 1, 14, 16, 15),
    GAMING_CUBE("case_gaming_cube", CaseStyle.GAMING, 2, 0, 2, 14, 12, 14),
    INDUSTRIAL("case_industrial", CaseStyle.INDUSTRIAL, 1, 0, 1, 15, 16, 15),
    SERVER("case_server", CaseStyle.SERVER, 0, 0, 0, 16, 16, 16);

    public final String id;
    public final CaseStyle style;
    private final VoxelShape[] shapes = new VoxelShape[4];

    CaseVariant(String id, CaseStyle style, int x0, int y0, int z0, int x1, int y1, int z1) {
        this.id = id;
        this.style = style;
        // Север, восток, юг, запад — как повороты в blockstate (y = 0, 90, 180, 270)
        shapes[0] = Block.box(x0, y0, z0, x1, y1, z1);
        shapes[1] = Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
        shapes[2] = Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
        shapes[3] = Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
    }

    public VoxelShape shape(Direction facing) {
        return shapes[switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        }];
    }
}
