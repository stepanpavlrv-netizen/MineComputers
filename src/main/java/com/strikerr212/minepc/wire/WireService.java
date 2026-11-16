package com.strikerr212.minepc.wire;

import com.strikerr212.minepc.lan.CableItem;
import com.strikerr212.minepc.lan.CableLabels;
import com.strikerr212.minepc.lan.LanData;
import com.strikerr212.minepc.network.WireLayPayload;
import com.strikerr212.minepc.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Провода 3.0 на сервере: проложить (продолжить, срастить), разрезать, снять. */
public final class WireService {

    private WireService() {
    }

    /** Дальше от игрока точки прокладки не бывают, блоков. */
    private static final int REACH = 96;
    /** Предел перебора на один участок пути (у сервера — с запасом против призрака). */
    public static final int SEARCH = 60_000;

    private static boolean fail(Player p, String key, Object... args) {
        if (p != null) p.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), true);
        return false;
    }

    private static boolean far(ServerPlayer p, double x, double y, double z) {
        return p.position().distanceToSqr(x, y, z) > (double) REACH * REACH;
    }

    private static boolean checkAnchor(ServerPlayer player, ServerLevel level, LanData data, WirePlan.Anchor a, boolean fiber) {
        if (a == null) return true;
        switch (a.kind()) {
            case WirePlan.SOCKET -> {
                if (far(player, a.dev().getX(), a.dev().getY(), a.dev().getZ())) return false;
                if (!WireSockets.socketFree(level, a.dev(), a.port())) return fail(player, "message.minepc.draw.socket_busy");
            }
            case WirePlan.END -> {
                WireLine l = data.line(a.line());
                if (l == null || !l.free(a.b())) return fail(player, "message.minepc.wire.end_gone");
                if (l.fiber != fiber) return fail(player, "message.minepc.wire.kind_mismatch");
                Vec3 e = l.end(a.b());
                if (far(player, e.x, e.y, e.z)) return false;
            }
            default -> {
                if (far(player, a.x() / 16.0, a.y() / 16.0, a.z() / 16.0)) return false;
            }
        }
        return true;
    }

    public static void lay(ServerPlayer player, WireLayPayload req) {
        InteractionHand hand = req.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof CableItem cable)) return;
        ServerLevel level = player.serverLevel();
        LanData data = LanData.get(level);
        boolean fiber = cable.fiber();
        for (int[] c : req.clicks()) if (far(player, c[0] / 16.0, c[1] / 16.0, c[2] / 16.0)) return;
        if (!checkAnchor(player, level, data, req.from(), fiber) || !checkAnchor(player, level, data, req.to(), fiber)) return;
        WirePlan.Anchor from = req.from(), to = req.to();
        if (to != null && from.kind() == WirePlan.END && to.kind() == WirePlan.END && from.line() == to.line()) {
            fail(player, "message.minepc.wire.same_line");
            return;
        }
        if (to != null && from.kind() == WirePlan.SOCKET && to.kind() == WirePlan.SOCKET && from.dev().equals(to.dev()) && from.port() == to.port()) return;
        WirePlan.Result r = WirePlan.build(level, WirePlan.Lines.of(data), fiber, from, req.clicks(), to, SEARCH);
        if (!r.ok()) {
            fail(player, r.problem() == null ? "message.minepc.wire.no_path" : r.problem());
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        if (!creative && stack.getCount() < r.meters()) {
            fail(player, "message.minepc.route.short", r.meters());
            return;
        }
        // Собрать провод целиком: [старый провод до стыка] + новый путь + [провод, к которому приросли]
        WireLine base = from.kind() == WirePlan.END ? data.line(from.line()) : null;
        if (base != null && !from.b()) base = base.reversed();
        WireLine tail = to != null && to.kind() == WirePlan.END ? data.line(to.line()) : null;
        if (tail != null && to.b()) tail = tail.reversed();
        List<int[]> nodes = new ArrayList<>();
        List<Boolean> hang = new ArrayList<>();
        if (base != null) {
            for (int i = 0; i < base.nodes(); i++) {
                nodes.add(new int[]{base.xs[i], base.ys[i], base.zs[i]});
                if (i > 0) hang.add(base.hang[i - 1]);
            }
        }
        for (int i = base != null ? 1 : 0; i < r.xs().length; i++) {
            nodes.add(new int[]{r.xs()[i], r.ys()[i], r.zs()[i]});
            if (nodes.size() > 1) hang.add(i > 0 ? r.hang()[i - 1] : false);
        }
        if (tail != null) {
            for (int i = 1; i < tail.nodes(); i++) {
                nodes.add(new int[]{tail.xs[i], tail.ys[i], tail.zs[i]});
                hang.add(tail.hang[i - 1]);
            }
        }
        if (nodes.size() > WireLine.MAX_NODES) {
            fail(player, "message.minepc.wire.too_long");
            return;
        }
        int n = nodes.size();
        int[] xs = new int[n], ys = new int[n], zs = new int[n];
        boolean[] hh = new boolean[n - 1];
        for (int i = 0; i < n; i++) {
            xs[i] = nodes.get(i)[0];
            ys[i] = nodes.get(i)[1];
            zs[i] = nodes.get(i)[2];
            if (i < n - 1) hh[i] = hang.get(i);
        }
        BlockPos devA = base != null ? base.devA : from.kind() == WirePlan.SOCKET ? from.dev() : null;
        int portA = base != null ? base.portA : from.kind() == WirePlan.SOCKET ? from.port() : -1;
        BlockPos devB = tail != null ? tail.devB : to != null && to.kind() == WirePlan.SOCKET ? to.dev() : null;
        int portB = tail != null ? tail.portB : to != null && to.kind() == WirePlan.SOCKET ? to.port() : -1;
        long id = base != null ? base.id : tail != null ? tail.id : data.newLineId();
        int color = base != null ? base.color : tail != null ? tail.color : cable.colorFor(player, hand);
        String label = base != null && !base.label.isEmpty() ? base.label : tail != null && !tail.label.isEmpty() ? tail.label : CableLabels.of(stack);
        WireLine line = new WireLine(id, fiber, color, label, xs, ys, zs, hh, devA, portA, devB, portB);
        if (base != null && tail != null) data.removeLine(tail.id);
        data.putLine(line);
        if (!creative) stack.shrink(r.meters());
        BlockPos at = BlockPos.containing(line.end(true));
        level.playSound(null, at, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1f, 1.1f);
        if (devB != null || (base == null && devA != null && to == null)) {
            level.playSound(null, at, com.strikerr212.minepc.registry.ModSounds.LAN_PLUG_IN.get(), SoundSource.BLOCKS, 0.8f, 1f);
        }
        player.displayClientMessage(Component.translatable(base != null || tail != null ? "message.minepc.wire.grown" : "message.minepc.wire.done",
                r.meters(), line.meters()), true);
    }

    /**
     * Проложить новый провод без игрока (сцены CI, проверки): путь ищется так же, как у игрока. Концы — гнёзда или
     * точки (не свободные концы других проводов). Возвращает провод или null (пути нет — причина в журнал не идёт).
     */
    public static WireLine layDirect(ServerLevel level, boolean fiber, int color, String label, WirePlan.Anchor from,
                                     List<int[]> clicks, WirePlan.Anchor to) {
        LanData data = LanData.get(level);
        for (WirePlan.Anchor a : new WirePlan.Anchor[]{from, to}) {
            if (a != null && a.kind() == WirePlan.SOCKET && !WireSockets.socketFree(level, a.dev(), a.port())) return null;
        }
        WirePlan.Result r = WirePlan.build(level, WirePlan.Lines.of(data), fiber, from, clicks, to, SEARCH);
        if (!r.ok()) return null;
        BlockPos devA = from.kind() == WirePlan.SOCKET ? from.dev() : null;
        BlockPos devB = to != null && to.kind() == WirePlan.SOCKET ? to.dev() : null;
        WireLine line = new WireLine(data.newLineId(), fiber, color, label, r.xs(), r.ys(), r.zs(), r.hang(),
                devA, devA != null ? from.port() : -1, devB, devB != null ? to.port() : -1);
        data.putLine(line);
        return line;
    }

    // ======================= Кусачки =======================

    /** На какой провод смотрит игрок: провод, номер отрезка, точка (блоки) и расстояние; null — ни на какой. */
    public record Hit(WireLine line, int segment, Vec3 point, double dist) {
    }

    public static Hit pick(Iterable<WireLine> lines, Vec3 eye, Vec3 look, double reach) {
        Hit best = null;
        for (WireLine l : lines) {
            if (l.bounds().inflate(0.3).clip(eye, eye.add(look.scale(reach))).isEmpty() && !l.bounds().contains(eye)) continue;
            for (int i = 0; i + 1 < l.nodes(); i++) {
                Vec3 a = l.node(i), b = l.node(i + 1);
                if (l.hang[i]) {
                    // висящий отрезок — по точкам дуги
                    List<Vec3> pts = com.strikerr212.minepc.wire.CordShape.points(a, b);
                    for (int k = 1; k < pts.size(); k++) best = closer(best, l, i, pts.get(k - 1), pts.get(k), eye, look, reach, 0.2);
                } else {
                    best = closer(best, l, i, a, b, eye, look, reach, 0.12);
                }
            }
        }
        return best;
    }

    private static Hit closer(Hit best, WireLine l, int seg, Vec3 a, Vec3 b, Vec3 eye, Vec3 look, double reach, double tol) {
        // ближайшие точки луча eye + t·look и отрезка a + s·(b - a)
        Vec3 u = look, v = b.subtract(a), w = eye.subtract(a);
        double A = u.dot(u), B = u.dot(v), C = v.dot(v), D = u.dot(w), E = v.dot(w);
        double den = A * C - B * B;
        double s = den < 1e-9 ? 0 : Math.max(0, Math.min(1, (A * E - B * D) / den));
        double t = Math.max(0, (B * s - D) / A);
        if (t > reach) return best;
        Vec3 pr = eye.add(u.scale(t)), ps = a.add(v.scale(s));
        double d = pr.distanceTo(ps);
        if (d > tol) return best;
        if (best != null && best.dist() <= t) return best;
        return new Hit(l, seg, ps, t);
    }

    /** Провод под прицелом игрока на сервере (блок ближе провода — тогда null). */
    public static Hit aimed(ServerPlayer player) {
        LanData data = LanData.get(player.serverLevel());
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1);
        double reach = player.blockInteractionRange() + 0.5;
        List<WireLine> near = new ArrayList<>();
        int cx = player.blockPosition().getX() >> 4, cz = player.blockPosition().getZ() >> 4;
        for (int x = cx - 1; x <= cx + 1; x++) for (int z = cz - 1; z <= cz + 1; z++) {
            for (WireLine l : data.linesInChunk(net.minecraft.world.level.ChunkPos.asLong(x, z))) if (!near.contains(l)) near.add(l);
        }
        Hit hit = pick(near, eye, look, reach);
        if (hit == null) return null;
        return visible(player, hit, reach);
    }

    /**
     * Провод под прицелом виден, если блок не ближе него — или провод спрятан в том самом блоке, на который смотрят
     * (маскировка «провод в стене», {@link WireMask}).
     */
    private static Hit visible(net.minecraft.world.entity.player.Player player, Hit hit, double reach) {
        var block = player.pick(reach, 1f, false);
        if (block.getType() == net.minecraft.world.phys.HitResult.Type.MISS || block.getLocation().distanceTo(player.getEyePosition()) + 0.15 >= hit.dist()) return hit;
        if (block instanceof net.minecraft.world.phys.BlockHitResult bh && bh.getBlockPos().equals(BlockPos.containing(hit.point()))
                && WireMask.inside(player.level(), hit.point())) return hit;
        return null;
    }

    /** Провод под прицелом у игрока (по его копии проводов; блок ближе провода — null). */
    public static Hit aimedClient(net.minecraft.world.entity.player.Player player) {
        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange() + 0.5;
        Hit hit = pick(WireStore.all(), eye, player.getViewVector(1), reach);
        if (hit == null) return null;
        return visible(player, hit, reach);
    }

    /** Бирка: подписать провод под прицелом (пустое имя — снять подпись). false — провода под прицелом нет. */
    public static boolean relabel(ServerPlayer player, String label) {
        Hit hit = aimed(player);
        if (hit == null) return false;
        WireLine l = hit.line();
        String name = label == null ? "" : label.strip();
        if (name.length() > LanData.MAX_LABEL) name = name.substring(0, LanData.MAX_LABEL);
        LanData data = LanData.get(player.serverLevel());
        data.putLine(new WireLine(l.id, l.fiber, l.color, name, l.xs, l.ys, l.zs, l.hang, l.devA, l.portA, l.devB, l.portB));
        player.serverLevel().playSound(null, BlockPos.containing(hit.point()), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.7f, 1.3f);
        player.displayClientMessage(name.isEmpty() ? Component.translatable("message.minepc.wire.unlabeled")
                : Component.translatable("message.minepc.wire.labeled", name), true);
        return true;
    }

    /** ПКМ кусачками в проводе: разрезать в этом месте или (shift) снять целиком. false — провода под прицелом нет. */
    public static boolean cut(ServerPlayer player, boolean whole) {
        ServerLevel level = player.serverLevel();
        LanData data = LanData.get(level);
        Hit hit = aimed(player);
        if (hit == null) return false;
        WireLine l = hit.line();
        BlockPos at = BlockPos.containing(hit.point());
        if (whole) {
            data.removeLine(l.id);
            give(player, l.fiber, l.meters(), l.label);
            level.playSound(null, at, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 0.8f, 1.4f);
            player.displayClientMessage(Component.translatable("message.minepc.wire.removed", l.meters()), true);
            return true;
        }
        WireLine[] parts = split(l, hit.segment(), hit.point(), data.newLineId());
        if (parts[0] == null && parts[1] == null) {
            data.removeLine(l.id);
        } else {
            if (parts[0] != null) data.putLine(parts[0]);
            else data.removeLine(l.id);
            if (parts[1] != null) data.putLine(parts[1]);
        }
        level.playSound(null, at, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 0.8f, 1.6f);
        player.displayClientMessage(Component.translatable("message.minepc.wire.cut"), true);
        return true;
    }

    /** Разрезать провод в точке (для проверок без прицела). */
    public static void cutAt(ServerLevel level, long id, int seg, Vec3 p) {
        LanData data = LanData.get(level);
        WireLine l = data.line(id);
        if (l == null) return;
        WireLine[] parts = split(l, seg, p, data.newLineId());
        if (parts[0] != null) data.putLine(parts[0]);
        else data.removeLine(l.id);
        if (parts[1] != null) data.putLine(parts[1]);
    }

    /** Разрезать провод на отрезке seg в точке p: две части со свободными концами у разреза (null — часть слишком мала). */
    static WireLine[] split(WireLine l, int seg, Vec3 p, long newId) {
        int ux = (int) Math.round(p.x * WireLine.UNITS), uy = (int) Math.round(p.y * WireLine.UNITS), uz = (int) Math.round(p.z * WireLine.UNITS);
        int ax = l.xs[seg], ay = l.ys[seg], az = l.zs[seg], bx = l.xs[seg + 1], by = l.ys[seg + 1], bz = l.zs[seg + 1];
        double len = Math.sqrt((double) (bx - ax) * (bx - ax) + (double) (by - ay) * (by - ay) + (double) (bz - az) * (bz - az));
        if (len < 1) return new WireLine[]{null, null};
        double t = ((ux - ax) * (double) (bx - ax) + (uy - ay) * (double) (by - ay) + (uz - az) * (double) (bz - az)) / (len * len);
        // зазор у разреза — по пикселю в обе стороны; точки — в центрах пикселей, как у всех узлов пути
        double gap = 2 / len;
        double t1 = Math.max(0, t - gap), t2 = Math.min(1, t + gap);
        int[] c1 = snap(ax + (bx - ax) * t1, ay + (by - ay) * t1, az + (bz - az) * t1);
        int[] c2 = snap(ax + (bx - ax) * t2, ay + (by - ay) * t2, az + (bz - az) * t2);
        WireLine left = part(l, 0, seg, c1, true, l.id);
        WireLine right = part(l, seg + 1, l.nodes() - 1, c2, false, newId);
        return new WireLine[]{left, right};
    }

    private static int[] snap(double x, double y, double z) {
        return new int[]{(int) Math.floor(x / 2) * 2 + 1, (int) Math.floor(y / 2) * 2 + 1, (int) Math.floor(z / 2) * 2 + 1};
    }

    /** Часть провода: узлы from..to и точка разреза cut — в конце (atEnd) или в начале. */
    private static WireLine part(WireLine l, int from, int to, int[] cut, boolean atEnd, long id) {
        List<int[]> nodes = new ArrayList<>();
        List<Boolean> hang = new ArrayList<>();
        if (!atEnd) nodes.add(cut);
        for (int i = from; i <= to; i++) {
            if (!nodes.isEmpty()) hang.add(i == from ? l.hang[Math.max(0, from - 1)] : l.hang[i - 1]);
            nodes.add(new int[]{l.xs[i], l.ys[i], l.zs[i]});
        }
        if (atEnd) {
            hang.add(l.hang[Math.min(l.hang.length - 1, to)]);
            nodes.add(cut);
        }
        // убрать совпадающие соседние узлы
        for (int i = nodes.size() - 1; i > 0; i--) {
            int[] a = nodes.get(i - 1), b = nodes.get(i);
            if (a[0] == b[0] && a[1] == b[1] && a[2] == b[2]) {
                nodes.remove(i);
                hang.remove(i - 1);
            }
        }
        if (nodes.size() < 2) return null;
        int n = nodes.size();
        int[] xs = new int[n], ys = new int[n], zs = new int[n];
        boolean[] hh = new boolean[n - 1];
        for (int i = 0; i < n; i++) {
            xs[i] = nodes.get(i)[0];
            ys[i] = nodes.get(i)[1];
            zs[i] = nodes.get(i)[2];
            if (i < n - 1) hh[i] = hang.get(i);
        }
        WireLine out = new WireLine(id, l.fiber, l.color, l.label, xs, ys, zs, hh,
                atEnd ? l.devA : null, atEnd ? l.portA : -1, atEnd ? null : l.devB, atEnd ? -1 : l.portB);
        return out.length() < 2 / 16.0 && out.devA == null && out.devB == null ? null : out;
    }

    /** Вернуть кабель игроку (в креативе — ничего). */
    public static void give(ServerPlayer player, boolean fiber, int meters, String label) {
        if (player.getAbilities().instabuild) return;
        while (meters > 0) {
            int n = Math.min(64, meters);
            meters -= n;
            ItemStack s = new ItemStack(fiber ? ModItems.FIBER.get() : ModItems.CABLE.get(), n);
            if (!label.isEmpty()) s.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(label));
            if (!player.getInventory().add(s)) player.drop(s, false);
        }
    }
}
