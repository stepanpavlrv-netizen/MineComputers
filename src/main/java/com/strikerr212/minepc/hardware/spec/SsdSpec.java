package com.strikerr212.minepc.hardware.spec;

import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.Perf;
import com.strikerr212.minepc.hardware.SsdData;
import com.strikerr212.minepc.hardware.Units;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.List;

import static com.strikerr212.minepc.hardware.spec.Param.Unit.*;

/** SSD: интерфейс (SATA или NVMe), объём, тип памяти, корпус/охлаждение, цвета. */
public class SsdSpec extends PartSpec {

    public static final int IFACE = 0, CAPACITY = 1, NAND = 2, COOLING = 3, COLOR = 4, ACCENT = 5, PCB = 6;

    private static final int[] IFACE_SPEED = {550, 3500, 7000, 12000};
    private static final int[] NAND_PERCENT = {60, 85, 95, 100};   // QLC, TLC, MLC, SLC
    /** Случайное чтение блоками по 4 КБ: тысяч операций в секунду на лучшей памяти. */
    private static final int[] IFACE_KIOPS = {95, 450, 1000, 1500};

    public SsdSpec() {
        super(HardwareType.SSD, List.of(
                Param.option("ssd.iface", 0, 1, 2, 3, 5),
                Param.number("ssd.capacity", 0, KB, new int[]{256, 512, 1024, 2048, 4096, 8192, 16384, 32768},
                        new int[]{1, 1, 1, 2, 3, 3, 4, 5}),
                Param.option("ssd.nand", 0, 1, 1, 2, 3),
                Param.option("ssd.cooling", 0, 1, 1, 2),
                Param.metal("ssd.color", 0),
                Param.led("ssd.accent", 0),
                Param.pcb("ssd.pcb", 0)
        ), 0, 1, 1, 0, 2, 0, 2);
    }

    public SsdData derive(List<Integer> v) {
        int iface = idx(v, IFACE);
        int speed = IFACE_SPEED[iface] * NAND_PERCENT[idx(v, NAND)] / 100;
        int iops = IFACE_KIOPS[iface] * NAND_PERCENT[idx(v, NAND)] * 10;
        return new SsdData(tier(v), iface, val(v, CAPACITY), speed, iops);
    }

    @Override
    public boolean isActive(int param, List<Integer> v) {
        boolean nvme = idx(v, IFACE) > 0;
        return switch (param) {
            case PCB -> nvme;                                    // у SATA плата спрятана в корпусе
            case COLOR -> !nvme || idx(v, COOLING) > 0;          // у голого NVMe нечего красить
            default -> true;
        };
    }

    @Override
    public int powerDraw(List<Integer> v) {
        return derive(v).powerDraw();
    }

    @Override
    public String nameKey(List<Integer> v) {
        return idx(v, IFACE) > 0 ? "item.minepc.ssd.nvme" : "item.minepc.ssd.sata";
    }

    @Override
    public void describe(List<Integer> v, List<Component> lines) {
        SsdData d = derive(v);
        lines.add(paramLine(v, IFACE));
        lines.add(line("tooltip.minepc.capacity", Units.gameSize(d.capacityMb())));
        lines.add(paramLine(v, NAND));
        lines.add(line("tooltip.minepc.speed", Units.perSecond(Perf.gameDiskBytesPerSecond(d.speedMbs()))));
        lines.add(perfLine("tooltip.minepc.perf_random", Units.iops(d.iops())));
        lines.add(perfLine("tooltip.minepc.perf_boot", Units.seconds(Perf.gameBootTicks(Perf.bootSeconds(d)) / 20.0)));
    }

    @Override
    public int visual(List<Integer> v, int dim) {
        return switch (dim) {
            case 0 -> idx(v, IFACE) > 0 ? 1 : 0;
            case 1 -> idx(v, COOLING);
            default -> 0;
        };
    }

    @Override
    public int tint(List<Integer> v, int layer) {
        return switch (layer) {
            case 1 -> color(v, COLOR);
            case 2 -> color(v, ACCENT);
            case 3 -> color(v, PCB);
            default -> 0xFFFFFF;
        };
    }

    @Override
    protected void baseCost(List<Integer> v, Cost cost) {
        cost.add(Items.QUARTZ, 1 + idx(v, CAPACITY) / 2).add(Items.REDSTONE, 2).add(Items.COPPER_INGOT, 1);
        if (idx(v, COOLING) > 0) cost.add(Items.IRON_INGOT, idx(v, COOLING));
    }
}
