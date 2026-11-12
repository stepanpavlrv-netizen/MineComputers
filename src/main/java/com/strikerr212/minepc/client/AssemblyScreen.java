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
import net.minecraft.network.chat.TextColor;
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
 * ┌ Сборочный стол ───────────────────────────────────────── ? ┐
 * │ [плата][ПРОЦЕССОР][кулер]…                     [Шаблоны ▾] │
 * │ КРИСТАЛЛ ─────────────────────── │ ┌──────────────────────┐ │
 * │ Ядра      1 2 4 (8) 12 16 24 32  │ │        3D-вид        │ │
 * │ Частота   0.5 1 2 (3.5 МГц) 4 …  │ └──────────────────────┘ │
 * │ КОРПУС ───────────────────────── │ Процессор (Ур. 3)        │
 * │ Цвет      ■■■■■■■                │ ЧТО ПОЛУЧИТСЯ            │
 * │ Надпись   [MINECORE X      ]     │ Одна программа  3.85 МГц │
 * │ [материалы]  Не хватает: …                [Сброс] [Собрать] │
 * └────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * Окно подстраивается под масштаб интерфейса: занимает доступное место (до {@link #MAX_W}×{@link #MAX_H}),
 * а если параметры не помещаются по высоте — список прокручивается колесом. Любой текст, который может
 * не влезть, обрезается многоточием; полный — во всплывающей подсказке.
 */
public class AssemblyScreen extends AbstractContainerScreen<AssemblyMenu> {

    // ----- Цвета интерфейса (ARGB) -----
    private static final int BG = 0xF0181A1F, PANEL = 0xFF22252B, PANEL_LIGHT = 0xFF2C3038, BORDER = 0xFF343943;
    private static final int CHIP_HOVER = 0xFF343944;
    private static final int ACCENT = 0xFF3FA9F5, TEXT = 0xFFE6E8EB, TEXT_DIM = 0xFF9097A1, TEXT_OFF = 0xFF555A63;
    private static final int PERF = 0xFF8FD3F0, BAD = 0xFFE05555, GOOD = 0xFF5BD47A;
    /** Цвет названия детали по уровню — как у редкости предмета. */
    private static final int[] TIER_COLOR = {TEXT, TEXT, TEXT, 0xFFF0D670, 0xFF70D8EE, 0xFFD996F2};

    // ----- Размеры -----
    private static final int MAX_W = 520, MAX_H = 360;
    private static final int TABS_Y = 16, TABS_H = 18, CONTENT_Y = 40, BOTTOM_H = 24;
    private static final int LINE = 12, CHIP_H = 11, SWATCH = 9, CHIP_GAP = 3;
    /** Строка списка «надпись на детали» (поле ввода внутри списка параметров). */
    private static final int ROW_LABEL = -2, ROW_HEADER = -1;

    // ----- Раскладка (считается в init под размер окна игры) -----
    private int w, h;
    private int parX, parW, pvX, pvW, pvH, resX, resW, resTop, contentBottom, labelW;

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
    private Runnable tooltipLater;

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

        // Две колонки: слева параметры, справа превью, название и «что получится»
        resW = clamp(Math.round(w * 0.36f), 130, 190);
        parX = 8;
        parW = w - 8 - 8 - resW - 10;
        pvX = resX = parX + parW + 10;
        pvW = resW;
        contentBottom = h - BOTTOM_H - 4;
        int contentH = contentBottom - CONTENT_Y;
        pvH = clamp(Math.min(Math.round(resW * 0.6f), contentH * 2 / 5), 48, 110);
        resTop = CONTENT_Y + pvH + 18;
        labelW = 0;
        for (PartSpec spec : Specs.ALL) {
            for (Param p : spec.params()) labelW = Math.max(labelW, font.width(p.label()) + 8);
        }
        labelW = Math.min(labelW, Math.max(56, parW * 2 / 5));

        String oldLabel = labelBox != null ? labelBox.getValue() : "";
        int boxW = Math.min(parW - labelW - 8, 130);
        labelBox = new EditBox(font, leftPos + parX + labelW, topPos + CONTENT_Y, boxW, 13, Component.translatable("gui.minepc.label"));
        labelBox.setMaxLength(24);
        labelBox.setValue(oldLabel);
        // Подсказка в пустом поле не обрезается самим полем — обрезаем сами
        labelBox.setHint(Component.literal(fit(Component.translatable("gui.minepc.label_hint").getString(), boxW - 10))
                .withStyle(ChatFormatting.DARK_GRAY));
        labelBox.setResponder(text -> PacketDistributor.sendToServer(new LabelPayload(text)));
        addRenderableWidget(labelBox);

        int by = topPos + h - 20;
        Component build = Component.translatable("gui.minepc.build"), reset = Component.translatable("gui.minepc.reset");
        int bw = Math.max(56, font.width(build) + 16), rw = Math.max(44, font.width(reset) + 16);
        buildButton = addRenderableWidget(Button.builder(build, b -> click(AssemblyMenu.BTN_BUILD))
                .bounds(leftPos + w - 8 - bw, by, bw, 16).build());
        addRenderableWidget(Button.builder(reset, b -> click(AssemblyMenu.BTN_RESET))
                .bounds(leftPos + w - 8 - bw - 4 - rw, by, rw, 16).build());
        costLimit = w - 8 - bw - 4 - rw - 8;
        Component tl = Component.translatable("gui.minepc.templates");
        int tw = font.width(tl) + 14;
        templatesButton = addRenderableWidget(Button.builder(tl, b -> templatesOpen = !templatesOpen)
                .bounds(leftPos + w - 8 - tw, topPos + TABS_Y, tw, TABS_H - 2).build());
    }

    /** До какой x (от левого края окна) можно рисовать материалы. */
    private int costLimit;

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

    /** Обрезать строку под ширину, с многоточием. */
    private String fit(String s, int width) {
        if (font.width(s) <= width) return s;
        return font.plainSubstrByWidth(s, Math.max(0, width - font.width("…"))) + "…";
    }

    // ======================= Раскладка параметров =======================

    private record Chip(int option, int x, int y, int w, String text) {
    }

    /** Строка списка: заголовок страницы, параметр со всеми вариантами или поле надписи. y — от начала списка. */
    private record Row(int param, int page, int y, int h, List<Chip> chips) {
    }

    private final Map<String, Boolean> sameUnit = new HashMap<>();

    private List<Row> rows(PartSpec spec, List<Integer> values) {
        List<Row> rows = new ArrayList<>();
        int y = 0;
        int chipsX = labelW, chipsR = parW - 6;
        for (int page = 0; page < spec.pages(); page++) {
            if (spec.pages() > 1) {
                if (page > 0) y += 4;
                rows.add(new Row(ROW_HEADER, page, y, 14, List.of()));
                y += 14;
            }
            for (int i : spec.order()) {
                Param p = spec.params().get(i);
                if (p.page() != page || !spec.isShown(i, values)) continue;
                List<Chip> chips = new ArrayList<>();
                int cx = chipsX, cy = 0;
                int selected = values.get(i);
                for (int o = 0; o < p.count(); o++) {
                    String text = p.isColor() ? "" : chipText(p, o, o == selected);
                    int cw = p.isColor() ? SWATCH : Math.min(font.width(text) + 6, chipsR - chipsX);
                    if (cx + cw > chipsR && cx > chipsX) {
                        cx = chipsX;
                        cy += LINE;
                    }
                    chips.add(new Chip(o, cx, cy, cw, text));
                    cx += cw + (p.isColor() ? 2 : CHIP_GAP);
                }
                int rh = cy + LINE + 3;
                rows.add(new Row(i, page, y, rh, chips));
                y += rh;
            }
        }
        y += 4;
        rows.add(new Row(ROW_LABEL, spec.pages() - 1, y, 17, List.of()));
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
        g.fill(x + 1, y + TABS_Y + TABS_H, x + w - 1, y + TABS_Y + TABS_H + 1, BORDER);
        g.fill(x + pvX, y + CONTENT_Y, x + pvX + pvW, y + CONTENT_Y + pvH, PANEL);
        g.fill(x + pvX - 6, y + CONTENT_Y, x + pvX - 5, y + contentBottom, BORDER);
        g.fill(x + 1, y + h - BOTTOM_H, x + w - 1, y + h - BOTTOM_H + 1, BORDER);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, fit(title.getString(), w - 30), 8, 5, TEXT, false);
        boolean hover = inside(mouseX, mouseY, leftPos + w - 16, topPos + 2, 12, 12);
        g.drawString(font, "?", w - 12, 5, hover ? ACCENT : TEXT_OFF, false);
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

        PartSpec spec = menu.spec();
        List<Integer> values = menu.values();
        List<Row> rows = rows(spec, values);
        paramScroll = clamp(paramScroll, 0, maxParamScroll(rows));
        placeLabelBox(rows);

        super.render(g, mx, my, partialTick);

        renderTabs(g, mx, my);
        Row hovered = rowAt(rows, mx, my);
        int page = hovered != null && hovered.param() >= 0 ? hovered.page() : previewPage;
        ItemStack preview = HardwarePresets.build(spec.type(), labelBox.getValue(), spec.previewValues(values, page));
        ItemStack result = HardwarePresets.build(spec.type(), labelBox.getValue(), values);
        renderPreview(g, preview, result, spec, values, page, mx, my);
        renderResults(g, spec, values);
        Runnable tooltip = renderParams(g, spec, values, rows, mx, my);
        Runnable costTip = renderCost(g, spec, values, mx, my);

        if (overlay) {
            renderTemplates(g, mouseX, mouseY);
        } else {
            if (tooltip != null) tooltip.run();
            if (costTip != null) costTip.run();
            if (inside(mouseX, mouseY, leftPos + w - 16, topPos + 2, 12, 12)) {
                g.renderTooltip(font, font.split(Component.translatable("gui.minepc.help_general"), 230), mouseX, mouseY);
            }
            renderTooltip(g, mouseX, mouseY);
        }
    }

    /** Поле надписи живёт внутри списка параметров: двигается с прокруткой и прячется, если не влезает. */
    private void placeLabelBox(List<Row> rows) {
        Row r = rows.get(rows.size() - 1);
        int y = paramsTop() + r.y() - paramScroll + 2;
        labelBox.setY(y);
        labelBox.visible = y >= paramsTop() && y + 13 <= topPos + contentBottom;
    }

    private int tabWidth(int i) {
        PartSpec spec = Specs.ALL.get(i);
        return i == menu.typeIndex() ? 22 + font.width(tabName(spec)) + 6 : 22;
    }

    private static Component tabName(PartSpec spec) {
        return Component.translatable("tab.minepc." + spec.type().name().toLowerCase());
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        int tx = leftPos + 8, ty = topPos + TABS_Y;
        Component hoverName = null;
        for (int i = 0; i < Specs.ALL.size(); i++) {
            PartSpec spec = Specs.ALL.get(i);
            int tw = tabWidth(i);
            boolean selected = i == menu.typeIndex();
            boolean hover = inside(mouseX, mouseY, tx, ty, tw, TABS_H);
            if (selected || hover) g.fill(tx, ty, tx + tw, ty + TABS_H, selected ? PANEL_LIGHT : PANEL);
            if (selected) g.fill(tx, ty + TABS_H - 2, tx + tw, ty + TABS_H, ACCENT);
            g.renderItem(HardwarePresets.build(spec.type(), "", spec.defaults()), tx + 3, ty + 1);
            if (selected) g.drawString(font, tabName(spec), tx + 22, ty + 5, TEXT, false);
            else if (hover) hoverName = tabName(spec);
            tx += tw + 2;
        }
        if (hoverName != null) g.renderTooltip(font, hoverName, mouseX, mouseY);
    }

    private void renderPreview(GuiGraphics g, ItemStack stack, ItemStack result, PartSpec spec, List<Integer> values,
                               int page, int mouseX, int mouseY) {
        int bx = leftPos + pvX, by = topPos + CONTENT_Y;
        int cx = bx + pvW / 2, cy = by + pvH / 2;
        g.enableScissor(bx, by, bx + pvW, by + pvH);
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
            g.drawString(font, fit(spec.pageName(page).getString(), pvW - 8), bx + 4, by + 4, TEXT_OFF, false);
        }

        // Название под превью — цветом уровня; полное название и что такое уровень — в подсказке
        int tier = spec.tier(values);
        String name = result.getHoverName().getString();
        int ny = by + pvH + 4;
        g.drawString(font, fit(name, pvW), bx, ny, TIER_COLOR[tier], false);
        if (inside(mouseX, mouseY, bx, ny - 1, pvW, 11)) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.add(Component.literal(name).getVisualOrderText());
            lines.addAll(font.split(Component.translatable("gui.minepc.tier_help").withStyle(ChatFormatting.GRAY), 220));
            tooltipLater = () -> g.renderTooltip(font, lines, mouseX, mouseY);
        }
    }

    /** Одна строка «что получится»: подпись слева (может быть пустой) и значение. */
    private record ResultLine(String label, FormattedCharSequence value, int color, int indent) {
    }

    private List<ResultLine> resultLines(PartSpec spec, List<Integer> values, int width) {
        List<Component> lines = new ArrayList<>();
        spec.describe(values, lines);
        int power = spec.powerDraw(values);
        if (power > 0) lines.add(Component.translatable("tooltip.minepc.power", Units.watts(power)));
        TextColor perfColor = TextColor.fromLegacyFormat(ChatFormatting.DARK_AQUA);
        List<ResultLine> out = new ArrayList<>();
        for (Component line : lines) {
            // Выбранные значения и так видны слева — оставляем то, что из них получается
            if (line.getContents() instanceof TranslatableContents t && t.getKey().equals("tooltip.minepc.param")) continue;
            boolean perf = perfColor.equals(line.getStyle().getColor());
            int color = perf ? PERF : TEXT;
            String text = line.getString();
            int colon = text.indexOf(": ");
            if (colon > 0) {
                String label = text.substring(0, colon + 1);
                String value = text.substring(colon + 2);
                if (font.width(label) + 4 + font.width(value) <= width) {
                    out.add(new ResultLine(label, Component.literal(value).getVisualOrderText(), color, font.width(label) + 4));
                } else {
                    out.add(new ResultLine(label, FormattedCharSequence.EMPTY, color, 0));
                    for (FormattedCharSequence s : font.split(Component.literal(value), width - 8)) {
                        out.add(new ResultLine("", s, color, 8));
                    }
                }
            } else {
                for (FormattedCharSequence s : font.split(Component.literal(text), width)) {
                    out.add(new ResultLine("", s, color, 0));
                }
            }
        }
        return out;
    }

    private void renderResults(GuiGraphics g, PartSpec spec, List<Integer> values) {
        int x = leftPos + resX, top = topPos + resTop;
        g.drawString(font, fit(Component.translatable("gui.minepc.result").getString().toUpperCase(), resW), x, top, TEXT_OFF, false);
        int areaTop = top + 13, areaBottom = topPos + contentBottom;
        List<ResultLine> lines = resultLines(spec, values, resW - 6);
        int max = Math.max(0, lines.size() * 10 - (areaBottom - areaTop));
        resultScroll = clamp(resultScroll, 0, max);
        g.enableScissor(x, areaTop, x + resW, areaBottom);
        int y = areaTop - resultScroll;
        for (ResultLine l : lines) {
            if (!l.label().isEmpty()) g.drawString(font, l.label(), x, y, TEXT_DIM, false);
            g.drawString(font, l.value(), x + l.indent(), y, l.color(), false);
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
        boolean inList = mouseY >= top && mouseY < bottom;
        g.enableScissor(x0 - 2, top, x0 + parW, bottom);
        for (Row row : rows) {
            int ry = top + row.y() - paramScroll;
            if (ry + row.h() < top || ry > bottom) continue;
            if (row.param() == ROW_HEADER) {
                String name = spec.pageName(row.page()).getString().toUpperCase();
                g.drawString(font, fit(name, parW - 20), x0, ry + 3, TEXT_OFF, false);
                int lx = x0 + Math.min(font.width(name), parW - 20) + 6;
                g.fill(lx, ry + 7, x0 + parW - 6, ry + 8, BORDER);
                continue;
            }
            if (row.param() == ROW_LABEL) {
                g.drawString(font, fit(Component.translatable("gui.minepc.label_row").getString(), labelW - 6), x0, ry + 5, TEXT_DIM, false);
                continue;
            }
            Param p = spec.params().get(row.param());
            boolean active = spec.isActive(row.param(), values);

            // Подпись: наведи — подсказка, что параметр делает
            boolean labelHover = inList && inside(mouseX, mouseY, x0, ry, labelW - 4, LINE);
            String label = fit(p.label().getString(), labelW - 6);
            g.drawString(font, label, x0, ry + 2, !active ? TEXT_OFF : labelHover ? TEXT : TEXT_DIM, false);
            if (labelHover) {
                String helpKey = "param.minepc." + p.key() + ".help";
                List<FormattedCharSequence> lines = new ArrayList<>();
                lines.add(p.label().copy().withStyle(ChatFormatting.WHITE).getVisualOrderText());
                if (I18n.exists(helpKey)) lines.addAll(font.split(Component.translatable(helpKey).withStyle(ChatFormatting.GRAY), 220));
                if (!active) lines.addAll(font.split(Component.translatable("gui.minepc.inactive").withStyle(ChatFormatting.DARK_GRAY), 220));
                tip = () -> g.renderTooltip(font, lines, mouseX, mouseY);
            }

            int selected = values.get(row.param());
            for (Chip c : row.chips()) {
                int cx = x0 + c.x(), cy = ry + c.y();
                boolean sel = c.option() == selected;
                boolean hover = active && inList && inside(mouseX, mouseY, cx - 1, cy, c.w() + 2, CHIP_H);
                if (p.isColor()) {
                    int sy = cy + 1;
                    int col = 0xFF000000 | p.color(c.option());
                    if (sel) outline(g, cx - 1, sy - 1, SWATCH + 2, SWATCH + 2, active ? TEXT : TEXT_OFF);
                    else if (hover) outline(g, cx - 1, sy - 1, SWATCH + 2, SWATCH + 2, TEXT_DIM);
                    g.fill(cx, sy, cx + SWATCH, sy + SWATCH, active ? col : (col & 0x00FFFFFF) | 0x50000000);
                } else {
                    if (sel) g.fill(cx, cy, cx + c.w(), cy + CHIP_H, active ? ACCENT : BORDER);
                    else if (hover) g.fill(cx, cy, cx + c.w(), cy + CHIP_H, CHIP_HOVER);
                    int color = !active ? TEXT_OFF : sel ? 0xFFFFFFFF : hover ? TEXT : TEXT_DIM;
                    g.drawString(font, fit(c.text(), c.w() - 6), cx + 3, cy + 2, color, false);
                }
                if (hover) {
                    int tier = p.tierAt(c.option());
                    List<Component> lines = new ArrayList<>();
                    lines.add(p.display(c.option()).copy().withStyle(ChatFormatting.WHITE));
                    if (tier > 1) lines.add(Component.translatable("gui.minepc.option_tier", tier).withStyle(ChatFormatting.GRAY));
                    tip = () -> g.renderComponentTooltip(font, lines, mouseX, mouseY);
                }
            }
        }
        g.disableScissor();
        int max = maxParamScroll(rows);
        if (max > 0) scrollBar(g, x0 + parW - 3, top, bottom - top, paramScroll, max);
        return tip;
    }

    private void scrollBar(GuiGraphics g, int x, int y, int height, int scroll, int max) {
        int bar = Math.max(12, height * height / (height + max));
        int by = y + (height - bar) * scroll / Math.max(1, max);
        g.fill(x, by, x + 2, by + bar, 0xFF4A505B);
    }

    /** Материалы внизу: сколько нужно и есть ли. Возвращает подсказку поверх всего (или null). */
    private Runnable renderCost(GuiGraphics g, PartSpec spec, List<Integer> values, int mouseX, int mouseY) {
        int x = leftPos + 8, y = topPos + h - BOTTOM_H + 4;
        boolean creative = minecraft != null && minecraft.player != null && minecraft.player.getAbilities().instabuild;
        boolean enough = true;
        Runnable tip = null;
        Component missing = null;
        int limit = leftPos + costLimit;
        for (Cost.Entry e : spec.cost(values).entries()) {
            ItemStack icon = new ItemStack(e.item());
            int have = minecraft != null && minecraft.player != null ? minecraft.player.getInventory().countItem(e.item()) : 0;
            boolean ok = creative || have >= e.count();
            enough &= ok;
            if (!ok && missing == null) {
                missing = Component.translatable("gui.minepc.missing", e.count() - have, icon.getHoverName());
            }
            if (x + 16 > limit) continue;
            if (!ok) g.fill(x, y, x + 16, y + 16, 0x60E05555);
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
        int room = limit - x - 6;
        if (note != null && room > 30) {
            g.drawString(font, fit(note.getString(), room), x + 4, y + 4, creative ? GOOD : BAD, false);
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
        return Math.max(leftPos + 4, leftPos + w - 8 - TPL_W);
    }

    private int tplY() {
        return topPos + TABS_Y + TABS_H;
    }

    private void renderTemplates(GuiGraphics g, int mouseX, int mouseY) {
        List<Template> list = templates();
        int x = tplX(), y = tplY();
        int rowsCount = list.size() + 2;   // + заголовок своих и «сохранить»
        g.flush();   // сначала дорисовать текст под списком, иначе он просвечивает поверх
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 400);
        int hgt = rowsCount * TPL_ROW + 6;
        g.fill(x, y, x + TPL_W, y + hgt, 0xFF181A1F);
        outline(g, x, y, TPL_W, hgt, BORDER);
        int ry = y + 3;
        int builtIn = (int) list.stream().filter(t -> t.saved() == null).count();
        for (int i = 0; i < list.size(); i++) {
            if (i == builtIn) {
                g.drawString(font, fit(Component.translatable("gui.minepc.templates_mine").getString(), TPL_W - 12), x + 6, ry + 2, TEXT_OFF, false);
                ry += TPL_ROW;
            }
            Template t = list.get(i);
            boolean hover = inside(mouseX, mouseY, x + 1, ry, TPL_W - 2, TPL_ROW);
            if (hover) g.fill(x + 1, ry, x + TPL_W - 1, ry + TPL_ROW, CHIP_HOVER);
            g.drawString(font, fit(t.name().getString(), TPL_W - 20), x + 6, ry + 2, hover ? TEXT : TEXT_DIM, false);
            if (t.saved() != null) {
                boolean xHover = inside(mouseX, mouseY, x + TPL_W - 12, ry, 10, TPL_ROW);
                g.drawString(font, "✕", x + TPL_W - 10, ry + 2, xHover ? BAD : TEXT_OFF, false);
            }
            ry += TPL_ROW;
        }
        if (builtIn == list.size()) {
            g.drawString(font, fit(Component.translatable("gui.minepc.templates_mine").getString(), TPL_W - 12), x + 6, ry + 2, TEXT_OFF, false);
            ry += TPL_ROW;
        }
        boolean hover = inside(mouseX, mouseY, x + 1, ry, TPL_W - 2, TPL_ROW);
        if (hover) g.fill(x + 1, ry, x + TPL_W - 1, ry + TPL_ROW, CHIP_HOVER);
        g.drawString(font, fit(Component.translatable("gui.minepc.template_save").getString(), TPL_W - 12), x + 6, ry + 2, ACCENT, false);
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
            int tx = leftPos + 8, ty = topPos + TABS_Y;
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
