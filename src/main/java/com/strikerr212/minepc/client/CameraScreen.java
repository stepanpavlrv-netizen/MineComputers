package com.strikerr212.minepc.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.lan.CameraBlockEntity;
import com.strikerr212.minepc.network.CameraActionPayload;
import com.strikerr212.minepc.network.CameraFramePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Окно камеры: живое изображение (сервер рисует кадр по снимку мира и присылает), поворот стрелками, кнопками
 * или перетаскиванием мышью по картинке, зум колесом, разрешение, частота, ночное видение, патруль.
 */
public class CameraScreen extends Screen {

    private static final int W = 334, H = 214;
    private static final int PW = 256, PH = 144;
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "camera_preview");

    private final BlockPos pos;
    private int left, top;
    private DynamicTexture texture;
    private int texW, texH;
    private boolean loading = true;
    private long lastRequest, lastFrame;
    private int frames, shownFps;
    private long fpsWindow;
    private boolean dragging;
    private double dragDx, dragDy;
    private Button resBtn, fpsBtn, nightBtn, patrolBtn;

    public CameraScreen(BlockPos pos) {
        super(Component.translatable("block.minepc.camera"));
        this.pos = pos;
    }

    private CameraBlockEntity be() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof CameraBlockEntity c ? c : null;
    }

    private void send(int action, float a, float b) {
        PacketDistributor.sendToServer(new CameraActionPayload(pos, action, a, b));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int bx = left + 8 + PW + 8, by = top + 22;
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> send(CameraActionPayload.TURN, 0, 10)).bounds(bx + 18, by, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> send(CameraActionPayload.TURN, -10, 0)).bounds(bx, by + 20, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("●"), b -> send(CameraActionPayload.LOOK, 0, -15)).bounds(bx + 18, by + 20, 18, 18)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.minepc.camera.center"))).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> send(CameraActionPayload.TURN, 10, 0)).bounds(bx + 36, by + 20, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> send(CameraActionPayload.TURN, 0, -10)).bounds(bx + 18, by + 40, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> zoom(-10)).bounds(bx, by + 66, 26, 18)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.minepc.camera.zoom_in"))).build());
        addRenderableWidget(Button.builder(Component.literal("−"), b -> zoom(10)).bounds(bx + 28, by + 66, 26, 18)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.minepc.camera.zoom_out"))).build());
        int ry = top + 22 + PH + 6, rw = (PW + 62) / 4 - 2;
        resBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            CameraBlockEntity c = be();
            if (c != null) send(CameraActionPayload.RES, (c.resIndex() + (hasShiftDown() ? CameraBlockEntity.RES.length - 1 : 1)) % CameraBlockEntity.RES.length, 0);
        }).bounds(left + 8, ry, rw, 18).build());
        fpsBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            CameraBlockEntity c = be();
            if (c != null) send(CameraActionPayload.FPS, (c.fpsIndex() + (hasShiftDown() ? CameraBlockEntity.FPS.length - 1 : 1)) % CameraBlockEntity.FPS.length, 0);
        }).bounds(left + 8 + (rw + 2), ry, rw, 18).build());
        nightBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            CameraBlockEntity c = be();
            if (c != null) send(CameraActionPayload.NIGHT, c.night() ? 0 : 1, 0);
        }).bounds(left + 8 + 2 * (rw + 2), ry, rw, 18).build());
        patrolBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            CameraBlockEntity c = be();
            if (c != null) send(CameraActionPayload.PATROL, c.patrol() ? 0 : 1, 0);
        }).bounds(left + 8 + 3 * (rw + 2), ry, rw, 18).build());
        updateButtons();
    }

    private void zoom(float d) {
        CameraBlockEntity c = be();
        if (c != null) send(CameraActionPayload.ZOOM, c.fov() + d, 0);
    }

    private void updateButtons() {
        CameraBlockEntity c = be();
        if (c == null || resBtn == null) return;
        resBtn.setMessage(Component.literal(c.width() + "×" + c.height()));
        fpsBtn.setMessage(Component.translatable("gui.minepc.camera.fps", c.frameRate()));
        nightBtn.setMessage(Component.translatable(c.night() ? "gui.minepc.camera.night_on" : "gui.minepc.camera.night_off"));
        patrolBtn.setMessage(Component.translatable(c.patrol() ? "gui.minepc.camera.patrol_on" : "gui.minepc.camera.patrol_off"));
    }

    @Override
    public void tick() {
        CameraBlockEntity c = be();
        if (c == null || minecraft.player == null || minecraft.player.distanceToSqr(pos.getCenter()) > 64) {
            onClose();
            return;
        }
        updateButtons();
        long now = System.currentTimeMillis();
        int fps = Math.max(1, Math.min(20, c.frameRate()));
        // следующий кадр — когда пришёл прошлый и прошло 1/fps (или 1 с — вдруг кадр потерялся)
        if ((lastFrame >= lastRequest && now - lastRequest >= 1000 / fps) || now - lastRequest > 1000) {
            lastRequest = now;
            send(CameraActionPayload.FRAME, 0, 0);
        }
        if (dragging && (Math.abs(dragDx) >= 1 || Math.abs(dragDy) >= 1)) {
            send(CameraActionPayload.TURN, (float) dragDx, (float) dragDy);
            dragDx = dragDy = 0;
        }
    }

    /** Пришёл кадр с сервера. */
    public void frame(CameraFramePayload p) {
        if (!p.pos().equals(pos)) return;
        lastFrame = System.currentTimeMillis();
        if (p.w() <= 0) {
            loading = true;
            return;
        }
        loading = false;
        int[] px = CameraFramePayload.unpack(p.data(), p.w() * p.h());
        if (texture == null || texW != p.w() || texH != p.h()) {
            if (texture != null) texture.close();
            texture = new DynamicTexture(p.w(), p.h(), false);
            texW = p.w();
            texH = p.h();
            minecraft.getTextureManager().register(TEX, texture);
        }
        NativeImage img = texture.getPixels();
        if (img == null) return;
        for (int y = 0; y < p.h(); y++) {
            for (int x = 0; x < p.w(); x++) {
                int c = px[y * p.w() + x];
                img.setPixelRGBA(x, y, 0xFF000000 | (c & 0xFF) << 16 | (c & 0xFF00) | (c >> 16) & 0xFF);
            }
        }
        texture.upload();
        frames++;
        if (lastFrame - fpsWindow >= 1000) {
            shownFps = frames;
            frames = 0;
            fpsWindow = lastFrame;
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (texture != null) {
            minecraft.getTextureManager().release(TEX);
            texture = null;
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, 0xFF1B1B1B);
        g.fill(left, top, left + W, top + H, 0xFF2A2D31);
        g.fill(left, top, left + W, top + 1, 0xFF4A4E54);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        CameraBlockEntity c = be();
        if (c == null) return;
        String name = c.label().isEmpty() ? Component.translatable("block.minepc.camera").getString() : c.label();
        g.drawString(font, name, left + 8, top + 7, 0xFFE6E8EC, false);
        String addr = c.address();
        g.drawString(font, addr, left + W - 8 - font.width(addr), top + 7, 0xFF8FE39A, false);
        int px = left + 8, py = top + 22;
        g.fill(px - 1, py - 1, px + PW + 1, py + PH + 1, 0xFF0B0C0E);
        if (texture != null && !loading) {
            g.blit(TEX, px, py, PW, PH, 0, 0, texW, texH, texW, texH);
        } else {
            Component t = Component.translatable("gui.minepc.camera.loading");
            g.drawString(font, t, px + (PW - font.width(t)) / 2, py + PH / 2 - 4, 0xFF8A9096, false);
        }
        // поверх картинки: запись, частота, время
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        if (blink) g.fill(px + 5, py + 5, px + 10, py + 10, 0xFFFF3A2A);
        g.drawString(font, "REC  " + shownFps + " fps", px + 13, py + 4, 0xFFFFFFFF, true);
        String angle = String.format(java.util.Locale.ROOT, "%+.0f° %+.0f°  ×%.1f", c.yaw(), c.pitch(), 70f / c.fov());
        g.drawString(font, angle, px + PW - 4 - font.width(angle), py + 4, 0xFFFFFFFF, true);
        // справа под кнопками: поток
        int bx = left + 8 + PW + 8;
        long kb = c.bytesPerSecond() / 1024;
        g.drawString(font, Component.translatable("gui.minepc.camera.stream"), bx, top + 112, 0xFF8A9096, false);
        g.drawString(font, kb + " КБ/с", bx, top + 123, 0xFFE6E8EC, false);
        g.drawString(font, Component.translatable("gui.minepc.camera.drag"), bx, top + 140, 0xFF6A7076, false);
        g.drawString(font, Component.translatable("gui.minepc.camera.drag2"), bx, top + 150, 0xFF6A7076, false);
        // нижняя строка
        Component hint = Component.translatable("gui.minepc.camera.hint");
        g.drawString(font, hint, left + 8, top + H - 13, 0xFF8A9096, false);
    }

    private boolean inPreview(double mx, double my) {
        return mx >= left + 8 && mx < left + 8 + PW && my >= top + 22 && my < top + 22 + PH;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && inPreview(mx, my)) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            CameraBlockEntity c = be();
            float k = c == null ? 0.4f : c.fov() / PH;     // при зуме — точнее
            dragDx -= dx * k;
            dragDy += dy * k;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging && button == 0) {
            dragging = false;
            if (Math.abs(dragDx) > 0.1 || Math.abs(dragDy) > 0.1) send(CameraActionPayload.TURN, (float) dragDx, (float) dragDy);
            dragDx = dragDy = 0;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (inPreview(mx, my)) {
            zoom((float) (-sy * 5));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> send(CameraActionPayload.TURN, -5, 0);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> send(CameraActionPayload.TURN, 5, 0);
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> send(CameraActionPayload.TURN, 0, 5);
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> send(CameraActionPayload.TURN, 0, -5);
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> zoom(-5);
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> zoom(5);
            default -> {
                return super.keyPressed(key, scan, mods);
            }
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
