package com.strikerr212.minepc.test;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.lan.TrayBlock;
import com.strikerr212.minepc.registry.ModBlocks;
import com.strikerr212.minepc.wire.WireLine;
import com.strikerr212.minepc.wire.WirePlan;
import com.strikerr212.minepc.wire.WireService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Игровые тесты проводов 3.0: кабельный лоток. */
@GameTestHolder(MineComputers.MODID)
@PrefixGameTestTemplate(false)
public class WireGameTests {

    /** Пиксели, по которым лежит провод в клетке cell (лежащие отрезки). */
    private static Set<Long> pixelsIn(WireLine l, BlockPos cell) {
        Set<Long> out = new HashSet<>();
        for (int i = 0; i + 1 < l.nodes(); i++) {
            if (l.hang[i]) continue;
            int ax = (l.xs[i] - 1) >> 1, ay = (l.ys[i] - 1) >> 1, az = (l.zs[i] - 1) >> 1;
            int bx = (l.xs[i + 1] - 1) >> 1, by = (l.ys[i + 1] - 1) >> 1, bz = (l.zs[i + 1] - 1) >> 1;
            int n = Math.abs(bx - ax) + Math.abs(by - ay) + Math.abs(bz - az);
            for (int k = 0; k <= n; k++) {
                int x = ax + Integer.signum(bx - ax) * k, y = ay + Integer.signum(by - ay) * k, z = az + Integer.signum(bz - az) * k;
                if (x >> 4 == cell.getX() && y >> 4 == cell.getY() && z >> 4 == cell.getZ()) out.add(BlockPos.asLong(x, y, z));
            }
        }
        return out;
    }

    /**
     * Ряд из трёх лотков на полу; три провода с пола через лоток на пол: лотки соединились в прямой ряд, провода
     * легли в желоб (над перекладинами), каждый — на свою дорожку.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void trayLanes(GameTestHelper h) {
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
        for (int x = 1; x <= 3; x++) {
            BlockPos p = h.absolutePos(new BlockPos(x, 1, 2));
            h.getLevel().setBlockAndUpdate(p, ModBlocks.TRAY.get().getStateForPlacement(
                    new net.minecraft.world.item.context.BlockPlaceContext(h.getLevel(), null, net.minecraft.world.InteractionHand.MAIN_HAND,
                            new net.minecraft.world.item.ItemStack(ModBlocks.TRAY.get()),
                            new net.minecraft.world.phys.BlockHitResult(p.getCenter().add(0, -0.5, 0), Direction.UP, p.below(), false))));
        }
        BlockPos mid = h.absolutePos(new BlockPos(2, 1, 2));
        var st = h.getLevel().getBlockState(mid);
        h.assertTrue(st.getBlock() instanceof TrayBlock, "Лоток не встал");
        h.assertTrue(TrayBlock.linkToward(st, Direction.EAST) == TrayBlock.Link.FLAT && TrayBlock.linkToward(st, Direction.WEST) == TrayBlock.Link.FLAT,
                "Лотки не соединились в ряд: " + st);
        BlockPos a = h.absolutePos(new BlockPos(0, 1, 2)), b = h.absolutePos(new BlockPos(4, 1, 2));
        List<Set<Long>> used = new java.util.ArrayList<>();
        for (int k = 0; k < 3; k++) {
            WireLine l = WireService.layDirect(h.getLevel(), false, k, "",
                    WirePlan.Anchor.pixel(new int[]{a.getX() * 16 + 4, a.getY() * 16, a.getZ() * 16 + 6 + k}), List.of(),
                    WirePlan.Anchor.pixel(new int[]{b.getX() * 16 + 12, b.getY() * 16, b.getZ() * 16 + 6 + k}));
            h.assertTrue(l != null, "Провод " + (k + 1) + " не проложился");
            Set<Long> px = pixelsIn(l, mid);
            h.assertTrue(!px.isEmpty(), "Провод " + (k + 1) + " прошёл мимо лотка");
            for (long p : px) {
                int y = BlockPos.getY(p) & 15;
                h.assertTrue(y == 1, "Провод " + (k + 1) + " не в желобе лотка (высота " + y + " px)");
            }
            for (Set<Long> o : used) {
                for (long p : px) h.assertTrue(!o.contains(p), "Провод " + (k + 1) + " лёг на чужую дорожку");
            }
            used.add(px);
        }
        h.succeed();
    }

    /** Лоток на полу у стены и лоток на стене над ним: нижний поднимается к верхнему, верхний опускается к нижнему. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void trayRise(GameTestHelper h) {
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
        for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(2, y, 3), Blocks.STONE_BRICKS);
        var t = ModBlocks.TRAY.get().defaultBlockState();
        h.setBlock(new BlockPos(2, 1, 2), t.setValue(TrayBlock.FACE, Direction.DOWN));
        h.setBlock(new BlockPos(2, 2, 2), t.setValue(TrayBlock.FACE, Direction.SOUTH));
        // соседи пересчитываются по обновлению — дёрнем оба
        for (int y = 1; y <= 2; y++) {
            BlockPos p = h.absolutePos(new BlockPos(2, y, 2));
            var st = h.getLevel().getBlockState(p);
            h.getLevel().setBlockAndUpdate(p, ((TrayBlock) st.getBlock()).connect(st, h.getLevel(), p));
        }
        var floor = h.getLevel().getBlockState(h.absolutePos(new BlockPos(2, 1, 2)));
        var wall = h.getLevel().getBlockState(h.absolutePos(new BlockPos(2, 2, 2)));
        h.assertTrue(TrayBlock.linkToward(floor, Direction.SOUTH) == TrayBlock.Link.BEND, "Лоток на полу не поднимается к стене: " + floor);
        h.assertTrue(TrayBlock.linkToward(wall, Direction.DOWN) == TrayBlock.Link.FLAT, "Лоток на стене не опускается к полу: " + wall);
        h.succeed();
    }

    /**
     * Маскировка «провод в стене»: на провод на полу ставят камень — провод цел, внутри камня спрятан, игрок,
     * глядящий на камень сверху, «видит» провод (кусачки, тестер, бирка).
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void wireMask(GameTestHelper h) {
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
        BlockPos a = h.absolutePos(new BlockPos(0, 1, 2)), b = h.absolutePos(new BlockPos(4, 1, 2));
        WireLine l = WireService.layDirect(h.getLevel(), false, 11, "",
                WirePlan.Anchor.pixel(new int[]{a.getX() * 16 + 4, a.getY() * 16, a.getZ() * 16 + 8}), List.of(),
                WirePlan.Anchor.pixel(new int[]{b.getX() * 16 + 12, b.getY() * 16, b.getZ() * 16 + 8}));
        h.assertTrue(l != null, "Провод не проложился");
        BlockPos mid = h.absolutePos(new BlockPos(2, 1, 2));
        h.getLevel().setBlockAndUpdate(mid, Blocks.STONE.defaultBlockState());
        var p = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        p.moveTo(mid.getX() + 0.5, mid.getY() + 1.2, mid.getZ() + 0.5, 0f, 90f);
        h.runAfterDelay(2, () -> {
            var now = com.strikerr212.minepc.lan.LanData.get(h.getLevel()).line(l.id);
            h.assertTrue(now != null && now.nodes() == l.nodes(), "Провод поменялся после камня: " + now);
            h.assertTrue(com.strikerr212.minepc.wire.WireMask.inside(h.getLevel(), new net.minecraft.world.phys.Vec3(mid.getX() + 0.5,
                    mid.getY() + 1 / 32.0, mid.getZ() + 0.5)), "Провод внутри камня не считается спрятанным");
            var hit = WireService.aimed(p);
            h.assertTrue(hit != null && hit.line().id == l.id, "Прицел сверху на камень не видит провод внутри");
            h.succeed();
        });
    }
}
