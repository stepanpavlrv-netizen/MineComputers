package com.strikerr212.minepc.hardware.spec;

import com.strikerr212.minepc.hardware.CpuData;
import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.Units;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.List;

import static com.strikerr212.minepc.hardware.spec.Param.Unit.*;

/**
 * Процессор.
 * Страница 0 «Кристалл»: сокет, ядра, частота, техпроцесс, кэш.
 * Страница 1 «Корпус»: крышка, её размер и цвет, цвет подложки, цвет логотипа.
 */
public class CpuSpec extends PartSpec {

    public static final int SOCKET = 0, CORES = 1, CLOCK = 2, PROCESS = 3, CACHE = 4,
            LID = 5, LID_SIZE = 6, LID_COLOR = 7, SUBSTRATE = 8, LOGO = 9;

    /** Чем тоньше техпроцесс, тем меньше нагрев на ядро и гигагерц. */
    private static final int[] PROCESS_NM = {90, 65, 45, 32, 22, 14, 10, 7, 5, 3};
    private static final float[] HEAT_FACTOR = {9f, 7f, 5.5f, 4.2f, 3.2f, 2.5f, 2.0f, 1.6f, 1.3f, 1.05f};

    public CpuSpec() {
        super(HardwareType.CPU, List.of(
                Param.option("cpu.socket", 0, 1, 1, 1),
                Param.number("cpu.cores", 0, NONE, new int[]{1, 2, 4, 6, 8, 12, 16, 24, 32},
                        new int[]{1, 1, 2, 3, 3, 4, 4, 5, 5}),
                Param.number("cpu.clock", 0, MHZ, new int[]{800, 1200, 1600, 2000, 2400, 2800, 3200, 3600, 4000, 4400, 5000, 5600},
                        new int[]{1, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 5}),
                Param.number("cpu.process", 0, NM, PROCESS_NM, new int[]{1, 1, 2, 2, 3, 3, 4, 4, 5, 5}),
                Param.number("cpu.cache", 0, MB, new int[]{1, 2, 4, 8, 16, 32, 64, 128}, new int[]{1, 1, 2, 2, 3, 4, 4, 5}),
                Param.option("cpu.lid", 1, 1, 1, 1, 2, 3),
                Param.option("cpu.lid_size", 1, 1, 1, 1),
                Param.metal("cpu.lid_color", 1),
                Param.pcb("cpu.substrate", 1),
                Param.led("cpu.logo", 1)
        ), 0, 0, 0, 0, 0, 2, 1, 0, 0, 0);
    }

    public CpuData derive(List<Integer> v) {
        int cores = val(v, CORES), clock = val(v, CLOCK);
        float factor = HEAT_FACTOR[idx(v, PROCESS)];
        int tdp = Math.round(cores * clock / 1000f * factor) + 5;
        return new CpuData(tier(v), val(v, SOCKET), cores, clock, val(v, PROCESS), val(v, CACHE), tdp);
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
        lines.add(line("tooltip.minepc.clock", Units.frequency(d.clockMhz())));
        lines.add(paramLine(v, PROCESS));
        lines.add(paramLine(v, CACHE));
        lines.add(line("tooltip.minepc.heat", Units.watts(d.tdp())));
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
    }
}
