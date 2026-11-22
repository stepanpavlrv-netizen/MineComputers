package com.strikerr212.minepc.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.strikerr212.minepc.block.HostRef;
import com.strikerr212.minepc.computer.Terminal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Картинка поверх экрана (кадр игры в высоком разрешении) — текстура, обновляется только с новым кадром. */
final class ImageLayer {

    private static final Map<HostRef, ImageLayer> LAYERS = new HashMap<>();

    private final ResourceLocation id;
    private DynamicTexture texture;
    private int w, h, version = -1;

    private ImageLayer(HostRef host) {
        id = ResourceLocation.fromNamespaceAndPath("minepc", "screen_image/" + Long.toHexString(host.pos().asLong()) + "_" + host.slot());
    }

    /** Текстура для картинки (обновляется, если картинка новая). */
    static ResourceLocation of(HostRef host, Terminal.Image im) {
        ImageLayer layer = LAYERS.computeIfAbsent(host, ImageLayer::new);
        layer.update(im);
        return layer.id;
    }

    static void releaseAll() {
        for (ImageLayer l : LAYERS.values()) l.close();
        LAYERS.clear();
    }

    private void close() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(id);
            texture = null;
        }
    }

    private void update(Terminal.Image im) {
        if (texture != null && im.version() == version) return;
        version = im.version();
        if (texture == null || w != im.w() || h != im.h()) {
            close();
            w = im.w();
            h = im.h();
            texture = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, w, h, false));
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
        NativeImage img = texture.getPixels();
        if (img == null) return;
        int[] rgb = im.rgb();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = rgb[y * w + x];
                img.setPixelRGBA(x, y, 0xFF000000 | (c & 0xFF) << 16 | (c & 0xFF00) | (c >> 16) & 0xFF);
            }
        }
        texture.upload();
    }
}
