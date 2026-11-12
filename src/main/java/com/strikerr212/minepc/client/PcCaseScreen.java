package com.strikerr212.minepc.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.MineConfig;
import com.strikerr212.minepc.block.CaseComputer;
import com.strikerr212.minepc.block.CaseStyle;
import com.strikerr212.minepc.block.PcCaseBlockEntity;
import com.strikerr212.minepc.computer.Computer;
import com.strikerr212.minepc.hardware.GpuData;
import com.strikerr212.minepc.hardware.HddData;
import com.strikerr212.minepc.hardware.MotherboardData;
import com.strikerr212.minepc.hardware.Perf;
import com.strikerr212.minepc.hardware.SsdData;
import com.strikerr212.minepc.hardware.SystemModel;
import com.strikerr212.minepc.hardware.Units;
import com.strikerr212.minepc.item.CoolerItem;
import com.strikerr212.minepc.item.GpuItem;
import com.strikerr212.minepc.item.HardwareItem;
import com.strikerr212.minepc.item.HddItem;
import com.strikerr212.minepc.item.SsdItem;
import com.strikerr212.minepc.menu.PcCaseMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Окно системного блока (248×228).
 *
 * ┌ Системный блок ───────────────────────── 1320 очков ┐
 * │ ┌ корпус изнутри ───────────────┐ ┌ экран ──────────┐ │
 * │ │ ЦП  кулер   Память ×3   │ БП  │ │ ⏻ Работает      │ │
 * │ │             (4-я ниже)  │ HDD │ │ Σ ⚡ ° ■        │ │
 * │ │ Видео       M.2 ×2      │ HDD │ │ строки состояния│ │
 * │ │ ═══ PCIe ═══            │ SSD │ │                 │ │
 * │ │ Плата                   │ SSD │ │                 │ │
 * │ └─────────────────────────┘     ┘ └─────────────────┘ │
 * │ Инвентарь                          ┌ USB  □ □ ──────┐ │
 * │ □□□□□□□□□                          │ Нагрузка ▁▃▅▇  │ │
 * └─────────────────────────────────────────────────────┘
 *
 * Оформление зависит от корпуса (см. {@link CaseTheme}), раскладка у всех одна.
 */
public class PcCaseScreen extends AbstractContainerScreen<PcCaseMenu> {

    private static final int W = 248, H = 228;
    private static final int CX0 = 7, CY0 = 17, CX1 = 153, CY1 = 129;          // корпус изнутри
    private static final int BX0 = 11, BY0 = 19, BX1 = 128, BY1 = 125;         // плата
    private static final int SX0 = 158, SY0 = 17, SX1 = 241, SY1 = 129;        // экран состояния
    private static final int PX0 = 176, PY0 = 137, PX1 = 241, PY1 = 221;       // передняя панель
    private static final int BTN_X = SX0 + 4, BTN_Y = SY0 + 4;                 // кнопка питания 16×15
    private static final int TAB_Y = SY0 + 23, ROWS_Y = TAB_Y + 15;
    private static final int TEXT_X = SX0 + 15, TEXT_W = SX1 - SX0 - 18;
    private static final int GX0 = PX0 + 4, GY0 = PY0 + 47, GX1 = PX1 - 4, GY1 = PY1 - 4;   // график нагрузки
    private static final int TABS = 5, TAB_STEP = 16, TAB_W = 15;
    private static final String[] TAB_KEYS = {"gui.minepc.tab.summary", "gui.minepc.tab.power", "gui.minepc.tab.heat",
            "gui.minepc.tab.drives", "gui.minepc.tab.net"};
    private static final String[] TAB_GLYPHS = {"sum", "power", "heat", "disk", "net"};

    /** Для скриншотов в CI: принудительная тема и вкладка. */
    public static CaseStyle ciTheme;
    public static int ciTab = -1;

    private static final String[] SLOT_HINTS = {
            "gui.minepc.slot.motherboard", "gui.minepc.slot.cpu", "gui.minepc.slot.cooler",
            "gui.minepc.slot.ram", "gui.minepc.slot.ram", "gui.minepc.slot.ram", "gui.minepc.slot.ram",
            "gui.minepc.slot.gpu", "gui.minepc.slot.psu", "gui.minepc.slot.hdd", "gui.minepc.slot.hdd",
            "gui.minepc.slot.sata", "gui.minepc.slot.sata", "gui.minepc.slot.m2", "gui.minepc.slot.m2",
            "gui.minepc.slot.usb", "gui.minepc.slot.usb", "gui.minepc.slot.cpu2", "gui.minepc.slot.psu2",
            "gui.minepc.slot.ram", "gui.minepc.slot.ram", "gui.minepc.slot.ram", "gui.minepc.slot.ram", "gui.minepc.slot.ram", "gui.minepc.slot.ram",
            "gui.minepc.slot.ram", "gui.minepc.slot.ram", "gui.minepc.slot.ram", "gui.minepc.slot.ram", "gui.minepc.slot.ram", "gui.minepc.slot.ram",
            "gui.minepc.slot.bay", "gui.minepc.slot.bay", "gui.minepc.slot.bay", "gui.minepc.slot.bay"};
    private static final String[] GHOSTS = {"motherboard", "cpu", "cooler", "ram", "ram", "ram", "ram", "gpu", "psu",
            "hdd", "hdd", "sata", "sata", "m2", "m2", "usb", "usb", "cpu", "psu",
            "ram", "ram", "ram", "ram", "ram", "ram", "ram", "ram", "ram", "ram", "ram", "ram", "hdd", "hdd", "hdd", "hdd"};

    /** Дорожки на плате: всегда один и тот же рисунок. */
    private static final List<int[]> TRACES = new ArrayList<>();
    static {
        Random rnd = new Random(3);
        for (int k = 0; k < 40; k++) {
            int x = BX0 + 2 + rnd.nextInt(BX1 - BX0 - 3), y = BY0 + 2 + rnd.nextInt(BY1 - BY0 - 3);
            int len = 3 + rnd.nextInt(8);
            if (rnd.nextBoolean()) TRACES.add(new int[]{x, y, Math.min(BX1 - 2, x + len), y});
            else TRACES.add(new int[]{x, y, x, Math.min(BY1 - 2, y + len)});
        }
    }

    /** Строка экрана состояния: значок, короткий текст, по желанию полоска и подсказка. */
    private record Row(String glyph, Component text, int color, float bar, int barColor, List<Component> tip) {
        static Row of(String glyph, Component text, int color) {
            return new Row(glyph, text, color, -1, 0, null);
        }

        Row bar(float fraction, int color) {
            return new Row(glyph, text, this.color, Math.max(0, Math.min(1, fraction)), color, tip);
        }

        Row tip(Component... lines) {
            return new Row(glyph, text, color, bar, barColor, List.of(lines));
        }
    }

    private CaseTheme th = CaseTheme.CLASSIC;
    private int tab;
    private List<Row> rows = List.of();
    private Component score;
    private int scoreColor;
    private int cores;
    private List<Component> report = List.of();
    private int age;
    private final int[] load = new int[(GX1 - GX0 + 1) / 3];
    private int loadCount;

    public PcCaseScreen(PcCaseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        refresh();
        // Сервер в стойке: своего монитора у него нет — экран открывается отсюда (или с консоли стойки)
        if (menu.isModule() && menu.blockEntity() != null) {
            var host = menu.blockEntity().hostRef();
            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("gui.minepc.open_terminal"), b -> {
                if (minecraft != null && minecraft.player != null) {
                    minecraft.player.closeContainer();
                    minecraft.setScreen(new TerminalScreen(host));
                }
            }).bounds(leftPos + imageWidth - 80, Math.max(2, topPos - 20), 80, 16).build());
        }
    }

    /** Для CI: сразу применить ciTheme/ciTab. */
    public void ciRefresh() {
        refresh();
    }

    private CaseTheme theme() {
        if (ciTheme != null) return CaseTheme.of(ciTheme);
        PcCaseBlockEntity be = menu.blockEntity();
        return CaseTheme.of(be != null ? be.style() : CaseStyle.CLASSIC);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (++age % 10 == 0 || age == 2) refresh();   // age 2 — данные меню уже пришли с сервера
        if (age % 20 == 0) {
            System.arraycopy(load, 1, load, 0, load.length - 1);
            boolean on = menu.computerState() == 1;
            load[load.length - 1] = on ? menu.loadPercent() : 0;
            loadCount = on ? Math.min(load.length, loadCount + 1) : 0;
        }
    }

    // ======================= Что показывает экран состояния =======================

    private ItemStack stack(int slot) {
        return menu.slots.get(slot).getItem();
    }

    private static net.minecraft.network.chat.MutableComponent tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /** Число с десятичной запятой по-русски: 3,9 вместо 3.9. */
    private static String dec(double v) {
        String s = v >= 10 ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
        boolean ru = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected().startsWith("ru");
        return ru ? s.replace('.', ',') : s;
    }

    /** Коротко: «37 млн», «850 тыс». */
    private static Component shortOps(long ops) {
        if (ops >= 1_000_000) return tr("unit.minepc.short.m", dec(ops / 1e6));
        if (ops >= 1000) return tr("unit.minepc.short.k", Math.round(ops / 1000.0));
        return Component.literal(String.valueOf(ops));
    }

    private static Component bytes(long b) {
        if (b >= 1024 * 1024) return tr("unit.minepc.mb", dec(b / 1048576.0));
        if (b >= 1024) return tr("unit.minepc.kb", dec(b / 1024.0));
        return tr("unit.minepc.bytes", b);
    }

    private void refresh() {
        th = theme();
        if (ciTab >= 0) tab = ciTab;
        PcCaseBlockEntity be = menu.blockEntity();
        if (be == null || minecraft == null) return;
        MineConfig.clientSingleplayer = minecraft.hasSingleplayerServer();
        List<Row> out = new ArrayList<>();
        boolean any = false;
        for (int i = 0; i < PcCaseBlockEntity.SLOT_COUNT; i++) {
            if (stack(i).getItem() instanceof HardwareItem) any = true;
        }
        score = null;
        report = be.statusLines(false);
        if (!any) {
            out.add(Row.of("sum", tr("gui.minepc.row.empty"), th.dim));
            rows = out;
            return;
        }
        SystemModel.Input in = be.collectInput();
        SystemModel.Report r = SystemModel.evaluate(in);
        Computer.Limits lim = CaseComputer.limitsFor(in, r, MineConfig.speedFor(null));
        cores = in.cpu() != null ? lim.cores() : 0;
        if (r.bootable()) {
            score = tr("gui.minepc.score", r.score());
            scoreColor = th.scoreColor;
        } else {
            score = tr("gui.minepc.no_boot_short");
            scoreColor = th.deco == CaseTheme.Deco.NONE ? CaseTheme.rgb(170, 30, 30) : th.bad;
        }
        switch (tab) {
            case 1 -> powerRows(out, in);
            case 2 -> heatRows(out, in, r);
            case 3 -> driveRows(out, in, r);
            case 4 -> netRows(out, be);
            default -> summaryRows(out, in, r, lim);
        }
        rows = out;
    }

    private void summaryRows(List<Row> out, SystemModel.Input in, SystemModel.Report r, Computer.Limits lim) {
        if (!r.bootable()) {
            for (SystemModel.Note n : r.notes()) {
                if (!n.issue().critical) continue;
                out.add(Row.of("warn", tr("gui.minepc.why." + n.issue().name().toLowerCase(Locale.ROOT)), th.bad));
                if (out.size() >= 4) break;
            }
            out.add(powerRow(in));
            return;
        }
        long perCore = (long) lim.opsPerTick() * Computer.TICKS_PER_SECOND;
        boolean slowCpu = r.cpuThrottle() < 1 || r.cpuMemory() < 0.85;
        int boostKhz = (int) Math.round(perCore / 1000.0 * (in.cpu() == null ? 1 : 1 + in.cpu().boostPct() / 100.0));
        out.add(Row.of("cpu", tr("gui.minepc.row.cpu", Units.gameClock((int) (perCore / 1000)), lim.cores()), slowCpu ? th.warn : th.fg)
                .tip(tr("message.minepc.status.game", Units.gameClock((int) (perCore / 1000)), Units.gameClock(boostKhz),
                        lim.cores(), Units.bytes(lim.memoryBytes()))));
        out.add(Row.of("ram", bytes(lim.memoryBytes()), th.fg));
        GpuData gpu = in.graphics();
        int[] screen = Perf.screenSize(gpu == null ? 0 : gpu.tier());
        out.add(Row.of("gpu", tr("gui.minepc.row.screen", screen[0], screen[1]), th.fg));
        int best = 0;
        for (SsdData d : in.ssds()) best = Math.max(best, d.speedMbs());
        for (HddData d : in.hdds()) best = Math.max(best, d.speedMbs());
        long speed = Math.round(Perf.gameDiskBytesPerSecond(best) * MineConfig.disk());
        out.add(Row.of("disk", tr("gui.minepc.row.disk", bytes(speed)), th.fg));
        out.add(powerRow(in));
        out.add(cpuHeatRow());
    }

    private Row powerRow(SystemModel.Input in) {
        int draw = in.powerDraw();
        if (in.psu() == null) return Row.of("power", tr("gui.minepc.why.no_psu"), th.bad);
        int max = in.psu().wattage();
        float f = max > 0 ? (float) draw / max : 2;
        int color = f > 1 ? th.bad : f > 0.85f ? th.warn : th.ok;
        return Row.of("power", tr("gui.minepc.row.watts", draw, max), f > 1 ? th.bad : th.fg).bar(f, color);
    }

    /** Условная температура по тому, сколько тепла чип выделяет и сколько отводит охлаждение. */
    private Row heatRow(String glyph, int heatW, int coolingW) {
        double f = (double) heatW / Math.max(1, coolingW);
        int temp = (int) Math.round(f <= 1 ? 30 + 40 * f : Math.min(99, 70 + 30 * (f - 1)));
        String state = f <= 0.8 ? "ok" : f <= 1 ? "warm" : "hot";
        int color = f <= 0.8 ? th.ok : f <= 1 ? th.warn : th.bad;
        return Row.of(glyph, tr("gui.minepc.row.temp", temp, tr("gui.minepc.heat." + state)), f > 1 ? th.bad : th.fg)
                .bar((float) Math.min(1, f), color);
    }

    /** Тепло процессоров (у сервера — обоих). */
    private int cpuHeat() {
        int w = 0;
        for (int slot : new int[]{PcCaseBlockEntity.SLOT_CPU, PcCaseBlockEntity.SLOT_CPU2}) {
            if (stack(slot).getItem() instanceof HardwareItem h) w += h.getHeat(stack(slot));
        }
        return w;
    }

    /** Отвод тепла: кулер, у серверного шасси — стена вентиляторов (как считает сам корпус). */
    private int cpuCooling() {
        PcCaseBlockEntity be = menu.blockEntity();
        if (be != null) {
            var c = be.collectInput().cooler();
            if (c != null) return c.coolingW();
        }
        ItemStack cooler = stack(PcCaseBlockEntity.SLOT_COOLER);
        return cooler.getItem() instanceof CoolerItem ? CoolerItem.data(cooler).coolingW() : Perf.PASSIVE_COOLING;
    }

    private Row cpuHeatRow() {
        if (!(stack(PcCaseBlockEntity.SLOT_CPU).getItem() instanceof HardwareItem)) return Row.of("heat", tr("gui.minepc.why.no_cpu"), th.dim);
        return heatRow("heat", cpuHeat(), cpuCooling());
    }

    private int power(int first, int count) {
        int w = 0;
        for (int i = first; i < first + count; i++) {
            if (stack(i).getItem() instanceof HardwareItem h) w += h.getPowerDraw(stack(i));
        }
        return w;
    }

    private void powerRows(List<Row> out, SystemModel.Input in) {
        out.add(Row.of("cpu", tr("gui.minepc.row.part_w", tr("gui.minepc.part.cpu"),
                power(PcCaseBlockEntity.SLOT_CPU, 2) + power(PcCaseBlockEntity.SLOT_CPU2, 1)), th.fg));
        out.add(Row.of("gpu", tr("gui.minepc.row.part_w", tr("gui.minepc.part.gpu"), power(PcCaseBlockEntity.SLOT_GPU, 1)), th.fg));
        out.add(Row.of("ram", tr("gui.minepc.row.part_w", tr("gui.minepc.part.ram"), power(PcCaseBlockEntity.SLOT_RAM, PcCaseBlockEntity.RAM_SLOTS)
                + power(PcCaseBlockEntity.SLOT_RAM_EXTRA, PcCaseBlockEntity.RAM_EXTRA_SLOTS)), th.fg));
        int drives = power(PcCaseBlockEntity.SLOT_HDD, PcCaseBlockEntity.HDD_SLOTS) + power(PcCaseBlockEntity.SLOT_SATA_SSD, PcCaseBlockEntity.SATA_SSD_SLOTS)
                + power(PcCaseBlockEntity.SLOT_BAY_EXTRA, PcCaseBlockEntity.BAY_EXTRA_SLOTS)
                + power(PcCaseBlockEntity.SLOT_M2, PcCaseBlockEntity.M2_SLOTS);
        out.add(Row.of("disk", tr("gui.minepc.row.part_w", tr("gui.minepc.part.drives"), drives), th.fg));
        out.add(Row.of("board", tr("gui.minepc.row.part_w", tr("gui.minepc.part.other"), power(PcCaseBlockEntity.SLOT_MOTHERBOARD, 1)), th.fg));
        out.add(powerRow(in));
    }

    private void heatRows(List<Row> out, SystemModel.Input in, SystemModel.Report r) {
        if (stack(PcCaseBlockEntity.SLOT_CPU).getItem() instanceof HardwareItem) {
            out.add(heatRow("cpu", cpuHeat(), cpuCooling()));
            out.add(Row.of("heat", tr("gui.minepc.row.watts", cpuHeat(), cpuCooling()), th.dim));
            if (r.cpuThrottle() < 1) out.add(Row.of("warn", tr("gui.minepc.row.speed", Math.round(r.cpuThrottle() * 100)), th.warn));
        } else {
            out.add(Row.of("cpu", tr("gui.minepc.why.no_cpu"), th.dim));
        }
        ItemStack gpu = stack(PcCaseBlockEntity.SLOT_GPU);
        if (gpu.getItem() instanceof GpuItem) {
            GpuData d = GpuItem.data(gpu);
            int cooling = d.hasCooler() ? d.coolingW() : Perf.PASSIVE_COOLING;
            out.add(heatRow("gpu", d.tdp(), cooling));
            out.add(Row.of("heat", tr("gui.minepc.row.watts", d.tdp(), cooling), th.dim));
            if (r.gpuThrottle() < 1) out.add(Row.of("warn", tr("gui.minepc.row.speed", Math.round(r.gpuThrottle() * 100)), th.warn));
        } else if (in.integrated() != null) {
            out.add(Row.of("gpu", tr("gui.minepc.row.igpu"), th.dim));       // греется вместе с процессором
        } else {
            out.add(Row.of("gpu", tr("gui.minepc.row.no_gpu"), th.dim));
        }
    }

    private void driveRows(List<Row> out, SystemModel.Input in, SystemModel.Report r) {
        List<Integer> order = new ArrayList<>(List.of(PcCaseBlockEntity.SLOT_M2, PcCaseBlockEntity.SLOT_M2 + 1));
        for (int i = 0; i < PcCaseBlockEntity.MAX_BAYS; i++) order.add(PcCaseBlockEntity.baySlot(i));
        for (int slot : order) {
            ItemStack s = stack(slot);
            String kind;
            int capacity, speed;
            if (s.getItem() instanceof SsdItem) {
                SsdData d = SsdItem.data(s);
                kind = d.isNvme() ? "NVMe" : "SATA";
                capacity = d.capacityMb();
                speed = d.speedMbs();
            } else if (s.getItem() instanceof HddItem) {
                HddData d = HddItem.data(s);
                kind = "HDD";
                capacity = d.capacityMb();
                speed = d.speedMbs();
            } else {
                continue;
            }
            long gameSpeed = Math.round(Perf.gameDiskBytesPerSecond(speed) * MineConfig.disk());
            var health = com.strikerr212.minepc.block.DiskHealth.of(s);
            int color = health.failed() ? th.bad : health.warning() ? th.warn : th.fg;
            Component text = health.failed() ? tr("gui.minepc.row.drive_failed", kind)
                    : tr("gui.minepc.row.drive", kind, bytes(Perf.gameStorageBytes(capacity)));
            out.add(Row.of("disk", text, color)
                    .tip(s.getHoverName(), tr("gui.minepc.row.disk", bytes(gameSpeed)).withStyle(ChatFormatting.GRAY),
                            tr("tooltip.minepc.disk_wear", health.percent()).withStyle(health.warning() ? ChatFormatting.GOLD : ChatFormatting.GRAY)));
        }
        if (out.isEmpty()) {
            out.add(Row.of("disk", tr("gui.minepc.row.no_drives"), th.bad));
        } else if (r.bootSeconds() > 0) {
            double seconds = MineConfig.bootTime() ? Perf.gameBootTicks(r.bootSeconds()) / 20.0 : 0;
            out.add(Row.of("clock", tr("gui.minepc.row.boot", dec(seconds)), th.fg));
        }
    }

    /** Скорость порта по плате: 100 Мбит/с … 10 Гбит/с. */
    private static Component bitsText(long bits) {
        if (bits >= 1_000_000_000L) {
            double g = bits / 1e9;
            return tr("gui.minepc.net.gbit", g == Math.rint(g) ? String.valueOf((long) g) : dec(g));
        }
        return tr("gui.minepc.net.mbit", Math.round(bits / 1e6));
    }

    private void netRows(List<Row> out, PcCaseBlockEntity be) {
        String addr = be.address();
        out.add(Row.of("net", Component.literal(addr.length() > 8 ? addr.substring(0, 8) : addr), th.fg)
                .tip(tr("gui.minepc.net.address"), Component.literal(addr).withStyle(ChatFormatting.GRAY)));
        String name = be.label();
        out.add(name.isEmpty() ? Row.of("cpu", tr("gui.minepc.net.noname"), th.dim)
                : Row.of("cpu", Component.literal(name), th.fg));
        com.strikerr212.minepc.hardware.MotherboardData board = be.motherboard();
        if (board != null && board.server()) {
            // BMC: свой адрес, работает от дежурного питания
            String bmc = be.bmc().address();
            out.add(Row.of("cpu", tr("gui.minepc.net.bmc", bmc.substring(0, 8)), be.psuCount() > 0 ? th.fg : th.dim)
                    .tip(tr("gui.minepc.net.bmc_tip"), Component.literal(bmc).withStyle(ChatFormatting.GRAY)));
        }
        if (board == null) {
            out.add(Row.of("warn", tr("gui.minepc.why.no_board"), th.bad));
        } else if (menu.netLink() == 0) {
            out.add(Row.of("plug", tr("gui.minepc.net.unplugged"), th.dim)
                    .tip(tr("gui.minepc.net.unplugged_tip")));
        } else {
            if ((menu.netLink() & 1) != 0) {
                long bits = com.strikerr212.minepc.block.PcCaseBlock.portBits(board);
                out.add(Row.of("plug", bitsText(bits), th.fg)
                        .tip(tr("gui.minepc.net.speed_tip", bytes(bits / 800))));
            }
            if ((menu.netLink() & 2) != 0) {
                long bits = menu.netWifiMbit() * 1_000_000L;
                int signal = menu.netLink() >> 2;
                out.add(Row.of("wifi", tr("gui.minepc.net.wifi", bitsText(bits), signal), signal >= 35 ? th.fg : th.warn)
                        .tip(tr("gui.minepc.net.wifi_tip"), tr("gui.minepc.net.speed_tip", bytes(bits / 800))));
            }
            out.add(Row.of("sum", tr("gui.minepc.net.peers", menu.netPeers()), menu.netPeers() > 0 ? th.fg : th.dim));
        }
        out.add(Row.of("traffic", tr("gui.minepc.net.traffic", menu.netKbReceived(), menu.netKbSent()), th.fg)
                .tip(tr("gui.minepc.net.traffic_tip")));
    }

    /** Слот, куда сейчас ничего нельзя поставить (на плате нет такого разъёма). */
    private boolean disabled(int slot) {
        PcCaseBlockEntity be = menu.blockEntity();
        if (be == null) return false;
        MotherboardData board = be.motherboard();
        if (slot == PcCaseBlockEntity.SLOT_MOTHERBOARD || slot == PcCaseBlockEntity.SLOT_PSU || slot == PcCaseBlockEntity.SLOT_PSU2
                || PcCaseBlockEntity.isUsb(slot)) {
            return false;
        }
        if (slot == PcCaseBlockEntity.SLOT_COOLER && be.isModule()) return true;   // в 1U кулер не влезет
        if (board == null) return true;
        if (slot == PcCaseBlockEntity.SLOT_CPU2) return board.cpuSockets() < 2;
        int ram = PcCaseBlockEntity.ramIndex(slot);
        if (ram >= 0) return ram >= board.ramSlots();
        if (slot >= PcCaseBlockEntity.SLOT_M2 && slot < PcCaseBlockEntity.SLOT_M2 + PcCaseBlockEntity.M2_SLOTS) {
            return slot - PcCaseBlockEntity.SLOT_M2 >= board.m2Slots();
        }
        return false;
    }

    // ======================= Рисование: примитивы (координаты включительно) =======================

    private static void rect(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0, y0, x1 + 1, y1 + 1, c);
    }

    private static void px(GuiGraphics g, int x, int y, int c) {
        g.fill(x, y, x + 1, y + 1, c);
    }

    private static void bevel(GuiGraphics g, int x0, int y0, int x1, int y1, int fill, int tl, int br) {
        rect(g, x0, y0, x1, y1, fill);
        rect(g, x0, y0, x1, y0, tl);
        rect(g, x0, y0, x0, y1, tl);
        rect(g, x0, y1, x1, y1, br);
        rect(g, x1, y0, x1, y1, br);
    }

    private static void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        rect(g, x0, y0, x1, y0, c);
        rect(g, x0, y1, x1, y1, c);
        rect(g, x0, y0, x0, y1, c);
        rect(g, x1, y0, x1, y1, c);
    }

    /** Значки 8×8 для строк и вкладок. */
    private static final java.util.Map<String, String[]> GLYPHS = java.util.Map.of(
            "cpu", new String[]{"..#.#...", ".######.", "##....##", ".#.#..#.", "##..#.##", ".#....#.", ".######.", "..#.#..."},
            "ram", new String[]{"........", "........", "########", "#......#", "#......#", "########", "..#.#.#.", "........"},
            "gpu", new String[]{"........", "########", "#......#", "#.###..#", "#..###.#", "#......#", "########", "........"},
            "disk", new String[]{".######.", ".#....#.", ".#....#.", ".#....#.", ".#.##.#.", ".#.##.#.", ".#....#.", ".######."},
            "power", new String[]{"....#...", "...#....", "...#....", "..####..", "....#...", "....#...", "...#....", "...#...."},
            "heat", new String[]{"...#....", "...#.#..", "...#....", "...#.#..", "...##...", "..###...", "..###...", "...#...."},
            "sum", new String[]{"#######.", ".#......", "..#.....", "...#....", "..#.....", ".#......", "#.......", "#######."},
            "clock", new String[]{"..####..", ".#.#..#.", "#..#...#", "#..###.#", "#......#", ".#....#.", "..####..", "........"},
            "kbd", new String[]{"........", "########", "#.#.#.##", "##.#.#.#", "#.####.#", "########", "........", "........"},
            "warn", new String[]{"...##...", "...##...", "..#..#..", "..#..#..", ".#.##.#.", ".#....#.", "#..##..#", "########"});
    private static final java.util.Map<String, String[]> GLYPHS2 = java.util.Map.of(
            "board", new String[]{"########", "#.##...#", "#.##.#.#", "#....#.#", "#.###..#", "#......#", "########", ".#.#.#.."},
            "net", new String[]{"...##...", "...##...", "....#...", ".######.", ".#....#.", "##....##", "##....##", "........"},
            "plug", new String[]{"........", ".######.", ".#....#.", ".#.##.#.", ".######.", "..####..", "...##...", "...##..."},
            "traffic", new String[]{"..#.....", ".###....", "#.#.....", "..#..#..", "..#..#..", ".....#.#", "....###.", ".....#.."},
            "wifi", new String[]{"........", ".######.", "#......#", "..####..", ".#....#.", "...##...", "........", "...##..."});

    private static void glyph(GuiGraphics g, int x, int y, String name, int c) {
        String[] rows = GLYPHS.getOrDefault(name, GLYPHS2.get(name));
        if (rows == null) return;
        for (int j = 0; j < rows.length; j++) {
            String row = rows[j];
            int start = -1;
            for (int i = 0; i <= row.length(); i++) {
                boolean on = i < row.length() && row.charAt(i) == '#';
                if (on && start < 0) start = i;
                if (!on && start >= 0) {
                    g.fill(x + start, y + j, x + i, y + j + 1, c);
                    start = -1;
                }
            }
        }
    }

    /** Значок питания: кольцо с разрывом сверху и черта в разрыве (9×8). */
    private static final String[] POWER_SYMBOL = {
            "....#....", ".#..#..#.", "#...#...#", "#...#...#", "#.......#", "#.......#", ".#.....#.", "..#####.."};

    private static void powerSymbol(GuiGraphics g, int x, int y, int c) {
        for (int j = 0; j < POWER_SYMBOL.length; j++) {
            for (int i = 0; i < POWER_SYMBOL[j].length(); i++) {
                if (POWER_SYMBOL[j].charAt(i) == '#') px(g, x + i, y + j, c);
            }
        }
    }

    /** Текст, который обязан влезть в ширину: длинное обрезается с «…». */
    private void text(GuiGraphics g, Component c, int x, int y, int color, int maxW, int shadow) {
        FormattedCharSequence seq;
        if (font.width(c) > maxW) {
            FormattedText cut = font.substrByWidth(c, maxW - font.width("…"));
            seq = net.minecraft.locale.Language.getInstance().getVisualOrder(FormattedText.composite(cut, Component.literal("…")));
        } else {
            seq = c.getVisualOrderText();
        }
        if (shadow != 0) g.drawString(font, seq, x + 1, y + 1, shadow, false);
        g.drawString(font, seq, x, y, color, false);
    }

    private void text(GuiGraphics g, Component c, int x, int y, int color, int maxW) {
        text(g, c, x, y, color, maxW, 0);
    }

    // ======================= Рисование окна =======================

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.pose().pushPose();
        g.pose().translate(leftPos, topPos, 0);
        g.drawManaged(() -> drawShapes(g));
        drawGhosts(g);
        drawTexts(g);
        g.pose().popPose();
    }

    private void slotFrame(GuiGraphics g, int x, int y) {
        bevel(g, x, y, x + 17, y + 17, th.slotFill, th.slotTL, th.slotBR);
        if (th.slotRing != 0) outline(g, x - 1, y - 1, x + 18, y + 18, th.slotRing);
    }

    private void drawShapes(GuiGraphics g) {
        // ----- окно -----
        rect(g, 0, 0, W - 1, H - 1, th.winFill);
        outline(g, 0, 0, W - 1, H - 1, th.winOutline);
        rect(g, 1, 1, W - 2, 1, th.winLight);
        rect(g, 1, 1, 1, H - 2, th.winLight);
        rect(g, 1, H - 2, W - 2, H - 2, th.winDark);
        rect(g, W - 2, 1, W - 2, H - 2, th.winDark);
        decoWindow(g);
        // ----- корпус изнутри и плата -----
        bevel(g, CX0, CY0, CX1, CY1, th.caseFill, th.caseTL, th.caseBR);
        decoCase(g);
        rect(g, BX0, BY0, BX1, BY1, th.pcb);
        outline(g, BX0, BY0, BX1, BY1, CaseTheme.sh(th.pcb, 0.6));
        int trace = CaseTheme.sh(th.pcb, 1.18);
        for (int[] t : TRACES) rect(g, t[0], t[1], t[2], t[3], trace);
        if (!menu.serverLayout()) {
            // слот PCIe под видеокартой
            rect(g, BX0 + 4, 86, BX0 + 100, 88, CaseTheme.rgb(40, 42, 46));
            rect(g, BX0 + 4, 86, BX0 + 100, 86, CaseTheme.rgb(70, 72, 78));
            for (int x = BX0 + 6; x < BX0 + 100; x += 3) px(g, x, 87, CaseTheme.rgb(200, 170, 70));
        } else {
            // серверная плата: разводка двух сокетов и отсек дисков с салазками
            for (int k = 0; k < 2; k++) outline(g, 12 + k * 18, 40, 31 + k * 18, 59, CaseTheme.rgb(200, 170, 70));
        }
        // ----- слоты корпуса -----
        for (int i = 0; i < PcCaseBlockEntity.SLOT_COUNT; i++) {
            if (PcCaseBlockEntity.isUsb(i) || !menu.slotShown(i)) continue;   // USB — на передней панели, рисуются после неё
            slotFrame(g, menu.slotX(i), menu.slotY(i));
        }
        // ----- экран состояния -----
        rect(g, SX0, SY0, SX1, SY1, th.bezel);
        rect(g, SX0 + 2, SY0 + 2, SX1 - 2, SY1 - 2, th.glass);
        if (th.scan) {
            for (int y = SY0 + 2; y < SY1 - 1; y += 2) rect(g, SX0 + 2, y, SX1 - 2, y, CaseTheme.sh(th.glass, 1.25));
        }
        if (th.roundCorners) {
            px(g, SX0 + 2, SY0 + 2, th.bezel);
            px(g, SX1 - 2, SY0 + 2, th.bezel);
            px(g, SX0 + 2, SY1 - 2, th.bezel);
            px(g, SX1 - 2, SY1 - 2, th.bezel);
        }
        drawPowerButton(g, menu.computerState() == 1);
        for (int k = 0; k < TABS; k++) {
            int tx = SX0 + 4 + k * TAB_STEP;
            rect(g, tx, TAB_Y, tx + TAB_W - 1, TAB_Y + 10, k == tab ? th.tabOn : th.tabOff);
            glyph(g, tx + 4, TAB_Y + 1, TAB_GLYPHS[k], k == tab ? th.fg : th.dim);
        }
        int y = ROWS_Y;
        for (Row row : rows) {
            if (y > SY1 - 10) break;
            glyph(g, SX0 + 4, y, row.glyph(), row.color());
            y += 10;
            if (row.bar() >= 0) {
                rect(g, TEXT_X, y, SX1 - 4, y + 2, th.barBg);
                int w = Math.round((SX1 - 4 - TEXT_X) * row.bar());
                if (w > 0) rect(g, TEXT_X, y, TEXT_X + w, y + 2, row.barColor());
                y += 6;
            }
        }
        if (!menu.hasKeyboard()) glyph(g, SX1 - 11, SY0 + 7, "kbd", th.warn);
        // ----- инвентарь игрока -----
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) slotFrame(g, PcCaseMenu.INV_X - 1 + col * 18, PcCaseMenu.INV_Y - 1 + row * 18);
        }
        for (int col = 0; col < 9; col++) slotFrame(g, PcCaseMenu.INV_X - 1 + col * 18, PcCaseMenu.INV_Y + 57);
        // ----- передняя панель: USB и нагрузка -----
        bevel(g, PX0, PY0, PX1, PY1, th.portsFill, th.portsTL, th.portsBR);
        if (th.deco == CaseTheme.Deco.GAMING) {
            for (int x = PX0 + 1; x < PX1; x++) px(g, x, PY1, CaseTheme.rgbAt((double) (x - PX0) / (PX1 - PX0)));
        }
        for (int i = PcCaseBlockEntity.SLOT_USB; i < PcCaseBlockEntity.SLOT_USB + PcCaseBlockEntity.USB_SLOTS; i++) {
            slotFrame(g, menu.slotX(i), menu.slotY(i));
        }
        rect(g, GX0, GY0, GX1, GY1, th.graphBg);
        for (int x = GX0 + 1; x < GX1; x += 2) px(g, x, (GY0 + GY1) / 2, CaseTheme.sh(th.graphBg, th.graphBg == CaseTheme.rgb(240, 244, 250) ? 0.9 : 1.8));
        int hMax = GY1 - GY0 - 2;
        for (int i = 0; i < loadCount; i++) {
            int v = load[load.length - 1 - i];
            int h = Math.max(1, Math.round(hMax * v / 100f));   // работает — хотя бы черта
            int x1 = GX1 - 1 - i * 3;
            if (h > 0 && x1 - 1 > GX0) {
                rect(g, x1 - 1, GY1 - h, x1, GY1 - 1, i % 2 == 0 ? th.graphFg : CaseTheme.sh(th.graphFg, 0.8));
            }
        }
        decoTop(g);
    }

    private void drawPowerButton(GuiGraphics g, boolean on) {
        int x = BTN_X, y = BTN_Y;
        if (th.roundButton) {
            double cx = x + 8, cy = y + 7.5;
            for (int xx = x; xx < x + 16; xx++) {
                for (int yy = y; yy < y + 16; yy++) {
                    double d = Math.hypot(xx + 0.5 - cx, yy + 0.5 - cy);
                    if (d <= 7.6) px(g, xx, yy, d > 6.3 ? th.btnRing : (yy < cy + 3 ? th.btnFace : CaseTheme.sh(th.btnFace, 0.85)));
                }
            }
            powerSymbol(g, x + 4, y + 4, th.btnSymbol);
            rect(g, x + 16, y, x + 17, y + 1, on ? th.ledOn : th.ledOff);
        } else {
            outline(g, x - 1, y - 1, x + 16, y + 15, th.btnRing);
            bevel(g, x, y, x + 15, y + 14, th.btnFace, CaseTheme.sh(th.btnFace, 1.2), CaseTheme.sh(th.btnFace, 0.65));
            powerSymbol(g, x + 3, y + 4, th.btnSymbol);
            rect(g, x + 12, y + 2, x + 13, y + 3, on ? th.ledOn : th.ledOff);
        }
    }

    private void drawGhosts(GuiGraphics g) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float a = ((th.ghost >>> 24) & 255) / 255f, r = ((th.ghost >> 16) & 255) / 255f,
                gr = ((th.ghost >> 8) & 255) / 255f, b = (th.ghost & 255) / 255f;
        for (int i = 0; i < PcCaseBlockEntity.SLOT_COUNT; i++) {
            if (menu.slots.get(i).hasItem() || !menu.slotShown(i)) continue;
            int x = menu.slotX(i), y = menu.slotY(i);
            if (disabled(i)) continue;
            RenderSystem.setShaderColor(r, gr, b, a);
            g.blit(ResourceLocation.fromNamespaceAndPath(MineComputers.MODID, "textures/gui/ghost/" + GHOSTS[i] + ".png"),
                    x + 1, y + 1, 0, 0, 16, 16, 16, 16);
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
        // Разъёма нет на этой плате — слот перечёркнут
        for (int i = 0; i < PcCaseBlockEntity.SLOT_COUNT; i++) {
            if (menu.slots.get(i).hasItem() || !disabled(i) || !menu.slotShown(i)) continue;
            int x = menu.slotX(i), y = menu.slotY(i);
            g.fill(x + 1, y + 1, x + 17, y + 17, 0x40A02020);
            for (int k = 0; k < 8; k++) {
                px(g, x + 5 + k, y + 5 + k, 0xA0802020);
                px(g, x + 12 - k, y + 5 + k, 0xA0802020);
            }
        }
    }

    private void drawTexts(GuiGraphics g) {
        text(g, title, 8, 5, th.titleColor, 150);
        int scoreW = 0;
        if (score != null) {
            scoreW = Math.min(font.width(score), 90);
            text(g, score, W - 8 - scoreW, 5, scoreColor, 90);
        }
        if (th.deco == CaseTheme.Deco.SERVER) {             // лампочки ядер в заголовке
            boolean on = menu.computerState() == 1;
            int x0 = W - 8 - scoreW - 8 - 8 * 5;
            for (int k = 0; k < 8; k++) {
                int c = k < cores ? (on ? CaseTheme.rgb(60, 255, 120) : CaseTheme.rgb(40, 90, 50)) : CaseTheme.rgb(30, 34, 40);
                rect(g, x0 + k * 5, 7, x0 + k * 5 + 2, 8, c);
            }
        }
        int shadow = th.labelShadow;
        if (menu.serverLayout()) {
            if (!menu.slotShown(PcCaseBlockEntity.SLOT_BAY_EXTRA)) text(g, tr("gui.minepc.group.board"), 33, 110, th.labelColor, 60, shadow);
            // Резерв питания: два блока — зелёная лампа, один — жёлтая
            PcCaseBlockEntity be = menu.blockEntity();
            if (be != null && be.psuCount() > 0) {
                int lamp = be.psuCount() >= 2 ? CaseTheme.rgb(60, 220, 110) : CaseTheme.rgb(240, 190, 40);
                rect(g, 151, 22, 152, 23, lamp);
            }
        } else {
            text(g, tr("gui.minepc.group.cpu"), 18, 21, th.labelColor, 36, shadow);
            text(g, tr("gui.minepc.group.memory"), 62, 21, th.labelColor, 54, shadow);
            text(g, tr("gui.minepc.group.video"), 18, 55, th.labelColor, 40, shadow);
            text(g, tr("gui.minepc.group.m2"), 62, 55, th.labelColor, 36, shadow);
            text(g, tr("gui.minepc.group.board"), 38, 103, th.labelColor, 60, shadow);
        }
        // экран состояния
        int state = menu.computerState();
        Component stateText = tr(state == 1 ? "gui.minepc.state.on" : state == 2 ? "gui.minepc.state.crashed" : "gui.minepc.state.off");
        int stateW = SX1 - SX0 - 26 - (menu.hasKeyboard() ? 0 : 11);
        text(g, stateText, SX0 + 24, SY0 + 7, state == 1 ? th.ok : state == 2 ? th.bad : th.dim, stateW);
        int y = ROWS_Y;
        for (Row row : rows) {
            if (y > SY1 - 10) break;
            text(g, row.text(), TEXT_X, y, row.color(), TEXT_W);
            y += row.bar() >= 0 ? 16 : 10;
        }
        text(g, playerInventoryTitle, 8, PcCaseMenu.INV_Y - 9, th.titleColor, 100);
        text(g, tr("gui.minepc.group.usb"), PX0 + 4, PY0 + 3, th.portsLabel, 40);
        text(g, tr("gui.minepc.load"), PX0 + 4, PY0 + 36, th.portsLabel, PX1 - PX0 - 6);
    }

    // ----- детали оформления по темам -----

    private void decoWindow(GuiGraphics g) {
        switch (th.deco) {
            case RETRO -> {
                for (int x = W - 60; x < W - 8; x += 3) rect(g, x, H - 6, x + 1, H - 4, CaseTheme.sh(CaseTheme.BEIGE, 0.75));
            }
            case OFFICE -> rect(g, 8, 14, W - 9, 14, CaseTheme.OFFICE_BLUE);
            case INDUSTRIAL -> {
                for (int y = 1; y <= 3; y++) {
                    for (int x = 1; x < W - 1; x++) {
                        px(g, x, y, ((x + y) / 4) % 2 == 0 ? CaseTheme.rgb(230, 190, 40) : CaseTheme.rgb(30, 30, 30));
                    }
                }
                int[][] rivets = {{3, 6}, {W - 5, 6}, {3, H - 5}, {W - 5, H - 5}};
                for (int[] r : rivets) {
                    rect(g, r[0], r[1], r[0] + 1, r[1] + 1, CaseTheme.rgb(190, 196, 200));
                    px(g, r[0] + 1, r[1] + 1, CaseTheme.rgb(60, 66, 70));
                }
            }
            case SERVER -> {
                for (int x0 : new int[]{1, W - 6}) {
                    rect(g, x0, 1, x0 + 4, H - 2, CaseTheme.rgb(58, 62, 70));
                    for (int y = 10; y < H - 8; y += 18) rect(g, x0 + 1, y, x0 + 3, y + 3, CaseTheme.rgb(20, 22, 26));
                }
            }
            default -> {
            }
        }
    }

    private void decoCase(GuiGraphics g) {
        switch (th.deco) {
            case GAMING -> {
                int per = 2 * ((CX1 - CX0) + (CY1 - CY0));
                int k = 0;
                for (int x = CX0; x <= CX1; x++) px(g, x, CY0, CaseTheme.rgbAt((double) k++ / per));
                for (int y = CY0; y <= CY1; y++) px(g, CX1, y, CaseTheme.rgbAt((double) k++ / per));
                for (int x = CX1; x >= CX0; x--) px(g, x, CY1, CaseTheme.rgbAt((double) k++ / per));
                for (int y = CY1; y >= CY0; y--) px(g, CX0, y, CaseTheme.rgbAt((double) k++ / per));
            }
            case INDUSTRIAL -> {
                int hole = CaseTheme.sh(CaseTheme.STEEL, 0.62);
                for (int x = CX0 + 2; x < CX1 - 1; x += 4) {
                    for (int y = CY0 + 2; y < CY1 - 1; y += 4) {
                        if (x >= BX0 && x <= BX1 && y >= BY0 && y <= BY1) continue;   // под платой не видно
                        px(g, x, y, hole);
                    }
                }
            }
            default -> {
            }
        }
    }

    private void decoTop(GuiGraphics g) {
        if (th.deco == CaseTheme.Deco.RETRO) {
            rect(g, SX0 + 3, SY1 + 2, SX0 + 20, SY1 + 3, CaseTheme.sh(CaseTheme.BEIGE, 0.7));   // прорезь под экраном
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Все надписи рисуются в renderBg вместе с оформлением темы
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (th.deco == CaseTheme.Deco.GAMING) {               // блик на стекле — поверх деталей
            g.pose().pushPose();
            g.pose().translate(leftPos, topPos, 250);
            for (int k = 0; k < 28; k++) {
                int y = CY0 + 2 + k;
                if (y < CY1 - 2) g.fill(CX0 + 60 + k, y, CX0 + 62 + k, y + 1, 0x2EFFFFFF);
            }
            g.pose().popPose();
        }
        renderTooltip(g, mouseX, mouseY);
        if (!menu.getCarried().isEmpty()) return;
        int mx = mouseX - leftPos, my = mouseY - topPos;
        // Пустой слот: что сюда ставить
        PcCaseBlockEntity hoverBe = menu.blockEntity();
        for (int i = 0; i < PcCaseBlockEntity.SLOT_COUNT; i++) {
            if (!menu.slotShown(i)) continue;
            int sx = menu.slotX(i), sy = menu.slotY(i);
            if (mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18 && hoverBe != null && hoverBe.lockedWhileRunning(i)
                    && menu.slots.get(i).hasItem()) {
                // Работающий сервер: деталь видно, но не вынуть
                List<Component> tip = new ArrayList<>(getTooltipFromContainerItem(menu.slots.get(i).getItem()));
                tip.add(tr("message.minepc.server_running").withStyle(ChatFormatting.GOLD));
                g.renderComponentTooltip(font, tip, mouseX, mouseY);
                return;
            }
            if (mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18 && !menu.slots.get(i).hasItem()) {
                List<Component> tip = new ArrayList<>();
                tip.add(tr(menu.serverLayout() && PcCaseBlockEntity.isDriveBay(i) ? "gui.minepc.slot.bay" : SLOT_HINTS[i]));
                if (disabled(i)) tip.add(tr("gui.minepc.slot.unavailable").withStyle(ChatFormatting.RED));
                else if (hoverBe != null && hoverBe.lockedWhileRunning(i)) tip.add(tr("message.minepc.server_running").withStyle(ChatFormatting.GOLD));
                g.renderComponentTooltip(font, tip, mouseX, mouseY);
                return;
            }
        }
        if (in(mx, my, BTN_X, BTN_Y, 17, 16)) {
            g.renderTooltip(font, tr(menu.computerState() == 1 ? "gui.minepc.power_tip.on" : "gui.minepc.power_tip.off"), mouseX, mouseY);
            return;
        }
        if (!menu.hasKeyboard() && in(mx, my, SX1 - 12, SY0 + 5, 10, 10)) {
            g.renderTooltip(font, tr("gui.minepc.terminal.no_keyboard").withStyle(ChatFormatting.GOLD), mouseX, mouseY);
            return;
        }
        for (int k = 0; k < TABS; k++) {
            if (in(mx, my, SX0 + 4 + k * TAB_STEP, TAB_Y, TAB_W, 11)) {
                g.renderTooltip(font, tr(TAB_KEYS[k]), mouseX, mouseY);
                return;
            }
        }
        if (in(mx, my, GX0, GY0, GX1 - GX0 + 1, GY1 - GY0 + 1)) {
            g.renderTooltip(font, tr("gui.minepc.load_tip", menu.computerState() == 1 ? menu.loadPercent() : 0), mouseX, mouseY);
            return;
        }
        if (in(mx, my, SX0, ROWS_Y - 2, SX1 - SX0 + 1, SY1 - ROWS_Y + 2)) {
            // Строка со своей подсказкой — она; иначе полный отчёт о сборке
            int y = ROWS_Y;
            for (Row row : rows) {
                int h = row.bar() >= 0 ? 16 : 10;
                if (row.tip() != null && my >= y - 1 && my < y + h - 1) {
                    g.renderComponentTooltip(font, row.tip(), mouseX, mouseY);
                    return;
                }
                y += h;
            }
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component c : report) lines.addAll(font.split(c, 220));
            g.renderTooltip(font, lines, mouseX, mouseY);
        }
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) Math.floor(mouseX) - leftPos, my = (int) Math.floor(mouseY) - topPos;
        if (button == 0 && minecraft != null) {
            if (in(mx, my, BTN_X, BTN_Y, 17, 16)) {
                click();
                if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PcCaseMenu.BTN_POWER);
                return true;
            }
            for (int k = 0; k < TABS; k++) {
                if (in(mx, my, SX0 + 4 + k * TAB_STEP, TAB_Y, TAB_W, 11)) {
                    if (tab != k) click();
                    tab = k;
                    refresh();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void click() {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** Клик мимо окна не должен выбрасывать предмет, если попал по передней панели или экрану. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        return mouseX < left || mouseY < top || mouseX >= left + imageWidth || mouseY >= top + imageHeight;
    }
}
