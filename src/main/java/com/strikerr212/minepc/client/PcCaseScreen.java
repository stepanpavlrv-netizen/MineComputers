package com.strikerr212.minepc.client;

import com.strikerr212.minepc.block.PcCaseBlockEntity;
import com.strikerr212.minepc.hardware.HardwarePresets;
import com.strikerr212.minepc.hardware.MotherboardData;
import com.strikerr212.minepc.menu.PcCaseMenu;
import com.strikerr212.minepc.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Окно системного блока.
 *
 * ┌───────────────────────────┬──────────────────────────────┐
 * │ плата ЦП кулер видео БП   │ [Питание]  Работает          │
 * │ ОЗУ ×4          M.2 ×2    │ характеристики, скорость,    │
 * │ HDD ×2  SSD ×2  USB ×2    │ охлаждение, узкие места      │
 * │ инвентарь игрока          │ (колесо мыши — прокрутка)    │
 * └───────────────────────────┴──────────────────────────────┘
 */
public class PcCaseScreen extends AbstractContainerScreen<PcCaseMenu> {

    private static final int BG = 0xF0181A1F, PANEL = 0xFF22252B, BORDER = 0xFF3A3F49, SLOT = 0xFF14161A;
    private static final int TEXT = 0xFFE6E8EB, TEXT_DIM = 0xFF8A9099, GOOD = 0xFF5BD47A, BAD = 0xFFE05555, WARN = 0xFFE0A040;
    private static final int W = 336, H = 184;
    private static final int INFO_X = 160, INFO_Y = 42, INFO_W = W - INFO_X - 8, INFO_H = H - INFO_Y - 8;

    /** Что положить в пустой слот — показываем бледной подсказкой. */
    private static ItemStack[] ghosts;
    private static final String[] SLOT_HINTS = {
            "gui.minepc.slot.motherboard", "gui.minepc.slot.cpu", "gui.minepc.slot.cooler",
            "gui.minepc.slot.ram", "gui.minepc.slot.ram", "gui.minepc.slot.ram", "gui.minepc.slot.ram",
            "gui.minepc.slot.gpu", "gui.minepc.slot.psu", "gui.minepc.slot.hdd", "gui.minepc.slot.hdd",
            "gui.minepc.slot.sata", "gui.minepc.slot.sata", "gui.minepc.slot.m2", "gui.minepc.slot.m2",
            "gui.minepc.slot.usb", "gui.minepc.slot.usb"};

    private Button powerButton;
    private int scroll;
    private List<FormattedCharSequence> info = List.of();
    private int infoAge;

    public PcCaseScreen(PcCaseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = H;
        inventoryLabelY = 10000;   // подпись «Инвентарь» не нужна
    }

    private static ItemStack[] ghosts() {
        if (ghosts == null) {
            ItemStack ram = HardwarePresets.ram(1), hdd = HardwarePresets.hdd(2), sata = HardwarePresets.ssd(1),
                    m2 = HardwarePresets.ssd(3), usb = new ItemStack(ModItems.FLASH_DRIVE.get());
            ghosts = new ItemStack[]{HardwarePresets.motherboard(1), HardwarePresets.cpu(1), HardwarePresets.cooler(1),
                    ram, ram, ram, ram, HardwarePresets.gpu(2), HardwarePresets.psu(1), hdd, hdd, sata, sata, m2, m2, usb, usb};
        }
        return ghosts;
    }

    @Override
    protected void init() {
        super.init();
        powerButton = addRenderableWidget(Button.builder(Component.translatable("gui.minepc.power"), b -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PcCaseMenu.BTN_POWER);
            }
        }).bounds(leftPos + INFO_X, topPos + 20, 64, 16).build());
    }

    /** Слот, куда сейчас ничего нельзя поставить (на плате нет такого разъёма). */
    private boolean disabled(int slot) {
        PcCaseBlockEntity be = menu.blockEntity();
        if (be == null) return false;
        MotherboardData board = be.motherboard();
        if (slot == PcCaseBlockEntity.SLOT_MOTHERBOARD || slot == PcCaseBlockEntity.SLOT_PSU || slot >= PcCaseBlockEntity.SLOT_USB) {
            return false;
        }
        if (board == null) return true;
        if (slot >= PcCaseBlockEntity.SLOT_RAM && slot < PcCaseBlockEntity.SLOT_RAM + PcCaseBlockEntity.RAM_SLOTS) {
            return slot - PcCaseBlockEntity.SLOT_RAM >= board.ramSlots();
        }
        if (slot >= PcCaseBlockEntity.SLOT_M2 && slot < PcCaseBlockEntity.SLOT_M2 + PcCaseBlockEntity.M2_SLOTS) {
            return slot - PcCaseBlockEntity.SLOT_M2 >= board.m2Slots();
        }
        return false;
    }

    // ======================= Отрисовка =======================

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + W, y + H, BG);
        outline(g, x, y, W, H, BORDER);
        g.fill(x + 1, y + 1, x + W - 1, y + 15, PANEL);
        // Отсек деталей и инфо-панель
        g.fill(x + 6, y + 18, x + 152, y + 92, PANEL);
        g.fill(x + INFO_X - 2, y + INFO_Y - 2, x + INFO_X + INFO_W + 2, y + INFO_Y + INFO_H + 2, PANEL);
        // Подписи групп
        drawSmall(g, Component.translatable("gui.minepc.group.memory"), x + 10, y + 41);
        drawSmall(g, Component.translatable("gui.minepc.group.m2"), x + 94, y + 41);
        drawSmall(g, Component.translatable("gui.minepc.group.drives"), x + 10, y + 65);
        drawSmall(g, Component.translatable("gui.minepc.group.usb"), x + 112, y + 65);
        // Слоты
        ItemStack[] gh = ghosts();
        for (int i = 0; i < PcCaseBlockEntity.SLOT_COUNT; i++) {
            int sx = x + PcCaseMenu.SLOT_POS[i][0], sy = y + PcCaseMenu.SLOT_POS[i][1];
            g.fill(sx, sy, sx + 18, sy + 18, BORDER);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT);
            Slot slot = menu.slots.get(i);
            if (!slot.hasItem()) {
                if (disabled(i)) {
                    g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF2A1A1A);
                    g.drawString(font, "×", sx + 6, sy + 5, 0xFF6A3A3A, false);
                } else {
                    g.renderFakeItem(gh[i], sx + 1, sy + 1);
                    g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xC014161A);   // бледная подсказка
                }
            }
        }
        // Инвентарь игрока
        for (int i = PcCaseBlockEntity.SLOT_COUNT; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, BORDER);
            g.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, SLOT);
        }
    }

    private void drawSmall(GuiGraphics g, Component text, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.6f, 0.6f, 1);
        g.drawString(font, text, 0, 0, TEXT_DIM, false);
        g.pose().popPose();
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 6, 4, TEXT, false);
        int state = menu.computerState();
        Component status = Component.translatable(state == 1 ? "gui.minepc.state.on" : state == 2 ? "gui.minepc.state.crashed" : "gui.minepc.state.off");
        g.drawString(font, status, INFO_X + 70, 24, state == 1 ? GOOD : state == 2 ? BAD : TEXT_DIM, false);
        if (!menu.hasKeyboard()) {
            g.drawString(font, Component.translatable("gui.minepc.no_keyboard_short"), INFO_X + 70, 33, WARN, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderInfo(g);
        renderTooltip(g, mouseX, mouseY);
        // Подсказка для пустого слота: что сюда ставить
        for (int i = 0; i < PcCaseBlockEntity.SLOT_COUNT; i++) {
            int sx = leftPos + PcCaseMenu.SLOT_POS[i][0], sy = topPos + PcCaseMenu.SLOT_POS[i][1];
            if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18 && !menu.slots.get(i).hasItem()
                    && menu.getCarried().isEmpty()) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable(SLOT_HINTS[i]));
                if (disabled(i)) tip.add(Component.translatable("gui.minepc.slot.unavailable").withStyle(ChatFormatting.RED));
                g.renderComponentTooltip(font, tip, mouseX, mouseY);
            }
        }
    }

    /** Характеристики сборки (тот же отчёт, что Shift+ПКМ по корпусу), с переносом строк и прокруткой. */
    private void renderInfo(GuiGraphics g) {
        PcCaseBlockEntity be = menu.blockEntity();
        if (be == null) return;
        if (infoAge-- <= 0) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component c : be.statusLines(false)) lines.addAll(font.split(c, (int) (INFO_W / 0.75f)));
            info = lines;
            infoAge = 10;
        }
        int lineH = 8;
        int visible = INFO_H / lineH;
        scroll = Math.max(0, Math.min(scroll, info.size() - visible));
        g.enableScissor(leftPos + INFO_X, topPos + INFO_Y, leftPos + INFO_X + INFO_W, topPos + INFO_Y + INFO_H);
        g.pose().pushPose();
        g.pose().translate(leftPos + INFO_X, topPos + INFO_Y, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        for (int i = scroll; i < info.size() && i < scroll + visible + 1; i++) {
            g.drawString(font, info.get(i), 0, (int) ((i - scroll) * lineH / 0.75f), TEXT, false);
        }
        g.pose().popPose();
        g.disableScissor();
        if (info.size() > visible) {
            int barH = Math.max(8, INFO_H * visible / info.size());
            int barY = topPos + INFO_Y + (INFO_H - barH) * scroll / Math.max(1, info.size() - visible);
            g.fill(leftPos + INFO_X + INFO_W - 2, barY, leftPos + INFO_X + INFO_W, barY + barH, BORDER);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= leftPos + INFO_X && mouseX < leftPos + INFO_X + INFO_W) {
            scroll -= (int) Math.signum(scrollY) * 2;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
