package com.strikerr212.minepc.client;

import com.mojang.math.Axis;
import com.strikerr212.minepc.hardware.HardwarePresets;
import com.strikerr212.minepc.hardware.Units;
import com.strikerr212.minepc.hardware.spec.Cost;
import com.strikerr212.minepc.hardware.spec.Param;
import com.strikerr212.minepc.hardware.spec.PartSpec;
import com.strikerr212.minepc.hardware.spec.Specs;
import com.strikerr212.minepc.menu.AssemblyMenu;
import com.strikerr212.minepc.network.LabelPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран сборочного стола.
 *
 * ┌──────────┬──────────────┬─────────────────────┐
 * │ вкладки  │  3D-превью   │ параметры (◀ ▶)     │
 * │ деталей  │              │                     │
 * │          │ характерист. │ надпись, стоимость  │
 * │          │              │ [Собрать] [Сброс]   │
 * └──────────┴──────────────┴─────────────────────┘
 */
public class AssemblyScreen extends AbstractContainerScreen<AssemblyMenu> {

    // ----- Цвета интерфейса (ARGB) -----
    private static final int BG = 0xF0181A1F, PANEL = 0xFF22252B, PANEL_LIGHT = 0xFF2C3038, BORDER = 0xFF3A3F49;
    private static final int ACCENT = 0xFF3FA9F5, TEXT = 0xFFE6E8EB, TEXT_DIM = 0xFF8A9099, TEXT_OFF = 0xFF555A63;
    private static final int GOOD = 0xFF5BD47A, BAD = 0xFFE05555;

    // ----- Раскладка -----
    private static final int W = 360, H = 226;
    private static final int TAB_X = 6, TAB_Y = 20, TAB_W = 82, TAB_H = 22;
    private static final int PV_X = 94, PV_Y = 20, PV_W = 116, PV_H = 104;
    private static final int PR_X = 216, PR_Y = 20, PR_W = 138;
    private static final int ROW_H = 13;

    private EditBox labelBox;
    private Button buildButton;

    // Вращение и масштаб превью
    private static final float DEFAULT_ZOOM = 4.6f, MIN_ZOOM = 2.5f, MAX_ZOOM = 7f;
    private static final float MAX_YAW = 75f, MAX_PITCH = 45f;
    private static final float DRAG_SPEED = 0.8f;   // градусов на пиксель интерфейса
    private float yaw = 0f, pitch = 0f, zoom = DEFAULT_ZOOM;
    private boolean dragging;

    public AssemblyScreen(AssemblyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        labelBox = new EditBox(font, leftPos + PR_X, topPos + 160, PR_W, 14, Component.translatable("gui.minepc.label"));
        labelBox.setMaxLength(24);
        labelBox.setHint(Component.translatable("gui.minepc.label_hint").withStyle(ChatFormatting.DARK_GRAY));
        labelBox.setResponder(text -> PacketDistributor.sendToServer(new LabelPayload(text)));
        addRenderableWidget(labelBox);

        buildButton = addRenderableWidget(Button.builder(Component.translatable("gui.minepc.build"),
                b -> click(AssemblyMenu.BTN_BUILD)).bounds(leftPos + PR_X, topPos + H - 22, 84, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.minepc.reset"),
                b -> click(AssemblyMenu.BTN_RESET)).bounds(leftPos + PR_X + 88, topPos + H - 22, 50, 16).build());
    }

    /** Отправить серверу нажатие кнопки с номером id. */
    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void clickSound() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    // ======================= Отрисовка =======================

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + W, y + H, BG);
        outline(g, x, y, W, H, BORDER);
        g.fill(x + 1, y + 1, x + W - 1, y + 15, PANEL);
        g.fill(x + PV_X, y + PV_Y, x + PV_X + PV_W, y + PV_Y + PV_H, PANEL);
        outline(g, x + PV_X, y + PV_Y, PV_W, PV_H, BORDER);
        g.fill(x + PV_X, y + PV_Y + PV_H + 4, x + PV_X + PV_W, y + H - 6, PANEL);
        g.fill(x + PR_X - 2, y + PR_Y, x + PR_X + PR_W + 2, y + 148, PANEL);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 6, 4, TEXT, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        PartSpec spec = menu.spec();
        List<Integer> values = menu.values();
        int page = menu.page();

        renderTabs(g, mouseX, mouseY);
        ItemStack preview = HardwarePresets.build(spec.type(), labelBox.getValue(), spec.previewValues(values, page));
        ItemStack result = HardwarePresets.build(spec.type(), labelBox.getValue(), values);
        renderPreview(g, preview, partialTick);
        renderStats(g, spec, values, result);
        renderParams(g, spec, values, page, mouseX, mouseY);
        boolean affordable = renderCost(g, spec, values, mouseX, mouseY);
        buildButton.active = affordable;

        g.drawString(font, Component.translatable("gui.minepc.label"), leftPos + PR_X, topPos + 151, TEXT_DIM, false);

        // Полная подсказка — при наведении на название детали под превью
        if (!dragging && inside(mouseX, mouseY, leftPos + PV_X, topPos + PV_Y + PV_H + 4, PV_W, 14)) {
            g.renderTooltip(font, result, mouseX, mouseY);
        }
        renderTooltip(g, mouseX, mouseY);
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < Specs.ALL.size(); i++) {
            int tx = leftPos + TAB_X, ty = topPos + TAB_Y + i * (TAB_H + 2);
            boolean selected = i == menu.typeIndex();
            boolean hover = inside(mouseX, mouseY, tx, ty, TAB_W, TAB_H);
            g.fill(tx, ty, tx + TAB_W, ty + TAB_H, selected ? PANEL_LIGHT : (hover ? 0xFF262A31 : PANEL));
            if (selected) g.fill(tx, ty, tx + 2, ty + TAB_H, ACCENT);
            PartSpec spec = Specs.ALL.get(i);
            g.renderItem(HardwarePresets.build(spec.type(), "", spec.defaults()), tx + 4, ty + 3);
            g.drawString(font, Component.translatable("tab.minepc." + spec.type().name().toLowerCase()),
                    tx + 23, ty + 7, selected ? TEXT : TEXT_DIM, false);
        }
    }

    private void renderPreview(GuiGraphics g, ItemStack stack, float partialTick) {
        int cx = leftPos + PV_X + PV_W / 2, cy = topPos + PV_Y + PV_H / 2;
        g.enableScissor(leftPos + PV_X + 1, topPos + PV_Y + 1, leftPos + PV_X + PV_W - 1, topPos + PV_Y + PV_H - 1);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 200);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.scale(zoom, zoom, zoom);
        // renderItem сам сдвигает предмет на 150 по глубине. Мы уже повернули систему координат,
        // и без компенсации этот сдвиг уводил бы модель вбок. Отменяем его заранее.
        pose.translate(0, 0, -150);
        g.renderItem(stack, -8, -8);
        pose.popPose();
        g.disableScissor();
        pose.pushPose();
        pose.scale(0.75f, 0.75f, 1f);
        g.drawString(font, Component.translatable("gui.minepc.rotate_hint"),
                Math.round((leftPos + PV_X + 4) / 0.75f), Math.round((topPos + PV_Y + PV_H - 9) / 0.75f), TEXT_OFF, false);
        pose.popPose();
        if (menu.spec().pages() > 1) {
            g.drawString(font, menu.spec().pageName(menu.page()), leftPos + PV_X + 4, topPos + PV_Y + 3, ACCENT, false);
        }
    }

    private void renderStats(GuiGraphics g, PartSpec spec, List<Integer> values, ItemStack result) {
        int x = leftPos + PV_X + 4, y = topPos + PV_Y + PV_H + 8;
        g.drawString(font, result.getHoverName(), x, y, TEXT, false);
        List<Component> lines = new ArrayList<>();
        spec.describe(values, lines);
        int power = spec.powerDraw(values);
        if (power > 0) {
            lines.add(Component.translatable("tooltip.minepc.power", Units.watts(power)).withStyle(ChatFormatting.GRAY));
        }
        var pose = g.pose();
        pose.pushPose();
        pose.scale(0.75f, 0.75f, 1f);
        int ly = y + 12;
        for (Component line : lines) {
            if (ly > topPos + H - 14) break;
            g.drawString(font, line, Math.round(x / 0.75f), Math.round(ly / 0.75f), TEXT_DIM, false);
            ly += 8;
        }
        pose.popPose();
    }

    private void renderParams(GuiGraphics g, PartSpec spec, List<Integer> values, int page, int mouseX, int mouseY) {
        int x = leftPos + PR_X, y = topPos + PR_Y + 2;
        if (spec.pages() > 1) {
            arrowButton(g, x, y, false, mouseX, mouseY, true);
            Component name = spec.pageName(page);
            g.drawString(font, name, x + PR_W / 2 - font.width(name) / 2, y + 1, ACCENT, false);
            arrowButton(g, x + PR_W - 10, y, true, mouseX, mouseY, true);
            y += 14;
        }
        var pose = g.pose();
        for (int i = 0; i < spec.params().size(); i++) {
            Param p = spec.params().get(i);
            if (p.page() != page) continue;
            boolean active = spec.isActive(i, values);
            if (inside(mouseX, mouseY, x - 2, y - 1, PR_W + 4, ROW_H)) {
                g.fill(x - 2, y - 1, x + PR_W + 2, y + ROW_H - 2, PANEL_LIGHT);
            }
            pose.pushPose();
            pose.scale(0.75f, 0.75f, 1f);
            g.drawString(font, p.label(), Math.round(x / 0.75f), Math.round((y + 2) / 0.75f), active ? TEXT_DIM : TEXT_OFF, false);
            pose.popPose();

            int vx = x + 64;
            arrowButton(g, vx, y, false, mouseX, mouseY, active);
            arrowButton(g, x + PR_W - 10, y, true, mouseX, mouseY, active);
            int index = values.get(i);
            Component shown = active ? p.display(index) : Component.literal("—");
            int areaL = vx + 12, areaR = x + PR_W - 12;
            int textX = (areaL + areaR) / 2;
            if (active && p.isColor()) {
                g.fill(areaL + 1, y + 2, areaL + 8, y + 9, 0xFF000000 | p.color(index));
                outline(g, areaL + 1, y + 2, 7, 7, 0xFF000000);
                textX += 5;
            }
            pose.pushPose();
            pose.scale(0.75f, 0.75f, 1f);
            int w = Math.round(font.width(shown) * 0.75f);
            g.drawString(font, shown, Math.round((textX - w / 2f) / 0.75f), Math.round((y + 2) / 0.75f),
                    active ? TEXT : TEXT_OFF, false);
            pose.popPose();
            y += ROW_H;
        }
    }

    private void arrowButton(GuiGraphics g, int x, int y, boolean right, int mouseX, int mouseY, boolean active) {
        boolean hover = active && inside(mouseX, mouseY, x, y, 10, 10);
        g.fill(x, y, x + 10, y + 10, hover ? ACCENT : (active ? BORDER : PANEL));
        g.drawString(font, right ? "▶" : "◀", x + 2, y + 1, active ? TEXT : TEXT_OFF, false);
    }

    /** Возвращает true, если материалов хватает (или творческий режим). */
    private boolean renderCost(GuiGraphics g, PartSpec spec, List<Integer> values, int mouseX, int mouseY) {
        int x = leftPos + PR_X, y = topPos + 178;
        boolean creative = minecraft != null && minecraft.player != null && minecraft.player.getAbilities().instabuild;
        g.drawString(font, Component.translatable(creative ? "gui.minepc.cost_free" : "gui.minepc.cost",
                spec.tier(values)), x, y, TEXT_DIM, false);
        boolean enough = true;
        int ix = x;
        ItemStack hovered = ItemStack.EMPTY;
        for (Cost.Entry e : spec.cost(values).entries()) {
            ItemStack icon = new ItemStack(e.item());
            int have = minecraft != null && minecraft.player != null ? minecraft.player.getInventory().countItem(e.item()) : 0;
            boolean ok = creative || have >= e.count();
            enough &= ok;
            int iy = y + 10;
            if (!ok) g.fill(ix, iy, ix + 16, iy + 16, 0x80E05555);
            g.renderItem(icon, ix, iy);
            g.renderItemDecorations(font, icon, ix, iy, String.valueOf(e.count()));
            if (inside(mouseX, mouseY, ix, iy, 16, 16)) hovered = icon;
            ix += 17;
            if (ix > x + PR_W - 16) break;
        }
        if (!hovered.isEmpty()) {
            g.renderTooltip(font, hovered, mouseX, mouseY);
        }
        return enough;
    }

    // ======================= Ввод =======================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // вкладки
            for (int i = 0; i < Specs.ALL.size(); i++) {
                if (inside(mouseX, mouseY, leftPos + TAB_X, topPos + TAB_Y + i * (TAB_H + 2), TAB_W, TAB_H)) {
                    if (i != menu.typeIndex()) {
                        click(AssemblyMenu.BTN_TYPE + i);
                        clickSound();
                    }
                    return true;
                }
            }
            // параметры
            PartSpec spec = menu.spec();
            List<Integer> values = menu.values();
            int x = leftPos + PR_X, y = topPos + PR_Y + 2;
            if (spec.pages() > 1) {
                if (inside(mouseX, mouseY, x, y, 10, 10)) { click(AssemblyMenu.BTN_PAGE_PREV); clickSound(); return true; }
                if (inside(mouseX, mouseY, x + PR_W - 10, y, 10, 10)) { click(AssemblyMenu.BTN_PAGE_NEXT); clickSound(); return true; }
                y += 14;
            }
            for (int i = 0; i < spec.params().size(); i++) {
                if (spec.params().get(i).page() != menu.page()) continue;
                if (spec.isActive(i, values)) {
                    if (inside(mouseX, mouseY, x + 64, y, 10, 10)) { click(AssemblyMenu.BTN_PARAM + i * 2); clickSound(); return true; }
                    if (inside(mouseX, mouseY, x + PR_W - 10, y, 10, 10)) { click(AssemblyMenu.BTN_PARAM + i * 2 + 1); clickSound(); return true; }
                }
                y += ROW_H;
            }
            // начало вращения превью
            if (inside(mouseX, mouseY, leftPos + PV_X, topPos + PV_Y, PV_W, PV_H)) {
                dragging = true;
                return true;
            }
        }
        // ПКМ по превью — вернуть вид по умолчанию
        if (button == 1 && inside(mouseX, mouseY, leftPos + PV_X, topPos + PV_Y, PV_W, PV_H)) {
            yaw = 0f;
            pitch = 0f;
            zoom = DEFAULT_ZOOM;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            yaw = clamp(yaw + (float) dragX * DRAG_SPEED, -MAX_YAW, MAX_YAW);
            pitch = clamp(pitch + (float) dragY * DRAG_SPEED, -MAX_PITCH, MAX_PITCH);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /** Колесо мыши над превью — приблизить или отдалить. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(mouseX, mouseY, leftPos + PV_X, topPos + PV_Y, PV_W, PV_H)) {
            zoom = clamp(zoom + (float) scrollY * 0.4f, MIN_ZOOM, MAX_ZOOM);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Пока печатаешь надпись, клавиши не должны закрывать окно (например, E). */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && minecraft != null && minecraft.player != null) {   // Esc
            minecraft.player.closeContainer();
            return true;
        }
        return labelBox.keyPressed(keyCode, scanCode, modifiers) || labelBox.canConsumeInput()
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ======================= Помощники =======================

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
