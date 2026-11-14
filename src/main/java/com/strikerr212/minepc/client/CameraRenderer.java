package com.strikerr212.minepc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.lan.CameraBlock;
import com.strikerr212.minepc.lan.CameraBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Голова камеры: корпус с объективом, козырёк, лампа «идёт запись». Поворачивается плавно — туда, куда
 * её повернули с компьютера или из окна камеры.
 */
public class CameraRenderer implements BlockEntityRenderer<CameraBlockEntity> {

    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "textures/entity/camera_head.png");
    /** Области текстуры 32×32 (в пикселях): корпус, объектив, тёмное, лампа. */
    private static final float[] BODY = {0, 0, 16, 16}, LENS = {16, 0, 32, 16}, DARK = {0, 16, 16, 32}, LED = {16, 16, 24, 24},
            LED_OFF = {24, 16, 32, 24};

    public CameraRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(CameraBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof CameraBlock)) return;
        float targetYaw = be.worldYaw(), targetPitch = be.pitch();
        if (Float.isNaN(be.renderYaw)) {
            be.renderYaw = targetYaw;
            be.renderPitch = targetPitch;
        }
        float dy = ((targetYaw - be.renderYaw + 540) % 360) - 180;
        be.renderYaw += dy * 0.2f;
        be.renderPitch += (targetPitch - be.renderPitch) * 0.2f;
        double[] j = CameraBlock.joint(state);
        pose.pushPose();
        pose.translate(j[0], j[1], j[2]);
        pose.mulPose(Axis.YP.rotationDegrees(-be.renderYaw));
        pose.mulPose(Axis.XP.rotationDegrees(be.renderPitch));
        VertexConsumer v = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
        PoseStack.Pose p = pose.last();
        float s = 1 / 16f;
        // корпус
        box(v, p, -2.5f * s, -2f * s, -3.5f * s, 2.5f * s, 2f * s, 3.5f * s, BODY, BODY, light, overlay);
        // объектив (выступает вперёд)
        box(v, p, -1.8f * s, -1.6f * s, -4.3f * s, 1.8f * s, 1.6f * s, -3.5f * s, LENS, DARK, light, overlay);
        // козырёк
        box(v, p, -3f * s, 2f * s, -4.9f * s, 3f * s, 2.6f * s, 3.5f * s, BODY, DARK, light, overlay);
        // задняя крышка
        box(v, p, -2.1f * s, -1.6f * s, 3.5f * s, 2.1f * s, 1.6f * s, 4.1f * s, DARK, DARK, light, overlay);
        // лампа записи — светится сама
        boolean on = be.live() && (be.getLevel() == null || (be.getLevel().getGameTime() / 10) % 2 == 0);
        float[] led = on ? LED : LED_OFF;
        box(v, p, 1.4f * s, 1.0f * s, -3.75f * s, 2.1f * s, 1.6f * s, -3.5f * s, led, led, on ? 0xF000F0 : light, overlay);
        pose.popPose();
    }

    /** Коробка: front — текстура передней грани (к -Z), other — остальных. */
    private static void box(VertexConsumer v, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1,
                            float[] front, float[] other, int light, int overlay) {
        quad(v, p, front, light, overlay, 0, 0, -1, x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0);   // перед
        quad(v, p, other, light, overlay, 0, 0, 1, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1);    // зад
        quad(v, p, other, light, overlay, 0, 1, 0, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);    // верх
        quad(v, p, other, light, overlay, 0, -1, 0, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);   // низ
        quad(v, p, other, light, overlay, -1, 0, 0, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0);   // лево
        quad(v, p, other, light, overlay, 1, 0, 0, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1);    // право
    }

    private static void quad(VertexConsumer v, PoseStack.Pose p, float[] uv, int light, int overlay, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        float u0 = uv[0] / 32f, v0 = uv[1] / 32f, u1 = uv[2] / 32f, v1 = uv[3] / 32f;
        v.addVertex(p, ax, ay, az).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
        v.addVertex(p, bx, by, bz).setColor(255, 255, 255, 255).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
        v.addVertex(p, cx, cy, cz).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
        v.addVertex(p, dx, dy, dz).setColor(255, 255, 255, 255).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
    }

    @Override
    public boolean shouldRenderOffScreen(CameraBlockEntity be) {
        return false;
    }
}
