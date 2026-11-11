package com.strikerr212.minepc.hardware.spec;

import com.strikerr212.minepc.hardware.CpuData;
import com.strikerr212.minepc.hardware.GpuData;
import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.Perf;
import com.strikerr212.minepc.hardware.Units;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.List;

import static com.strikerr212.minepc.hardware.spec.Param.Unit.*;

/**
 * Процессор.
 * Страница 0 «Кристалл»: сокет, ядра, частота, техпроцесс, кэш, видеоядро.
 * Страница 1 «Корпус»: крышка, её размер и цвет, цвет подложки, цвет логотипа.
 */
public class CpuSpec extends PartSpec {

    public static final int SOCKET = 0, CORES = 1, CLOCK = 2, PROCESS = 3, CACHE = 4,
            LID = 5, LID_SIZE = 6, LID_COLOR = 7, SUBSTRATE = 8, LOGO = 9, IGPU = 10;
    /** Сколько тепла добавляет видеоядро, Вт (нет, слабое, среднее, сильное). */
    private static final int[] IGPU_TDP = {0, 4, 9, 18};

    /** Чем тоньше техпроцесс, тем меньше нагрев на ядро и мегагерц и тем сильнее буст. */
    private static final int[] PROCESS_NM = {90, 65, 45, 32, 22, 14, 10, 7, 5, 3};
    private static final float[] HEAT_FACTOR = {9f, 7f, 5.5f, 4.2f, 3.2f, 2.5f, 2.0f, 1.6f, 1.3f, 1.05f};
    private static final int[] BOOST_PCT = {0, 0, 5, 5, 10, 10, 15, 20, 25, 30};
    /** Базовая частота ядра в игре, кГц: одна команда Lua — один такт (3500 = 3,5 МГц). */
    private static final int[] CLOCK_KHZ = {500, 750, 1000, 2000, 2500, 3500, 4000, 5000, 5500, 6000, 7000, 8000};

    public CpuSpec() {
        super(HardwareType.CPU, List.of(
                Param.option("cpu.socket", 0, 1, 1, 1),
                Param.number("cpu.cores", 0, NONE, new int[]{1, 2, 4, 6, 8, 12, 16, 24, 32},
                        new int[]{1, 1, 2, 3, 3, 4, 4, 5, 5}),
                Param.number("cpu.clock", 0, KHZ, CLOCK_KHZ,
                        new int[]{1, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 5}),
                Param.number("cpu.process", 0, NM, PROCESS_NM, new int[]{1, 1, 2, 2, 3, 3, 4, 4, 5, 5}),
                Param.number("cpu.cache", 0, KB, new int[]{1, 2, 4, 8, 16, 32, 64, 128}, new int[]{1, 1, 2, 2, 3, 4, 4, 5}),
                Param.option("cpu.lid", 1, 1, 1, 1, 2, 3),
                Param.option("cpu.lid_size", 1, 1, 1, 1),
                Param.metal("cpu.lid_color", 1),
                Param.pcb("cpu.substrate", 1),
                Param.led("cpu.logo", 1),
                Param.option("cpu.igpu", 0, 1, 1, 2, 3)
        ), 0, 0, 0, 0, 0, 2, 1, 0, 0, 0, 0);
    }

    public CpuData derive(List<Integer> v) {
        int cores = val(v, CORES), clock = val(v, CLOCK);
        float factor = HEAT_FACTOR[idx(v, PROCESS)];
        int igpu = idx(v, IGPU);
        int tdp = Math.round(cores * clock / 1000f * factor * 0.75f) + 5 + IGPU_TDP[igpu];
        return new CpuData(tier(v), val(v, SOCKET), cores, clock, BOOST_PCT[idx(v, PROCESS)], val(v, PROCESS), val(v, CACHE), tdp, igpu);
    }

    @Override
    public boolean isActive(int param, List<Integer> v) {
        return param != LOGO || idx(v, LID) == 4;   // логотип есть только на флагманской крышке
    }

    @Override
    public int powerDraw(List<Integer> v) {
        return derive(v).tdp();
    }

    @Override
    public String nameKey(List<Integer> v) {
        return "item.minepc.cpu";
    }

    @Override
    public String labelKey() {
        return "tooltip.minepc.engraving";
    }

    @Override
    public void describe(List<Integer> v, List<Component> lines) {
        CpuData d = derive(v);
        lines.add(paramLine(v, SOCKET));
        lines.add(line("tooltip.minepc.cores", d.cores()));
        lines.add(d.boostPct() > 0
                ? Component.translatable("tooltip.minepc.clock_boost", Units.gameClock(d.clockKhz()).copy().withStyle(net.minecraft.ChatFormatting.WHITE),
                        Units.gameClock(Math.round(d.clockKhz() * (1 + d.boostPct() / 100f))).copy().withStyle(net.minecraft.ChatFormatting.WHITE))
                    .withStyle(net.minecraft.ChatFormatting.GRAY)
                : line("tooltip.minepc.clock", Units.gameClock(d.clockKhz())));
        lines.add(paramLine(v, PROCESS));
        lines.add(paramLine(v, CACHE));
        if (d.igpu() > 0) {
            GpuData g = Perf.integratedGpu(d, Integer.MAX_VALUE);
            int[] screen = Perf.screenSize(g.tier());
            lines.add(paramLine(v, IGPU));
            lines.add(perfLine("tooltip.minepc.igpu_perf", Units.cells(Perf.gpuCellsPerSecond(Perf.level(Perf.gpuTflops(g), Perf.STOCK_GPU_TFLOPS))),
                    screen[0] + "×" + screen[1]));
        }
        lines.add(line("tooltip.minepc.heat", Units.watts(d.tdp())));
        double total = Perf.cpuMhz(d);
        lines.add(perfLine("tooltip.minepc.perf_one", Units.gameClock(Math.round(d.clockKhz() * (1 + d.boostPct() / 100f)))));
        if (d.cores() > 1) lines.add(perfLine("tooltip.minepc.perf_all", d.cores(), Units.gameClock((int) Math.round(total * 1000)),
                Units.level(Perf.level(total, Perf.STOCK_CPU_MHZ))));
        else lines.add(perfLine("tooltip.minepc.perf_level", Units.level(Perf.level(total, Perf.STOCK_CPU_MHZ))));
        lines.add(perfLine("tooltip.minepc.perf_mem_need", Units.gameBandwidth(Perf.cpuMemoryDemand(d))));
    }

    @Override
    public int visual(List<Integer> v, int dim) {
        return switch (dim) {
            case 0 -> idx(v, LID);
            case 1 -> idx(v, LID_SIZE);
            default -> 0;
        };
    }

    @Override
    public int tint(List<Integer> v, int layer) {
        return switch (layer) {
            case 1 -> color(v, LID_COLOR);
            case 2 -> color(v, LOGO);
            case 3 -> color(v, SUBSTRATE);
            default -> 0xFFFFFF;
        };
    }

    @Override
    protected void baseCost(List<Integer> v, Cost cost) {
        cost.add(Items.QUARTZ, 2).add(Items.COPPER_INGOT, 1).add(Items.REDSTONE, 2 + idx(v, CORES));
        if (idx(v, LID) > 0) cost.add(Items.IRON_INGOT, 1);
        if (idx(v, IGPU) > 0) cost.add(Items.GLOWSTONE_DUST, idx(v, IGPU));   // видеоядро
    }
}
