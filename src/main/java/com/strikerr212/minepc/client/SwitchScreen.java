package com.strikerr212.minepc.client;

import com.strikerr212.minepc.network.SwitchOpenPayload;
import com.strikerr212.minepc.network.SwitchPortPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Окно коммутатора: таблица портов. Кнопка «вкл/выкл» и цвет VLAN (ЛКМ — следующий цвет, ПКМ — предыдущий):
 * порты разных цветов друг друга не слышат, без цвета — общая сеть.
 */
public class SwitchScreen extends Screen {

    private static final int W = 300, ROW = 13, MAX_ROWS = 12;
    private static final int BG = 0xF01D2026, PANEL = 0xFF272B33, LINE = 0xFF3A404B, FG = 0xFFE6E9EF, DIM = 0xFF8D95A3,
            GOOD = 0xFF5BD982, BAD = 0xFFE5534B, OFF = 0xFF555B66, PC = 0xFF7FD48A;

    private record Row(int port, char side, int number, boolean link, String device, boolean enabled, int vlan) {
    }

    private final BlockPos pos;
    private String kind, id;
    private List<Row> rows = List.of();
    private int left, top, panelH, scroll, age;

    public SwitchScreen(SwitchOpenPayload p) {
        super(Component.translatable("gui.minepc.switch.title"));
        this.pos = p.pos();
        update(p);
    }

    public BlockPos pos() {
        return pos;
    }

    public void update(SwitchOpenPayload p) {
        kind = p.kind();
        id = p.id();
        List<Row> list = new ArrayList<>();
        for (String line : p.ports().split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length < 7) continue;
            try {
                list.add(new Row(Integer.parseInt(f[0]), f[1].charAt(0), Integer.parseInt(f[2]), f[3].equals("1"), f[4],
                        f[5].equals("1"), Integer.parseInt(f[6])));
            } catch (RuntimeException ignored) {
            }
        }
        rows = list;
        scroll = Math.max(0, Math.min(scroll, rows.size() - MAX_ROWS));
    }

    @Override
    protected void init() {
        panelH = 44 + Math.max(1, Math.min(MAX_ROWS, rows.size())) * ROW + 40;
        left = (width - W) / 2;
        top = (height - panelH) / 2;
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + W - 90, top + panelH - 22, 80, 16).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (++age % 40 == 0) PacketDistributor.sendToServer(new SwitchPortPayload(pos, -1, true, 0));
        int want = 44 + Math.max(1, Math.min(MAX_ROWS, rows.size())) * ROW + 40;
        if (want != panelH) rebuildWidgets();
    }

    private String sideName(Row r) {
        String key = switch (r.side) {
            case 'F' -> "gui.minepc.switch.front";
            case 'B' -> "gui.minepc.switch.back";
            case 'L' -> "gui.minepc.switch.left";
            case 'R' -> "gui.minepc.switch.right";
            default -> "gui.minepc.switch.cord";
        };
        return Component.translatable(key, r.number).getString();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left - 1, top - 1, left + W + 1, top + panelH + 1, LINE);
        g.fill(left, top, left + W, top + panelH, BG);
        g.fill(left, top, left + W, top + 20, PANEL);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, Component.translatable("gui.minepc.switch.kind." + kind), left + 8, top + 6, FG, false);
        String idText = "id " + id;
        g.drawString(font, idText, left + W - 8 - font.width(idText), top + 6, DIM, false);
        int y = top + 26;
        g.drawString(font, Component.translatable("gui.minepc.switch.port"), left + 8, y, DIM, false);
        g.drawString(font, Component.translatable("gui.minepc.switch.device"), left + 92, y, DIM, false);
        g.drawString(font, "VLAN", left + W - 72, y, DIM, false);
        y += 12;
        g.fill(left + 6, y - 2, left + W - 6, y - 1, LINE);
        if (rows.isEmpty()) g.drawString(font, Component.translatable("gui.minepc.switch.empty"), left + 8, y + 2, DIM, false);
        for (int i = scroll; i < Math.min(rows.size(), scroll + MAX_ROWS); i++) {
            Row r = rows.get(i);
            boolean hover = mouseX >= left + 6 && mouseX < left + W - 6 && mouseY >= y && mouseY < y + ROW;
            if (hover) g.fill(left + 6, y, left + W - 6, y + ROW, 0x18FFFFFF);
            // лампочка связи
            g.fill(left + 8, y + 4, left + 12, y + 8, !r.enabled ? OFF : r.link ? GOOD : LINE);
            g.drawString(font, sideName(r), left + 16, y + 2, r.enabled ? FG : DIM, false);
            String dev = r.device.equals("!") ? Component.translatable("gui.minepc.switch.too_long").getString()
                    : r.device.isEmpty() ? (r.link ? "" : "—")
                    : r.device.startsWith("@") ? r.device.substring(1)
                    : Component.translatable("gui.minepc.switch.dev." + r.device.split(" ")[0]).getString()
                    + (r.device.contains(" ") ? r.device.substring(r.device.indexOf(' ')) : "");
            int devColor = r.device.equals("!") ? BAD : r.device.startsWith("@") ? PC : DIM;
            g.drawString(font, font.plainSubstrByWidth(dev, W - 92 - 84), left + 92, y + 2, devColor, false);
            // VLAN: квадратик цвета (пусто — общий)
            int vx = left + W - 72;
            g.fill(vx, y + 1, vx + 11, y + 12, LINE);
            if (r.vlan > 0) g.fill(vx + 1, y + 2, vx + 10, y + 11, 0xFF000000 | DyeColor.byId(r.vlan - 1).getTextureDiffuseColor());
            else g.drawString(font, "·", vx + 4, y + 2, DIM, false);
            // вкл/выкл
            int bx = left + W - 46;
            g.fill(bx, y + 1, bx + 38, y + 12, r.enabled ? 0xFF2E6B45 : 0xFF6B2E2E);
            Component on = Component.translatable(r.enabled ? "gui.minepc.switch.on" : "gui.minepc.switch.off");
            g.drawString(font, on, bx + 19 - font.width(on) / 2, y + 3, FG, false);
            y += ROW;
        }
        if (rows.size() > MAX_ROWS) {
            String more = (scroll + 1) + "–" + Math.min(rows.size(), scroll + MAX_ROWS) + " / " + rows.size();
            g.drawString(font, more, left + 8, top + panelH - 18, DIM, false);
        }
        g.drawString(font, Component.translatable("gui.minepc.switch.hint"), left + 8, top + panelH - 34, DIM, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int y = top + 38;
        for (int i = scroll; i < Math.min(rows.size(), scroll + MAX_ROWS); i++) {
            Row r = rows.get(i);
            if (my >= y && my < y + ROW) {
                int vx = left + W - 72, bx = left + W - 46;
                if (mx >= vx && mx < vx + 11) {
                    int v = (r.vlan + (button == 1 ? 16 : 1)) % 17;
                    PacketDistributor.sendToServer(new SwitchPortPayload(pos, r.port, r.enabled, v));
                    return true;
                }
                if (mx >= bx && mx < bx + 38) {
                    PacketDistributor.sendToServer(new SwitchPortPayload(pos, r.port, !r.enabled, r.vlan));
                    return true;
                }
            }
            y += ROW;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = Math.max(0, Math.min(Math.max(0, rows.size() - MAX_ROWS), scroll - (int) Math.signum(dy)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
