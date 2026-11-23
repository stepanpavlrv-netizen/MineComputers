package com.strikerr212.minepc.test;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.lan.TowerLegBlock;
import com.strikerr212.minepc.lan.TowerSectionBlock;
import com.strikerr212.minepc.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Игровые тесты радиовышки: опоры широкой (выше 17 секций) вышки. */
@GameTestHolder(MineComputers.MODID)
@PrefixGameTestTemplate(false)
public class TowerGameTests {

    private static final BlockPos BASE = new BlockPos(4, 1, 4);

    private static void section(GameTestHelper h, int i) {
        TowerSectionBlock b = ModBlocks.TOWER_SECTION.get();
        BlockPos abs = h.absolutePos(BASE.above(i));
        h.getLevel().setBlockAndUpdate(abs, b.shaped(b.defaultBlockState(), h.getLevel(), abs));
    }

    private static int legs(GameTestHelper h, int i) {
        int n = 0;
        for (int[] d : TowerLegBlock.CELLS) {
            BlockState s = h.getBlockState(BASE.above(i).offset(d[0], 0, d[1]));
            if (s.getBlock() instanceof TowerLegBlock) n++;
        }
        return n;
    }

    private static String missing(GameTestHelper h, int i) {
        StringBuilder sb = new StringBuilder();
        for (int[] d : TowerLegBlock.CELLS) {
            BlockPos p = BASE.above(i).offset(d[0], 0, d[1]);
            BlockState s = h.getBlockState(p);
            if (!(s.getBlock() instanceof TowerLegBlock)) sb.append("; ").append(p.toShortString()).append(" = ").append(s);
        }
        return sb.toString();
    }

    /**
     * Вышка в 20 секций: у трёх нижних (над ними 17+ секций) — по 16 опор, у нижней — с основаниями; выше — ни одной.
     * Сняли 4 верхние — опоры исчезли. Камень на месте опоры мешает надстроить вышку до 18 секций.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void wideTowerLegs(GameTestHelper h) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
                for (int y = 1; y <= 21; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);   // в заготовке бывают барьеры
            }
        }
        for (int i = 0; i < 20; i++) section(h, i);
        h.startSequence()
                .thenWaitUntil(() -> {
                    for (int i = 0; i < 3; i++) h.assertTrue(legs(h, i) == 16, "у секции " + i + " опор " + legs(h, i) + " из 16" + missing(h, i));
                    h.assertTrue(legs(h, 3) == 0, "у секции 3 (над ней 16) не должно быть опор: " + legs(h, 3));
                    BlockState leg = h.getBlockState(BASE.offset(-2, 0, -2));
                    h.assertTrue(leg.getValue(TowerLegBlock.FOOT) && leg.getValue(TowerLegBlock.ABOVE) == 19,
                            "нижняя угловая опора: " + leg);
                })
                .thenExecute(() -> {
                    for (int i = 19; i >= 16; i--) h.setBlock(BASE.above(i), Blocks.AIR);
                })
                .thenWaitUntil(() -> {
                    for (int i = 0; i < 3; i++) h.assertTrue(legs(h, i) == 0, "после разборки у секции " + i + " осталось опор " + legs(h, i));
                })
                .thenExecute(() -> {
                    h.setBlock(BASE.offset(-2, 0, 0), Blocks.STONE);
                    section(h, 16);   // 17 секций — ещё 3×3, камень не мешает
                    h.assertTrue(TowerSectionBlock.blockedFor(h.getLevel(), h.absolutePos(BASE.above(16))) == null,
                            "17 секций: опоры не нужны, а камень мешает");
                    BlockPos blocked = TowerSectionBlock.blockedFor(h.getLevel(), h.absolutePos(BASE.above(17)));
                    h.assertTrue(h.absolutePos(BASE.offset(-2, 0, 0)).equals(blocked), "18 секций: должен мешать камень, а не " + blocked);
                })
                .thenSucceed();
    }
}
