package com.strikerr212.minepc.client;

import com.strikerr212.minepc.block.DiskHealth;
import com.strikerr212.minepc.block.PcCaseBlockEntity;
import com.strikerr212.minepc.hardware.MotherboardData;
import com.strikerr212.minepc.hardware.spec.CoolerSpec;
import com.strikerr212.minepc.hardware.spec.GpuSpec;
import com.strikerr212.minepc.hardware.spec.Palette;
import com.strikerr212.minepc.hardware.spec.PsuSpec;
import com.strikerr212.minepc.hardware.spec.SsdSpec;
import com.strikerr212.minepc.item.CoolerItem;
import com.strikerr212.minepc.item.HardwareItem;
import com.strikerr212.minepc.item.PsuItem;
import com.strikerr212.minepc.menu.CaseLayout;
import com.strikerr212.minepc.menu.PcCaseMenu;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static com.strikerr212.minepc.block.PcCaseBlockEntity.*;
import static com.strikerr212.minepc.menu.CaseLayout.*;

/**
 * Корпус без боковой крышки (окно 2.0). Рисуется поверх значков слотов: детали — своей формой и цветом
 * (цвета берутся из дизайна детали), свободные места — пунктирным силуэтом, места, которых у платы нет, не рисуются.
 * Координаты — от левого верхнего угла окна, прямоугольники [x0, x1) × [y0, y1).
 */
final class CaseView {

    private final GuiGraphics g;
    private final Font font;
    private final CaseTheme th;
    private final PcCaseMenu menu;
    private final PcCaseBlockEntity be;
    private final boolean powered;
    /** Время для вращения вентиляторов и мигания ламп, тики. */
    private final float time;
    private final int hovered;
    private final ItemStack carried;

    CaseView(GuiGraphics g, Font font, CaseTheme th, PcCaseMenu menu, boolean powered, float time, int hovered, ItemStack carried) {
        this.g = g;
        this.font = font;
        this.th = th;
        this.menu = menu;
        this.be = menu.blockEntity();
        this.powered = powered;
        this.time = time;
        this.hovered = hovered;
        this.carried = carried;
    }

    // ======================= Примитивы =======================

    private void box(int x0, int y0, int x1, int y1, int c) {
        if (x1 > x0 && y1 > y0) g.fill(x0, y0, x1, y1, c);
    }

    private void px(int x, int y, int c) {
        g.fill(x, y, x + 1, y + 1, c);
    }

    private void frame(int x0, int y0, int x1, int y1, int c) {
        box(x0, y0, x1, y0 + 1, c);
        box(x0, y1 - 1, x1, y1, c);
        box(x0, y0, x0 + 1, y1, c);
        box(x1 - 1, y0, x1, y1, c);
    }

    private void dashed(int x0, int y0, int x1, int y1, int c) {
        int phase = 0;
        for (int x = x0; x < x1; x++, phase++) {
            if ((phase & 2) == 0) {
                px(x, y0, c);
                px(x, y1 - 1, c);
            }
        }
        phase = 0;
        for (int y = y0; y < y1; y++, phase++) {
            if ((phase & 2) == 0) {
                px(x0, y, c);
                px(x1 - 1, y, c);
            }
        }
    }

    private static int argb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static int alpha(int c, int a) {
        return (a << 24) | (c & 0xFFFFFF);
    }

    private static int sh(int c, double k) {
        return CaseTheme.sh(c, k);
    }

    /** Вентилятор: рамка, лопасти (крутятся у включённого), втулка; ring — подсветка кольца (0 — нет). */
    private void fan(int cx, int cy, int r, int frameC, int blade, int hub, int ring, boolean spin) {
        box(cx - r, cy - r, cx + r + 1, cy + r + 1, frameC);
        double a0 = spin ? time * 0.45 : 0.6;
        int inner = sh(frameC, 0.45);
        for (int y = -r; y <= r; y++) {
            for (int x = -r; x <= r; x++) {
                double d = Math.sqrt(x * x + y * y);
                if (d > r - 0.4) continue;
                if (ring != 0 && d > r - 1.6) {
                    px(cx + x, cy + y, ring);
                    continue;
                }
                if (d < Math.max(1.5, r * 0.28)) {
                    px(cx + x, cy + y, hub);
                    continue;
                }
                double ang = Math.atan2(y, x) - a0 - d * 0.09;
                double f = (ang * 7 / (2 * Math.PI)) % 1.0;
                if (f < 0) f += 1;
                px(cx + x, cy + y, f < 0.42 ? blade : inner);
            }
        }
    }

    private int tint(ItemStack stack, int layer) {
        if (!(stack.getItem() instanceof HardwareItem h)) return 0x808080;
        return h.spec().tint(h.values(stack), layer);
    }

    private int idx(ItemStack stack, int param) {
        if (!(stack.getItem() instanceof HardwareItem h)) return 0;
        List<Integer> v = h.values(stack);
        return param < v.size() ? v.get(param) : 0;
    }

    /** Подсветка детали: цвет светодиода, если он есть и компьютер включён, иначе 0. */
    private int led(ItemStack stack, int layer) {
        int c = tint(stack, layer);
        return powered && c != Palette.LED_OFF ? argb(c) : 0;
    }

    private ItemStack stack(int slot) {
        return menu.slots.get(slot).getItem();
    }

    private boolean shown(int slot) {
        return menu.slotShown(slot);
    }

    private int[] hit(int slot) {
        return menu.hit(slot);
    }

    // ======================= Всё окно корпуса =======================

    void draw() {
        if (menu.serverLayout()) drawServer();
        else drawDesktop();
        drawSlotStates();
    }

    // ======================= Обычный корпус: вид сбоку =======================

    private void drawDesktop() {
        MotherboardData board = be == null ? null : be.motherboard();
        // стенка с перфорацией
        box(CX0, CY0, CX1, CY1, argb(th.wall));
        for (int y = CY0 + 3; y < SHROUD_Y - 1; y += 4) {
            for (int x = CX0 + 6; x < CX1 - 4; x += 4) {
                if (((x + y) / 4) % 2 == 0) px(x, y, argb(th.wallDot));
            }
        }
        // задняя стенка с заглушками слотов расширения, передняя стенка
        box(CX0, CY0, CX0 + 4, CY1, argb(th.frame));
        for (int i = 0; i < 4; i++) box(CX0, 80 + i * 7, CX0 + 3, 84 + i * 7, argb(sh(th.frame, 0.7)));
        box(CX1 - 3, CY0, CX1, CY1, argb(th.frame));
        box(CX0, CY0, CX1, CY0 + 1, argb(sh(th.frame, 1.1)));
        // кабельные вырезы с резинками
        for (int y : new int[]{34, 66}) box(134, y, 139, y + 16, argb(sh(th.wall, 0.45)));
        // кожух блока питания
        box(CX0 + 1, SHROUD_Y, CX1 - 1, CY1, argb(th.shroud));
        box(CX0 + 1, SHROUD_Y, CX1 - 1, SHROUD_Y + 1, argb(sh(th.shroud, 1.18)));
        if (th.rgbLights && powered) {
            for (int x = CX0 + 4; x < 128; x++) px(x, SHROUD_Y, argb(CaseTheme.rgbAt((x - time * 0.6) / 90.0)));
        }
        // корпусные вентиляторы спереди (или радиатор СВО)
        ItemStack cooler = stack(SLOT_COOLER);
        boolean aio = cooler.getItem() instanceof CoolerItem && idx(cooler, CoolerSpec.TYPE) == 4;
        if (!aio) {
            for (int i = 0; i < 2; i++) caseFan(163, 32 + i * 30, 12);
        }

        if (board == null) {
            emptyBoard(BX0, BY0, BX1, BY1, "gui.minepc.case.board_first");
        } else {
            drawBoard(board);
        }
        // блок питания и диски (места корпуса)
        if (!stack(SLOT_PSU).isEmpty()) psu(hit(SLOT_PSU), stack(SLOT_PSU), true);
        for (int i = 0; i < 2; i++) {
            int slot = SLOT_HDD + i;
            int[] r = hit(slot);
            box(r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, argb(sh(th.shroud, 0.78)));
            if (!stack(slot).isEmpty()) hdd(r, stack(slot), false);
        }
        for (int i = 0; i < 2; i++) {
            int slot = SLOT_SATA_SSD + i;
            if (!stack(slot).isEmpty()) sataSsd(hit(slot), stack(slot));
        }
        if (board != null) {
            // кабели: 24-pin от БП к плате, питание видеокарты
            if (!stack(SLOT_PSU).isEmpty()) {
                int cable = th.rgbLights ? argb(0x3C2850) : argb(0x1E1E22);
                box(140, 76, 144, SHROUD_Y, cable);
                box(127, 72, 144, 76, cable);
                box(141, 76, 142, SHROUD_Y, argb(sh(cable == argb(0x1E1E22) ? 0x505058 : 0x6A4A90, 1)));
                ItemStack gpu = stack(SLOT_GPU);
                if (gpu.getItem() instanceof HardwareItem) {
                    int end = gpuEnd(gpu);
                    box(end - 6, 75, end - 4, 78, argb(0x141416));
                    box(end - 6, 74, end + 3, 76, argb(0x1E1E22));
                    box(end + 1, 76, end + 3, SHROUD_Y, argb(0x1E1E22));
                }
                // питание процессора — сверху из-за платы
                box(24, CY0 + 1, 27, 24, argb(0x1E1E22));
            }
        }
        if (aio) aioRadiator(cooler);
        if (th.deco == CaseTheme.Deco.GAMING) {
            int per = 2 * ((CX1 - CX0) + (CY1 - CY0));
            int k = 0;
            double shift = powered ? time / 120.0 : 0;
            for (int x = CX0; x < CX1; x++) px(x, CY0, argb(CaseTheme.rgbAt((double) k++ / per + shift)));
            for (int y = CY0; y < CY1; y++) px(CX1 - 1, y, argb(CaseTheme.rgbAt((double) k++ / per + shift)));
            for (int x = CX1 - 1; x >= CX0; x--) px(x, CY1 - 1, argb(CaseTheme.rgbAt((double) k++ / per + shift)));
            for (int y = CY1 - 1; y >= CY0; y--) px(CX0, y, argb(CaseTheme.rgbAt((double) k++ / per + shift)));
        }
    }

    private void caseFan(int cx, int cy, int r) {
        int frameC = th.rgbLights ? argb(0x1A1820) : argb(sh(th.frame, 0.85));
        int blade = th.rgbLights ? argb(0x50406E) : argb(sh(th.frame, 1.15));
        int ring = th.rgbLights && powered ? argb(CaseTheme.rgbAt(cy / 140.0 + time / 200.0)) : 0;
        fan(cx, cy, r, frameC, blade, argb(sh(th.frame, 0.6)), ring, powered);
    }

    /** Места под плату ещё нет детали: пунктирный контур и подпись. */
    private void emptyBoard(int x0, int y0, int x1, int y1, String key) {
        int acc = argb(th.accent);
        boolean target = carried.getItem() instanceof com.strikerr212.minepc.item.MotherboardItem;
        if (target || hovered == SLOT_MOTHERBOARD) box(x0, y0, x1, y1, alpha(th.accent, 0x30));
        dashed(x0, y0, x1, y1, acc);
        int cx = (x0 + x1) / 2, cy = (y0 + y1) / 2 - 14;
        dashed(cx - 9, cy - 9, cx + 9, cy + 9, acc);
        box(cx - 6, cy - 6, cx + 6, cy + 6, argb(0x2E7A4A));
        box(cx - 3, cy - 3, cx + 2, cy + 2, argb(0xBEBEBE));
        Component a = Component.translatable(key + ".title"), b = Component.translatable(key + ".hint");
        g.drawString(font, a, cx - font.width(a) / 2, cy + 14, acc, false);
        g.drawString(font, b, cx - font.width(b) / 2, cy + 25, argb(sh(th.accent, 0.75)), false);
    }

    private void drawBoard(MotherboardData board) {
        ItemStack mb = stack(SLOT_MOTHERBOARD);
        int pcb = argb(tint(mb, 3)), heat = argb(tint(mb, 1));
        int ledC = led(mb, 2);
        box(BX0, BY0, BX1, BY1, pcb);
        frame(BX0, BY0, BX1, BY1, argb(sh(pcb, 0.7)));
        int trace = argb(sh(pcb, 1.25));
        for (int i = 0; i < 6; i++) box(64 + i * 3, 60, 65 + i * 3, 70, trace);
        for (int i = 0; i < 3; i++) box(26, 104 + i * 2, 70, 105 + i * 2, trace);
        for (int i = 0; i < 4; i++) box(108, 28 + i * 3, 120, 29 + i * 3, trace);
        int hole = argb(0xBEBEAA);
        for (int[] p : new int[][]{{20, 25}, {125, 25}, {20, 108}, {125, 108}, {69, 25}, {69, 108}}) box(p[0], p[1], p[0] + 2, p[1] + 2, hole);
        // задние разъёмы
        box(BX0, 26, BX0 + 8, 62, argb(0x787C82));
        for (int i = 0; i < 5; i++) box(BX0 + 1, 28 + i * 7, BX0 + 6, 32 + i * 7, argb(0x3C3E42));
        // радиаторы питания вокруг сокета
        box(29, 26, 67, 32, heat);
        box(29, 32, 35, 62, heat);
        for (int x = 29; x < 67; x += 3) box(x, 26, x + 1, 32, argb(sh(heat, 0.72)));
        if (ledC != 0) box(29, 31, 67, 32, ledC);
        // 8-pin процессора
        box(23, 23, 29, 26, argb(0x141416));
        // чипсет
        box(91, 84, 111, 102, heat);
        box(93, 86, 109, 100, argb(sh(heat, 1.15)));
        if (ledC != 0) box(93, 99, 109, 100, ledC);
        // 24-pin
        box(124, 58, 128, 82, argb(0x141416));
        for (int i = 0; i < 11; i++) px(126, 59 + i * 2, argb(0xC8BE3C));
        // слоты PCIe
        box(29, 82, 87, 85, argb(0x1C1C1E));
        box(87, 82, 89, 85, argb(0xE6E6E6));
        box(29, 98, 65, 101, argb(0x1C1C1E));
        // сокет
        if (!stack(SLOT_CPU).isEmpty() || shown(SLOT_CPU)) {
            box(41, 36, 63, 58, argb(0xAAACB0));
            box(43, 38, 61, 56, argb(0x3C3A34));
            for (int y = 40; y < 55; y += 2) for (int x = 45; x < 60; x += 2) px(x, y, argb(0xC8AA5A));
        }
        // разъёмы памяти и M.2 — сколько есть на плате
        for (int i = 0; i < 4; i++) {
            int slot = ramSlot(i);
            if (!shown(slot)) continue;
            int[] r = hit(slot);
            box(r[0] + 2, 27, r[0] + 5, 71, argb(0x1C1C1E));
            box(r[0] + 2, 27, r[0] + 5, 29, argb(0xE6E6E6));
            box(r[0] + 2, 69, r[0] + 5, 71, argb(0xE6E6E6));
        }
        for (int i = 0; i < M2_SLOTS; i++) {
            int slot = SLOT_M2 + i;
            if (!shown(slot)) continue;
            int[] r = hit(slot);
            box(r[0] + 1, r[1] + 1, r[0] + 4, r[3] - 1, argb(0x1C1C1E));
            box(r[2] - 3, r[1] + 2, r[2] - 1, r[1] + 4, hole);
        }
        // детали на плате
        if (!stack(SLOT_CPU).isEmpty()) cpu(hit(SLOT_CPU), stack(SLOT_CPU));
        for (int i = 0; i < 4; i++) {
            int slot = ramSlot(i);
            if (!stack(slot).isEmpty()) ramStick(hit(slot), stack(slot));
        }
        for (int i = 0; i < M2_SLOTS; i++) {
            int slot = SLOT_M2 + i;
            if (!stack(slot).isEmpty()) m2(hit(slot), stack(slot));
        }
        if (!stack(SLOT_GPU).isEmpty()) gpu(stack(SLOT_GPU));
        if (!stack(SLOT_COOLER).isEmpty()) cooler(stack(SLOT_COOLER));
    }

    // ----- детали обычного корпуса -----

    private void cpu(int[] r, ItemStack s) {
        box(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, argb(tint(s, 3)));
        int lid = argb(tint(s, 1));
        box(r[0] + 3, r[1] + 3, r[2] - 3, r[3] - 3, lid);
        box(r[0] + 3, r[3] - 4, r[2] - 3, r[3] - 3, argb(sh(lid, 0.78)));
        box(r[0] + 3, r[1] + 3, r[2] - 3, r[1] + 4, argb(sh(lid, 1.12)));
        int cx = (r[0] + r[2]) / 2, cy = (r[1] + r[3]) / 2;
        box(cx - 3, cy - 1, cx + 3, cy + 1, argb(tint(s, 2)));
    }

    private void cooler(ItemStack s) {
        int type = idx(s, CoolerSpec.TYPE);
        int body = argb(tint(s, 1));
        int ring = led(s, 2);
        int fins = argb(sh(body, 0.78));
        int fanFrame = th.rgbLights ? argb(0x222028) : argb(0x3C3C40);
        int blade = th.rgbLights ? argb(0x4A3C66) : argb(0x6E6E74);
        switch (type) {
            case 0, 1 -> {   // коробочный и низкий: плоский радиатор с вентилятором сверху
                box(37, 32, 67, 62, body);
                for (int x = 37; x < 67; x += 3) box(x, 32, x + 1, 62, fins);
                fan(52, 47, 11, fanFrame, blade, argb(0x28282C), ring, powered);
            }
            case 4 -> {      // СВО: помпа на процессоре, шланги к радиатору спереди
                box(44, 39, 60, 55, argb(0x1E1E22));
                int c = ring != 0 ? ring : argb(sh(body, 0.9));
                frame(45, 40, 59, 54, c);
                box(49, 44, 55, 50, argb(sh(body, 1.1)));
                box(54, 17, 57, 39, argb(0x141416));
                box(58, 20, 61, 39, argb(0x141416));
                box(54, 17, 149, 19, argb(0x141416));
                box(58, 20, 149, 22, argb(0x141416));
            }
            default -> {     // башня (или две): рёбра и большой вентилятор
                int x0 = type == 3 ? 30 : 34, x1 = type == 3 ? 74 : 70;
                box(x0, 29, x1, 65, body);
                for (int y = 29; y < 65; y += 2) box(x0, y, x1, y + 1, fins);
                fan(52, 47, 16, fanFrame, blade, argb(0x1E1E22), ring, powered);
            }
        }
    }

    private void aioRadiator(ItemStack s) {
        int mm = idx(s, CoolerSpec.RADIATOR);       // 0 — 120, 1 — 240, 2 — 360
        int fans = mm + 1;
        int body = argb(sh(tint(s, 1), 0.7));
        box(149, 18, 176, 18 + fans * 30 + 2, body);
        for (int i = 0; i < fans; i++) caseFan(163, 32 + i * 30, 12);
    }

    private void ramStick(int[] r, ItemStack s) {
        int x0 = r[0] + 1, x1 = r[0] + 6;
        int body = argb(tint(s, 1));
        box(x0, 27, x1, 71, body);
        box(x0, 27, x0 + 1, 71, argb(sh(body, 1.2)));
        box(x0, 69, x1, 71, argb(tint(s, 3)));
        box(x0 + 1, 70, x1 - 1, 71, argb(0xC8AA3C));
        int ledC = led(s, 2);
        if (ledC != 0) {
            box(x0, 27, x1, 31, ledC);
            box(x0, 31, x1, 32, argb(sh(ledC, 1.3)));
        } else {
            for (int k = 0; k < 4; k++) box(x0 + 1, 33 + k * 9, x1 - 1, 39 + k * 9, argb(sh(body, 0.7)));
        }
    }

    private void m2(int[] r, ItemStack s) {
        int pcb = argb(tint(s, 3));
        box(r[0] + 2, r[1] + 1, r[2] - 2, r[3] - 1, pcb);
        if (idx(s, SsdSpec.COOLING) > 0) {
            int c = argb(tint(s, 1));
            box(r[0] + 3, r[1] + 1, r[2] - 3, r[3] - 1, c);
            box(r[0] + 3, r[1] + 3, r[2] - 3, r[1] + 4, argb(sh(c, 1.25)));
        } else {
            for (int k = 0; k < 3; k++) box(r[0] + 5 + k * 9, r[1] + 2, r[0] + 11 + k * 9, r[3] - 2, argb(0x181818));
            box(r[2] - 9, r[1] + 2, r[2] - 5, r[3] - 2, argb(tint(s, 2)));
        }
    }

    private int gpuEnd(ItemStack s) {
        int len = idx(s, GpuSpec.LENGTH);
        return 22 + (len == 0 ? 70 : len == 1 ? 88 : 106);
    }

    private void gpu(ItemStack s) {
        int end = gpuEnd(s);
        int thick = idx(s, GpuSpec.THICKNESS);
        int y1 = 88 + thick * 3;
        int cool = idx(s, GpuSpec.COOLER);
        int body = argb(tint(s, 1));
        int pcb = argb(tint(s, 3));
        // плата карты (виден торец) и кожух
        box(22, 79, end, 82, pcb);
        box(22, 82, end, y1, cool <= GpuSpec.COOLER_PASSIVE ? argb(0x9A9EA4) : body);
        if (cool <= GpuSpec.COOLER_PASSIVE) {
            for (int x = 24; x < end - 2; x += 3) box(x, 82, x + 1, y1, argb(0x6E7278));
        } else {
            box(22, 82, end, 83, argb(sh(body, 1.25)));
            box(22, y1 - 1, end, y1, argb(sh(body, 0.7)));
            if (cool == GpuSpec.COOLER_BLOWER) {
                for (int y = 84; y < y1 - 1; y += 2) box(end - 8, y, end - 1, y + 1, argb(0x141416));
            } else {
                int fans = cool == GpuSpec.COOLER_TRIPLE ? 3 : 2;
                int step = (end - 26) / fans;
                for (int i = 0; i < fans; i++) box(26 + i * step + 2, y1 - 2, 26 + (i + 1) * step - 2, y1 - 1, argb(0x141416));
            }
        }
        int ledC = led(s, 2);
        if (ledC != 0) box(30, 85, Math.min(end - 12, 82), 87, ledC);
        // скоба на задней стенке
        box(CX0 + 1, 78, CX0 + 4, y1 + 1, argb(0xB4B8BE));
    }

    private void psu(int[] r, ItemStack s, boolean desktop) {
        int body = argb(tint(s, 1));
        box(r[0], r[1], r[2], r[3], body);
        box(r[0] + 2, r[1] + 2, r[2] - 2, r[3] - 2, argb(sh(body, 1.1)));
        int watts = s.getItem() instanceof PsuItem ? PsuItem.data(s).wattage() : 0;
        if (desktop) {
            box(r[0] + 2, r[1] + 4, r[0] + 40, r[3] - 4, argb(0xDCDCD2));
            Component w = Component.translatable("gui.minepc.case.watts", watts);
            g.drawString(font, w, r[0] + 2 + (38 - font.width(w)) / 2, r[1] + 8, argb(0x28282A), false);
            for (int i = 0; i < 5; i++) box(r[0] + 42, r[1] + 5 + i * 3, r[2] - 2, r[1] + 6 + i * 3, argb(0x141416));
            int ledC = idx(s, PsuSpec.SIDE) == PsuSpec.SIDE_LED ? led(s, 2) : 0;
            if (ledC != 0) box(r[0] + 1, r[3] - 2, r[2] - 1, r[3] - 1, ledC);
        } else {
            for (int i = 0; i < 7; i++) box(r[0] + 3, r[1] + 4 + i * 3, r[2] - 3, r[1] + 5 + i * 3, argb(0x26282C));
            int reserve = be == null ? -1 : be.reserve(be.collectInput().powerDraw());
            int lamp = powered ? (reserve == 1 ? argb(0x3CDC6E) : argb(0xF0BE28)) : argb(0x3C3C3C);
            box(r[0] + 6, r[3] - 5, r[2] - 6, r[3] - 3, lamp);
        }
    }

    private void hdd(int[] r, ItemStack s, boolean server) {
        int body = argb(tint(s, 1));
        box(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, body);
        box(r[0] + 1, r[1] + 1, r[2] - 1, r[1] + 2, argb(sh(body, 1.2)));
        box(r[0] + 3, r[1] + 3, r[0] + 22, r[3] - 3, argb(tint(s, 2)));
        diskLed(r[2] - 4, r[1] + 3, r[3] - 3, s);
    }

    private void diskLed(int x, int y0, int y1, ItemStack s) {
        var health = DiskHealth.of(s);
        int c = !powered ? argb(0x3C3C3C) : health.failed() ? argb(0xFF4A3A) : health.warning() ? argb(0xFFB02A)
                : ((int) (time / 3 + x) % 7 == 0 ? argb(0x5CF06A) : argb(0x2E8A3A));
        box(x, y0, x + 2, y1, argb(0x26282C));
        box(x, y0 + 1, x + 2, y0 + 3, c);
    }

    private void sataSsd(int[] r, ItemStack s) {
        int body = argb(tint(s, 1));
        box(r[0], r[1], r[2], r[3], argb(sh(th.shroud, 0.7)));
        box(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, body);
        box(r[0] + 3, r[1] + 3, r[0] + 12, r[3] - 3, argb(tint(s, 2)));
    }

    // ======================= Серверное шасси: вид сверху =======================

    private void drawServer() {
        MotherboardData board = be == null ? null : be.motherboard();
        box(CX0, CY0, CX1, CY1, argb(th.wall));
        frame(CX0, CY0, CX1, CY1, argb(th.frame));
        // корзина дисков спереди
        box(CX0 + 1, CY0 + 1, 42, CY1 - 1, argb(sh(th.wall, 0.75)));
        // стена вентиляторов
        box(42, CY0 + 1, 60, CY1 - 1, argb(sh(th.wall, 0.85)));
        for (int i = 0; i < 4; i++) fan(51, 33 + i * 31, 8, argb(0x282C30), argb(0x5A6068), argb(0x1E2024), 0, powered);
        // стрелки потока воздуха
        int arrow = argb(sh(th.wall, 1.6));
        for (int x = 66; x < 156; x += 12) {
            for (int y : new int[]{17, 141}) {
                px(x, y, arrow);
                px(x + 1, y, arrow);
            }
        }
        if (board == null) {
            emptyBoard(SBX0, SBY0, SBX1, SBY1, "gui.minepc.case.server_board_first");
        } else {
            ItemStack mb = stack(SLOT_MOTHERBOARD);
            int pcb = argb(tint(mb, 3));
            box(SBX0, SBY0, SBX1, SBY1, pcb);
            frame(SBX0, SBY0, SBX1, SBY1, argb(sh(pcb, 0.7)));
            int hole = argb(0xBEBEAA);
            for (int[] p : new int[][]{{65, 21}, {154, 21}, {65, 138}, {154, 138}, {108, 80}}) box(p[0], p[1], p[0] + 2, p[1] + 2, hole);
            // BMC и его лампа
            box(111, 50, 124, 62, argb(0x1E1E22));
            frame(111, 50, 124, 62, argb(0x46464C));
            box(126, 52, 128, 54, powered || (be != null && be.psuCount() > 0) ? argb(0x5CF06A) : argb(0x3C3C3C));
            // райзер PCIe
            box(111, 74, 147, 77, argb(0x1C1C1E));
            // 24-pin
            box(151, 108, 155, 128, argb(0x141416));
            for (int slot : new int[]{SLOT_CPU, SLOT_CPU2}) {
                if (!shown(slot) && stack(slot).isEmpty()) continue;
                int[] r = hit(slot);
                box(r[0], r[1], r[2], r[3], argb(0xAAACB0));
                box(r[0] + 2, r[1] + 2, r[2] - 2, r[3] - 2, argb(0x3C3A34));
                for (int y = r[1] + 4; y < r[3] - 3; y += 2) for (int x = r[0] + 4; x < r[2] - 3; x += 2) px(x, y, argb(0xC8AA5A));
            }
            for (int k = 0; k < MAX_RAM_SLOTS; k++) {
                int slot = ramSlot(k);
                if (!shown(slot)) continue;
                int[] r = hit(slot);
                box(r[0], r[1] + 1, r[2], r[3] - 1, argb(0x1C1C1E));
                box(r[0], r[1] + 1, r[0] + 2, r[3] - 1, argb(0xE6E6E6));
                box(r[2] - 2, r[1] + 1, r[2], r[3] - 1, argb(0xE6E6E6));
            }
            for (int i = 0; i < M2_SLOTS; i++) {
                int slot = SLOT_M2 + i;
                if (!shown(slot)) continue;
                int[] r = hit(slot);
                box(r[0], r[1] + 1, r[0] + 3, r[3] - 1, argb(0x1C1C1E));
            }
            // детали
            for (int slot : new int[]{SLOT_CPU, SLOT_CPU2}) if (!stack(slot).isEmpty()) passiveHeatsink(hit(slot), stack(slot));
            for (int k = 0; k < MAX_RAM_SLOTS; k++) {
                int slot = ramSlot(k);
                if (stack(slot).isEmpty() || hit(slot) == null) continue;
                int[] r = hit(slot);
                int body = argb(tint(stack(slot), 1));
                box(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, body);
                box(r[0] + 1, r[1] + 1, r[2] - 1, r[1] + 2, argb(sh(body, 1.3)));
            }
            for (int i = 0; i < M2_SLOTS; i++) {
                int slot = SLOT_M2 + i;
                if (!stack(slot).isEmpty()) m2(hit(slot), stack(slot));
            }
            if (!stack(SLOT_GPU).isEmpty()) {
                int[] r = hit(SLOT_GPU);
                ItemStack s = stack(SLOT_GPU);
                box(r[0], r[1] + 2, r[2], r[3], argb(tint(s, 1)));
                box(r[0], r[1] + 2, r[2], r[1] + 3, argb(sh(tint(s, 1), 1.25)));
                box(r[0] + 2, r[3] - 3, r[2] - 2, r[3] - 2, argb(tint(s, 3)));
            }
            if (!stack(SLOT_COOLER).isEmpty()) {
                int[] r = hit(SLOT_COOLER);
                ItemStack s = stack(SLOT_COOLER);
                box(r[0], r[1], r[2], r[3], argb(tint(s, 1)));
                fan((r[0] + r[2]) / 2, (r[1] + r[3]) / 2, 14, argb(0x2A2A30), argb(0x6A6A72), argb(0x1E1E22), led(s, 2), powered);
            }
        }
        // блоки питания и SFP+ сзади
        for (int slot : new int[]{SLOT_PSU, SLOT_PSU2}) {
            int[] r = hit(slot);
            box(r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, argb(sh(th.wall, 0.7)));
            if (!stack(slot).isEmpty()) psu(r, stack(slot), false);
        }
        for (int i = 0; i < 2; i++) {
            int y = 112 + i * 14;
            box(160, y, 174, y + 11, argb(0xAAAEB4));
            box(162, y + 2, 172, y + 8, argb(0x1E1E20));
            boolean link = (menu.netLink() & 1) != 0 && i == 0;
            box(164, y + 9, 167, y + 10, link && powered ? argb(0x5CF06A) : argb(0x3C3C3C));
        }
        // отсеки дисков
        for (int b = 0; b < menu.bays(); b++) {
            int slot = baySlot(b);
            int[] r = hit(slot);
            if (r == null) continue;
            box(r[0], r[1], r[2], r[3], argb(0x16181A));
            if (stack(slot).isEmpty()) continue;
            ItemStack s = stack(slot);
            box(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, argb(0x969BA0));
            box(r[0] + 3, r[1] + 2, r[2] - 7, r[3] - 2, argb(sh(tint(s, 1), 1.0)));
            for (int y = r[1] + 4; y < r[3] - 3; y += 3) box(r[0] + 5, y, r[2] - 9, y + 1, argb(0x6E7278));
            diskLed(r[2] - 5, r[1] + 2, r[3] - 2, s);
        }
    }

    private void passiveHeatsink(int[] r, ItemStack s) {
        box(r[0] - 2, r[1] - 1, r[2] + 2, r[3] + 1, argb(0x9A9EA4));
        for (int y = r[1]; y < r[3]; y += 2) box(r[0] - 2, y, r[2] + 2, y + 1, argb(0x70747A));
        int cx = (r[0] + r[2]) / 2, cy = (r[1] + r[3]) / 2;
        box(cx - 4, cy - 3, cx + 4, cy + 3, argb(tint(s, 1)));
    }

    // ======================= Пустые места, подсветка, лишние детали =======================

    private boolean dimPlace(int slot) {
        // Блок питания и диски — места корпуса: до платы видны приглушённо
        return be != null && be.motherboard() == null
                && (slot == SLOT_PSU || slot == SLOT_PSU2 || isDriveBay(slot));
    }

    private void drawSlotStates() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (slot == SLOT_MOTHERBOARD || isUsb(slot) || !shown(slot)) continue;
            int[] r = hit(slot);
            if (r == null) continue;
            boolean empty = stack(slot).isEmpty();
            if (empty) {
                boolean can = !carried.isEmpty() && be != null && be.canPlace(slot, carried);
                int c = dimPlace(slot) ? argb(CaseTheme.lerp(argb(th.wall), argb(th.frame), 0.9)) : argb(th.accent);
                if (can) box(r[0], r[1], r[2], r[3], alpha(th.accent, 0x40));
                dashed(r[0], r[1], r[2], r[3], c);
                if (slot == SLOT_PSU && !menu.serverLayout()) {
                    Component t = Component.translatable("gui.minepc.case.psu");
                    g.drawString(font, t, (r[0] + r[2] - font.width(t)) / 2, (r[1] + r[3]) / 2 - 4, c, false);
                }
            }
            if (be != null && be.slotExtra(slot)) {
                box(r[0], r[1], r[2], r[3], 0x50FF2020);
                frame(r[0], r[1], r[2], r[3], argb(0xFF4040));
            }
        }
        if (hovered >= 0 && hovered < SLOT_COUNT) {
            int[] r = hit(hovered);
            if (r != null) {
                box(r[0], r[1], r[2], r[3], alpha(0xFFFFFF, 0x30));
                frame(r[0], r[1], r[2], r[3], argb(th.accent));
            }
        }
    }
}
