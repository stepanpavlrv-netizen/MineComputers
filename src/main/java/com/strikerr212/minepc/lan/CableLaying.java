package com.strikerr212.minepc.lan;

import com.strikerr212.minepc.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Сервер: проложить провод по точкам, которые поставил игрок (тот же путь, что он видел призраком). */
public final class CableLaying {

    private CableLaying() {
    }

    /** Самое дальнее расстояние от игрока до точки пути, блоков. */
    private static final int REACH = 96;

    public static void lay(ServerPlayer player, InteractionHand hand, List<CablePath.Point> points) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof CableItem cable) || points.size() < 2) return;
        ServerLevel level = player.serverLevel();
        for (CablePath.Point p : points) {
            if (p.cell().distSqr(player.blockPosition()) > (double) REACH * REACH) return;
            if (p.socket() && (p.dev().distManhattan(p.cell()) != 1 || !(level.getBlockState(p.dev()).getBlock() instanceof LanDevice))) return;
        }
        int color = cable.colorFor(player, hand);
        CablePath.Point a = points.get(0), b = points.get(points.size() - 1);
        // Два кронштейна без изломов — воздушная линия
        if (points.size() == 2 && a.socket() && b.socket() && level.getBlockState(a.dev()).getBlock() instanceof BracketBlock
                && level.getBlockState(b.dev()).getBlock() instanceof BracketBlock) {
            CableItem.aerial(level, player, stack, a.dev(), b.dev(), color);
            return;
        }
        // Гнёзда на концах должны быть свободны и подходить проводу (сервер проверяет сам)
        for (CablePath.Point p : List.of(a, b)) {
            if (!p.socket()) continue;
            List<LanDevice.Socket> socks = LanDevice.socketsAt(level, p.dev());
            if (p.port() < 0 || p.port() >= socks.size() || !socks.get(p.port()).accepts(cable.fiber())) {
                CableItem.fail(player, "message.minepc.draw.socket_kind");
                return;
            }
            if (!CablePath.socketFree(level, p.dev(), p.port())) {
                CableItem.fail(player, "message.minepc.draw.socket_busy");
                return;
            }
        }
        CablePath.Result r = CablePath.build(level, points, cable.fiber(), color);
        if (!r.ok()) {
            CablePath.Problem pr = r.problems().isEmpty() ? null : r.problems().get(0);
            CableItem.fail(player, pr == null ? "message.minepc.draw.no_path" : pr.reason());
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        int need = r.length();
        if (!creative && stack.getCount() < need) {
            CableItem.fail(player, "message.minepc.route.short", need);
            return;
        }
        // Сначала всё проверить, потом класть: если клетка занята чем-то — ничего не кладём
        for (CablePath.Piece p : r.pieces()) {
            if (!CablePath.open(level, p.cell())) {
                CableItem.fail(player, "message.minepc.draw.no_support");
                return;
            }
        }
        int label = CableLabels.id(level, stack);
        Set<BlockPos> touched = new LinkedHashSet<>();
        for (CablePath.Piece p : r.pieces()) {
            if (!(level.getBlockState(p.cell()).getBlock() instanceof CableBlock)) {
                level.setBlockAndUpdate(p.cell(), ModBlocks.CABLE.get().defaultBlockState());
            }
            if (level.getBlockEntity(p.cell()) instanceof CableBlockEntity be) {
                Wire w = p.wire(color, cable.fiber());
                w.label = label;
                if (be.fits(w)) be.add(w);
                touched.add(p.cell());
            }
        }
        for (int pass = 0; pass < 2; pass++) {
            for (BlockPos c : touched) {
                if (level.getBlockEntity(c) instanceof CableBlockEntity be) {
                    be.reconnect();
                    be.reconnectNeighbours();
                }
            }
        }
        if (!creative) stack.shrink(need);
        level.playSound(null, b.cell(), SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1f, 1.1f);
        if (b.socket()) level.playSound(null, b.cell(), com.strikerr212.minepc.registry.ModSounds.LAN_PLUG_IN.get(), SoundSource.BLOCKS, 0.8f, 1f);
        player.displayClientMessage(Component.translatable("message.minepc.draw.done", need), true);
    }
}
