package com.strikerr212.minepc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.network.WiresPayload;
import com.strikerr212.minepc.wire.WireLine;
import com.strikerr212.minepc.wire.WireStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/**
 * Провода 3.0 у игрока: хранит то, что прислал сервер ({@link WireStore}), и рисует — трубкой по отрезкам, висящие
 * отрезки — дугой, на концах в гнёздах — вилки. Свет берётся у каждого отрезка свой.
 */
@EventBusSubscriber(modid = MineComputers.MODID, value = Dist.CLIENT)
public final class WireRenderer {

    private WireRenderer() {
    }

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "textures/block/network_cable.png");
    private static final ResourceLocation PLUG = ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "textures/block/network_plug.png");
    /** Толщина: провод лежит на поверхности (центр — в полпикселе от неё). */
    private static final float R = 0.9f / 32;
    private static final double VIEW = 96;

    public static void onPayload(WiresPayload p) {
        for (long id : p.removed()) WireStore.remove(id);
        for (WireLine l : p.lines()) WireStore.put(l);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        WireStore.clear();
    }

    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.Clone event) {
        // смена измерения: сервер пришлёт провода нового мира с его чанками
        if (event.getOldPlayer().level().dimension() != event.getNewPlayer().level().dimension()) WireStore.clear();
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || WireStore.all().isEmpty()) return;
        Vec3 cam = event.getCamera().getPosition();
        Frustum frustum = event.getFrustum();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        // вне отрисовки сущностей карту света и наложение надо включить самим, иначе провод не виден
        mc.gameRenderer.lightTexture().turnOnLightLayer();
        mc.gameRenderer.overlayTexture().setupOverlayColor();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (WireLine l : WireStore.all()) {
            var box = l.bounds();
            if (box.distanceToSqr(cam) > VIEW * VIEW || (frustum != null && !frustum.isVisible(box))) continue;
            draw(vc, pose.last(), level, l);
        }
        buffers.endBatch(RenderType.entityCutoutNoCull(TEXTURE));
        VertexConsumer plug = buffers.getBuffer(RenderType.entityCutoutNoCull(PLUG));
        for (WireLine l : WireStore.all()) {
            if (l.bounds().distanceToSqr(cam) > VIEW * VIEW) continue;
            for (int e = 0; e < 2; e++) {
                boolean b = e == 1;
                if (l.free(b)) continue;
                Vec3 p = l.end(b);
                Vec3 q = l.node(b ? l.nodes() - 2 : 1);
                // вилка: чуть выступает из гнезда в сторону провода
                Vec3 c = p.add(q.subtract(p).normalize().scale(0.05));
                int light = LevelRenderer.getLightColor(level, BlockPos.containing(q));
                cube(plug, pose.last(), c, 0.06f, light, 235, 238, 240);
                if (!l.label.isEmpty()) {
                    // бирка: белый флажок на проводе чуть дальше вилки
                    Vec3 dir = q.subtract(p).normalize();
                    Vec3 side = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
                    Vec3 f = p.add(dir.scale(Math.min(0.22, p.distanceTo(q) * 0.5))).add(side.scale(0.06));
                    cube(plug, pose.last(), f, 0.05f, light, 250, 250, 252);
                }
            }
        }
        buffers.endBatch(RenderType.entityCutoutNoCull(PLUG));
        mc.gameRenderer.overlayTexture().teardownOverlayColor();
        mc.gameRenderer.lightTexture().turnOffLightLayer();
        pose.popPose();
    }

    private static void draw(VertexConsumer vc, PoseStack.Pose m, Level level, WireLine l) {
        int rgb = l.fiber ? 0xE8C23A : DyeColor.byId(l.color).getTextureDiffuseColor();
        if (l.fiber && l.color != DyeColor.YELLOW.getId()) rgb = DyeColor.byId(l.color).getTextureDiffuseColor();
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int k = 0;
        for (int i = 0; i + 1 < l.nodes(); i++) {
            Vec3 a = l.node(i), c = l.node(i + 1);
            if (l.hang[i]) {
                List<Vec3> pts = com.strikerr212.minepc.wire.CordShape.points(a, c);
                for (int j = 1; j < pts.size(); j++) {
                    Vec3 mid = pts.get(j - 1).add(pts.get(j)).scale(0.5);
                    tube(vc, m, pts.get(j - 1), pts.get(j), r, g, b, LevelRenderer.getLightColor(level, BlockPos.containing(mid)), k++);
                }
            } else {
                // прямой отрезок режем по границам блоков — у каждого куска свой свет; кусок внутри блока
                // (маскировка «провод в стене») не рисуем
                List<Double> cuts = new java.util.ArrayList<>();
                cuts.add(0.0);
                for (int ax = 0; ax < 3; ax++) {
                    double va = ax == 0 ? a.x : ax == 1 ? a.y : a.z, vc2 = ax == 0 ? c.x : ax == 1 ? c.y : c.z;
                    if (Math.abs(vc2 - va) < 1e-9) continue;
                    for (double t = Math.floor(Math.min(va, vc2)) + 1; t < Math.max(va, vc2); t++) cuts.add((t - va) / (vc2 - va));
                }
                cuts.add(1.0);
                java.util.Collections.sort(cuts);
                for (int j = 0; j + 1 < cuts.size(); j++) {
                    if (cuts.get(j + 1) - cuts.get(j) < 1e-6) continue;
                    Vec3 p0 = a.lerp(c, cuts.get(j)), p1 = a.lerp(c, cuts.get(j + 1));
                    Vec3 mid = p0.add(p1).scale(0.5);
                    if (com.strikerr212.minepc.wire.WireMask.inside(level, mid)) continue;
                    // небольшой нахлёст, чтобы на изгибах не было щелей
                    Vec3 d = p1.subtract(p0).normalize().scale(R);
                    if (j == 0 && i > 0) p0 = p0.subtract(d);
                    if (j + 2 == cuts.size() && i + 2 < l.nodes()) p1 = p1.add(d);
                    tube(vc, m, p0, p1, r, g, b, LevelRenderer.getLightColor(level, BlockPos.containing(mid)), k++);
                }
            }
        }
    }

    /** Отрезок — четырёхгранная трубка. */
    static void tube(VertexConsumer vc, PoseStack.Pose m, Vec3 from, Vec3 to, int r, int g, int b, int light, int i) {
        Vec3 d = to.subtract(from);
        if (d.lengthSqr() < 1e-10) return;
        Vec3 dn = d.normalize();
        Vec3 up = Math.abs(dn.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s1 = dn.cross(up).normalize().scale(R);
        Vec3 s2 = dn.cross(s1).normalize().scale(R);
        Vec3[] corners = {s1.add(s2), s1.subtract(s2), s1.scale(-1).subtract(s2), s1.scale(-1).add(s2)};
        float v0 = (i % 8) / 8f, v1 = v0 + 0.125f;
        for (int k = 0; k < 4; k++) {
            Vec3 c0 = corners[k], c1 = corners[(k + 1) % 4];
            Vec3 n = c0.add(c1).normalize();
            vertex(vc, m, from.add(c0), r, g, b, 0, v0, light, n);
            vertex(vc, m, to.add(c0), r, g, b, 0, v1, light, n);
            vertex(vc, m, to.add(c1), r, g, b, 0.125f, v1, light, n);
            vertex(vc, m, from.add(c1), r, g, b, 0.125f, v0, light, n);
        }
    }

    static void cube(VertexConsumer vc, PoseStack.Pose m, Vec3 c, float h, int light, int r, int g, int b) {
        Vec3[][] faces = {
                {new Vec3(-h, -h, -h), new Vec3(h, -h, -h), new Vec3(h, -h, h), new Vec3(-h, -h, h), new Vec3(0, -1, 0)},
                {new Vec3(-h, h, -h), new Vec3(-h, h, h), new Vec3(h, h, h), new Vec3(h, h, -h), new Vec3(0, 1, 0)},
                {new Vec3(-h, -h, -h), new Vec3(-h, h, -h), new Vec3(h, h, -h), new Vec3(h, -h, -h), new Vec3(0, 0, -1)},
                {new Vec3(-h, -h, h), new Vec3(h, -h, h), new Vec3(h, h, h), new Vec3(-h, h, h), new Vec3(0, 0, 1)},
                {new Vec3(-h, -h, -h), new Vec3(-h, -h, h), new Vec3(-h, h, h), new Vec3(-h, h, -h), new Vec3(-1, 0, 0)},
                {new Vec3(h, -h, -h), new Vec3(h, h, -h), new Vec3(h, h, h), new Vec3(h, -h, h), new Vec3(1, 0, 0)}};
        for (Vec3[] f : faces) {
            for (int i = 0; i < 4; i++) vertex(vc, m, c.add(f[i]), r, g, b, i == 1 || i == 2 ? 0.25f : 0, i >= 2 ? 0.25f : 0, light, f[4]);
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose m, Vec3 p, int r, int g, int b, float u, float v, int light, Vec3 n) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(m, (float) n.x, (float) n.y, (float) n.z);
    }
}
