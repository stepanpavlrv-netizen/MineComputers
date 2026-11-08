package com.strikerr212.minepc.client;

import com.strikerr212.minepc.computer.Cp1251;
import com.strikerr212.minepc.computer.Terminal;
import com.strikerr212.minepc.network.TerminalInputPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Экран компьютера крупно: картинка с сервера + ввод с клавиатуры.
 * Esc — закрыть, Ctrl+T — прервать программу, Ctrl+V — вставить текст из буфера обмена.
 */
public class TerminalScreen extends Screen {

    private static final int CELL_W = 6, CELL_H = 9;
    private static final int BEZEL = 0xFF1B1D22, BEZEL_LIGHT = 0xFF2C3038, TEXT_DIM = 0xFF8A9099;

    private final BlockPos casePos;
    private Button powerButton;
    private int termX, termY;
    private float scale = 1;

    public TerminalScreen(BlockPos casePos) {
        super(Component.translatable("gui.minepc.terminal"));
        this.casePos = casePos;
    }

    @Override
    protected void init() {
        ClientTerminals.requestFull(casePos);
        powerButton = addRenderableWidget(Button.builder(Component.translatable("gui.minepc.power"),
                b -> send(TerminalInputPayload.POWER, "", 0)).bounds(width - 66, 6, 60, 16).build());
    }

    private void send(int kind, String text, int flags) {
        PacketDistributor.sendToServer(new TerminalInputPayload(casePos, kind, text, flags));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ======================= Отрисовка =======================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);   // фон и кнопка питания
        ClientTerminals.Snapshot s = ClientTerminals.get(casePos);
        int tw = (s != null ? s.width() : 50) * CELL_W, th = (s != null ? s.height() : 16) * CELL_H;
        // Масштаб: экран занимает как можно больше места (большие экраны — почти во всё окно)
        scale = Math.min(2f, Math.min((width - 12f) / tw, (height - 56f) / th));
        if (scale > 1) scale = (float) Math.floor(scale * 2) / 2;   // целые и половинные — чётче
        scale = Math.max(0.25f, scale);
        int sw = Math.round(tw * scale), sh = Math.round(th * scale);
        termX = (width - sw) / 2;
        termY = (height - sh) / 2 + 6;

        // Рамка монитора
        g.fill(termX - 8, termY - 8, termX + sw + 8, termY + sh + 8, BEZEL);
        g.fill(termX - 9, termY - 9, termX + sw + 9, termY - 8, BEZEL_LIGHT);
        g.fill(termX - 2, termY - 2, termX + sw + 2, termY + sh + 2, 0xFF000000);

        if (s == null) {
            g.drawCenteredString(font, Component.translatable("gui.minepc.terminal.off"), width / 2, termY + sh / 2 - 4, TEXT_DIM);
        } else {
            g.pose().pushPose();
            g.pose().translate(termX, termY, 0);
            g.pose().scale(scale, scale, 1);
            drawTerminal(g, s);
            g.pose().popPose();
        }

        boolean keyboard = ClientTerminals.keyboard(casePos);
        Component hint = keyboard ? Component.translatable("gui.minepc.terminal.hint")
                : Component.translatable("gui.minepc.terminal.no_keyboard");
        g.drawCenteredString(font, hint, width / 2, termY + sh + 14, keyboard ? TEXT_DIM : 0xFFE0A040);
        g.drawString(font, title, 8, 10, 0xFFE6E8EB, false);
    }

    /** Нарисовать сетку символов (общая для окна и, в упрощённом виде, для монитора в мире). */
    private void drawTerminal(GuiGraphics g, ClientTerminals.Snapshot s) {
        // Всё рисуется одной пачкой: на экране 160×50 это 8000 символов за кадр
        g.drawManaged(() -> drawCells(g, s));
    }

    private void drawCells(GuiGraphics g, ClientTerminals.Snapshot s) {
        int w = s.width(), h = s.height();
        for (int y = 0; y < h; y++) {
            // Фон: подряд идущие клетки одного цвета — одним прямоугольником
            int x = 0;
            while (x < w) {
                int color = s.bg()[y * w + x] & 15, start = x;
                while (x < w && (s.bg()[y * w + x] & 15) == color) x++;
                g.fill(start * CELL_W, y * CELL_H, x * CELL_W, (y + 1) * CELL_H, 0xFF000000 | Terminal.PALETTE[color]);
            }
            for (x = 0; x < w; x++) {
                int p = y * w + x;
                byte ch = s.chars()[p];
                if (ch == ' ' || ch == 0) continue;
                int cx = x * CELL_W + (CELL_W - Glyphs.width(font, ch)) / 2;
                g.drawString(font, Glyphs.text(ch), cx, y * CELL_H + 1, 0xFF000000 | Terminal.PALETTE[s.fg()[p] & 15], false);
            }
        }
        // Мигающий курсор
        if (s.blink() && s.cursorX() >= 0 && s.cursorX() < w && s.cursorY() >= 0 && s.cursorY() < h
                && (System.currentTimeMillis() / 400) % 2 == 0) {
            int cx = s.cursorX() * CELL_W, cy = s.cursorY() * CELL_H;
            g.fill(cx, cy + CELL_H - 2, cx + CELL_W, cy + CELL_H - 1, 0xFFFFFFFF);
        }
    }

    // ======================= Клавиатура =======================

    private static int flags(int modifiers) {
        return ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 ? 1 : 0) | ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 2 : 0);
    }

    /** Имя клавиши для программ ("enter", "up", "a", "f1"...) или null. */
    static String keyName(int key) {
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) return String.valueOf((char) ('a' + key - GLFW.GLFW_KEY_A));
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) return String.valueOf((char) ('0' + key - GLFW.GLFW_KEY_0));
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12) return "f" + (key - GLFW.GLFW_KEY_F1 + 1);
        return switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> "enter";
            case GLFW.GLFW_KEY_BACKSPACE -> "backspace";
            case GLFW.GLFW_KEY_DELETE -> "delete";
            case GLFW.GLFW_KEY_TAB -> "tab";
            case GLFW.GLFW_KEY_LEFT -> "left";
            case GLFW.GLFW_KEY_RIGHT -> "right";
            case GLFW.GLFW_KEY_UP -> "up";
            case GLFW.GLFW_KEY_DOWN -> "down";
            case GLFW.GLFW_KEY_HOME -> "home";
            case GLFW.GLFW_KEY_END -> "end";
            case GLFW.GLFW_KEY_PAGE_UP -> "pageUp";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "pageDown";
            case GLFW.GLFW_KEY_INSERT -> "insert";
            case GLFW.GLFW_KEY_SPACE -> "space";
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> "ctrl";
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> "shift";
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> "alt";
            default -> null;
        };
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (!ClientTerminals.keyboard(casePos)) return super.keyPressed(keyCode, scanCode, modifiers);
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_V && minecraft != null) {
            String clip = minecraft.keyboardHandler.getClipboard();
            if (clip.length() > TerminalInputPayload.MAX_TEXT) clip = clip.substring(0, TerminalInputPayload.MAX_TEXT);
            send(TerminalInputPayload.PASTE, clip.replace("\r", ""), 0);
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_T) {
            send(TerminalInputPayload.TERMINATE, "", 0);
            return true;
        }
        String name = keyName(keyCode);
        if (name != null) send(TerminalInputPayload.KEY, name, flags(modifiers));
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ClientTerminals.keyboard(casePos)) {
            String name = keyName(keyCode);
            if (name != null) send(TerminalInputPayload.KEY_UP, name, flags(modifiers));
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!ClientTerminals.keyboard(casePos)) return false;
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) return true;   // Ctrl+буква — это команда, а не текст
        if (Cp1251.supports(codePoint)) send(TerminalInputPayload.CHAR, String.valueOf(codePoint), 0);
        return true;
    }
}
