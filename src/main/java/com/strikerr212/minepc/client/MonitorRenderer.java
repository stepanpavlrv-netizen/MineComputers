package com.strikerr212.minepc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.strikerr212.minepc.block.DeviceBlock;
import com.strikerr212.minepc.block.MonitorBlockEntity;
import com.strikerr212.minepc.computer.Terminal;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/**
 * Картинка на мониторе в мире. Экран — передняя грань панели (x 1..15, y 3..15 пикселей),
 * на неё ложится сетка символов с сервера.
 */
public class MonitorRenderer implements BlockEntityRenderer<MonitorBlockEntity> {

    // Область экрана в пикселях блока (для монитора, смотрящего на север)
    private static final float X0 = 1, X1 = 15, Y0 = 3, Y1 = 15, Z = 11;
    private static final int GLYPH_W = 6, GLYPH_H = 9;

    private final Font font;

    public MonitorRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(MonitorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        ClientTerminals.Snapshot s = ClientTerminals.get(be.casePos());
        if (s == null) return;   // выключен — видна обычная тёмная текстура экрана

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

        // Начало координат — левый верхний угол экрана (если смотреть на монитор): X вправо, Y вниз,
        // Z — к зрителю (так же, как у табличек, иначе текст окажется изнанкой к игроку)
        pose.translate(X1, Y1, Z - 0.01f);
        pose.scale(-1, -1, -1);
        float areaW = X1 - X0, areaH = Y1 - Y0;
        float cellW = areaW / s.width(), cellH = areaH / s.height();

        int full = LightTexture.FULL_BRIGHT;
        Matrix4f m = pose.last().pose();
        VertexConsumer bg = buffers.getBuffer(RenderType.textBackground());
        for (int y = 0; y < s.height(); y++) {
            int x = 0;
            while (x < s.width()) {
                int color = s.bg()[y * s.width() + x] & 15, start = x;
                while (x < s.width() && (s.bg()[y * s.width() + x] & 15) == color) x++;
                quad(bg, m, start * cellW, y * cellH, x * cellW, (y + 1) * cellH, 0xFF000000 | Terminal.PALETTE[color], full);
            }
        }

        // Издалека мелкий текст большого экрана не разглядеть — рисуем только цветной фон
        var cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dist = cam.distanceToSqr(be.getBlockPos().getCenter());
        if (s.width() * s.height() > 2000 && dist > 8 * 8) {
            pose.popPose();
            return;
        }

        // Символы: каждый в своей клетке, чуть ближе к игроку, чем фон
        pose.translate(0, 0, 0.01f);
        float glyphScale = Math.min(cellW / GLYPH_W, cellH / GLYPH_H);
        for (int y = 0; y < s.height(); y++) {
            for (int x = 0; x < s.width(); x++) {
                int p = y * s.width() + x;
                byte ch = s.chars()[p];
                if (ch == ' ' || ch == 0) continue;
                String str = Glyphs.text(ch);
                pose.pushPose();
                pose.translate(x * cellW + (cellW - Glyphs.width(font, ch) * glyphScale) / 2, y * cellH + cellH * 0.08f, 0);
                pose.scale(glyphScale, glyphScale, 1);
                font.drawInBatch(str, 0, 0, 0xFF000000 | Terminal.PALETTE[s.fg()[p] & 15], false,
                        pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, full);
                pose.popPose();
            }
        }
        pose.popPose();
    }

    /** Прямоугольник фона; рисуем с обеих сторон, чтобы не зависеть от порядка вершин. */
    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int argb, int light) {
        vc.addVertex(m, x0, y0, 0).setColor(argb).setLight(light);
        vc.addVertex(m, x0, y1, 0).setColor(argb).setLight(light);
        vc.addVertex(m, x1, y1, 0).setColor(argb).setLight(light);
        vc.addVertex(m, x1, y0, 0).setColor(argb).setLight(light);
        vc.addVertex(m, x1, y0, 0).setColor(argb).setLight(light);
        vc.addVertex(m, x1, y1, 0).setColor(argb).setLight(light);
        vc.addVertex(m, x0, y1, 0).setColor(argb).setLight(light);
        vc.addVertex(m, x0, y0, 0).setColor(argb).setLight(light);
    }

    @Override
    public int getViewDistance() {
        return 32;
    }
}
