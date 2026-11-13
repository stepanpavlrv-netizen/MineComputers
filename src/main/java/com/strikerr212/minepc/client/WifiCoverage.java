package com.strikerr212.minepc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.lan.RouterBlockEntity;
import com.strikerr212.minepc.lan.WifiAdapterItem;
import com.strikerr212.minepc.lan.WifiSignal;
import com.strikerr212.minepc.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Карта покрытия Wi-Fi: с роутером, точкой доступа или Wi-Fi адаптером в руке на полу вокруг видны точки —
 * зелёные (сильный сигнал), жёлтые (слабее), красные (край зоны). Считается так же, как подключение компьютера
 * ({@link WifiSignal}): с адаптером — по его дальности, с роутером в руке — по радиусу роутеров.
 * Вверху экрана — сигнал там, где стоит игрок.
 */
@EventBusSubscriber(modid = MineComputers.MODID, value = Dist.CLIENT)
public final class WifiCoverage {

    private WifiCoverage() {
    }

    /** Сколько блоков вокруг игрока раскрашивать. */
    private static final int R = 16;

    private record Ap(BlockPos pos, int radius, int wallCost, String ssid) {
    }

    /** Точки на полу: x, y (верх пола), z и сигнал — по 4 числа. */
    private static int[] dots = new int[0];
    private static int dotCount;
    private static long computedAt = -100;
    private static BlockPos computedFrom;
    private static int hereSignal;
    private static String hereSsid = "";
    private static boolean active;

    /** Дальность адаптера в руке; 0 — в руке роутер (считаем по радиусу роутера); -1 — ничего подходящего. */
    private static int heldRange(Minecraft mc) {
        int best = -1;
        for (ItemStack s : new ItemStack[]{mc.player.getMainHandItem(), mc.player.getOffhandItem()}) {
            if (s.getItem() instanceof WifiAdapterItem a) best = Math.max(best, a.range());
            else if (s.is(ModItems.ROUTER.get()) || s.is(ModItems.ACCESS_POINT.get())) best = Math.max(best, 0);
        }
        return best;
    }

    private static List<Ap> routersNear(Level level, BlockPos at, int reach) {
        List<Ap> out = new ArrayList<>();
        int cr = (reach >> 4) + 1;
        int cx = at.getX() >> 4, cz = at.getZ() >> 4;
        for (int x = cx - cr; x <= cx + cr; x++) {
            for (int z = cz - cr; z <= cz + cr; z++) {
                if (!level.hasChunk(x, z)) continue;
                for (BlockEntity be : level.getChunk(x, z).getBlockEntities().values()) {
                    if (be instanceof RouterBlockEntity r && r.getBlockPos().distSqr(at) <= (double) (reach + R) * (reach + R)) {
                        out.add(new Ap(r.getBlockPos(), r.radius(), WifiSignal.wallCost(be.getBlockState()), r.ssid()));
                    }
                }
            }
        }
        return out;
    }

    /** Сигнал (лучший из точек раздачи) в блоке p; ssid[0] — чья сеть. */
    private static int signalAt(Level level, BlockPos p, List<Ap> aps, int adapterRange, String[] ssid) {
        int best = 0;
        for (Ap ap : aps) {
            int range = adapterRange > 0 ? Math.min(adapterRange, ap.radius) : ap.radius;
            double dist = Math.sqrt(p.distSqr(ap.pos));
            if (dist > range) continue;
            int s = WifiSignal.signal(dist, range, WifiSignal.walls(level, p, ap.pos), ap.wallCost);
            if (s > best) {
                best = s;
                if (ssid != null) ssid[0] = ap.ssid;
            }
        }
        return best;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        active = false;
        if (mc.player == null || mc.level == null) return;
        int range = heldRange(mc);
        if (range < 0) {
            dotCount = 0;
            computedFrom = null;
            return;
        }
        active = true;
        Level level = mc.level;
        BlockPos me = mc.player.blockPosition();
        long now = level.getGameTime();
        if (computedFrom != null && now - computedAt < 20 && me.distManhattan(computedFrom) < 2) return;
        computedAt = now;
        computedFrom = me.immutable();
        List<Ap> aps = routersNear(level, me, range > 0 ? range : 48);
        String[] ssid = {""};
        hereSignal = signalAt(level, me, aps, range, ssid);
        hereSsid = ssid[0];
        int[] out = new int[(2 * R + 1) * (2 * R + 1) * 4];
        int n = 0;
        if (!aps.isEmpty()) {
            for (int dx = -R; dx <= R; dx++) {
                for (int dz = -R; dz <= R; dz++) {
                    if (dx * dx + dz * dz > R * R) continue;
                    // Пол: сплошной верх, над ним — воздух (ищем от уровня головы вниз)
                    for (int dy = 1; dy >= -4; dy--) {
                        BlockPos floor = me.offset(dx, dy - 1, dz);
                        BlockPos above = floor.above();
                        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) continue;
                        if (level.getBlockState(above).isSolidRender(level, above)) continue;
                        int s = signalAt(level, above, aps, range, null);
                        if (s > 0) {
                            out[n++] = above.getX();
                            out[n++] = above.getY();
                            out[n++] = above.getZ();
                            out[n++] = s;
                        }
                        break;
                    }
                }
            }
        }
        dots = out;
        dotCount = n / 4;
    }

    private static int color(int signal) {
        return signal >= 60 ? 0x4CD964 : signal >= 35 ? 0xF2C94C : 0xE5534B;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !active || dotCount == 0) return;
        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        VertexConsumer q = buffers.getBuffer(XrayRenderTypes.FLAT);
        for (int i = 0; i < dotCount; i++) {
            int x = dots[4 * i], y = dots[4 * i + 1], z = dots[4 * i + 2], c = color(dots[4 * i + 3]);
            float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
            float yy = y + 0.02f, x0 = x + 0.32f, x1 = x + 0.68f, z0 = z + 0.32f, z1 = z + 0.68f;
            q.addVertex(m, x0, yy, z0).setColor(r, g, b, 0.8f);
            q.addVertex(m, x0, yy, z1).setColor(r, g, b, 0.8f);
            q.addVertex(m, x1, yy, z1).setColor(r, g, b, 0.8f);
            q.addVertex(m, x1, yy, z0).setColor(r, g, b, 0.8f);
        }
        pose.popPose();
        buffers.endBatch(XrayRenderTypes.FLAT);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.screen != null || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        Component text = hereSignal > 0
                ? Component.translatable("hud.minepc.wifi_here", hereSignal, hereSsid)
                : Component.translatable("hud.minepc.wifi_none");
        int w = mc.font.width(text);
        int x = (g.guiWidth() - w) / 2, y = 6;
        g.fill(x - 4, y - 3, x + w + 4, y + 10, 0xB0101216);
        g.fill(x - 4, y - 3, x - 2, y + 10, 0xFF000000 | (hereSignal > 0 ? color(hereSignal) : 0x6A707A));
        g.drawString(mc.font, text, x, y, 0xFFFFFF, false);
    }
}
