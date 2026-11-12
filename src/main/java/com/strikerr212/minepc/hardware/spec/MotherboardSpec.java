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
            STYLE = 7, PCB = 8, COLOR = 9, LED_ON = 10, LED_COLOR = 11,
            FORM = 12, SRV_SLOTS = 13, SRV_CHANNELS = 14;
    /** Вид модели серверной платы (обычные — 0..4 по оформлению). */
    public static final int SERVER_LOOK = 5;
    /** Серверная плата потребляет больше: второй сокет, много каналов, BMC и сетевые на 10 Гбит. */
    private static final int SERVER_EXTRA_W = 25;

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
                Param.led("mobo.led_color", 1),
                Param.option("mobo.form", 0, 1, 3),
                Param.number("mobo.srv_slots", 0, NONE, new int[]{8, 12, 16}, new int[]{3, 4, 5}),
                Param.number("mobo.srv_channels", 0, NONE, new int[]{4, 6, 8}, new int[]{3, 4, 5})
        ), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public static boolean isServer(List<Integer> v) {
        return v.size() > FORM && v.get(FORM) == 1;
    }

    public MotherboardData derive(List<Integer> v) {
        boolean server = isServer(v);
        int slots = val(v, server ? SRV_SLOTS : RAM_SLOTS);
        int channels = Math.min(val(v, server ? SRV_CHANNELS : CHANNELS), slots);   // каналов не больше, чем слотов
        int chipset = idx(v, CHIPSET);
        // Серверная плата держит любые серверные процессоры; чипсет у неё влияет на уровень и потребление
        return new MotherboardData(tier(v), server ? MotherboardData.SOCKET_SERVER : val(v, SOCKET), chipset, server ? 5 : chipset + 1,
                slots, channels, val(v, RAM_SPEED), val(v, M2), val(v, SATA) + (server ? 2 : 0),
                10 + (chipset + 1) * 5 + (server ? SERVER_EXTRA_W : 0), server ? 2 : 1, server);
    }

    @Override
    public boolean isShown(int param, List<Integer> v) {
        boolean server = isServer(v);
        return switch (param) {
            case SOCKET, RAM_SLOTS, CHANNELS, STYLE, LED_ON, LED_COLOR -> !server;   // у серверной свой вид, без подсветки
            case SRV_SLOTS, SRV_CHANNELS -> server;
            default -> true;
        };
    }

    @Override
    public int[] order() {
        return new int[]{FORM, SOCKET, CHIPSET, RAM_SLOTS, SRV_SLOTS, CHANNELS, SRV_CHANNELS, RAM_SPEED, M2, SATA,
                STYLE, PCB, COLOR, LED_ON, LED_COLOR};
    }

    private boolean isLedOn(List<Integer> v) {
        return !isServer(v) && idx(v, LED_ON) == 1 && idx(v, STYLE) >= 3;
    }

    @Override
    public boolean isActive(int param, List<Integer> v) {
        return switch (param) {
            case LED_ON -> idx(v, STYLE) >= 3;            // подсветка есть только у игровых и премиальных
            case LED_COLOR -> isLedOn(v);
            case COLOR -> isServer(v) || idx(v, STYLE) >= 1;   // у бюджетной нет радиаторов, у серверной — радиаторы питания
            default -> true;
        };
    }

    @Override
    public int powerDraw(List<Integer> v) {
        return derive(v).powerDraw();
    }

    @Override
    public String nameKey(List<Integer> v) {
        return isServer(v) ? "item.minepc.motherboard_server" : "item.minepc.motherboard";
    }

    @Override
    public void describe(List<Integer> v, List<Component> lines) {
        MotherboardData d = derive(v);
        if (d.server()) {
            lines.add(paramLine(v, FORM));
            lines.add(line("tooltip.minepc.socket_server", d.cpuSockets()));
            lines.add(line("tooltip.minepc.server_board", "ECC"));
            lines.add(line("tooltip.minepc.server_net", "2 × 10"));
        } else {
            lines.add(paramLine(v, SOCKET));
        }
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
            case 0 -> isServer(v) ? SERVER_LOOK : idx(v, STYLE);
            case 1 -> idx(v, isServer(v) ? SRV_SLOTS : RAM_SLOTS);   // серверная: 8, 12 или 16 слотов на модели
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
        if (!isServer(v) && idx(v, STYLE) >= 1) cost.add(Items.IRON_INGOT, idx(v, STYLE));
        if (isServer(v)) cost.add(Items.COPPER_INGOT, 4).add(Items.REDSTONE, 6).add(Items.QUARTZ, 2);
    }
}
