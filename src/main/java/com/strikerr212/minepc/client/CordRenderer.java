package com.strikerr212.minepc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.lan.CordList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Патч-корды: провисающий провод между двумя устройствами. Рисует тот конец, у которого координата меньше.
 * Этот же класс — отрисовщик для коммутаторов, патч-панелей, радиомодемов и роутеров (у них больше ничего не рисуется).
 */
public class CordRenderer<T extends BlockEntity & CordList.Holder> implements BlockEntityRenderer<T> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "textures/block/network_cable.png");
    private static final ResourceLocation PLUG = ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "textures/block/network_plug.png");
    private static final float R = 0.035f;
    private static final int SEGMENTS = 16;

    public CordRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(T be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        renderCords(be, be.cords(), pose, buffers);
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        return new AABB(be.getBlockPos()).inflate(5);
    }

    /** Точка, где корд выходит из устройства: сбоку, ближе к другому концу, на высоте гнёзд. */
    private static Vec3 anchor(Level level, BlockPos pos, BlockPos toward) {
        BlockState s = level.getBlockState(pos);
        double y = s.getShape(level, pos).isEmpty() ? 0.5 : Math.min(0.5, s.getShape(level, pos).max(net.minecraft.core.Direction.Axis.Y) * 0.6);
        Vec3 c = new Vec3(pos.getX() + 0.5, pos.getY() + y, pos.getZ() + 0.5);
        Vec3 dir = new Vec3(toward.getX() - pos.getX(), 0, toward.getZ() - pos.getZ());
        if (dir.lengthSqr() > 1e-4) {
            dir = dir.normalize();
            double k = 0.48 / Math.max(Math.abs(dir.x), Math.abs(dir.z));
            c = c.add(dir.scale(Math.min(0.5, k)));
        }
        return c;
    }

    public static void renderCords(BlockEntity be, CordList list, PoseStack pose, MultiBufferSource buffers) {
        Level level = be.getLevel();
        if (level == null || list.ends().isEmpty()) return;
        BlockPos pos = be.getBlockPos();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (CordList.End e : list.ends()) {
            if (e.other().asLong() < pos.asLong()) continue;          // этот корд рисует другой конец
            Vec3 a = anchor(level, pos, e.other()).subtract(pos.getX(), pos.getY(), pos.getZ());
            Vec3 b = anchor(level, e.other(), pos).subtract(pos.getX(), pos.getY(), pos.getZ());
            double dist = a.distanceTo(b);
            double sag = 0.15 + 0.12 * dist;
            int rgb = DyeColor.byId(e.color()).getTextureDiffuseColor();
            int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255;
            BlockPos mid = BlockPos.containing(pos.getX() + (a.x + b.x) / 2, pos.getY() + Math.min(a.y, b.y), pos.getZ() + (a.z + b.z) / 2);
            int light = LevelRenderer.getLightColor(level, mid);
            Vec3 prev = a;
            for (int i = 1; i <= SEGMENTS; i++) {
                double t = i / (double) SEGMENTS;
                Vec3 p = a.lerp(b, t).add(0, -sag * 4 * t * (1 - t), 0);
                tube(vc, pose.last(), prev, p, r, g, bl, light, i);
                prev = p;
            }
            // вилки на концах
            VertexConsumer plug = buffers.getBuffer(RenderType.entityCutoutNoCull(PLUG));
            for (Vec3 end : new Vec3[]{a, b}) cube(plug, pose.last(), end, 0.06f, light);
            vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        }
    }

    /** Отрезок провода — четырёхгранная трубка. */
    private static void tube(VertexConsumer vc, PoseStack.Pose m, Vec3 from, Vec3 to, int r, int g, int b, int light, int i) {
        Vec3 d = to.subtract(from);
        if (d.lengthSqr() < 1e-8) return;
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

    private static void cube(VertexConsumer vc, PoseStack.Pose m, Vec3 c, float h, int light) {
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
            for (int k = 0; k < 4; k++) vertex(vc, m, c.add(f[k].scale(h)), 255, 255, 255, uv[k][0], uv[k][1], light, f[4]);
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
