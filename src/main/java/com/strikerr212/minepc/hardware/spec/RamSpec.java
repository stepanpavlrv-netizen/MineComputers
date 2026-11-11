package com.strikerr212.minepc.hardware.spec;

import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.Perf;
import com.strikerr212.minepc.hardware.RamData;
import com.strikerr212.minepc.hardware.Units;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.List;

import static com.strikerr212.minepc.hardware.spec.Param.Unit.*;

/** Модуль памяти: чипы, их объём, частота, цвет платы, радиатор и его цвет, подсветка. */
public class RamSpec extends PartSpec {

    public static final int CHIPS = 0, CHIP_MB = 1, SPEED = 2, HEATSINK = 3, PCB = 4, COLOR = 5, LED_ON = 6, LED_COLOR = 7;

    public RamSpec() {
        super(HardwareType.RAM, List.of(
                Param.number("ram.chips", 0, NONE, new int[]{4, 8, 16}, new int[]{1, 2, 3}),
                Param.number("ram.chip_mb", 0, KB, new int[]{64, 128, 256, 512, 1024, 2048}, new int[]{1, 1, 2, 3, 4, 5}),
                Param.number("ram.speed", 0, RAM, new int[]{800, 1600, 2400, 3200, 4000, 4800, 6000, 7200},
                        new int[]{1, 1, 2, 3, 3, 4, 5, 5}),
                Param.option("ram.heatsink", 0, 1, 1, 2, 3),
                Param.pcb("ram.pcb", 0),
                Param.metal("ram.color", 0),
                Param.toggle("ram.led", 0),
                Param.led("ram.led_color", 0)
        ), 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public RamData derive(List<Integer> v) {
        return new RamData(tier(v), val(v, CHIPS), val(v, CHIP_MB), val(v, SPEED));
    }

    private boolean hasHeatsink(List<Integer> v) {
        return idx(v, HEATSINK) > 0;
    }

    private boolean isLedOn(List<Integer> v) {
        return hasHeatsink(v) && idx(v, LED_ON) == 1;
    }

    @Override
    public boolean isActive(int param, List<Integer> v) {
        return switch (param) {
            case COLOR, LED_ON -> hasHeatsink(v);
            case LED_COLOR -> isLedOn(v);
            default -> true;
        };
    }

    @Override
    public int powerDraw(List<Integer> v) {
        return derive(v).powerDraw() + (isLedOn(v) ? 1 : 0);
    }

    @Override
    public String nameKey(List<Integer> v) {
        return "item.minepc.ram";
    }

    @Override
    public void describe(List<Integer> v, List<Component> lines) {
        RamData d = derive(v);
        lines.add(line("tooltip.minepc.capacity", Units.gameSize(d.capacityMb())));
        lines.add(line("tooltip.minepc.chips",
                Component.translatable("tooltip.minepc.chips_value", d.chips(), Units.gameSize(d.chipMb()))));
        lines.add(line("tooltip.minepc.ram_speed", Units.gameBandwidth(Perf.ramChannelMbs(d.speedMhz()))));
        lines.add(perfLine("tooltip.minepc.perf_ram_two", Units.gameBandwidth(Perf.ramChannelMbs(d.speedMhz()) * 2)));
    }

    @Override
    public int visual(List<Integer> v, int dim) {
        return switch (dim) {
            case 0 -> idx(v, HEATSINK);
            case 1 -> hasHeatsink(v) ? idx(v, LED_ON) : 0;   // планка подсветки есть только на радиаторе
            case 2 -> hasHeatsink(v) ? 0 : idx(v, CHIPS);     // чипы видны только без радиатора
            default -> 0;
        };
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
        cost.add(Items.COPPER_INGOT, 1).add(Items.REDSTONE, val(v, CHIPS) / 2).add(Items.QUARTZ, 1);
        if (hasHeatsink(v)) cost.add(Items.IRON_INGOT, idx(v, HEATSINK));
    }
}
