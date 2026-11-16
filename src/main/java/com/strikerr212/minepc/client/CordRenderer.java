package com.strikerr212.minepc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.strikerr212.minepc.MineComputers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Отрисовщик коммутаторов, патч-панелей, радиомодемов и роутеров: лампочки над гнёздами коммутатора (сами провода
 * рисует {@link WireRenderer}).
 */
public class CordRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {


    public CordRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static final ResourceLocation LED = ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");

    @Override
    public void render(T be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        renderLeds(be, pose, buffers);
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        return new AABB(be.getBlockPos()).inflate(1);
    }

    /**
     * Лампочки над гнёздами коммутаторов: горит зелёным, если в гнездо воткнут провод; мигает жёлтым, когда через
     * коммутатор идут сообщения.
     */
    private static void renderLeds(BlockEntity be, PoseStack pose, MultiBufferSource buffers) {
        Level level = be.getLevel();
        if (level == null) return;
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof com.strikerr212.minepc.lan.SwitchBlock sw)) return;
        BlockPos pos = be.getBlockPos();
        var sockets = sw.sockets(state);
        long time = level.getGameTime();
        VertexConsumer vc = null;
        for (int i = 0; i < sockets.size(); i++) {
            var s = sockets.get(i);
            if (!com.strikerr212.minepc.wire.WireSockets.plugged(level, pos, i)) continue;
            if (vc == null) vc = buffers.getBuffer(RenderType.entityCutoutNoCull(LED));
            boolean blink = NetHud.blinking(pos, i, time);
            int rgb = blink ? 0xFFB020 : 0x50FF78;
            float lift = 1;
            // Точка на лицевой плоскости над гнездом, чуть впереди неё («над» — вверх, у настенного — от стены)
            net.minecraft.core.Direction f = s.face();
            net.minecraft.core.Direction up = com.strikerr212.minepc.lan.SwitchBlock.onWall(state)
                    ? state.getValue(com.strikerr212.minepc.lan.SwitchBlock.FACING) : net.minecraft.core.Direction.UP;
            float plane = f.getAxisDirection() == net.minecraft.core.Direction.AxisDirection.POSITIVE ? 16 - s.depth() + 0.05f : s.depth() - 0.05f;
            var fa = com.strikerr212.minepc.lan.LanDevice.faceAxes(f);
            float[] p = new float[3];
            p[fa[0].ordinal()] = s.along() + 0.5f;
            p[fa[1].ordinal()] = s.height() + 0.5f;
            p[f.getAxis().ordinal()] = plane;
            float x = p[0] + lift * up.getStepX(), y = p[1] + lift * up.getStepY(), z = p[2] + lift * up.getStepZ();
            cube(vc, pose.last(), new Vec3(x / 16, y / 16, z / 16), 0.5f / 16, 0xF000F0, (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
        }
    }

    private static void cube(VertexConsumer vc, PoseStack.Pose m, Vec3 c, float h, int light, int r, int g, int b) {
        Vec3[][] faces = {
                {new Vec3(-1, -1, -1), new Vec3(-1, 1, -1), new Vec3(1, 1, -1), new Vec3(1, -1, -1), new Vec3(0, 0, -1)},
                {new Vec3(-1, -1, 1), new Vec3(1, -1, 1), new Vec3(1, 1, 1), new Vec3(-1, 1, 1), new Vec3(0, 0, 1)},
                {new Vec3(-1, -1, -1), new Vec3(-1, -1, 1), new Vec3(-1, 1, 1), new Vec3(-1, 1, -1), new Vec3(-1, 0, 0)},
                {new Vec3(1, -1, -1), new Vec3(1, 1, -1), new Vec3(1, 1, 1), new Vec3(1, -1, 1), new Vec3(1, 0, 0)},
                {new Vec3(-1, 1, -1), new Vec3(-1, 1, 1), new Vec3(1, 1, 1), new Vec3(1, 1, -1), new Vec3(0, 1, 0)},
                {new Vec3(-1, -1, -1), new Vec3(1, -1, -1), new Vec3(1, -1, 1), new Vec3(-1, -1, 1), new Vec3(0, -1, 0)},
        };
        float[][] uv = {{0, 0}, {0, 0.25f}, {0.25f, 0.25f}, {0.25f, 0}};
        for (Vec3[] f : faces) {
            for (int k = 0; k < 4; k++) vertex(vc, m, c.add(f[k].scale(h)), r, g, b, uv[k][0], uv[k][1], light, f[4]);
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose m, Vec3 p, int r, int g, int b, float u, float v, int light, Vec3 n) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(m, (float) n.x, (float) n.y, (float) n.z);
    }

    @SuppressWarnings("unused")
    private static Vector3f v(Vec3 p) {
        return new Vector3f((float) p.x, (float) p.y, (float) p.z);
    }
}
