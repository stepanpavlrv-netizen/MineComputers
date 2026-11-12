package com.strikerr212.minepc.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Фон и «пиксели» экрана одной текстурой: каждая клетка — две точки по вертикали (верх и низ).
 * Пробелы, полублоки ▀ ▄, блок █ и штриховки ░▒▓ целиком помещаются в текстуру, поэтому экран из
 * 32 тысяч клеток рисуется одним прямоугольником, а не тысячами. Сверху рисуются только буквы и рамки.
 * Текстура обновляется, лишь когда пришёл новый кадр.
 */
final class PixelLayer {

    private static final Map<com.strikerr212.minepc.block.HostRef, PixelLayer> LAYERS = new HashMap<>();

    private final ResourceLocation id;
    private DynamicTexture texture;
    private int w, h;
    private ClientTerminals.Snapshot source;

    private PixelLayer(com.strikerr212.minepc.block.HostRef host) {
        id = ResourceLocation.fromNamespaceAndPath("minepc", "screen/" + Long.toHexString(host.pos().asLong()) + "_" + host.slot());
    }

    /** Клетка целиком нарисована текстурой (не нужны ни прямоугольники, ни буква). */
    static boolean covered(char c) {
        return c <= ' ' || c == '▀' || c == '▄' || c == '█' || (c >= '░' && c <= '▓');
    }

    /** Текстура для кадра (обновляется, если кадр новый). */
    static ResourceLocation of(ClientTerminals.Snapshot s) {
        PixelLayer layer = LAYERS.computeIfAbsent(s.host(), PixelLayer::new);
        layer.update(s);
        return layer.id;
    }

    static void release(com.strikerr212.minepc.block.HostRef pos) {
        PixelLayer l = LAYERS.remove(pos);
        if (l != null) l.close();
    }

    static void releaseAll() {
        for (PixelLayer l : LAYERS.values()) l.close();
        LAYERS.clear();
    }

    private void close() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(id);
            texture = null;
        }
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    /** RGB → ABGR, как хранит NativeImage. */
    private static int abgr(int rgb) {
        return 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16) & 0xFF;
    }

    private void update(ClientTerminals.Snapshot s) {
        if (s == source && texture != null) return;
        source = s;
        int sw = s.width(), sh = s.height();
        if (texture == null || w != sw || h != sh) {
            close();
            w = sw;
            h = sh;
            texture = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, w, h * 2, false));
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
        NativeImage img = texture.getPixels();
        if (img == null) return;
        char[] chars = s.chars();
        int[] fg = s.fg(), bg = s.bg();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = y * w + x;
                char c = chars[p];
                int top, bot;
                switch (c) {
                    case '▀' -> { top = fg[p]; bot = bg[p]; }
                    case '▄' -> { top = bg[p]; bot = fg[p]; }
                    case '█' -> { top = fg[p]; bot = fg[p]; }
                    case '░' -> top = bot = mix(bg[p], fg[p], 0.25f);
                    case '▒' -> top = bot = mix(bg[p], fg[p], 0.5f);
                    case '▓' -> top = bot = mix(bg[p], fg[p], 0.75f);
                    default -> top = bot = bg[p];
                }
                img.setPixelRGBA(x, 2 * y, abgr(top));
                img.setPixelRGBA(x, 2 * y + 1, abgr(bot));
            }
        }
        texture.upload();
    }
}
