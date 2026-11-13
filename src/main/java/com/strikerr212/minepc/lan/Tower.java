package com.strikerr212.minepc.lan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Радиовышка рядом с модемом: сколько секций, какие антенны и есть ли громоотвод.
 *
 * <p>Модем ставится вплотную к нижней секции (в том числе по диагонали). Антенны: <b>штырь</b> на макушке — во все
 * стороны; <b>секторная панель</b> на боку любой секции — на свою четверть круга, быстрее штыря; <b>тарелка</b> на боку —
 * только на другую тарелку, повёрнутую навстречу, зато вдвое дальше и намного быстрее. Громоотвод (обычный, из игры)
 * на макушке, над штырём или вместо него, — защищает модем от молнии.
 *
 * @param sections секций в вышке
 * @param top      верхняя секция
 * @param sectors  стороны секторных панелей (биты Direction.get2DDataValue)
 * @param dishes   стороны тарелок (так же)
 */
public record Tower(BlockPos bottom, int sections, BlockPos top, boolean pin, int sectors, int dishes, boolean rod) {

    /** Дальность по высоте вышки: 4 секции — 300 блоков, 8 — 600, 12 — 1000, 20 — 1800, 32 — 3000. */
    public static int range(int sections) {
        if (sections <= 0) return 0;
        int r = sections * (70 + sections);
        return Math.min(3000, Math.max(50, Math.round(r / 50f) * 50));
    }

    public int range() {
        return range(sections);
    }

    public boolean antennas() {
        return pin || sectors != 0 || dishes != 0;
    }

    /** Высота антенн над модемом (для прямой видимости). */
    public int height(BlockPos modem) {
        return top.getY() - modem.getY() + (pin ? 2 : 1);
    }

    /** Нижняя секция вышки рядом с модемом (до 2 блоков — шкаф стоит снаружи широкой вышки) или null. */
    public static BlockPos bottomNear(BlockGetter level, BlockPos modem) {
        for (int r = 1; r <= 2; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    BlockPos p = modem.offset(dx, 0, dz);
                    BlockState s = level.getBlockState(p);
                    if (s.getBlock() instanceof TowerSectionBlock && s.getValue(TowerSectionBlock.HEIGHT) == 0) return p.immutable();
                }
            }
        }
        return null;
    }

    /** Вышка у модема или null. */
    public static Tower near(BlockGetter level, BlockPos modem) {
        BlockPos bottom = bottomNear(level, modem);
        return bottom == null ? null : scan(level, bottom);
    }

    /** Вышка, стоящая на секции bottom. */
    public static Tower scan(BlockGetter level, BlockPos bottom) {
        int n = 0, sectors = 0, dishes = 0;
        BlockPos p = bottom;
        while (n < TowerSectionBlock.MAX_SECTIONS && level.getBlockState(p).getBlock() instanceof TowerSectionBlock) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockState s = level.getBlockState(p.relative(d));
                if (s.getBlock() instanceof TowerAntennaBlock a && s.getValue(TowerAntennaBlock.FACING) == d) {
                    int bit = 1 << d.get2DDataValue();
                    if (a.dish()) dishes |= bit;
                    else sectors |= bit;
                }
            }
            n++;
            p = p.above();
        }
        BlockPos top = p.below();
        boolean pin = false, rod = false;
        for (int i = 0; i < 3; i++) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof TowerPinBlock) pin = true;
            else if (s.is(Blocks.LIGHTNING_ROD)) rod = true;
            else break;
            p = p.above();
        }
        return new Tower(bottom, n, top, pin, sectors, dishes, rod);
    }

    /** Вышка поменялась (секция, антенна, громоотвод): модемы у её подножия пересчитывают дальность. */
    public static void notifyModems(Level level, BlockPos anyPart) {
        BlockPos p = anyPart;
        for (int i = 0; i < 40; i++) {
            BlockState below = level.getBlockState(p.below());
            if (!(below.getBlock() instanceof TowerSectionBlock || below.getBlock() instanceof TowerPinBlock || below.is(Blocks.LIGHTNING_ROD))) break;
            p = p.below();
        }
        for (BlockPos q : new BlockPos[]{p, anyPart}) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos m = q.offset(dx, 0, dz);
                    if (level.getBlockState(m).getBlock() instanceof RadioBlock) LanManager.updateDevice(level, m);
                }
            }
        }
    }

    // ======================= Для схемы сети (Device.mask) =======================

    /** Бит 0 — вышка, 1 — штырь, 2..5 — секторы, 6..9 — тарелки, 10 — громоотвод, 11..16 — секций. */
    public int mask() {
        return 1 | (pin ? 2 : 0) | sectors << 2 | dishes << 6 | (rod ? 1 << 10 : 0) | Math.min(63, sections) << 11;
    }

    public static boolean isTower(int m) {
        return (m & 1) != 0;
    }

    public static boolean hasPin(int m) {
        return (m & 2) != 0;
    }

    public static int sectors(int m) {
        return (m >>> 2) & 15;
    }

    public static int dishes(int m) {
        return (m >>> 6) & 15;
    }

    public static boolean hasRod(int m) {
        return (m & (1 << 10)) != 0;
    }

    public static int sections(int m) {
        return (m >>> 11) & 63;
    }
}
