package com.strikerr212.minepc.lan;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.network.CameraTexturesPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Откуда сервер берёт текстуры для камер: с диска рядом с миром, а если там нет — просит у вошедшего игрока
 * (его игра собирает таблицу из своих ресурсов) и сохраняет.
 */
@EventBusSubscriber(modid = MineComputers.MODID)
public final class CameraTextureSync {

    private CameraTextureSync() {
    }

    /** Больше этого таблица не бывает (сжатая; обычно 0,3–1 МБ). */
    private static final int MAX_BYTES = 16 << 20;

    private static final Map<UUID, byte[][]> uploads = new HashMap<>();
    private static MinecraftServer server;

    private static Path file(MinecraftServer s) {
        return s.getWorldPath(LevelResource.ROOT).resolve("minepc_camera_textures.bin");
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        server = event.getServer();
        uploads.clear();
        BlockTextures.clear();
        BlockTextures.load(file(server));
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        uploads.clear();
        BlockTextures.clear();
        server = null;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!BlockTextures.ready() && event.getEntity() instanceof ServerPlayer p) {
            PacketDistributor.sendToPlayer(p, new CameraTexturesPayload(-1, 0, new byte[0]));
        }
    }

    /** Кусок таблицы от игрока; собралась целиком — ставим и сохраняем. */
    public static void chunk(ServerPlayer player, CameraTexturesPayload p) {
        if (BlockTextures.ready() || p.total() <= 0 || p.total() > MAX_BYTES / CameraTexturesPayload.CHUNK + 1
                || p.index() < 0 || p.index() >= p.total()) {
            return;
        }
        byte[][] parts = uploads.computeIfAbsent(player.getUUID(), k -> new byte[p.total()][]);
        if (parts.length != p.total()) {
            parts = new byte[p.total()][];
            uploads.put(player.getUUID(), parts);
        }
        parts[p.index()] = p.data();
        for (byte[] part : parts) if (part == null) return;
        uploads.remove(player.getUUID());
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (byte[] part : parts) all.writeBytes(part);
        if (BlockTextures.install(all.toByteArray()) && server != null) {
            BlockTextures.save(file(server));
            com.mojang.logging.LogUtils.getLogger().info("MineComputers: текстуры для камер получены от {} ({} КБ)",
                    player.getGameProfile().getName(), all.size() / 1024);
        }
    }
}
