package com.strikerr212.minepc.hardware.spec;

import com.strikerr212.minepc.hardware.GpuData;
import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.Perf;
import com.strikerr212.minepc.hardware.Units;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

import static com.strikerr212.minepc.hardware.spec.Param.Unit.*;

/**
 * Видеокарта. Собирается в два этапа:
 * страница 0 «Плата» — графический чип, частота, чипы видеопамяти, тип памяти, длина, цвет текстолита;
 * страница 1 «Охлаждение» — тип системы охлаждения, толщина, цвет кожуха, подсветка.
 */
public class GpuSpec extends PartSpec {

    public static final int CHIP = 0, CLOCK = 1, VRAM_CHIPS = 2, VRAM_CHIP_MB = 3, VRAM_TYPE = 4, LENGTH = 5, PCB = 6,
            COOLER = 7, THICKNESS = 8, COLOR = 9, LED_ON = 10, LED_COLOR = 11;

    public static final int COOLER_NONE = 0, COOLER_PASSIVE = 1, COOLER_BLOWER = 2, COOLER_DUAL = 3, COOLER_TRIPLE = 4;

    private static final int[] SHADERS = {256, 1024, 2560, 6144, 12288};
    private static final int[] BASE_TDP = {30, 75, 150, 250, 350};
    private static final int[] COOLER_W = {0, 60, 160, 260, 380};
    private static final int[] THICKNESS_PERCENT = {100, 120, 140};

    public GpuSpec() {
        super(HardwareType.GPU, List.of(
                Param.option("gpu.chip", 0, 1, 2, 3, 4, 5),
                Param.number("gpu.clock", 0, MHZ, new int[]{600, 900, 1200, 1500, 1800, 2100, 2400, 2700},
                        new int[]{1, 1, 2, 2, 3, 4, 4, 5}),
                Param.number("gpu.vram_chips", 0, NONE, new int[]{2, 4, 6, 8, 12}, new int[]{1, 1, 2, 3, 4}),
                Param.number("gpu.vram_chip_mb", 0, MB, new int[]{128, 256, 512, 1024, 2048}, new int[]{1, 2, 3, 4, 5}),
                Param.option("gpu.vram_type", 0, 1, 2, 3, 5),
                Param.option("gpu.length", 0, 1, 1, 2),
                Param.pcb("gpu.pcb", 0),
                Param.option("gpu.cooler", 1, 1, 1, 1, 2, 3),
                Param.option("gpu.thickness", 1, 1, 2, 3),
                Param.metal("gpu.color", 1),
                Param.toggle("gpu.led", 1),
                Param.led("gpu.led_color", 1)
        ), 0, 0, 0, 0, 0, 0, 2, 3, 0, 2, 0, 0);
    }

    public GpuData derive(List<Integer> v) {
        int chip = idx(v, CHIP);
        int clock = val(v, CLOCK);
        int tdp = Math.round(BASE_TDP[chip] * clock / 1800f) + val(v, VRAM_CHIPS) * 2;
        int cooler = idx(v, COOLER);
        int cooling = COOLER_W[cooler];
        if (cooler >= COOLER_DUAL) cooling = cooling * THICKNESS_PERCENT[idx(v, THICKNESS)] / 100;
        return new GpuData(tier(v), SHADERS[chip], clock, val(v, VRAM_CHIPS), val(v, VRAM_CHIPS) * val(v, VRAM_CHIP_MB),
                idx(v, VRAM_TYPE), tdp, cooling);
    }

    private boolean hasLedStrip(List<Integer> v) {
        return idx(v, COOLER) >= COOLER_BLOWER;
    }

    private boolean isLedOn(List<Integer> v) {
        return hasLedStrip(v) && idx(v, LED_ON) == 1;
    }

    @Override
    public boolean isActive(int param, List<Integer> v) {
        int cooler = idx(v, COOLER);
        return switch (param) {
            case THICKNESS -> cooler >= COOLER_DUAL;
            case COLOR -> cooler != COOLER_NONE;
            case LED_ON -> hasLedStrip(v);
            case LED_COLOR -> isLedOn(v);
            default -> true;
        };
    }

    @Override
    public int powerDraw(List<Integer> v) {
        return derive(v).tdp() + (idx(v, COOLER) >= COOLER_BLOWER ? 3 : 0);
    }

    @Override
    public int heat(List<Integer> v) {
        return derive(v).tdp();
    }

    @Override
    public String nameKey(List<Integer> v) {
        return idx(v, COOLER) == COOLER_NONE ? "item.minepc.gpu.bare" : "item.minepc.gpu";
    }

    @Override
    public void describe(List<Integer> v, List<Component> lines) {
        GpuData d = derive(v);
        lines.add(paramLine(v, CHIP));
        lines.add(line("tooltip.minepc.shaders", d.shaders()));
        lines.add(line("tooltip.minepc.clock", Units.frequency(d.clockMhz())));
        lines.add(line("tooltip.minepc.vram", Component.translatable("tooltip.minepc.vram_value",
                Units.size(d.vramMb()), params().get(VRAM_TYPE).display(idx(v, VRAM_TYPE)))));
        lines.add(line("tooltip.minepc.chips",
                Component.translatable("tooltip.minepc.chips_value", val(v, VRAM_CHIPS), Units.size(val(v, VRAM_CHIP_MB)))));
        lines.add(line("tooltip.minepc.heat", Units.watts(d.tdp())));
        lines.add(paramLine(v, COOLER));
        if (d.hasCooler()) {
            lines.add(line("tooltip.minepc.cooling", Units.watts(d.coolingW())));
        }
        int[] screen = Perf.screenSize(d.tier());
        lines.add(perfLine(screen[0] > 160 ? "tooltip.minepc.screen_wall" : "tooltip.minepc.screen", screen[0] + "×" + screen[1]));
        lines.add(perfLine("tooltip.minepc.gpu_draw", Perf.gpuCellsPerSecond(d.tier()) / 1000,
                Math.round(Perf.vramCells(d) / 1000.0)));
        double tflops = Perf.gpuTflops(d);
        lines.add(perfLine("tooltip.minepc.perf_gpu", Units.tflops(tflops), Units.level(Perf.level(tflops, Perf.STOCK_GPU_TFLOPS))));
        lines.add(perfLine("tooltip.minepc.perf_bus", Units.bits(Perf.gpuBusBits(d)), Units.bandwidth(Perf.gpuMemoryGbs(d))));
        double throttle = Perf.throttle(d.tdp(), d.hasCooler() ? d.coolingW() : Perf.PASSIVE_COOLING);
        if (throttle < 1) lines.add(perfLine("tooltip.minepc.perf_throttle", Units.percent(throttle)));
        double memory = Perf.gpuMemoryFactor(d);
        if (memory < 0.85) lines.add(perfLine("tooltip.minepc.perf_vram_bound", Math.round((1 - memory) * 100)));
    }

    @Override
    public int visual(List<Integer> v, int dim) {
        int cooler = idx(v, COOLER);
        return switch (dim) {
            case 0 -> cooler;
            case 1 -> idx(v, LENGTH);
            case 2 -> cooler == COOLER_NONE ? idx(v, VRAM_CHIPS) : (cooler >= COOLER_DUAL ? idx(v, THICKNESS) : 0);
            default -> 0;
        };
    }

    /** На странице «Плата» показываем голую плату без охлаждения. */
    @Override
    public List<Integer> previewValues(List<Integer> v, int page) {
        if (page != 0) return v;
        List<Integer> bare = new ArrayList<>(sanitize(v));
        bare.set(COOLER, COOLER_NONE);
        return bare;
    }

    @Override
    public int tint(List<Integer> v, int layer) {
        return switch (layer) {
            case 1 -> color(v, COLOR);
            case 2 -> ledColor(isLedOn(v), color(v, LED_COLOR));
            case 3 -> color(v, PCB);
            default -> 0xFFFFFF;
        };
    }

    @Override
    protected boolean hasLed(List<Integer> v) {
        return isLedOn(v);
    }

    @Override
    protected void baseCost(List<Integer> v, Cost cost) {
        cost.add(Items.QUARTZ, 2 + idx(v, CHIP)).add(Items.REDSTONE, 4 + val(v, VRAM_CHIPS)).add(Items.COPPER_INGOT, 2);
        int cooler = idx(v, COOLER);
        if (cooler != COOLER_NONE) cost.add(Items.IRON_INGOT, 2 + cooler).add(Items.COPPER_INGOT, cooler);
    }
}
