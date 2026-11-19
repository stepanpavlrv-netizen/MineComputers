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
    /** Кнопки управления головой и зумом — прячутся, пока открыта панель «Доступ». */
    private final java.util.List<Button> turnBtns = new java.util.ArrayList<>();

    // ----- панель «Доступ»: пароль и журнал входов -----
    private boolean accessMode;
    private Button accessBtn, backBtn, saveBtn, removeBtn;
    private net.minecraft.client.gui.components.EditBox passBox;
    private java.util.List<net.minecraft.nbt.CompoundTag> log = java.util.List.of();
    private boolean logLoaded;
    private int logScroll;
    private Component status;
    private long statusUntil;
    private static final int LOG_ROWS = 6;

    public CameraScreen(BlockPos pos) {
        super(Component.translatable("block.minepc.camera"));
        this.pos = pos;
    }

    /** Открыть сразу на панели «Доступ». */
    public CameraScreen withAccess() {
        accessMode = true;
        return this;
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
        turnBtns.clear();
        turn(Button.builder(Component.literal("▲"), b -> send(CameraActionPayload.TURN, 0, 10)).bounds(bx + 18, by, 18, 18));
        turn(Button.builder(Component.literal("◀"), b -> send(CameraActionPayload.TURN, -10, 0)).bounds(bx, by + 20, 18, 18));
        turn(Button.builder(Component.literal("●"), b -> send(CameraActionPayload.LOOK, 0, -15)).bounds(bx + 18, by + 20, 18, 18)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.minepc.camera.center"))));
        turn(Button.builder(Component.literal("▶"), b -> send(CameraActionPayload.TURN, 10, 0)).bounds(bx + 36, by + 20, 18, 18));
        turn(Button.builder(Component.literal("▼"), b -> send(CameraActionPayload.TURN, 0, -10)).bounds(bx + 18, by + 40, 18, 18));
        turn(Button.builder(Component.literal("+"), b -> zoom(-10)).bounds(bx, by + 66, 26, 18)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.minepc.camera.zoom_in"))));
        turn(Button.builder(Component.literal("−"), b -> zoom(10)).bounds(bx + 28, by + 66, 26, 18)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.minepc.camera.zoom_out"))));
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
        // «Доступ» — на месте подсказки про мышь
        accessBtn = addRenderableWidget(Button.builder(Component.translatable("gui.minepc.camera.access"), b -> setAccess(true))
                .bounds(bx, top + 140, 54, 18).build());
        backBtn = addRenderableWidget(Button.builder(Component.translatable("gui.minepc.camera.back"), b -> setAccess(false))
                .bounds(bx, top + 22, 54, 18).build());
        int px = left + 8, py = top + 22;
        String typed = passBox == null ? "" : passBox.getValue();
        passBox = new net.minecraft.client.gui.components.EditBox(font, px + 50, py + 7, 120, 16, Component.translatable("gui.minepc.camera.password"));
        passBox.setMaxLength(com.strikerr212.minepc.network.ModNetwork.MAX_CAM_PASSWORD);
        passBox.setFormatter((text, start) -> net.minecraft.util.FormattedCharSequence.forward("•".repeat(text.length()), net.minecraft.network.chat.Style.EMPTY));
        passBox.setValue(typed);
        addRenderableWidget(passBox);
        saveBtn = addRenderableWidget(Button.builder(Component.translatable("gui.minepc.camera.save"), b -> {
            String pw = passBox.getValue();
            if (pw.isEmpty()) return;
            PacketDistributor.sendToServer(new com.strikerr212.minepc.network.CameraAccessPayload(pos, pw, new net.minecraft.nbt.CompoundTag()));
            passBox.setValue("");
            say(Component.translatable("gui.minepc.camera.saved"));
        }).bounds(px + 176, py + 6, 74, 18).build());
        removeBtn = addRenderableWidget(Button.builder(Component.translatable("gui.minepc.camera.remove"), b -> {
            PacketDistributor.sendToServer(new com.strikerr212.minepc.network.CameraAccessPayload(pos, "", new net.minecraft.nbt.CompoundTag()));
            say(Component.translatable("gui.minepc.camera.removed"));
        }).bounds(px + 6, py + 40, 100, 18).build());
        setAccess(accessMode);
        updateButtons();
    }

    private void turn(Button.Builder b) {
        turnBtns.add(addRenderableWidget(b.build()));
    }

    private void say(Component c) {
        status = c;
        statusUntil = System.currentTimeMillis() + 4000;
    }

    private void setAccess(boolean on) {
        accessMode = on;
        for (Button b : turnBtns) b.visible = !on;
        accessBtn.visible = !on;
        backBtn.visible = on;
        passBox.visible = on;
        saveBtn.visible = on;
        removeBtn.visible = on;
        if (on) {
            setFocused(passBox);
            passBox.setFocused(true);
            if (!logLoaded) PacketDistributor.sendToServer(new com.strikerr212.minepc.network.CameraAccessPayload(pos,
                    com.strikerr212.minepc.network.CameraAccessPayload.ASK, new net.minecraft.nbt.CompoundTag()));
        } else {
            passBox.setFocused(false);
            setFocused(null);
        }
    }

    /** Журнал входов пришёл с сервера (с сервера — новые в конце, показываем новые сверху). */
    public void access(com.strikerr212.minepc.network.CameraAccessPayload p) {
        if (!p.pos().equals(pos)) return;
        java.util.List<net.minecraft.nbt.CompoundTag> l = new java.util.ArrayList<>();
        for (var t : p.data().getList("Log", net.minecraft.nbt.Tag.TAG_COMPOUND)) l.add((net.minecraft.nbt.CompoundTag) t);
        java.util.Collections.reverse(l);
        log = l;
        logLoaded = true;
        logScroll = Math.max(0, Math.min(logScroll, log.size() - LOG_ROWS));
    }

    private static String when(long t) {
        java.time.ZonedDateTime z = java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.systemDefault());
        java.time.LocalDate today = java.time.LocalDate.now();
        if (z.toLocalDate().equals(today)) return String.format(java.util.Locale.ROOT, "%02d:%02d", z.getHour(), z.getMinute());
        if (z.toLocalDate().equals(today.minusDays(1))) return Component.translatable("gui.minepc.camera.yesterday").getString();
        return String.format(java.util.Locale.ROOT, "%02d.%02d", z.getDayOfMonth(), z.getMonthValue());
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
        if (removeBtn != null) removeBtn.active = c.locked();
        if (saveBtn != null) saveBtn.active = !passBox.getValue().isEmpty();
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
        if (accessMode) return;     // картинка спрятана — кадры не нужны
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
        if (accessMode) {
            // фон панели «Доступ» — под кнопками и полем пароля (они рисуются после фона)
            int px = left + 8, py = top + 22;
            g.fill(px - 1, py - 1, px + PW + 1, py + PH + 1, 0xFF0B0C0E);
            g.fill(px, py, px + PW, py + PH, 0xFF1D1F23);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        CameraBlockEntity c = be();
        if (c == null) return;
        String name = c.label().isEmpty() ? Component.translatable("block.minepc.camera").getString() : c.label();
        g.drawString(font, name, left + 8, top + 7, 0xFFE6E8EC, false);
        String addr = c.address();
        int ax = left + W - 8 - font.width(addr);
        g.drawString(font, addr, ax, top + 7, 0xFF8FE39A, false);
        if (c.locked()) {
            Component lk = Component.translatable("gui.minepc.camera.locked");
            int lx = ax - 8 - font.width(lk);
            g.drawString(font, lk, lx, top + 7, 0xFFFFCF5A, false);
            drawLock(g, lx - 10, top + 6);
        }
        int px = left + 8, py = top + 22;
        if (accessMode) {
            renderAccess(g, c, px, py);
            return;
        }
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
        // нижняя строка
        Component hint = Component.translatable("gui.minepc.camera.hint");
        g.drawString(font, hint, left + 8, top + H - 13, 0xFF8A9096, false);
    }

    /** Замочек 7×8 точек. */
    private static void drawLock(GuiGraphics g, int x, int y) {
        int c = 0xFFFFCF5A;
        g.fill(x + 1, y, x + 6, y + 1, c);
        g.fill(x + 1, y, x + 2, y + 4, c);
        g.fill(x + 5, y, x + 6, y + 4, c);
        g.fill(x, y + 3, x + 7, y + 8, c);
        g.fill(x + 3, y + 5, x + 4, y + 7, 0xFF2A2D31);
    }

    private void renderAccess(GuiGraphics g, CameraBlockEntity c, int px, int py) {
        g.drawString(font, Component.translatable("gui.minepc.camera.password"), px + 6, py + 11, 0xFFE6E8EC, false);
        // надёжность того, что набрано
        int st = CameraBlockEntity.strength(passBox.getValue());
        int my = py + 28;
        int[] colors = {0xFF8A9096, 0xFFFF6A5A, 0xFFFFCF5A, 0xFFB5E36A, 0xFF8FE39A};
        if (st == 0) {
            g.drawString(font, Component.translatable(c.locked() ? "gui.minepc.camera.pw_set" : "gui.minepc.camera.pw_none"), px + 6, my, colors[0], false);
        } else {
            for (int i = 0; i < 4; i++) g.fill(px + 6 + i * 15, my + 2, px + 19 + i * 15, my + 6, i < st ? colors[st] : 0xFF444444);
            g.drawString(font, Component.translatable("gui.minepc.camera.pw_" + st), px + 70, my, colors[st], false);
        }
        // строка у «Убрать пароль» (после действия — что сделано)
        boolean showStatus = status != null && System.currentTimeMillis() < statusUntil;
        Component side = showStatus ? status : Component.translatable(c.locked() ? "gui.minepc.camera.remove_hint" : "gui.minepc.camera.open_hint");
        g.drawString(font, side, px + 112, py + 45, showStatus ? 0xFF8FE39A : 0xFF6A7076, false);
        // журнал входов по сети
        g.drawString(font, Component.translatable("gui.minepc.camera.log", CameraBlockEntity.LOG_MAX), px + 6, py + 66, 0xFF8A9096, false);
        int ly = py + 78;
        if (!logLoaded) {
            g.drawString(font, "…", px + 6, ly, 0xFF6A7076, false);
        } else if (log.isEmpty()) {
            g.drawString(font, Component.translatable("gui.minepc.camera.log_empty"), px + 6, ly, 0xFF6A7076, false);
        } else {
            for (int i = 0; i < LOG_ROWS && logScroll + i < log.size(); i++) {
                var e = log.get(logScroll + i);
                int y = ly + i * 10;
                boolean ok = e.getBoolean("Ok");
                g.drawString(font, when(e.getLong("T")), px + 6, y, 0xFFE6E8EC, false);
                g.drawString(font, e.getString("A"), px + 42, y, 0xFFE6E8EC, false);
                String who = e.getString("C");
                g.drawString(font, font.plainSubstrByWidth(who.isEmpty() ? "—" : who, PW - 100 - 22), px + 100, y, 0xFFE6E8EC, false);
                g.drawString(font, ok ? "✓" : "✗", px + PW - 14, y, ok ? 0xFF8FE39A : 0xFFFF6A5A, false);
            }
            if (log.size() > LOG_ROWS) {
                String more = (logScroll + 1) + "–" + Math.min(log.size(), logScroll + LOG_ROWS) + " / " + log.size();
                g.drawString(font, more, px + PW - 6 - font.width(more), py + 66, 0xFF6A7076, false);
            }
        }
        // справа: рядом с камерой пароль не нужен
        int bx = left + 8 + PW + 8, y = top + 48;
        for (var line : font.split(Component.translatable("gui.minepc.camera.near"), left + W - bx - 4)) {
            g.drawString(font, line, bx, y, 0xFF8A9096, false);
            y += 10;
        }
        g.drawString(font, Component.translatable("gui.minepc.camera.access_hint"), left + 8, top + H - 13, 0xFF8A9096, false);
    }

    private boolean inPreview(double mx, double my) {
        return mx >= left + 8 && mx < left + 8 + PW && my >= top + 22 && my < top + 22 + PH;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && !accessMode && inPreview(mx, my)) {
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
        if (accessMode && inPreview(mx, my)) {
            logScroll = Math.max(0, Math.min(log.size() - LOG_ROWS, logScroll - (int) Math.signum(sy)));
            return true;
        }
        if (inPreview(mx, my)) {
            zoom((float) (-sy * 5));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (accessMode) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (saveBtn.active) saveBtn.onPress();
                return true;
            }
            if (key != GLFW.GLFW_KEY_ESCAPE && passBox.isFocused()) {
                passBox.keyPressed(key, scan, mods);
                return true;     // буквы — в поле пароля, а не на поворот камеры (WASD) и не «E» — закрыть
            }
            return super.keyPressed(key, scan, mods);
        }
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
