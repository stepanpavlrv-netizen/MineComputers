package com.strikerr212.minepc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.strikerr212.minepc.lan.TowerSectionBlock;
import com.strikerr212.minepc.lan.TowerSectionBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Заградительные огни вышки: красные фонари по углам макушки и каждые 6 секций. Ночью (и в грозу) мигают раз
 * в секунду, днём — тёмные.
 */
public class TowerLampRenderer implements BlockEntityRenderer<TowerSectionBlockEntity> {

    public TowerLampRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(TowerSectionBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof TowerSectionBlock) || !TowerSectionBlockEntity.lamps(state)) return;
        Level level = be.getLevel();
        if (level == null) return;
        long day = level.getDayTime() % 24000;
        boolean dark = (day > 12600 && day < 23400) || level.isThundering();
        boolean on = dark && (level.getGameTime() / 20) % 2 == 0;
        float r = on ? 1f : 0.45f, g = on ? 0.12f : 0.05f, b = on ? 0.1f : 0.05f;
        // Углы вышки на верху секции
        int above = state.getValue(TowerSectionBlock.ABOVE);
        float w = TowerSectionBlock.width(Math.max(0, above - 1)) / 16f;
        float half = w / 2 - 0.06f;
        VertexConsumer q = buffers.getBuffer(XrayRenderTypes.FLAT);
        Matrix4f m = pose.last().pose();
        float s = on ? 0.09f : 0.07f;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                float cx = 0.5f + sx * half, cz = 0.5f + sz * half, cy = 1.0f + s;
                box(q, m, cx - s, cy - s, cz - s, cx + s, cy + s, cz + s, r, g, b);
            }
        }
    }

    private static void box(VertexConsumer q, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b) {
        float[][] faces = {
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1},
                {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0},
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0},
                {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}};
        for (float[] f : faces) {
            for (int i = 0; i < 12; i += 3) q.addVertex(m, f[i], f[i + 1], f[i + 2]).setColor(r, g, b, 1f);
        }
    }

    @Override
    public AABB getRenderBoundingBox(TowerSectionBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.6);
    }
}
