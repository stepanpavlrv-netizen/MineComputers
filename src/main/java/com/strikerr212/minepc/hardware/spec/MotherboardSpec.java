package com.strikerr212.minepc.hardware.spec;

import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.MotherboardData;
import com.strikerr212.minepc.hardware.Perf;
import com.strikerr212.minepc.hardware.Units;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.List;

import static com.strikerr212.minepc.hardware.spec.Param.Unit.*;

/**
 * Материнская плата.
 * Страница 0 «Возможности»: сокет, чипсет, слоты и каналы памяти, частота памяти, M.2, SATA.
 * Страница 1 «Внешний вид»: оформление, цвет текстолита, цвет радиаторов, подсветка.
 */
public class MotherboardSpec extends PartSpec {

    public static final int SOCKET = 0, CHIPSET = 1, RAM_SLOTS = 2, CHANNELS = 3, RAM_SPEED = 4, M2 = 5, SATA = 6,
            STYLE = 7, PCB = 8, COLOR = 9, LED_ON = 10, LED_COLOR = 11;

    public MotherboardSpec() {
        super(HardwareType.MOTHERBOARD, List.of(
                Param.option("mobo.socket", 0, 1, 1, 1),
                Param.option("mobo.chipset", 0, 1, 2, 3, 4, 5),
                Param.number("mobo.ram_slots", 0, NONE, new int[]{2, 4}, new int[]{1, 2}),
                Param.number("mobo.channels", 0, NONE, new int[]{1, 2, 4}, new int[]{1, 2, 4}),
                Param.number("mobo.ram_speed", 0, RAM, new int[]{1600, 2400, 3200, 4400, 5600, 6400, 8000},
                        new int[]{1, 1, 2, 3, 4, 4, 5}),
                Param.number("mobo.m2", 0, NONE, new int[]{0, 1, 2}, new int[]{1, 2, 3}),
                Param.number("mobo.sata", 0, NONE, new int[]{2, 4, 6}, new int[]{1, 1, 2}),
                Param.option("mobo.style", 1, 1, 1, 2, 3, 4),
                Param.pcb("mobo.pcb", 1),
                Param.metal("mobo.color", 1),
                Param.toggle("mobo.led", 1),
                Param.led("mobo.led_color", 1)
        ), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public MotherboardData derive(List<Integer> v) {
        int slots = val(v, RAM_SLOTS);
        int channels = Math.min(val(v, CHANNELS), slots);   // каналов не больше, чем слотов
        int chipset = idx(v, CHIPSET);
        return new MotherboardData(tier(v), val(v, SOCKET), chipset, chipset + 1, slots, channels,
                val(v, RAM_SPEED), val(v, M2), val(v, SATA), 10 + (chipset + 1) * 5);
    }

    private boolean isLedOn(List<Integer> v) {
        return idx(v, LED_ON) == 1 && idx(v, STYLE) >= 3;
    }

    @Override
    public boolean isActive(int param, List<Integer> v) {
        return switch (param) {
            case LED_ON -> idx(v, STYLE) >= 3;            // подсветка есть только у игровых и премиальных
            case LED_COLOR -> isLedOn(v);
            case COLOR -> idx(v, STYLE) >= 1;             // у бюджетной нет радиаторов
            default -> true;
        };
    }

    @Override
    public int powerDraw(List<Integer> v) {
        return derive(v).powerDraw();
    }

    @Override
    public String nameKey(List<Integer> v) {
        return "item.minepc.motherboard";
    }

    @Override
    public void describe(List<Integer> v, List<Component> lines) {
        MotherboardData d = derive(v);
        lines.add(paramLine(v, SOCKET));
        lines.add(paramLine(v, CHIPSET));
        lines.add(line("tooltip.minepc.cpu_support", d.maxCpuTier()));
        lines.add(line("tooltip.minepc.ram_slots", d.ramSlots()));
        lines.add(line("tooltip.minepc.channels", d.channels()));
        lines.add(line("tooltip.minepc.max_ram_speed", Units.gameBandwidth(Perf.ramChannelMbs(d.maxRamMhz()))));
        lines.add(line("tooltip.minepc.m2_slots", d.m2Slots()));
        lines.add(line("tooltip.minepc.sata_ports", d.sataPorts()));
        lines.add(perfLine("tooltip.minepc.perf_board_bw", Units.gameBandwidth(Perf.ramChannelMbs(d.maxRamMhz()) * d.channels())));
    }

    @Override
    public int visual(List<Integer> v, int dim) {
        return switch (dim) {
            case 0 -> idx(v, STYLE);
            case 1 -> idx(v, RAM_SLOTS);
            case 2 -> idx(v, M2);
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
        cost.add(Items.COPPER_INGOT, 4).add(Items.REDSTONE, 6).add(Items.IRON_INGOT, 1)
                .add(Items.QUARTZ, 1 + idx(v, CHIPSET)).add(Items.GREEN_DYE, 1);
        if (idx(v, STYLE) >= 1) cost.add(Items.IRON_INGOT, idx(v, STYLE));
    }
}
