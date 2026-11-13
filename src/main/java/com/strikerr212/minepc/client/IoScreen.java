package com.strikerr212.minepc.client;

import com.strikerr212.minepc.lan.IoControllerBlockEntity;
import com.strikerr212.minepc.network.IoActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Окно контроллера ввода-вывода: шесть сторон (щелчок по плашке — вход/выход, по шкале выхода — уровень),
 * датчики и журнал. Данные — из блок-сущности на клиенте (сервер присылает её при каждом изменении).
 */
public class IoScreen extends Screen {

    private static final int W = 256, H = 206;
    private static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, DARK = 0xFF555555, OUTLINE = 0xFF1B1B1B,
            SLOT = 0xFF8B8B8B, SLOT_TL = 0xFF373737, TEXT = 0xFF3F3F3F, RED_ON = 0xFFE0241C, RED_OFF = 0xFF5A2A28,
            OUT_BADGE = 0xFF3F6FB0, IN_BADGE = 0xFF7A5A2A;
    /** Порядок плашек: север, юг, запад, восток, верх, низ (как на макете). */
    private static final Direction[] ORDER = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN};
    private static final int TW = 76, TH = 44;

    private final BlockPos pos;
    private int left, top;

    public IoScreen(BlockPos pos) {
        super(Component.translatable("block.minepc.io_controller"));
        this.pos = pos;
    }

    private IoControllerBlockEntity be() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof IoControllerBlockEntity io ? io : null;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    @Override
    public void tick() {
        if (be() == null || minecraft.player == null || minecraft.player.distanceToSqr(pos.getCenter()) > 64) onClose();
    }

    private int tileX(int k) {
        return left + 8 + (k % 3) * (TW + 4);
    }

    private int tileY(int k) {
        return top + 30 + (k / 3) * (TH + 4);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, OUTLINE);
        g.fill(left, top, left + W, top + H, PANEL);
        g.fill(left, top, left + W, top + 2, LIGHT);
        g.fill(left, top, left + 2, top + H, LIGHT);
        g.fill(left, top + H - 2, left + W, top + H, DARK);
        g.fill(left + W - 2, top, left + W, top + H, DARK);
    }

    private void slot(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, SLOT);
        g.fill(x0, y0, x1, y0 + 1, SLOT_TL);
        g.fill(x0, y0, x0 + 1, y1, SLOT_TL);
        g.fill(x0, y1 - 1, x1, y1, LIGHT);
        g.fill(x1 - 1, y0, x1, y1, LIGHT);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        IoControllerBlockEntity io = be();
        if (io == null) return;
        String name = io.label().isEmpty() ? Component.translatable("block.minepc.io_controller").getString() : io.label();
        g.drawString(font, name, left + 8, top + 7, TEXT, false);
        String addr = io.address();
        g.drawString(font, addr, left + W - 8 - font.width(addr), top + 7, 0xFF1D5C25, false);
        g.drawString(font, Component.translatable("gui.minepc.io.hint"), left + 8, top + 18, 0xFF6A6A6A, false);
        for (int k = 0; k < 6; k++) drawTile(g, io, k, mouseX, mouseY);
        // датчики
        int sy = top + 30 + 2 * (TH + 4) + 2;
        String players = io.players().isEmpty() ? Component.translatable("gui.minepc.io.nobody").getString() : String.join(", ", io.players());
        g.drawString(font, Component.translatable("gui.minepc.io.players", players), left + 8, sy, TEXT, false);
        g.drawString(font, Component.translatable("gui.minepc.io.light", io.light()), left + W - 8 - font.width(Component.translatable("gui.minepc.io.light", io.light())), sy, TEXT, false);
        // журнал
        int ly = sy + 12;
        slot(g, left + 8, ly, left + W - 8, top + H - 8);
        int y = ly + 3;
        for (String line : io.log()) {
            if (y > top + H - 18) break;
            String t = line.length() > 2 && font.width(line) > W - 24 ? font.plainSubstrByWidth(line, W - 28) + "…" : line;
            g.drawString(font, t, left + 12, y, 0xFFFFFFFF, true);
            y += 10;
        }
        if (io.log().isEmpty()) g.drawString(font, Component.translatable("gui.minepc.io.log_empty"), left + 12, y, 0xFFDDDDDD, true);
    }

    private void drawTile(GuiGraphics g, IoControllerBlockEntity io, int k, int mx, int my) {
        Direction d = ORDER[k];
        int i = d.get3DDataValue();
        int x = tileX(k), y = tileY(k);
        slot(g, x, y, x + TW, y + TH);
        g.drawString(font, Component.translatable("gui.minepc.io.side." + IoControllerBlockEntity.SIDE_NAMES[i]), x + 4, y + 4, 0xFFFFFFFF, true);
        boolean out = io.isOutput(i);
        Component badge = Component.translatable(out ? "gui.minepc.io.out" : "gui.minepc.io.in");
        int bw = font.width(badge) + 6, bx = x + TW - 3 - bw;
        g.fill(bx, y + 3, bx + bw, y + 13, out ? OUT_BADGE : IN_BADGE);
        if (mx >= bx && mx < bx + bw && my >= y + 3 && my < y + 13) g.fill(bx, y + 3, bx + bw, y + 13, 0x40FFFFFF);
        g.drawString(font, badge, bx + 3, y + 4, 0xFFFFFFFF, false);
        int level = out ? io.outLevel(i) : io.input(i);
        int bar0 = x + 4, barW = TW - 8, segW = barW / 15;
        for (int s = 0; s < 15; s++) {
            int sx = bar0 + s * segW;
            g.fill(sx, y + 17, sx + segW - 1, y + 25, s < level ? RED_ON : RED_OFF);
        }
        if (out && my >= y + 15 && my < y + 27 && mx >= bar0 && mx < bar0 + segW * 15) {
            int s = (mx - bar0) / segW;
            g.fill(bar0 + s * segW, y + 15, bar0 + s * segW + segW - 1, y + 27, 0x60FFFFFF);
        }
        BlockState n = minecraft.level.getBlockState(pos.relative(d));
        Component what = n.isAir() ? Component.translatable("gui.minepc.io.air") : n.getBlock().getName();
        int fill = io.fill(i);
        String right = fill >= 0 ? fill + "/15" : String.valueOf(level);
        int rw = font.width(right);
        String w = what.getString();
        if (font.width(w) > TW - 12 - rw) w = font.plainSubstrByWidth(w, TW - 14 - rw) + "…";
        g.drawString(font, w, x + 4, y + 31, 0xFFE8E8E8, true);
        g.drawString(font, right, x + TW - 4 - rw, y + 31, fill >= 0 ? 0xFFFFE9A0 : 0xFFE8E8E8, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        IoControllerBlockEntity io = be();
        if (io != null && button == 0) {
            for (int k = 0; k < 6; k++) {
                int i = ORDER[k].get3DDataValue();
                int x = tileX(k), y = tileY(k);
                Component badge = Component.translatable(io.isOutput(i) ? "gui.minepc.io.out" : "gui.minepc.io.in");
                int bw = font.width(badge) + 6, bx = x + TW - 3 - bw;
                if (mouseX >= bx && mouseX < bx + bw && mouseY >= y + 3 && mouseY < y + 13) {
                    send(i, IoActionPayload.MODE, 0);
                    return true;
                }
                int bar0 = x + 4, segW = (TW - 8) / 15;
                if (io.isOutput(i) && mouseY >= y + 15 && mouseY < y + 27 && mouseX >= bar0 && mouseX < bar0 + segW * 15) {
                    int s = (int) (mouseX - bar0) / segW;
                    int level = io.outLevel(i) == s + 1 ? 0 : s + 1;   // щелчок по горящему краю — выключить
                    send(i, IoActionPayload.LEVEL, level);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void send(int side, int action, int value) {
        PacketDistributor.sendToServer(new IoActionPayload(pos, side, action, value));
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
