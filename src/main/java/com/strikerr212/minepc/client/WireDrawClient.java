package com.strikerr212.minepc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.lan.CableItem;
import com.strikerr212.minepc.lan.CablePath;
import com.strikerr212.minepc.lan.LanDevice;
import com.strikerr212.minepc.network.WireLayPayload;
import com.strikerr212.minepc.wire.LevelVoxels;
import com.strikerr212.minepc.wire.WireGeom;
import com.strikerr212.minepc.wire.WireLine;
import com.strikerr212.minepc.wire.WirePlan;
import com.strikerr212.minepc.wire.WireStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Прокладка провода 3.0 щелчками мотка. Первый щелчок — откуда (гнездо, свободный конец провода или место на
 * поверхности), дальше — точки пути; путь между ними ищется сам, вдоль блоков. Щелчок по гнезду или по свободному
 * концу другого провода — конец; повторный щелчок в то же место — оставить свободный конец. Shift + ПКМ — шаг назад.
 * Призрак считает тот же {@link WirePlan}, что и сервер.
 */
@EventBusSubscriber(modid = MineComputers.MODID, value = Dist.CLIENT)
public final class WireDrawClient {

    private WireDrawClient() {
    }

    private static WirePlan.Anchor from;
    private static final List<int[]> clicks = new ArrayList<>();
    private static InteractionHand hand = InteractionHand.MAIN_HAND;
    private static long lastClick = -100;

    private static String previewKey = "";
    private static long previewAt;
    private static WirePlan.Result preview;
    private static WirePlan.Anchor cursor;
    private static AABB cursorBox;
    private static final List<AABB> freeSockets = new ArrayList<>();
    private static final List<AABB> freeEnds = new ArrayList<>();
    private static long scanAt = -100;
    private static final List<Component> lines = new ArrayList<>();
    private static boolean holding;

    /** Предел перебора у призрака (сервер ищет с запасом). */
    private static final int SEARCH = 30_000;

    public static boolean active() {
        return from != null;
    }

    private static CableItem cableIn(InteractionHand h) {
        var p = Minecraft.getInstance().player;
        return p != null && p.getItemInHand(h).getItem() instanceof CableItem c ? c : null;
    }

    private static void say(String key, Object... args) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.displayClientMessage(Component.translatable(key, args), true);
    }

    /** Свободный конец провода нужного вида рядом с точкой (до 0,25 блока). */
    private static WirePlan.Anchor freeEndNear(Vec3 hit, boolean fiber) {
        WirePlan.Anchor best = null;
        double bestD = 0.25 * 0.25;
        for (WireLine l : WireStore.all()) {
            if (l.fiber != fiber) continue;
            for (int e = 0; e < 2; e++) {
                boolean b = e == 1;
                if (!l.free(b)) continue;
                double d = l.end(b).distanceToSqr(hit);
                if (d < bestD) {
                    bestD = d;
                    best = WirePlan.Anchor.end(l.id, b);
                }
            }
        }
        return best;
    }

    /** Что под прицелом: гнездо, свободный конец провода или место на поверхности. null — некуда. */
    private static WirePlan.Anchor anchorAt(Level level, BlockPos pos, Direction side, Vec3 hit, boolean fiber) {
        WirePlan.Anchor end = freeEndNear(hit, fiber);
        if (end != null) return end;
        if (level.getBlockState(pos).getBlock() instanceof LanDevice) {
            int port = CablePath.socketAt(level, pos, hit, side, fiber);
            if (port >= 0) return WirePlan.Anchor.socket(pos.immutable(), port);
        }
        int[] px = WireGeom.surfacePixel(hit, side);
        if (new LevelVoxels(level).solid(px[0], px[1], px[2])) return null;
        return WirePlan.Anchor.pixel(px);
    }

    /** ПКМ мотком по блоку. */
    public static void click(InteractionHand h, BlockPos pos, Direction side, Vec3 hit) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        CableItem cable = cableIn(h);
        if (cable == null) return;
        long now = mc.level.getGameTime();
        if (now - lastClick < 5) return;               // зажатая кнопка повторяет щелчок — это не новые точки
        lastClick = now;
        if (mc.player.isShiftKeyDown()) {
            undo();
            return;
        }
        if (from != null && h != hand) cancel();
        WirePlan.Anchor a = anchorAt(mc.level, pos, side, hit, cable.fiber());
        if (a == null) {
            say(mc.level.getBlockState(pos).getBlock() instanceof LanDevice ? "message.minepc.draw.no_free_socket" : "message.minepc.draw.no_support");
            return;
        }
        if (from == null) {
            hand = h;
            from = a;
            mc.player.playSound(SoundEvents.WOOL_PLACE, 0.5f, 1.5f);
            say(a.kind() == WirePlan.SOCKET ? "message.minepc.draw.start_socket" : a.kind() == WirePlan.END ? "message.minepc.wire.start_end"
                    : "message.minepc.draw.start", a.port() + 1);
            return;
        }
        if (a.kind() != WirePlan.PIXEL) {
            if (a.same(from)) return;
            finish(cable, a);
            return;
        }
        int[] p = {a.x(), a.y(), a.z()};
        int[] last = clicks.isEmpty() ? (from.kind() == WirePlan.PIXEL ? new int[]{from.x(), from.y(), from.z()} : null) : clicks.get(clicks.size() - 1);
        if (last != null && Math.abs(last[0] - p[0]) + Math.abs(last[1] - p[1]) + Math.abs(last[2] - p[2]) <= 2) {
            finish(cable, null);                       // повторный щелчок в то же место — свободный конец здесь
            return;
        }
        clicks.add(p);
        mc.player.playSound(SoundEvents.WOOL_PLACE, 0.4f, 1.8f);
    }

    /** ПКМ в воздух: Shift — шаг назад. */
    public static void air(InteractionHand h) {
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isShiftKeyDown() && mc.level != null && mc.level.getGameTime() - lastClick >= 5) {
            lastClick = mc.level.getGameTime();
            undo();
        }
    }

    private static void undo() {
        if (from == null) return;
        if (clicks.isEmpty()) {
            cancel();
            say("message.minepc.draw.cancel");
            return;
        }
        clicks.remove(clicks.size() - 1);
        say("message.minepc.draw.undo", clicks.size() + 1);
    }

    private static void cancel() {
        from = null;
        clicks.clear();
        preview = null;
        previewKey = "";
    }

    private static void finish(CableItem cable, WirePlan.Anchor to) {
        Minecraft mc = Minecraft.getInstance();
        if (to == null && clicks.isEmpty()) return;
        if (to != null && to.kind() == WirePlan.END && from.kind() == WirePlan.END && to.line() == from.line()) {
            say("message.minepc.wire.same_line");
            return;
        }
        WirePlan.Result r = WirePlan.build(mc.level, WireStore::get, cable.fiber(), from, clicks, to, SEARCH * 2);
        if (!r.ok()) {
            say(r.problem() == null ? "message.minepc.wire.no_path" : r.problem());
            if (to == null && !clicks.isEmpty()) clicks.remove(clicks.size() - 1);
            return;
        }
        PacketDistributor.sendToServer(new WireLayPayload(hand == InteractionHand.OFF_HAND, from, List.copyOf(clicks), to));
        cancel();
    }

    // ======================= Призрак и подсказки =======================

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        lines.clear();
        cursorBox = null;
        holding = false;
        if (mc.player == null || mc.level == null) {
            cancel();
            return;
        }
        CableItem cable = cableIn(from == null ? InteractionHand.MAIN_HAND : hand);
        if (cable == null && from == null) cable = cableIn(InteractionHand.OFF_HAND);
        if (cable == null) {
            cancel();
            freeSockets.clear();
            freeEnds.clear();
            return;
        }
        holding = true;
        Level level = mc.level;
        long now = level.getGameTime();
        if (now - scanAt >= 10) {
            scanAt = now;
            scan(level, mc.player.blockPosition(), cable.fiber());
        }
        cursor = null;
        if (mc.hitResult instanceof BlockHitResult bhr && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            cursor = anchorAt(level, bhr.getBlockPos(), bhr.getDirection(), bhr.getLocation(), cable.fiber());
            if (cursor != null) cursorBox = boxOf(level, cursor);
        }
        if (from == null) {
            lines.add(Component.translatable("hud.minepc.wire.hint_start").withStyle(s -> s.withColor(0x8FD3F0)));
            lines.add(Component.translatable("hud.minepc.wire.hint_start2").withStyle(s -> s.withColor(0x9097A1)));
            if (cursor != null && cursor.kind() == WirePlan.SOCKET) lines.add(Component.translatable("hud.minepc.draw.port", cursor.port() + 1).withStyle(s -> s.withColor(0x5BD47A)));
            if (cursor != null && cursor.kind() == WirePlan.END) lines.add(Component.translatable("hud.minepc.wire.continue").withStyle(s -> s.withColor(0xFFD84A)));
            return;
        }
        List<int[]> path = new ArrayList<>(clicks);
        WirePlan.Anchor to = null;
        if (cursor != null) {
            if (cursor.kind() == WirePlan.PIXEL) path.add(new int[]{cursor.x(), cursor.y(), cursor.z()});
            else if (!cursor.same(from)) to = cursor;
        }
        String key = key(path, to);
        if (!key.equals(previewKey) && now - previewAt >= 2) {
            previewKey = key;
            previewAt = now;
            preview = path.isEmpty() && to == null ? null : WirePlan.build(level, WireStore::get, cable.fiber(), from, path, to, SEARCH);
        }
        int have = mc.player.getAbilities().instabuild ? Integer.MAX_VALUE : mc.player.getItemInHand(hand).getCount();
        int len = preview == null ? 0 : preview.meters();
        lines.add(Component.translatable("hud.minepc.wire.status", len).withStyle(s -> s.withColor(len > have ? 0xE05555 : 0x8FD3F0)));
        if (len > have) lines.add(Component.translatable("message.minepc.route.short", len).withStyle(s -> s.withColor(0xE05555)));
        if (preview != null && !preview.ok() && preview.problem() != null) {
            lines.add(Component.translatable(preview.problem()).withStyle(s -> s.withColor(0xE05555)));
        } else if (to != null) {
            lines.add(Component.translatable(to.kind() == WirePlan.SOCKET ? "hud.minepc.draw.port_end" : "hud.minepc.wire.grow", to.port() + 1)
                    .withStyle(s -> s.withColor(0x5BD47A)));
        }
        lines.add(Component.translatable("hud.minepc.wire.hint").withStyle(s -> s.withColor(0x9097A1)));
        lines.add(Component.translatable("hud.minepc.draw.hint2").withStyle(s -> s.withColor(0x9097A1)));
    }

    private static String key(List<int[]> path, WirePlan.Anchor to) {
        StringBuilder b = new StringBuilder();
        for (int[] p : path) b.append(p[0]).append(',').append(p[1]).append(',').append(p[2]).append(';');
        if (to != null) b.append(to.kind()).append(to.dev()).append(to.port()).append(to.line()).append(to.b());
        return b.toString();
    }

    private static AABB boxOf(Level level, WirePlan.Anchor a) {
        switch (a.kind()) {
            case WirePlan.SOCKET -> {
                LanDevice.Socket so = WireGeom.socket(level, a.dev(), a.port());
                if (so == null) return null;
                int[] n = WireGeom.socketNode(a.dev(), so);
                Vec3 c = new Vec3(n[0] / 32.0, n[1] / 32.0, n[2] / 32.0);
                return new AABB(c, c).inflate(1.2 / 16);
            }
            case WirePlan.END -> {
                WireLine l = WireStore.get(a.line());
                if (l == null) return null;
                Vec3 c = l.end(a.b());
                return new AABB(c, c).inflate(1.5 / 16);
            }
            default -> {
                return new AABB(a.x() / 16.0, a.y() / 16.0, a.z() / 16.0, (a.x() + 1) / 16.0, (a.y() + 1) / 16.0, (a.z() + 1) / 16.0).inflate(0.01);
            }
        }
    }

    /** Свободные гнёзда подходящего вида и свободные концы проводов рядом (до 8 блоков). */
    private static void scan(Level level, BlockPos me, boolean fiber) {
        freeSockets.clear();
        freeEnds.clear();
        int cx = me.getX() >> 4, cz = me.getZ() >> 4;
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                if (!level.hasChunk(x, z)) continue;
                for (BlockEntity be : level.getChunk(x, z).getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (pos.distSqr(me) > 64 || !(be.getBlockState().getBlock() instanceof LanDevice)) continue;
                    List<LanDevice.Socket> socks = LanDevice.socketsAt(level, pos);
                    for (int i = 0; i < socks.size() && freeSockets.size() < 200; i++) {
                        if (socks.get(i).accepts(fiber) && CablePath.socketFree(level, pos, i)) {
                            AABB b = boxOf(level, WirePlan.Anchor.socket(pos, i));
                            if (b != null) freeSockets.add(b);
                        }
                    }
                }
            }
        }
        Vec3 eye = Vec3.atCenterOf(me);
        for (WireLine l : WireStore.all()) {
            if (l.fiber != fiber) continue;
            for (int e = 0; e < 2; e++) {
                if (!l.free(e == 1)) continue;
                Vec3 c = l.end(e == 1);
                if (c.distanceToSqr(eye) < 100) freeEnds.add(new AABB(c, c).inflate(1.5 / 16));
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !holding) return;
        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        if (preview != null && preview.xs().length >= 2) {
            boolean ok = preview.ok();
            VertexConsumer q = buffers.getBuffer(XrayRenderTypes.FLAT);
            for (int i = 0; i + 1 < preview.xs().length; i++) {
                Vec3 a = new Vec3(preview.xs()[i] / 32.0, preview.ys()[i] / 32.0, preview.zs()[i] / 32.0);
                Vec3 b = new Vec3(preview.xs()[i + 1] / 32.0, preview.ys()[i + 1] / 32.0, preview.zs()[i + 1] / 32.0);
                if (preview.hang()[i]) {
                    List<Vec3> pts = com.strikerr212.minepc.lan.CordShape.points(a, b);
                    for (int k = 1; k < pts.size(); k++) ghost(q, m, pts.get(k - 1), pts.get(k), ok);
                } else {
                    ghost(q, m, a, b, ok);
                }
            }
            buffers.endBatch(XrayRenderTypes.FLAT);
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        for (AABB b : freeSockets) LevelRenderer.renderLineBox(pose, vc, b, 0.35f, 1f, 0.5f, 0.9f);
        for (AABB b : freeEnds) LevelRenderer.renderLineBox(pose, vc, b, 1f, 0.85f, 0.3f, 0.9f);
        if (cursorBox != null) LevelRenderer.renderLineBox(pose, vc, cursorBox, 1f, 1f, 1f, 1f);
        if (preview != null && preview.stuck() != null) {
            int[] s = preview.stuck();
            LevelRenderer.renderLineBox(pose, vc, new AABB(s[0] / 16.0, s[1] / 16.0, s[2] / 16.0, (s[0] + 1) / 16.0, (s[1] + 1) / 16.0, (s[2] + 1) / 16.0).inflate(0.06),
                    1f, 0.3f, 0.3f, 1f);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    /** Отрезок призрака — полупрозрачная трубка (голубая; если путь не найден — красная). */
    private static void ghost(VertexConsumer q, Matrix4f m, Vec3 a, Vec3 b, boolean ok) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1e-10) return;
        Vec3 dn = d.normalize();
        Vec3 up = Math.abs(dn.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        double r = 1.3 / 32;
        Vec3 s1 = dn.cross(up).normalize().scale(r), s2 = dn.cross(s1).normalize().scale(r);
        a = a.subtract(dn.scale(r));
        b = b.add(dn.scale(r));
        Vec3[] c = {s1.add(s2), s1.subtract(s2), s1.scale(-1).subtract(s2), s1.scale(-1).add(s2)};
        float cr = ok ? 0.45f : 0.95f, cg = ok ? 0.8f : 0.4f, cb = ok ? 1f : 0.35f;
        for (int k = 0; k < 4; k++) {
            Vec3 c0 = c[k], c1 = c[(k + 1) % 4];
            for (Vec3 p : new Vec3[]{a.add(c0), b.add(c0), b.add(c1), a.add(c1)}) {
                q.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(cr, cg, cb, 0.6f);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (lines.isEmpty() || mc.screen != null || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        var font = mc.font;
        int w = 0;
        for (Component c : lines) w = Math.max(w, font.width(c));
        int x = g.guiWidth() / 2 - w / 2, y = g.guiHeight() / 2 + 14;
        int h = lines.size() * 10 + 6;
        g.fill(x - 4, y - 3, x + w + 4, y + h - 3, 0xB0101216);
        for (Component c : lines) {
            g.drawString(font, c, x, y, 0xFFFFFF, false);
            y += 10;
        }
    }

    // ======================= Для CI =======================

    public static void debugPlan(InteractionHand h, WirePlan.Anchor a, List<int[]> pts) {
        cancel();
        hand = h;
        from = a;
        clicks.addAll(pts);
    }

    public static void debugCancel() {
        cancel();
    }

    public static WirePlan.Result debugPreview() {
        return preview;
    }
}
