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

    private static final ResourceLocation LED = ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");

    @Override
    public void render(T be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        renderCords(be, be.cords(), pose, buffers);
        renderLeds(be, pose, buffers);
    }

    /** Воздушные линии длинные — рамка отрисовки с запасом на самый длинный пролёт. */
    @Override
    public AABB getRenderBoundingBox(T be) {
        int r = 5;
        for (CordList.End e : be.cords().ends()) r = Math.max(r, (int) Math.ceil(Math.sqrt(e.other().distSqr(be.getBlockPos()))) + 2);
        return new AABB(be.getBlockPos()).inflate(r);
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
            if (!plugged(level, pos, s.face(), i)) continue;
            if (vc == null) vc = buffers.getBuffer(RenderType.entityCutoutNoCull(LED));
            boolean blink = NetHud.blinking(pos, i, time);
            int rgb = blink ? 0xFFB020 : 0x50FF78;
            float lift = sw.rack() ? 2 : 1;
            // Точка на лицевой плоскости над гнездом, чуть впереди неё
            float along = s.along() + 0.5f, y = s.height() + lift + 0.5f;
            float plane = s.face().getAxisDirection() == net.minecraft.core.Direction.AxisDirection.POSITIVE ? 16 - s.depth() + 0.05f : s.depth() - 0.05f;
            float x, z;
            if (s.face().getAxis() == net.minecraft.core.Direction.Axis.Z) {
                x = along;
                z = plane;
            } else {
                x = plane;
                z = along;
            }
            cube(vc, pose.last(), new Vec3(x / 16, y / 16, z / 16), 0.5f / 16, 0xF000F0, (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
        }
    }

    /** Воткнут ли провод в гнездо port устройства pos (по проводам соседней клетки у игрока). */
    private static boolean plugged(Level level, BlockPos pos, net.minecraft.core.Direction face, int port) {
        if (!(level.getBlockEntity(pos.relative(face)) instanceof com.strikerr212.minepc.lan.CableBlockEntity cable)) return false;
        for (var w : cable.wires()) {
            int k = w.index(face.getOpposite());
            if (k >= 0 && w.port(k) == port) return true;
        }
        return false;
    }

    public static void renderCords(BlockEntity be, CordList list, PoseStack pose, MultiBufferSource buffers) {
        Level level = be.getLevel();
        if (level == null || list.ends().isEmpty()) return;
        BlockPos pos = be.getBlockPos();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (CordList.End e : list.ends()) {
            if (e.other().asLong() < pos.asLong()) continue;          // этот корд рисует другой конец
            Vec3 a = com.strikerr212.minepc.lan.CordShape.anchor(level, pos, e.other()).subtract(pos.getX(), pos.getY(), pos.getZ());
            Vec3 b = com.strikerr212.minepc.lan.CordShape.anchor(level, e.other(), pos).subtract(pos.getX(), pos.getY(), pos.getZ());
            int rgb = DyeColor.byId(e.color()).getTextureDiffuseColor();
            int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255;
            BlockPos mid = BlockPos.containing(pos.getX() + (a.x + b.x) / 2, pos.getY() + Math.min(a.y, b.y), pos.getZ() + (a.z + b.z) / 2);
            int light = LevelRenderer.getLightColor(level, mid);
            var pts = com.strikerr212.minepc.lan.CordShape.points(a, b);
            for (int i = 1; i < pts.size(); i++) tube(vc, pose.last(), pts.get(i - 1), pts.get(i), r, g, bl, light, i);
            // вилки на концах
            VertexConsumer plug = buffers.getBuffer(RenderType.entityCutoutNoCull(PLUG));
            for (Vec3 end : new Vec3[]{a, b}) cube(plug, pose.last(), end, 0.06f, light, 255, 255, 255);
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
