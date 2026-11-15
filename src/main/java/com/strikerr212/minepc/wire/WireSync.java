package com.strikerr212.minepc.wire;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.lan.LanData;
import com.strikerr212.minepc.network.WiresPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Рассылка проводов игрокам: при загрузке чанка у игрока — провода в нём, при изменении — тем, кто его видит. */
@EventBusSubscriber(modid = MineComputers.MODID)
public final class WireSync {

    private WireSync() {
    }

    @SubscribeEvent
    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        ServerLevel level = event.getLevel();
        List<WireLine> lines = LanData.get(level).linesInChunk(event.getPos().toLong());
        if (!lines.isEmpty()) PacketDistributor.sendToPlayer(event.getPlayer(), new WiresPayload(List.copyOf(lines), new long[0]));
    }

    private static Set<ServerPlayer> watchers(ServerLevel level, WireLine l) {
        Set<ServerPlayer> out = new HashSet<>();
        for (long c : l.chunks()) out.addAll(level.getChunkSource().chunkMap.getPlayers(new ChunkPos(c), false));
        return out;
    }

    /** Провод добавлен или заменён (old — прежний вид, если был: его тоже видели игроки). */
    public static void changed(ServerLevel level, WireLine old, WireLine now) {
        Set<ServerPlayer> to = watchers(level, now);
        if (old != null) {
            Set<ServerPlayer> before = watchers(level, old);
            before.removeAll(to);
            for (ServerPlayer p : before) PacketDistributor.sendToPlayer(p, new WiresPayload(List.of(), new long[]{old.id}));
        }
        for (ServerPlayer p : to) PacketDistributor.sendToPlayer(p, new WiresPayload(List.of(now), new long[0]));
    }

    public static void removed(ServerLevel level, WireLine old) {
        for (ServerPlayer p : watchers(level, old)) PacketDistributor.sendToPlayer(p, new WiresPayload(List.of(), new long[]{old.id}));
    }
}
