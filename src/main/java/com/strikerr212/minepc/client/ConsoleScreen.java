package com.strikerr212.minepc.client;

import com.strikerr212.minepc.block.PcCaseBlockEntity;
import com.strikerr212.minepc.block.RackBlock;
import com.strikerr212.minepc.block.RackBlockEntity;
import com.strikerr212.minepc.computer.HostRef;
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

    @Override
    protected void init() {
        List<Entry> list = entries();
        int w = 220, x = width / 2 - w / 2, y = Math.max(30, height / 2 - list.size() * 11);
        for (Entry e : list) {
            String name = e.server.label().isEmpty() ? e.server.address().substring(0, Math.min(8, e.server.address().length())) : e.server.label();
            int state = e.server.displayState();
            Component status = Component.translatable(state == 1 ? "gui.minepc.console.on" : state == 2 ? "gui.minepc.console.error" : "gui.minepc.console.off");
            Component text = Component.translatable("gui.minepc.console.entry", e.rack + 1, e.host.slot() + 1, name, status);
            addRenderableWidget(Button.builder(text, b -> {
                if (minecraft != null) minecraft.setScreen(new TerminalScreen(e.host));
            }).bounds(x, y, w, 20).build());
            y += 22;
        }
        if (list.isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.minepc.console.none"), b -> onClose())
                    .bounds(x, y, w, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("gui.minepc.console.hint"), width / 2, 24, 0x9097A1);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
