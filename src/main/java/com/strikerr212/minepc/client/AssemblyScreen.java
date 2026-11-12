package com.strikerr212.minepc.client;

import com.mojang.math.Axis;
import com.strikerr212.minepc.hardware.HardwarePresets;
import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.Units;
import com.strikerr212.minepc.hardware.spec.Cost;
import com.strikerr212.minepc.hardware.spec.Param;
import com.strikerr212.minepc.hardware.spec.PartSpec;
import com.strikerr212.minepc.hardware.spec.Specs;
import com.strikerr212.minepc.menu.AssemblyMenu;
import com.strikerr212.minepc.network.AssemblyValuesPayload;
import com.strikerr212.minepc.network.LabelPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Экран сборочного стола.
 *
 * <pre>
 * ┌ Сборочный стол ─────────────────────────── Shift + колесо — менять значение ┐
 * │ [плата][ПРОЦЕССОР][кулер]…                                     [Шаблоны ▾] │
 * │ КРИСТАЛЛ               │ ┌─────────┐ │ ЧТО ПОЛУЧИТСЯ                       │
 * │ Ядра   1 2 4 [8] 12 …  │ │ 3D-вид  │ │ Одна программа: 3.85 МГц …          │
 * │ Частота 0.5 … [3.5 МГц]│ └─────────┘ │                                     │
 * │ КОРПУС                 │ название, ур.│                                    │
 * │ Цвет ■■■■■■            │ надпись [  ] │                                    │
 * │ [материалы: есть/нужно]                              [Сброс] [Собрать]     │
 * └────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * Окно подстраивается под масштаб интерфейса: занимает доступное место (до {@link #MAX_W}×{@link #MAX_H}),
 * а если параметры не помещаются по высоте — список прокручивается колесом.
 */
public class AssemblyScreen extends AbstractContainerScreen<AssemblyMenu> {

    // ----- Цвета интерфейса (ARGB) -----
    private static final int BG = 0xF0181A1F, PANEL = 0xFF22252B, PANEL_LIGHT = 0xFF2C3038, BORDER = 0xFF3A3F49;
    private static final int CHIP = 0xFF262A31, CHIP_HOVER = 0xFF343944;
    private static final int ACCENT = 0xFF3FA9F5, TEXT = 0xFFE6E8EB, TEXT_DIM = 0xFF8A9099, TEXT_OFF = 0xFF555A63;
    private static final int BAD = 0xFFE05555, GOOD = 0xFF5BD47A;
    /** Цвет варианта по уровню технологии — как цвет названия детали этого уровня. */
    private static final int[] TIER_COLOR = {0xFFB4BAC4, 0xFFB4BAC4, 0xFFE6E8EB, 0xFFF0D670, 0xFF70D8EE, 0xFFD996F2};

    // ----- Размеры -----
    private static final int MAX_W = 540, MAX_H = 380;
    private static final int TABS_Y = 16, TABS_H = 18, CONTENT_Y = 38, BOTTOM_H = 24;
    private static final int LINE = 11, CHIP_H = 10, SWATCH = 9;

    // ----- Раскладка (считается в init под размер окна игры) -----
    private int w, h;
    private int parX, parW, pvX, pvW, pvH, resX, resW, contentBottom, labelW;

    private EditBox labelBox;
    private Button buildButton, templatesButton;

    // Вращение и масштаб превью
    private static final float DEFAULT_ZOOM = 4.6f, MIN_ZOOM = 2.5f, MAX_ZOOM = 8f;
    private static final float MAX_YAW = 75f, MAX_PITCH = 45f;
    private static final float DRAG_SPEED = 0.8f;   // градусов на пиксель интерфейса
    private float yaw = 0f, pitch = 0f, zoom = DEFAULT_ZOOM;
    private boolean dragging;

    private int paramScroll, resultScroll, lastType = -1;
    /** Страница, которую показывает превью (у видеокарты: плата без охлаждения или целиком). */
    private int previewPage;
    private boolean templatesOpen;

    /** Для снимков в CI: открыть (true) или закрыть (false) список шаблонов. */
    public static Boolean ciTemplates;

    public AssemblyScreen(AssemblyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        w = Math.min(MAX_W, width - 8);
        h = Math.min(MAX_H, height - 8);
        imageWidth = w;
        imageHeight = h;
        super.init();

        pvW = clamp(Math.round(w * 0.2f), 80, 130);
        resW = clamp(Math.round(w * 0.31f), 110, 180);
        parX = 6;
        parW = w - 12 - pvW - resW - 12;
        pvX = parX + parW + 6;
        resX = pvX + pvW + 6;
        contentBottom = h - BOTTOM_H - 2;
        int contentH = contentBottom - CONTENT_Y;
        pvH = Math.max(40, Math.min(pvW, contentH - 62));
        labelW = 0;
        for (PartSpec spec : Specs.ALL) {
            for (Param p : spec.params()) labelW = Math.max(labelW, font.width(p.label()) + 10);
        }
        labelW = Math.min(labelW, Math.max(52, parW * 2 / 5));

        String oldLabel = labelBox != null ? labelBox.getValue() : "";
        labelBox = new EditBox(font, leftPos + pvX, topPos + contentBottom - 14, pvW, 14, Component.translatable("gui.minepc.label"));
        labelBox.setMaxLength(24);
        labelBox.setValue(oldLabel);
        labelBox.setHint(Component.translatable("gui.minepc.label_hint").withStyle(ChatFormatting.DARK_GRAY));
        labelBox.setResponder(text -> PacketDistributor.sendToServer(new LabelPayload(text)));
        addRenderableWidget(labelBox);

        int by = topPos + h - 20;
        buildButton = addRenderableWidget(Button.builder(Component.translatable("gui.minepc.build"),
                b -> click(AssemblyMenu.BTN_BUILD)).bounds(leftPos + w - 6 - 64, by, 64, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.minepc.reset"),
                b -> click(AssemblyMenu.BTN_RESET)).bounds(leftPos + w - 6 - 64 - 4 - 50, by, 50, 16).build());
        Component tl = Component.translatable("gui.minepc.templates");
        int tw = font.width(tl) + 14;
        templatesButton = addRenderableWidget(Button.builder(tl, b -> templatesOpen = !templatesOpen)
                .bounds(leftPos + w - 6 - tw, topPos + TABS_Y, tw, TABS_H - 2).build());
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

    // ======================= Раскладка параметров =======================

    private record Chip(int option, int x, int y, int w, String text) {
    }

    /** Строка списка: заголовок страницы (param = -1) или параметр со всеми вариантами. y — от начала списка. */
    private record Row(int param, int page, int y, int h, List<Chip> chips) {
    }

    private final Map<String, Boolean> sameUnit = new HashMap<>();

    private List<Row> rows(PartSpec spec, List<Integer> values) {
        List<Row> rows = new ArrayList<>();
        int y = 0;
        int chipsX = labelW, chipsR = parW - 6;
        for (int page = 0; page < spec.pages(); page++) {
            if (spec.pages() > 1) {
                rows.add(new Row(-1, page, y, 12, List.of()));
                y += 12;
            }
            for (int i = 0; i < spec.params().size(); i++) {
                Param p = spec.params().get(i);
                if (p.page() != page) continue;
                List<Chip> chips = new ArrayList<>();
                int cx = chipsX, cy = 0;
                int selected = values.get(i);
                for (int o = 0; o < p.count(); o++) {
                    String text = p.isColor() ? "" : chipText(p, o, o == selected);
                    int cw = p.isColor() ? SWATCH : font.width(text) + 4;
                    if (cx + cw > chipsR && cx > chipsX) {
                        cx = chipsX;
                        cy += LINE;
                    }
                    chips.add(new Chip(o, cx, cy, cw, text));
                    cx += cw + 2;
                }
                int rh = cy + LINE + 2;
                rows.add(new Row(i, page, y, rh, chips));
                y += rh;
            }
        }
        return rows;
    }

    /** Подпись варианта: выбранный — полностью, с единицей; остальные — коротко, чтобы все поместились. */
    private String chipText(Param p, int option, boolean selected) {
        String full = p.display(option).getString();
        if (selected || p.kind() != Param.Kind.NUMBER) return full;
        int sp = full.lastIndexOf(' ');
        if (sp <= 0) return full;
        String num = full.substring(0, sp), unit = full.substring(sp + 1);
        boolean same = sameUnit.computeIfAbsent(p.key(), k -> {
            String first = null;
            for (int o = 0; o < p.count(); o++) {
                String s = p.display(o).getString();
                String u = s.lastIndexOf(' ') > 0 ? s.substring(s.lastIndexOf(' ') + 1) : "";
                if (first == null) first = u;
                else if (!first.equals(u)) return false;
            }
            return true;
        });
        return same || unit.isEmpty() ? num : num + unit.charAt(0);
    }

    private int paramsTop() {
        return topPos + CONTENT_Y;
    }

    private int listHeight(List<Row> rows) {
        if (rows.isEmpty()) return 0;
        Row last = rows.get(rows.size() - 1);
        return last.y() + last.h();
    }

    private int maxParamScroll(List<Row> rows) {
        return Math.max(0, listHeight(rows) - (contentBottom - CONTENT_Y));
    }

    // ======================= Отрисовка =======================

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + w, y + h, BG);
        outline(g, x, y, w, h, BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 14, PANEL);
        g.fill(x + 1, y + TABS_Y + TABS_H, x + w - 1, y + TABS_Y + TABS_H + 1, BORDER);
        // колонки
        g.fill(x + pvX, y + CONTENT_Y, x + pvX + pvW, y + CONTENT_Y + pvH, PANEL);
        outline(g, x + pvX, y + CONTENT_Y, pvW, pvH, BORDER);
        g.fill(x + resX - 3, y + CONTENT_Y, x + resX - 2, y + contentBottom, BORDER);
        g.fill(x + pvX - 4, y + CONTENT_Y, x + pvX - 3, y + contentBottom, BORDER);
        // нижняя полоса
        g.fill(x + 1, y + h - BOTTOM_H, x + w - 1, y + h - 1, PANEL);
        g.fill(x + 1, y + h - BOTTOM_H, x + w - 1, y + h - BOTTOM_H + 1, BORDER);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 6, 4, TEXT, false);
        Component hint = Component.translatable("gui.minepc.wheel_hint");
        if (font.width(title) + font.width(hint) + 24 < w) {
            g.drawString(font, hint, w - 6 - font.width(hint), 4, TEXT_OFF, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (menu.typeIndex() != lastType) {
            if (lastType >= 0) templatesOpen = false;   // сменили вкладку — шаблоны другой детали
            lastType = menu.typeIndex();
            paramScroll = 0;
            resultScroll = 0;
            previewPage = menu.spec().pages() - 1;
        }
        if (ciTemplates != null) {
            templatesOpen = ciTemplates;
            ciTemplates = null;
        }
        boolean overlay = templatesOpen;
        int mx = overlay ? -1000 : mouseX, my = overlay ? -1000 : mouseY;   // под списком шаблонов ничего не подсвечиваем
        super.render(g, mx, my, partialTick);

        PartSpec spec = menu.spec();
        List<Integer> values = menu.values();
        List<Row> rows = rows(spec, values);
        paramScroll = clamp(paramScroll, 0, maxParamScroll(rows));

        renderTabs(g, mx, my);
        Row hovered = rowAt(rows, mx, my);
        int page = hovered != null ? hovered.page() : previewPage;
        ItemStack preview = HardwarePresets.build(spec.type(), labelBox.getValue(), spec.previewValues(values, page));
        ItemStack result = HardwarePresets.build(spec.type(), labelBox.getValue(), values);
        renderPreview(g, preview, result, spec, values, mx, my);
        renderResults(g, spec, values);
        Runnable tooltip = renderParams(g, spec, values, rows, mx, my);
        Runnable costTip = renderCost(g, spec, values, mx, my);

        if (overlay) {
            renderTemplates(g, mouseX, mouseY);
        } else {
            if (tooltip != null) tooltip.run();
            if (costTip != null) costTip.run();
            renderTooltip(g, mouseX, mouseY);
        }
    }

    private int tabWidth(int i) {
        PartSpec spec = Specs.ALL.get(i);
        return i == menu.typeIndex() ? 22 + font.width(tabName(spec)) + 6 : 22;
    }

    private static Component tabName(PartSpec spec) {
        return Component.translatable("tab.minepc." + spec.type().name().toLowerCase());
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        int tx = leftPos + 6, ty = topPos + TABS_Y;
        Component hoverName = null;
        for (int i = 0; i < Specs.ALL.size(); i++) {
            PartSpec spec = Specs.ALL.get(i);
            int tw = tabWidth(i);
            boolean selected = i == menu.typeIndex();
            boolean hover = inside(mouseX, mouseY, tx, ty, tw, TABS_H);
            g.fill(tx, ty, tx + tw, ty + TABS_H, selected ? PANEL_LIGHT : (hover ? CHIP_HOVER : PANEL));
            if (selected) g.fill(tx, ty + TABS_H - 2, tx + tw, ty + TABS_H, ACCENT);
            g.renderItem(HardwarePresets.build(spec.type(), "", spec.defaults()), tx + 3, ty + 1);
            if (selected) g.drawString(font, tabName(spec), tx + 22, ty + 5, TEXT, false);
            else if (hover) hoverName = tabName(spec);
            tx += tw + 2;
        }
        if (hoverName != null) g.renderTooltip(font, hoverName, mouseX, mouseY);
    }

    private void renderPreview(GuiGraphics g, ItemStack stack, ItemStack result, PartSpec spec, List<Integer> values,
                               int mouseX, int mouseY) {
        int bx = leftPos + pvX, by = topPos + CONTENT_Y;
        int cx = bx + pvW / 2, cy = by + pvH / 2;
        g.enableScissor(bx + 1, by + 1, bx + pvW - 1, by + pvH - 1);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 200);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        float z = zoom * pvH / 104f;
        pose.scale(z, z, z);
        // renderItem сам сдвигает предмет на 150 по глубине. Мы уже повернули систему координат,
        // и без компенсации этот сдвиг уводил бы модель вбок. Отменяем его заранее.
        pose.translate(0, 0, -150);
        g.renderItem(stack, -8, -8);
        pose.popPose();
        g.disableScissor();
        if (spec.pages() > 1) {
            int page = rowAtPage(mouseX, mouseY, spec, values);
            g.drawString(font, spec.pageName(page < 0 ? previewPage : page), bx + 3, by + 3, TEXT_OFF, false);
        }

        // Название и уровень под превью
        int y = by + pvH + 4;
        int tier = spec.tier(values);
        List<FormattedCharSequence> name = font.split(result.getHoverName(), pvW);
        for (int i = 0; i < Math.min(2, name.size()); i++) {
            g.drawString(font, name.get(i), bx, y, tier >= 3 ? TIER_COLOR[tier] : TEXT, false);
            y += 10;
        }
        Component tierText = Component.translatable("gui.minepc.tier", tier);
        g.drawString(font, tierText, bx, y, TIER_COLOR[tier], false);
        if (inside(mouseX, mouseY, bx, y, font.width(tierText), 9)) {
            tooltipLater = () -> g.renderTooltip(font, font.split(Component.translatable("gui.minepc.tier_help"), 200), mouseX, mouseY);
        }
        // Надпись
        int ly = topPos + contentBottom - 25;
        if (ly > y + 9) g.drawString(font, Component.translatable("gui.minepc.label"), bx, ly, TEXT_DIM, false);
        // «?» в углу превью — как вращать (подсказка не закрывает саму деталь, пока не наведёшь на знак)
        boolean helpHover = inside(mouseX, mouseY, bx + pvW - 10, by + 1, 9, 10);
        g.drawString(font, "?", bx + pvW - 8, by + 3, helpHover ? ACCENT : TEXT_OFF, false);
        if (helpHover) {
            tooltipLater = () -> g.renderTooltip(font, font.split(Component.translatable("gui.minepc.rotate_hint"), 200), mouseX, mouseY);
        }
    }

    private Runnable tooltipLater;

    private void renderResults(GuiGraphics g, PartSpec spec, List<Integer> values) {
        int x = leftPos + resX, top = topPos + CONTENT_Y;
        g.drawString(font, Component.translatable("gui.minepc.result"), x, top, ACCENT, false);
        List<Component> lines = new ArrayList<>();
        spec.describe(values, lines);
        int power = spec.powerDraw(values);
        if (power > 0) {
            lines.add(Component.translatable("tooltip.minepc.power", Units.watts(power)).withStyle(ChatFormatting.GRAY));
        }
        // Выбранные значения и так видны слева — оставляем то, что из них получается
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) {
            if (line.getContents() instanceof TranslatableContents t && t.getKey().equals("tooltip.minepc.param")) continue;
            wrapped.addAll(font.split(line, resW - 4));
        }
        int areaTop = top + 12, areaBottom = topPos + contentBottom;
        int max = Math.max(0, wrapped.size() * 10 - (areaBottom - areaTop));
        resultScroll = clamp(resultScroll, 0, max);
        g.enableScissor(x, areaTop, x + resW, areaBottom);
        int y = areaTop - resultScroll;
        for (FormattedCharSequence s : wrapped) {
            g.drawString(font, s, x, y, TEXT_DIM, false);
            y += 10;
        }
        g.disableScissor();
        if (max > 0) scrollBar(g, x + resW - 2, areaTop, areaBottom - areaTop, resultScroll, max);
    }

    /** Список параметров. Возвращает подсказку, которую надо нарисовать поверх всего (или null). */
    private Runnable renderParams(GuiGraphics g, PartSpec spec, List<Integer> values, List<Row> rows, int mouseX, int mouseY) {
        int x0 = leftPos + parX, top = paramsTop(), bottom = topPos + contentBottom;
        Runnable tip = tooltipLater;
        tooltipLater = null;
        g.enableScissor(x0, top, x0 + parW, bottom);
        for (Row row : rows) {
            int ry = top + row.y() - paramScroll;
            if (ry + row.h() < top || ry > bottom) continue;
            if (row.param() < 0) {
                Component name = spec.pageName(row.page()).copy();
                g.drawString(font, name.getString().toUpperCase(), x0, ry + 2, ACCENT, false);
                continue;
            }
            Param p = spec.params().get(row.param());
            boolean active = spec.isActive(row.param(), values);
            boolean rowHover = inside(mouseX, mouseY, x0, ry, parW, row.h()) && mouseY >= top && mouseY < bottom;
            if (rowHover) g.fill(x0 - 2, ry - 1, x0 + parW - 4, ry + row.h() - 2, 0x14FFFFFF);

            // Подпись и «?», если у параметра есть объяснение
            String helpKey = "param.minepc." + p.key() + ".help";
            boolean hasHelp = I18n.exists(helpKey);
            String label = font.plainSubstrByWidth(p.label().getString(), labelW - (hasHelp ? 12 : 4));
            g.drawString(font, label, x0, ry + 1, active ? TEXT_DIM : TEXT_OFF, false);
            if (hasHelp) g.drawString(font, "?", x0 + font.width(label) + 3, ry + 1, active ? ACCENT : TEXT_OFF, false);
            if (inside(mouseX, mouseY, x0, ry, labelW - 2, LINE) && mouseY >= top && mouseY < bottom) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                lines.add(p.label().copy().withStyle(ChatFormatting.WHITE).getVisualOrderText());
                if (hasHelp) lines.addAll(font.split(Component.translatable(helpKey).withStyle(ChatFormatting.GRAY), 220));
                if (!active) lines.addAll(font.split(Component.translatable("gui.minepc.inactive").withStyle(ChatFormatting.DARK_GRAY), 220));
                tip = () -> g.renderTooltip(font, lines, mouseX, mouseY);
            }

            int selected = values.get(row.param());
            for (Chip c : row.chips()) {
                int cx = x0 + c.x(), cy = ry + c.y();
                boolean sel = c.option() == selected;
                boolean hover = active && inside(mouseX, mouseY, cx, cy, c.w(), CHIP_H) && mouseY >= top && mouseY < bottom;
                int tier = p.tierAt(c.option());
                if (p.isColor()) {
                    int col = 0xFF000000 | p.color(c.option());
                    if (sel) outline(g, cx - 1, cy - 1, SWATCH + 2, SWATCH + 2, active ? ACCENT : TEXT_OFF);
                    else if (hover) outline(g, cx - 1, cy - 1, SWATCH + 2, SWATCH + 2, TEXT);
                    g.fill(cx, cy, cx + SWATCH, cy + SWATCH, active ? col : (col & 0x00FFFFFF) | 0x50000000);
                } else {
                    g.fill(cx, cy, cx + c.w(), cy + CHIP_H, sel ? (active ? ACCENT : BORDER) : (hover ? CHIP_HOVER : CHIP));
                    int color = !active ? TEXT_OFF : sel ? 0xFFFFFFFF : TIER_COLOR[Math.max(0, Math.min(5, tier))];
                    g.drawString(font, c.text(), cx + 2, cy + 1, color, false);
                }
                if (hover) {
                    Component full = p.display(c.option()).copy().withStyle(ChatFormatting.WHITE);
                    Component lv = Component.translatable("gui.minepc.option_tier", tier).withStyle(tierStyle(tier));
                    tip = () -> g.renderComponentTooltip(font, List.of(full, lv), mouseX, mouseY);
                }
            }
        }
        g.disableScissor();
        int max = maxParamScroll(rows);
        if (max > 0) scrollBar(g, x0 + parW - 3, top, bottom - top, paramScroll, max);
        return tip;
    }

    private static ChatFormatting tierStyle(int tier) {
        return switch (tier) {
            case 3 -> ChatFormatting.YELLOW;
            case 4 -> ChatFormatting.AQUA;
            case 5 -> ChatFormatting.LIGHT_PURPLE;
            default -> ChatFormatting.GRAY;
        };
    }

    private void scrollBar(GuiGraphics g, int x, int y, int height, int scroll, int max) {
        g.fill(x, y, x + 2, y + height, PANEL);
        int bar = Math.max(12, height * height / (height + max));
        int by = y + (height - bar) * scroll / Math.max(1, max);
        g.fill(x, by, x + 2, by + bar, BORDER + 0x00202020);
    }

    /** Материалы внизу: сколько нужно и есть ли. Возвращает подсказку поверх всего (или null). */
    private Runnable renderCost(GuiGraphics g, PartSpec spec, List<Integer> values, int mouseX, int mouseY) {
        int x = leftPos + 6, y = topPos + h - BOTTOM_H + 4;
        boolean creative = minecraft != null && minecraft.player != null && minecraft.player.getAbilities().instabuild;
        boolean enough = true;
        Runnable tip = null;
        Component missing = null;
        int limit = leftPos + w - 6 - 64 - 4 - 50 - 6;
        for (Cost.Entry e : spec.cost(values).entries()) {
            ItemStack icon = new ItemStack(e.item());
            int have = minecraft != null && minecraft.player != null ? minecraft.player.getInventory().countItem(e.item()) : 0;
            boolean ok = creative || have >= e.count();
            enough &= ok;
            if (!ok && missing == null) {
                missing = Component.translatable("gui.minepc.missing", e.count() - have, icon.getHoverName());
            }
            if (x + 16 > limit) continue;
            if (!ok) g.fill(x, y, x + 16, y + 16, 0x80E05555);
            g.renderItem(icon, x, y);
            g.renderItemDecorations(font, icon, x, y, String.valueOf(e.count()));
            if (inside(mouseX, mouseY, x, y, 16, 16)) {
                List<Component> lines = List.of(icon.getHoverName(),
                        Component.translatable("gui.minepc.have_need", have, e.count())
                                .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
                tip = () -> g.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
            x += 18;
        }
        Component note = creative ? Component.translatable("gui.minepc.cost_free_short")
                : missing != null ? missing : null;
        if (note != null && x + 6 + font.width(note) < limit) {
            g.drawString(font, note, x + 4, y + 4, creative ? GOOD : BAD, false);
        }
        buildButton.active = enough;
        return tip;
    }

    // ======================= Шаблоны =======================

    private record Template(Component name, List<Integer> values, String label, AssemblyTemplates.Saved saved) {
    }

    private static final int TPL_W = 150, TPL_ROW = 12;

    private List<Template> templates() {
        HardwareType type = menu.spec().type();
        List<Template> list = new ArrayList<>();
        for (int tier = 1; tier <= HardwarePresets.MAX_TIER; tier++) {
            list.add(new Template(Component.translatable("template.minepc." + tier), HardwarePresets.stockValues(type, tier), null, null));
        }
        List<Integer> server = HardwarePresets.serverValues(type);
        if (server != null) list.add(new Template(Component.translatable("template.minepc.server"), server, null, null));
        for (AssemblyTemplates.Saved s : AssemblyTemplates.of(type)) {
            list.add(new Template(Component.literal(s.name()), s.values(), s.label(), s));
        }
        return list;
    }

    private int tplX() {
        return Math.max(leftPos + 4, leftPos + w - 6 - TPL_W);
    }

    private int tplY() {
        return topPos + TABS_Y + TABS_H;
    }

    private void renderTemplates(GuiGraphics g, int mouseX, int mouseY) {
        List<Template> list = templates();
        int x = tplX(), y = tplY();
        int rowsCount = list.size() + 2;   // + заголовок своих и «сохранить»
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 400);
        int hgt = rowsCount * TPL_ROW + 6;
        g.fill(x, y, x + TPL_W, y + hgt, 0xF8181A1F);
        outline(g, x, y, TPL_W, hgt, ACCENT);
        int ry = y + 3;
        int builtIn = (int) list.stream().filter(t -> t.saved() == null).count();
        for (int i = 0; i < list.size(); i++) {
            if (i == builtIn) {
                g.drawString(font, Component.translatable("gui.minepc.templates_mine"), x + 4, ry + 2, TEXT_OFF, false);
                ry += TPL_ROW;
            }
            Template t = list.get(i);
            boolean hover = inside(mouseX, mouseY, x + 1, ry, TPL_W - 2, TPL_ROW);
            if (hover) g.fill(x + 1, ry, x + TPL_W - 1, ry + TPL_ROW, CHIP_HOVER);
            int tier = Specs.of(menu.spec().type()).tier(t.values());
            g.drawString(font, font.plainSubstrByWidth(t.name().getString(), TPL_W - 24), x + 4, ry + 2,
                    t.saved() == null ? TIER_COLOR[tier] : TEXT, false);
            if (t.saved() != null) {
                boolean xHover = inside(mouseX, mouseY, x + TPL_W - 12, ry, 10, TPL_ROW);
                g.drawString(font, "✕", x + TPL_W - 10, ry + 2, xHover ? BAD : TEXT_OFF, false);
            }
            ry += TPL_ROW;
        }
        if (builtIn == list.size()) {
            g.drawString(font, Component.translatable("gui.minepc.templates_mine"), x + 4, ry + 2, TEXT_OFF, false);
            ry += TPL_ROW;
        }
        boolean hover = inside(mouseX, mouseY, x + 1, ry, TPL_W - 2, TPL_ROW);
        if (hover) g.fill(x + 1, ry, x + TPL_W - 1, ry + TPL_ROW, CHIP_HOVER);
        g.drawString(font, Component.translatable("gui.minepc.template_save"), x + 4, ry + 2, ACCENT, false);
        pose.popPose();
    }

    /** Клик при открытом списке шаблонов. Всегда «съедает» клик. */
    private void clickTemplates(double mouseX, double mouseY) {
        List<Template> list = templates();
        int x = tplX(), ry = tplY() + 3;
        int builtIn = (int) list.stream().filter(t -> t.saved() == null).count();
        HardwareType type = menu.spec().type();
        for (int i = 0; i < list.size(); i++) {
            if (i == builtIn) ry += TPL_ROW;
            Template t = list.get(i);
            if (inside(mouseX, mouseY, x + 1, ry, TPL_W - 2, TPL_ROW)) {
                if (t.saved() != null && inside(mouseX, mouseY, x + TPL_W - 12, ry, 10, TPL_ROW)) {
                    AssemblyTemplates.remove(t.saved());
                    clickSound();
                    return;
                }
                PacketDistributor.sendToServer(new AssemblyValuesPayload(menu.typeIndex(), t.values()));
                if (t.label() != null) labelBox.setValue(t.label());
                clickSound();
                templatesOpen = false;
                return;
            }
            ry += TPL_ROW;
        }
        if (builtIn == list.size()) ry += TPL_ROW;
        if (inside(mouseX, mouseY, x + 1, ry, TPL_W - 2, TPL_ROW)) {
            String label = labelBox.getValue().trim();
            String name = !label.isEmpty() ? label
                    : Component.translatable("gui.minepc.template_mine", AssemblyTemplates.of(type).size() + 1).getString();
            boolean ok = AssemblyTemplates.add(new AssemblyTemplates.Saved(type, name, label, new ArrayList<>(menu.values())));
            if (!ok && minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable("gui.minepc.templates_full",
                        AssemblyTemplates.MAX_PER_TYPE).withStyle(ChatFormatting.RED), true);
            }
            clickSound();
            return;
        }
        templatesOpen = false;   // клик мимо списка — закрыть
    }

    // ======================= Ввод =======================

    private Row rowAt(List<Row> rows, double mouseX, double mouseY) {
        int top = paramsTop(), bottom = topPos + contentBottom;
        if (mouseY < top || mouseY >= bottom || mouseX < leftPos + parX || mouseX >= leftPos + parX + parW) return null;
        for (Row row : rows) {
            int ry = top + row.y() - paramScroll;
            if (mouseY >= ry && mouseY < ry + row.h()) return row;
        }
        return null;
    }

    private int rowAtPage(int mouseX, int mouseY, PartSpec spec, List<Integer> values) {
        Row r = rowAt(rows(spec, values), mouseX, mouseY);
        return r == null ? -1 : r.page();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (templatesOpen) {
            if (inside(mouseX, mouseY, templatesButton.getX(), templatesButton.getY(), templatesButton.getWidth(), templatesButton.getHeight())) {
                templatesOpen = false;
            } else if (button == 0) {
                clickTemplates(mouseX, mouseY);
            } else {
                templatesOpen = false;
            }
            return true;
        }
        if (button == 0) {
            // вкладки
            int tx = leftPos + 6, ty = topPos + TABS_Y;
            for (int i = 0; i < Specs.ALL.size(); i++) {
                int tw = tabWidth(i);
                if (inside(mouseX, mouseY, tx, ty, tw, TABS_H)) {
                    if (i != menu.typeIndex()) {
                        click(AssemblyMenu.BTN_TYPE + i);
                        clickSound();
                    }
                    return true;
                }
                tx += tw + 2;
            }
            // варианты параметров
            PartSpec spec = menu.spec();
            List<Integer> values = menu.values();
            Row row = rowAt(rows(spec, values), mouseX, mouseY);
            if (row != null && row.param() >= 0) {
                previewPage = row.page();
                if (spec.isActive(row.param(), values)) {
                    int ry = paramsTop() + row.y() - paramScroll;
                    for (Chip c : row.chips()) {
                        if (inside(mouseX, mouseY, leftPos + parX + c.x() - 1, ry + c.y() - 1, c.w() + 2, CHIP_H + 1)) {
                            if (c.option() != values.get(row.param())) {
                                click(AssemblyMenu.BTN_SET + row.param() * AssemblyMenu.SET_STRIDE + c.option());
                                clickSound();
                            }
                            return true;
                        }
                    }
                }
                return true;
            }
            // начало вращения превью
            if (inside(mouseX, mouseY, leftPos + pvX, topPos + CONTENT_Y, pvW, pvH)) {
                dragging = true;
                return true;
            }
        }
        // ПКМ по превью — вернуть вид по умолчанию
        if (button == 1 && inside(mouseX, mouseY, leftPos + pvX, topPos + CONTENT_Y, pvW, pvH)) {
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

    /**
     * Колесо: над превью — масштаб; над параметрами — прокрутка, а с Shift — сменить вариант под мышкой;
     * над «что получится» — прокрутка.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (templatesOpen) return true;
        if (inside(mouseX, mouseY, leftPos + pvX, topPos + CONTENT_Y, pvW, pvH)) {
            zoom = clamp(zoom + (float) scrollY * 0.4f, MIN_ZOOM, MAX_ZOOM);
            return true;
        }
        PartSpec spec = menu.spec();
        List<Integer> values = menu.values();
        List<Row> rows = rows(spec, values);
        Row row = rowAt(rows, mouseX, mouseY);
        if (row != null) {
            if (Screen.hasShiftDown() && row.param() >= 0 && spec.isActive(row.param(), values)) {
                Param p = spec.params().get(row.param());
                int next = p.clamp(values.get(row.param()) + (scrollY > 0 ? 1 : -1));
                if (next != values.get(row.param())) {
                    click(AssemblyMenu.BTN_SET + row.param() * AssemblyMenu.SET_STRIDE + next);
                    clickSound();
                }
                previewPage = row.page();
                return true;
            }
            paramScroll = clamp(paramScroll - (int) Math.round(scrollY * LINE * 2), 0, maxParamScroll(rows));
            return true;
        }
        if (inside(mouseX, mouseY, leftPos + resX, topPos + CONTENT_Y, resW, contentBottom - CONTENT_Y)) {
            resultScroll = Math.max(0, resultScroll - (int) Math.round(scrollY * 20));
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
        if (keyCode == 256) {   // Esc: сначала закрыть список шаблонов, потом окно
            if (templatesOpen) {
                templatesOpen = false;
                return true;
            }
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.closeContainer();
                return true;
            }
        }
        return labelBox.keyPressed(keyCode, scanCode, modifiers) || labelBox.canConsumeInput()
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ======================= Помощники =======================

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int clamp(int v, int min, int max) {
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
