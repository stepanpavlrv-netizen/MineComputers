package com.strikerr212.minepc.wire;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.lan.LanData;
import com.strikerr212.minepc.lan.LanDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Висящие провода (воздушные линии, патч-корды) не проходят сквозь блоки: блок, поставленный поперёк провисающего
 * пролёта, не ставится; а если блок оказался там иначе (поршень, выросло дерево, упал песок) — провод обрывается и
 * падает (кабель — в добычу).
 */
@EventBusSubscriber(modid = MineComputers.MODID)
public final class WireGuard {

    private WireGuard() {
    }

    /** Раз в сколько тиков проверять висящие пролёты. */
    private static final int PERIOD = 40;
    /**
     * Короче этого пролёт не проверяем, блоков: короткие висящие кусочки прокладка делает сама (провод
     * перешагивает ребро блока), их дуга нарисована с запасом и может задеть блок — это не перегородка.
     */
    private static final double MIN_SPAN = 1.5;
    /** У концов пролёта (у опор) точки не проверяем, блоков. */
    private static final double END_GAP = 0.4;

    /** Точки висящего отрезка i, которые проверяем (пусто — отрезок не проверяется). */
    private static List<Vec3> checked(WireLine l, int i) {
        Vec3 a = l.node(i), b = l.node(i + 1);
        if (!l.hang[i] || a.distanceTo(b) < MIN_SPAN) return List.of();
        List<Vec3> pts = CordShape.points(a, b), out = new ArrayList<>();
        for (int k = 1; k < pts.size(); k++) {
            for (int q = 0; q < 4; q++) {
                Vec3 p = pts.get(k - 1).lerp(pts.get(k), q / 4.0);
                if (p.distanceTo(a) > END_GAP && p.distanceTo(b) > END_GAP) out.add(p);
            }
        }
        return out;
    }

    /** Игрок (или кто-то ещё) ставит блок: если он перегородил бы висящий провод — не ставим. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        if (event.getPlacedBlock().getBlock() instanceof LanDevice) return;   // устройство на конце провода — можно
        for (WireLine l : LanData.get(level).linesInChunk(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4))) {
            if (!l.bounds().contains(Vec3.atCenterOf(pos)) || !crosses(level, l, pos, event.getPlacedBlock())) continue;
            event.setCanceled(true);
            if (event.getEntity() instanceof Player p) {
                p.displayClientMessage(Component.translatable("message.minepc.cord.in_way").withStyle(net.minecraft.ChatFormatting.RED), true);
            }
            return;
        }
    }

    @SubscribeEvent
    public static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % PERIOD != 13) return;
        LanData data = LanData.get(level);
        if (data.lines().isEmpty()) return;
        List<WireLine> broken = new ArrayList<>();
        for (WireLine l : data.lines()) {
            if (hangs(l) && blocker(level, l) != null) broken.add(l);
        }
        for (WireLine l : broken) {
            BlockPos at = blocker(level, l);
            data.removeLine(l.id);
            BlockPos drop = at == null ? BlockPos.containing(l.end(false)) : at;
            ItemStack s = new ItemStack(l.fiber ? com.strikerr212.minepc.registry.ModItems.FIBER.get() : com.strikerr212.minepc.registry.ModItems.CABLE.get(),
                    Math.min(64, l.meters()));
            Block.popResource(level, drop.above(), com.strikerr212.minepc.lan.CableLabels.named(s, l.label));
            level.playSound(null, drop, SoundEvents.LEASH_KNOT_BREAK, SoundSource.BLOCKS, 1f, 1f);
        }
    }

    private static boolean hangs(WireLine l) {
        for (int i = 0; i < l.hang.length; i++) if (l.hang[i] && l.node(i).distanceTo(l.node(i + 1)) >= MIN_SPAN) return true;
        return false;
    }

    /** Блок, сквозь который теперь проходит висящий пролёт провода (только в загруженных местах), или null. */
    public static BlockPos blocker(ServerLevel level, WireLine l) {
        for (int i = 0; i + 1 < l.nodes(); i++) {
            for (Vec3 p : checked(l, i)) {
                BlockPos cell = BlockPos.containing(p);
                if (cell.equals(l.devA) || cell.equals(l.devB) || !level.isLoaded(cell)) continue;
                if (inside(level, cell, level.getBlockState(cell), p)) return cell.immutable();
            }
        }
        return null;
    }

    /** Прошёл бы висящий пролёт провода сквозь блок state, поставленный в pos. */
    private static boolean crosses(ServerLevel level, WireLine l, BlockPos pos, BlockState state) {
        if (pos.equals(l.devA) || pos.equals(l.devB)) return false;
        AABB cell = new AABB(pos);
        for (int i = 0; i + 1 < l.nodes(); i++) {
            for (Vec3 p : checked(l, i)) if (cell.contains(p) && inside(level, pos, state, p)) return true;
        }
        return false;
    }

    private static boolean inside(ServerLevel level, BlockPos cell, BlockState s, Vec3 p) {
        if (s.isAir()) return false;
        VoxelShape shape = s.getCollisionShape(level, cell);
        if (shape.isEmpty()) return false;
        Vec3 local = p.subtract(cell.getX(), cell.getY(), cell.getZ());
        for (AABB box : shape.toAabbs()) if (box.inflate(0.02).contains(local)) return true;
        return false;
    }
}
