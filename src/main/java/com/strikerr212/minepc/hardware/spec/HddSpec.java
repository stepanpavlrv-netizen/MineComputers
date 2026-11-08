package com.strikerr212.minepc.hardware.spec;

import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.Perf;
import com.strikerr212.minepc.hardware.HddData;
import com.strikerr212.minepc.hardware.Units;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.List;

import static com.strikerr212.minepc.hardware.spec.Param.Unit.*;

/** Жёсткий диск: форм-фактор, объём, обороты, кэш, крышка, цвета. */
public class HddSpec extends PartSpec {

    public static final int FORM = 0, CAPACITY = 1, SPINDLE = 2, CACHE = 3, LID = 4, COLOR = 5, LABEL = 6;

    public HddSpec() {
        super(HardwareType.HDD, List.of(
                Param.option("hdd.form", 0, 1, 1),
                Param.number("hdd.capacity", 0, MB, new int[]{128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768},
                        new int[]{1, 1, 1, 2, 2, 3, 4, 5, 5}),
                Param.number("hdd.rpm", 0, RPM, new int[]{4200, 5400, 7200, 10000, 15000}, new int[]{1, 1, 2, 3, 4}),
                Param.number("hdd.cache", 0, MB, new int[]{8, 16, 32, 64, 128, 256, 512}, new int[]{1, 1, 2, 2, 3, 4, 5}),
                Param.option("hdd.lid", 0, 1, 2, 3),
                Param.metal("hdd.color", 0),
                Param.led("hdd.label", 0)
        ), 1, 3, 1, 1, 0, 0, 0);
    }

    public HddData derive(List<Integer> v) {
        return new HddData(tier(v), idx(v, FORM) == 0, val(v, CAPACITY), val(v, SPINDLE), val(v, CACHE));
    }

    @Override
    public int powerDraw(List<Integer> v) {
        return derive(v).powerDraw();
    }

    @Override
    public String nameKey(List<Integer> v) {
        return "item.minepc.hdd";
    }

    @Override
    public void describe(List<Integer> v, List<Component> lines) {
        HddData d = derive(v);
        lines.add(paramLine(v, FORM));
        lines.add(line("tooltip.minepc.capacity", Units.size(d.capacityMb())));
        lines.add(line("tooltip.minepc.rpm", Component.translatable("unit.minepc.rpm", d.rpm())));
        lines.add(line("tooltip.minepc.cache", Units.size(d.cacheMb())));
        lines.add(line("tooltip.minepc.speed", Units.speed(d.speedMbs())));
        lines.add(perfLine("tooltip.minepc.perf_random", Units.iops(d.iops())));
        lines.add(perfLine("tooltip.minepc.perf_boot", Units.seconds(Perf.bootSeconds(d))));
    }

    @Override
    public int visual(List<Integer> v, int dim) {
        return switch (dim) {
            case 0 -> idx(v, FORM);
            case 1 -> idx(v, LID);
            default -> 0;
        };
    }

    @Override
    public int tint(List<Integer> v, int layer) {
        return switch (layer) {
            case 1 -> color(v, COLOR);
            case 2 -> color(v, LABEL);
            default -> 0xFFFFFF;
        };
    }

    @Override
    protected void baseCost(List<Integer> v, Cost cost) {
        cost.add(Items.IRON_INGOT, 2 + idx(v, FORM)).add(Items.REDSTONE, 2).add(Items.COPPER_INGOT, 1)
                .add(Items.PAPER, 1);
    }
}
