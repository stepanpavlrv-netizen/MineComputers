package com.strikerr212.minepc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.lan.CableBlockEntity;
import com.strikerr212.minepc.lan.CableTesterItem;
import com.strikerr212.minepc.lan.Wire;
import com.strikerr212.minepc.network.TesterPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Кабельный тестер у игрока: подсветка прозвоненного кабеля сквозь стены (10 секунд), мигающие концы и обрывы,
 * и экранчик тестера в углу, пока тестер в руке.
 */
@EventBusSubscriber(modid = MineComputers.MODID, value = Dist.CLIENT)
public final class TesterHud {

    private TesterHud() {
    }

    /** Сколько тиков держится подсветка. */
    private static final int SHOW_TICKS = 200;

    private static final List<AABB> wireBoxes = new ArrayList<>();
    private static final List<AABB> passiveBoxes = new ArrayList<>();
    private static final List<AABB> endBoxes = new ArrayList<>();
    private static final List<AABB> openBoxes = new ArrayList<>();
    private static final List<Vec3[]> cordLines = new ArrayList<>();
    private static long until;
    /** Строки экранчика (последний результат). */
    private static final List<Component> screen = new ArrayList<>();
    private static boolean screenBad;

    public static void onResult(TesterPayload p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        wireBoxes.clear();
        passiveBoxes.clear();
        endBoxes.clear();
        openBoxes.clear();
        cordLines.clear();
        for (int i = 0; i + 1 < p.wires().length && wireBoxes.size() < 6000; i += 2) {
            BlockPos cell = BlockPos.of(p.wires()[i]);
            Wire w = Wire.unpack(p.wires()[i + 1]);
            for (float[] b : CableBlockEntity.boxes(w)) {
                wireBoxes.add(new AABB(b[0] / 16, b[1] / 16, b[2] / 16, b[3] / 16, b[4] / 16, b[5] / 16).move(cell.getX(), cell.getY(), cell.getZ()));
            }
        }
        for (long t : p.trays()) wireBoxes.add(new AABB(BlockPos.of(t)).deflate(0.25, 0.25, 0.25));
        for (long t : p.passives()) passiveBoxes.add(new AABB(BlockPos.of(t)).deflate(0.15));
        for (long t : p.ends()) endBoxes.add(new AABB(BlockPos.of(t)).inflate(0.03));
        for (long t : p.open()) openBoxes.add(new AABB(BlockPos.of(t)).deflate(0.2));
        for (int i = 0; i + 1 < p.cords().length; i += 2) {
            cordLines.add(new Vec3[]{Vec3.atCenterOf(BlockPos.of(p.cords()[i])), Vec3.atCenterOf(BlockPos.of(p.cords()[i + 1]))});
        }
        until = mc.level.getGameTime() + SHOW_TICKS;
        buildScreen(p.info());
    }

    /** Экранчик: длина, скорость, концы, бирка, обрыв. */
    private static void buildScreen(String info) {
        screen.clear();
        String[] f = info.split("\t", -1);
        if (f.length < 8) return;
        int len = Integer.parseInt(f[0]), limit = Integer.parseInt(f[1]), open = Integer.parseInt(f[6]), ends = Integer.parseInt(f[7]);
        boolean fiber = f[2].equals("1");
        long kbit = Long.parseLong(f[3]);
        screenBad = open > 0 || len > limit || ends == 0;
        screen.add(Component.translatable("hud.minepc.tester.len", len, limit,
                Component.translatable(fiber ? "tooltip.minepc.tester.fiber" : "tooltip.minepc.tester.copper")));
        screen.add(Component.translatable("hud.minepc.tester.speed", CableTesterItem.speedText(kbit)));
        screen.add(Component.translatable(open > 0 ? "hud.minepc.tester.open" : "hud.minepc.tester.whole"));
        if (!f[4].isEmpty()) screen.add(Component.translatable("hud.minepc.port_label", f[4]));
        if (ends == 0) screen.add(Component.translatable("hud.minepc.tester.no_ends"));
        if (!f[5].isEmpty()) {
            for (String e : f[5].split("\u001e")) {
                String[] q = e.split("\u001f", -1);
                if (q.length < 3) continue;
                Component c = Component.literal("→ ").append(Component.translatable(q[0]));
                c = c.copy().append(Component.translatable("hud.minepc.tester.port", q[1]));
                if (!q[2].isEmpty()) c = c.copy().append(" " + q[2]);
                screen.add(c);
            }
        }
        if (ends > 4) screen.add(Component.literal("+" + (ends - 4)));
    }

    private static boolean holding(Minecraft mc) {
        return mc.player != null && (mc.player.getMainHandItem().getItem() instanceof CableTesterItem
                || mc.player.getOffhandItem().getItem() instanceof CableTesterItem);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        if (now > until || wireBoxes.isEmpty() && endBoxes.isEmpty() && openBoxes.isEmpty() && cordLines.isEmpty()) return;
        float pulse = 0.75f + 0.25f * (float) Math.sin(now * 0.35);
        float fade = Math.min(1f, (until - now) / 40f);
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        // Заливка провода — оранжевая, пульсирует
        VertexConsumer q = buffers.getBuffer(XrayRenderTypes.QUADS);
        Matrix4f m = pose.last().pose();
        for (AABB b : wireBoxes) fill(q, m, b.inflate(0.03), 1f, 0.55f, 0.1f, 0.6f * pulse * fade);
        boolean blink = (now / 5) % 2 == 0;
        if (blink) for (AABB b : openBoxes) fill(q, m, b, 0.95f, 0.2f, 0.2f, 0.35f * fade);
        // «Без проверки глубины» у RenderType сама глубину не выключает — выключаем на время отрисовки
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        buffers.endBatch(XrayRenderTypes.QUADS);
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        VertexConsumer vc = buffers.getBuffer(XrayRenderTypes.LINES);
        for (AABB b : wireBoxes) LevelRenderer.renderLineBox(pose, vc, b.inflate(0.03), 1f, 0.62f, 0.15f, pulse * fade);
        for (AABB b : passiveBoxes) LevelRenderer.renderLineBox(pose, vc, b, 0.55f, 0.82f, 1f, 0.9f * fade);
        if (blink) for (AABB b : endBoxes) LevelRenderer.renderLineBox(pose, vc, b, 0.35f, 1f, 0.45f, fade);
        if (blink) for (AABB b : openBoxes) LevelRenderer.renderLineBox(pose, vc, b, 1f, 0.25f, 0.25f, fade);
        PoseStack.Pose last = pose.last();
        for (Vec3[] l : cordLines) {
            Vec3 n = l[1].subtract(l[0]).normalize();
            vc.addVertex(last, (float) l[0].x, (float) l[0].y, (float) l[0].z).setColor(1f, 0.6f, 0.15f, fade).setNormal(last, (float) n.x, (float) n.y, (float) n.z);
            vc.addVertex(last, (float) l[1].x, (float) l[1].y, (float) l[1].z).setColor(1f, 0.6f, 0.15f, fade).setNormal(last, (float) n.x, (float) n.y, (float) n.z);
        }
        pose.popPose();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        buffers.endBatch(XrayRenderTypes.LINES);
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }

    /** Залитая коробка (все шесть граней). */
    private static void fill(VertexConsumer q, Matrix4f m, AABB b, float r, float g, float bl, float a) {
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ, x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        float[][] faces = {
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1},
                {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0},
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0},
                {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}};
        for (float[] f : faces) {
            for (int i = 0; i < 12; i += 3) q.addVertex(m, f[i], f[i + 1], f[i + 2]).setColor(r, g, bl, a);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.options.hideGui || screen.isEmpty() || !holding(mc)) return;
        GuiGraphics g = event.getGuiGraphics();
        var font = mc.font;
        int w = font.width(Component.translatable("hud.minepc.tester.title"));
        for (Component c : screen) w = Math.max(w, font.width(c));
        int h = (screen.size() + 1) * 10 + 8;
        // Справа посередине: не мешает строке над хотбаром и подсказкам у прицела
        int x = g.guiWidth() - w - 14, y = Math.max(12, g.guiHeight() / 2 - h / 2 - 20);
        // Корпус тестера и экранчик
        g.fill(x - 8, y - 8, x + w + 8, y + h + 4, 0xFFE0A21B);
        g.fill(x - 6, y - 6, x + w + 6, y + h + 2, 0xFF2B2F33);
        g.fill(x - 4, y - 4, x + w + 4, y + h, 0xFF16261A);
        int fg = screenBad ? 0xFFF5C26B : 0xFF9CF59C;
        g.drawString(font, Component.translatable("hud.minepc.tester.title"), x, y, 0xFF5FBF6A, false);
        y += 12;
        for (Component c : screen) {
            g.drawString(font, c, x, y, fg, false);
            y += 10;
        }
    }
}
