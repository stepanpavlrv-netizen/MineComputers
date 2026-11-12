package com.strikerr212.minepc.client;

import com.strikerr212.minepc.block.PcCaseBlockEntity;
import com.strikerr212.minepc.block.RackBlock;
import com.strikerr212.minepc.block.RackBlockEntity;
import com.strikerr212.minepc.block.HostRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Консоль стойки (KVM): список серверов в этой колонне стоек — выбрал, и открывается его экран
 * (клавиатура консоли печатает в него).
 */
public class ConsoleScreen extends Screen {

    private final BlockPos rackPos;

    public ConsoleScreen(BlockPos rackPos) {
        super(Component.translatable("gui.minepc.console"));
        this.rackPos = rackPos;
    }

    private record Entry(HostRef host, PcCaseBlockEntity server, int rack) {
    }

    private List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        var level = Minecraft.getInstance().level;
        if (level == null) return out;
        List<BlockPos> column = RackBlock.column(level, rackPos);
        for (int r = 0; r < column.size(); r++) {
            if (!(level.getBlockEntity(column.get(r)) instanceof RackBlockEntity rack)) continue;
            for (int s = RackBlockEntity.SLOTS - 1; s >= 0; s--) {
                PcCaseBlockEntity pc = rack.server(s);
                if (pc != null) out.add(new Entry(new HostRef(column.get(r), s), pc, r));
            }
        }
        // Сверху вниз, как в стойке
        java.util.Collections.reverse(out);
        out.sort((a, b) -> b.rack != a.rack ? Integer.compare(b.rack, a.rack) : Integer.compare(b.host.slot(), a.host.slot()));
        return out;
    }

    private int panelX, panelY, panelW, panelH;

    @Override
    protected void init() {
        List<Entry> list = entries();
        int rows = Math.max(1, list.size());
        panelW = Math.min(width - 16, 260);
        panelH = 44 + rows * 22 + 6;
        panelX = width / 2 - panelW / 2;
        panelY = Math.max(8, height / 2 - panelH / 2);
        int x = panelX + 8, w = panelW - 16, y = panelY + 40;
        for (Entry e : list) {
            String name = e.server.label().isEmpty() ? e.server.address().substring(0, Math.min(8, e.server.address().length())) : e.server.label();
            int state = e.server.displayState();
            Component status = Component.translatable(state == 1 ? "gui.minepc.console.on" : state == 2 ? "gui.minepc.console.error" : "gui.minepc.console.off");
            Component text = Component.translatable("gui.minepc.console.entry", e.rack + 1, e.host.slot() + 1, name, status);
            addRenderableWidget(Button.builder(text, b -> {
                if (minecraft != null) minecraft.setScreen(new TerminalScreen(e.host));
            }).bounds(x, y, w - 24, 20).build());
            // Кнопка питания сервера — прямо с консоли, не выдвигая модуль
            Button power = Button.builder(Component.literal("⏻"), b -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new com.strikerr212.minepc.network.TerminalInputPayload(e.host, com.strikerr212.minepc.network.TerminalInputPayload.POWER, "", 0, 0, 0)))
                    .bounds(x + w - 20, y, 20, 20).build();
            power.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(
                    state == 1 ? "gui.minepc.power_tip.on" : "gui.minepc.power_tip.off")));
            addRenderableWidget(power);
            y += 22;
        }
        if (list.isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.minepc.console.none"), b -> onClose())
                    .bounds(x, y, w, 20).build());
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        // Панель консоли: тёмный экран в стальной рамке
        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF5A6068);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0101418);
        g.fill(panelX, panelY, panelX + panelW, panelY + 30, 0xFF161C22);
        g.fill(panelX, panelY + 30, panelX + panelW, panelY + 31, 0xFF2A323A);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, panelX + 8, panelY + 6, 0xFF6EE08A, false);
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("gui.minepc.console.hint").getString(), panelW - 16),
                panelX + 8, panelY + 18, 0xFF8A96A0, false);
    }

    private int age;

    /** Состояние серверов меняется (кнопка питания, BMC) — раз в секунду перестроить список. */
    @Override
    public void tick() {
        super.tick();
        if (++age % 20 == 0) rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
