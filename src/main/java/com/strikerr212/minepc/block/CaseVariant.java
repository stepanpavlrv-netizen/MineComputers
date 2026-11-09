package com.strikerr212.minepc.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Корпуса-варианты: тот же системный блок, но в другом облике и с окном своей темы.
 * Модели собраны из воксельных эскизов (tools/assetgen/cases.py), габариты — для корпуса, смотрящего на север.
 */
public enum CaseVariant {
    RETRO_TOWER("case_retro_tower", 0xFFF4D4, CaseStyle.RETRO, null, false, 3, 0, 1, 13, 16, 15),
    RETRO_DESKTOP("case_retro_desktop", 0xFFF4D4, CaseStyle.RETRO, null, false, 1, 0, 1, 15, 5, 14),
    HOME80("case_home80", 0xBF9C79, CaseStyle.RETRO, null, true, 1, 0, 3, 15, 3, 12),
    OFFICE_TOWER("case_office_tower", 0x454850, CaseStyle.OFFICE, null, false, 4, 0, 1, 12, 14, 15),
    MINI("case_mini", 0xF2F7FF, CaseStyle.OFFICE, null, false, 4, 0, 4, 12, 5, 12),
    WORKSTATION("case_workstation", 0xF5F9FF, CaseStyle.OFFICE, null, false, 3, 0, 1, 13, 16, 15),
    GAMING("case_gaming", 0x292931, CaseStyle.GAMING, null, false, 2, 0, 1, 14, 16, 15),
    GAMING_WHITE("case_gaming_white", 0xF9FBFF, CaseStyle.GAMING, null, false, 2, 0, 1, 14, 16, 15),
    GAMING_CUBE("case_gaming_cube", 0x2D2D36, CaseStyle.GAMING, null, false, 2, 0, 2, 14, 12, 14),
    INDUSTRIAL("case_industrial", 0xA6BCC6, CaseStyle.INDUSTRIAL, null, false, 1, 0, 1, 15, 16, 15),
    SERVER("case_server", 0x404550, CaseStyle.SERVER, null, false, 0, 0, 0, 16, 16, 16),
    AIO("case_aio", 0xF9FBFF, CaseStyle.OFFICE, new Screen(2, 14, 2, 10, 8, 0), true, 1, 0, 2, 15, 15, 15),
    RACK_KVM("case_rack_kvm", 0x404550, CaseStyle.SERVER, new Screen(3, 13, 3, 4, 7, 0), true, 0, 0, 0, 16, 16, 16),
    CONSOLE("case_console", 0xA6BCC6, CaseStyle.INDUSTRIAL, new Screen(3, 13, 2, 5, 7.07f, 45), true, 0, 0, 0, 16, 15, 14);

    public final String id;
    /** Цвет основы корпуса (некрашеного): им же красится модель через tintindex 0. */
    public final int paintBase;
    public final CaseStyle style;
    /** Встроенный экран (моноблок, стойка с консолью, пульт) или null. */
    public final Screen screen;
    /** Встроенная клавиатура. */
    public final boolean keyboard;
    private final VoxelShape[] shapes = new VoxelShape[4];

    CaseVariant(String id, int paintBase, CaseStyle style, Screen screen, boolean keyboard, int x0, int y0, int z0, int x1, int y1, int z1) {
        this.id = id;
        this.paintBase = paintBase;
        this.style = style;
        this.screen = screen;
        this.keyboard = keyboard;
        // Север, восток, юг, запад — как повороты в blockstate (y = 0, 90, 180, 270)
        shapes[0] = Block.box(x0, y0, z0, x1, y1, z1);
        shapes[1] = Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
        shapes[2] = Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
        shapes[3] = Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
    }

    /**
     * Встроенный экран: u — от левого края блока, v — от верха (как видит зритель), z — верхний край экрана
     * у корпуса, смотрящего на север; height — высота по плоскости экрана; tilt — наклон назад, градусы.
     */
    public record Screen(float uL, float uR, float vT, float z, float height, float tilt) {
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
