package com.strikerr212.minepc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.strikerr212.minepc.block.DeviceBlock;
import com.strikerr212.minepc.block.MonitorBlockEntity;
import com.strikerr212.minepc.block.MonitorGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Картинка на мониторе в мире. Большой монитор (прямоугольник из блоков) показывает один экран:
 * каждый блок рисует свою часть, поэтому картинка идёт без швов.
 */
public class MonitorRenderer implements BlockEntityRenderer<MonitorBlockEntity> {

    private static final float Z = 11;                   // передняя грань панели (пиксели блока)
    private static final int CELL_W = 6, CELL_H = 9;     // пропорции клетки экрана
    private static final int FULL = LightTexture.FULL_BRIGHT;

    private final Font font;

    public MonitorRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(MonitorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        ClientTerminals.Snapshot s = ClientTerminals.get(be.casePos());
        if (s == null) return;   // выключен — видна обычная тёмная текстура экрана
        MonitorGroup g = be.group();

        Direction facing = be.getBlockState().getValue(DeviceBlock.FACING);
        pose.pushPose();
        // Поворачиваем так, будто монитор смотрит на север, и переходим в пиксели
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(switch (facing) {
            case SOUTH -> 180;
            case WEST -> 90;
            case EAST -> -90;
            default -> 0;
        }));
        pose.translate(-0.5, -0.5, -0.5);
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        // Начало координат — левый верхний угол экрана всего монитора (рамка 1 пиксель): X вправо, Y вниз,
        // Z — к зрителю (как у табличек, иначе текст окажется изнанкой к игроку)
        pose.translate(16 + g.col() * 16 - 1, 16 + g.row() * 16 - 1, Z - 0.01f);
        pose.scale(-1, -1, -1);

        float areaW = g.cols() * 16 - 2, areaH = g.rows() * 16 - 4;
        // Этот блок — прямоугольник внутри общего экрана; рисуем только то, что в него попадает
        float bx0 = g.col() * 16 - 1, by0 = g.row() * 16 - 1, bx1 = bx0 + 16, by1 = by0 + 16;

        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        quadClip(vc, m, 0, 0, areaW, areaH, 0x000000, bx0, by0, bx1, by1);

        // Сохраняем пропорции экрана: по краям — чёрные поля
        float tw = s.width() * CELL_W, th = s.height() * CELL_H;
        float k = Math.min(areaW / tw, areaH / th);
        float ox = (areaW - tw * k) / 2, oy = (areaH - th * k) / 2;
        float cw = CELL_W * k, ch = CELL_H * k;

        int w = s.width(), h = s.height();
        pose.translate(0, 0, 0.002f);
        m = pose.last().pose();
        for (int y = 0; y < h; y++) {
            float y0 = oy + y * ch, y1 = y0 + ch;
            if (y1 < by0 || y0 > by1) continue;
            int x = 0;
            while (x < w) {
                int color = s.bg()[y * w + x], start = x;
                while (x < w && s.bg()[y * w + x] == color) x++;
                quadClip(vc, m, ox + start * cw, y0, ox + x * cw, y1, color, bx0, by0, bx1, by1);
            }
            for (x = 0; x < w; x++) {
                int p = y * w + x;
                float cx = ox + x * cw;
                if (cx + cw < bx0 || cx > bx1) continue;
                final Matrix4f mm = m;
                final float fy = y0;
                CellArt.draw(s.chars()[p], s.fg()[p], s.bg()[p], (a0, b0, a1, b1, rgb) ->
                        quadClip(vc, mm, cx + a0 * cw, fy + b0 * ch, cx + a1 * cw, fy + b1 * ch, rgb, bx0, by0, bx1, by1));
            }
        }

        // Издалека мелкий текст не разглядеть — тогда только цветной фон
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dist = Math.sqrt(cam.distanceToSqr(be.getBlockPos().getCenter()));
        float pixelsPerChar = ch * 16;   // высота символа в «пикселях» на расстоянии 1 блока
        if (dist > pixelsPerChar * 4 + 3) {
            pose.popPose();
            return;
        }

        pose.translate(0, 0, 0.002f);
        float glyph = Math.min(cw / CELL_W, ch / CELL_H);
        for (int y = 0; y < h; y++) {
            float y0 = oy + y * ch;
            for (int x = 0; x < w; x++) {
                int p = y * w + x;
                char c = s.chars()[p];
                if (c <= ' ' || TerminalScreen.isArt(c)) continue;
                float cx = ox + x * cw;
                float mid = cx + cw / 2, midY = y0 + ch / 2;
                if (mid < bx0 || mid >= bx1 || midY < by0 || midY >= by1) continue;   // символ рисует его блок
                pose.pushPose();
                pose.translate(cx + (cw - Glyphs.width(font, c) * glyph) / 2, y0 + ch * 0.08f, 0);
                pose.scale(glyph, glyph, 1);
                font.drawInBatch(Glyphs.text(c), 0, 0, 0xFF000000 | s.fg()[p], false,
                        pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, FULL);
                pose.popPose();
            }
        }
        pose.popPose();
    }

    /** Прямоугольник, обрезанный по границам блока; рисуем с обеих сторон, чтобы не зависеть от порядка вершин. */
    private static void quadClip(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int rgb,
                                 float cx0, float cy0, float cx1, float cy1) {
        x0 = Math.max(x0, cx0);
        y0 = Math.max(y0, cy0);
        x1 = Math.min(x1, cx1);
        y1 = Math.min(y1, cy1);
        if (x0 >= x1 || y0 >= y1) return;
        int argb = 0xFF000000 | rgb;
        vc.addVertex(m, x0, y0, 0).setColor(argb).setLight(FULL);
        vc.addVertex(m, x0, y1, 0).setColor(argb).setLight(FULL);
        vc.addVertex(m, x1, y1, 0).setColor(argb).setLight(FULL);
        vc.addVertex(m, x1, y0, 0).setColor(argb).setLight(FULL);
        vc.addVertex(m, x1, y0, 0).setColor(argb).setLight(FULL);
        vc.addVertex(m, x1, y1, 0).setColor(argb).setLight(FULL);
        vc.addVertex(m, x0, y1, 0).setColor(argb).setLight(FULL);
        vc.addVertex(m, x0, y0, 0).setColor(argb).setLight(FULL);
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    /** Большой монитор виден, даже если его «главный» блок за краем экрана. */
    @Override
    public AABB getRenderBoundingBox(MonitorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1);
    }
}
