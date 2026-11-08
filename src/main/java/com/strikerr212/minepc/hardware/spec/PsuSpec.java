package com.strikerr212.minepc.hardware.spec;

import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.Perf;
import com.strikerr212.minepc.hardware.PsuData;
import com.strikerr212.minepc.hardware.Units;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.List;

import static com.strikerr212.minepc.hardware.spec.Param.Unit.*;

/** Блок питания: мощность, сертификат, модульность, боковая панель, цвета. */
public class PsuSpec extends PartSpec {

    public static final int WATTAGE = 0, CERT = 1, MODULAR = 2, SIDE = 3, COLOR = 4, LED_COLOR = 5;
    public static final int SIDE_PLAIN = 0, SIDE_MESH = 1, SIDE_LED = 2;

    public PsuSpec() {
        super(HardwareType.PSU, List.of(
                Param.number("psu.wattage", 0, WATTS, new int[]{300, 450, 550, 650, 750, 850, 1000, 1200, 1600},
                        new int[]{1, 1, 2, 2, 3, 3, 4, 4, 5}),
                Param.option("psu.cert", 0, 1, 2, 3, 4, 5),
                Param.option("psu.modular", 0, 1, 2, 3),
                Param.option("psu.side", 0, 1, 1, 2),
                Param.metal("psu.color", 0),
                Param.led("psu.led_color", 0)
        ), 0, 0, 0, 0, 2, 0);
    }

    public PsuData derive(List<Integer> v) {
        return new PsuData(tier(v), val(v, WATTAGE), idx(v, CERT));
    }

    @Override
    public boolean isActive(int param, List<Integer> v) {
        return param != LED_COLOR || idx(v, SIDE) == SIDE_LED;
    }

    /** Блок питания — источник энергии, а не потребитель. */
    @Override
    public int powerDraw(List<Integer> v) {
        return 0;
    }

    @Override
    public String nameKey(List<Integer> v) {
        return "item.minepc.psu";
    }

    @Override
    public void describe(List<Integer> v, List<Component> lines) {
        PsuData d = derive(v);
        lines.add(line("tooltip.minepc.wattage", Units.watts(d.wattage())));
        lines.add(line("tooltip.minepc.certificate", Component.translatable(d.certificateKey())));
        lines.add(line("tooltip.minepc.efficiency", d.efficiencyPercent() + "%"));
        lines.add(paramLine(v, MODULAR));
        int wall = Perf.wallWatts(d.wattage(), d.efficiencyPercent());
        lines.add(perfLine("tooltip.minepc.perf_wall", Units.watts(wall), Units.watts(wall - d.wattage())));
    }

    @Override
    public int visual(List<Integer> v, int dim) {
        return switch (dim) {
            case 0 -> idx(v, SIDE);
            case 1 -> idx(v, MODULAR);
            default -> 0;
        };
    }

    @Override
    public int tint(List<Integer> v, int layer) {
        return switch (layer) {
            case 1 -> color(v, COLOR);
            case 2 -> ledColor(idx(v, SIDE) == SIDE_LED, color(v, LED_COLOR));
            default -> 0xFFFFFF;
        };
    }

    @Override
    protected boolean hasLed(List<Integer> v) {
        return idx(v, SIDE) == SIDE_LED;
    }

    @Override
    protected void baseCost(List<Integer> v, Cost cost) {
        cost.add(Items.IRON_INGOT, 4).add(Items.COPPER_INGOT, 3 + idx(v, WATTAGE)).add(Items.REDSTONE, 4);
    }
}
