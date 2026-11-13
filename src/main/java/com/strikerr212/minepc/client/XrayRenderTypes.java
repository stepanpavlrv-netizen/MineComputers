package com.strikerr212.minepc.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

import java.util.OptionalDouble;

/** Линии и заливка поверх всего (без проверки глубины) — для подсветки кабеля тестером сквозь стены. */
public final class XrayRenderTypes extends RenderType {

    private XrayRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                            Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    public static final RenderType LINES = create("minepc_xray_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES,
            4096, false, false, CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new LineStateShard(OptionalDouble.of(2.5)))
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .createCompositeState(false));

    public static final RenderType QUADS = create("minepc_xray_quads", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
            4096, false, false, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .createCompositeState(false));

    /** Заливка поверх блоков, но с проверкой глубины (точки карты покрытия на полу). */
    public static final RenderType FLAT = create("minepc_flat_quads", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
            8192, false, false, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));
}
