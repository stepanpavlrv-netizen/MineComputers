package com.strikerr212.minepc.hardware.spec;

import com.strikerr212.minepc.hardware.CoolerData;
import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.Units;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.List;

import static com.strikerr212.minepc.hardware.CoolerData.*;
import static com.strikerr212.minepc.hardware.spec.Param.Unit.*;

/** Процессорное охлаждение: тип, тепловые трубки, вентиляторы, радиатор СВО, цвета, подсветка. */
public class CoolerSpec extends PartSpec {

    public static final int TYPE = 0, PIPES = 1, FANS = 2, RADIATOR = 3, COLOR = 4, LED_ON = 5, LED_COLOR = 6;

    public CoolerSpec() {
        super(HardwareType.COOLER, List.of(
                Param.option("cooler.type", 0, 1, 1, 2, 3, 4),
                Param.number("cooler.pipes", 0, NONE, new int[]{2, 3, 4, 5, 6, 8}, new int[]{1, 1, 2, 2, 3, 4}),
                Param.option("cooler.fans", 0, 1, 2),
                Param.number("cooler.radiator", 0, MM, new int[]{120, 240, 360}, new int[]{3, 4, 5}),
                Param.metal("cooler.color", 0),
                Param.toggle("cooler.led", 0),
                Param.led("cooler.led_color", 0)
        ), 2, 2, 0, 1, 0, 0, 0);
    }

    public CoolerData derive(List<Integer> v) {
        int type = idx(v, TYPE), pipes = val(v, PIPES);
        boolean twoFans = idx(v, FANS) == 1;
        int cooling = switch (type) {
            case BOX -> 50;
            case LOW_PROFILE -> 50 + pipes * 10;
            case TOWER -> 60 + pipes * 20;
            case DUAL_TOWER -> 80 + pipes * 25;
            default -> 50 + val(v, RADIATOR) * 11 / 12;   // 120 → 160, 240 → 270, 360 → 380
        };
        if (twoFans && (type == TOWER || type == DUAL_TOWER)) cooling = cooling * 115 / 100;
        int fans = switch (type) {
            case BOX, LOW_PROFILE -> 1;
            case TOWER, DUAL_TOWER -> twoFans ? 2 : 1;
            default -> val(v, RADIATOR) / 120;
        };
        int power = fans * 2 + (type == AIO ? 4 : 0) + (isLedOn(v) ? 2 : 0);
        return new CoolerData(tier(v), type, cooling, power);
    }

    private boolean isLedOn(List<Integer> v) {
        return idx(v, LED_ON) == 1;
    }

    @Override
    public boolean isActive(int param, List<Integer> v) {
        int type = idx(v, TYPE);
        return switch (param) {
            case PIPES -> type == LOW_PROFILE || type == TOWER || type == DUAL_TOWER;
            case FANS -> type == TOWER || type == DUAL_TOWER;
            case RADIATOR -> type == AIO;
            case LED_COLOR -> isLedOn(v);
            default -> true;
        };
    }

    @Override
    public int powerDraw(List<Integer> v) {
        return derive(v).powerDraw();
    }

    /** Кулер почти не греется сам — он отводит тепло. */
    @Override
    public int heat(List<Integer> v) {
        return 0;
    }

    @Override
    public String nameKey(List<Integer> v) {
        return "item.minepc.cooler." + new String[]{"box", "low", "tower", "dual", "aio"}[idx(v, TYPE)];
    }

    @Override
    public void describe(List<Integer> v, List<Component> lines) {
        CoolerData d = derive(v);
        if (isActive(PIPES, v)) lines.add(paramLine(v, PIPES));
        if (isActive(FANS, v)) lines.add(paramLine(v, FANS));
        if (isActive(RADIATOR, v)) lines.add(paramLine(v, RADIATOR));
        lines.add(line("tooltip.minepc.cooling", Units.watts(d.coolingW())));
        lines.add(perfLine("tooltip.minepc.perf_cooler", Units.watts(maxTdp(d.coolingW(), 1.1)), Units.watts(maxTdp(d.coolingW(), 1.0))));
    }

    /** Самый горячий процессор (TDP, Вт), который под полной нагрузкой держит частоту не ниже {@code factor} от базовой (буст +10%). */
    public static int maxTdp(int coolingW, double factor) {
        int best = 0;
        for (int tdp = 1; tdp <= 2000; tdp++) {
            if (com.strikerr212.minepc.computer.CpuClock.steady(tdp, coolingW, 0.10, 1.0) >= factor - 1e-3) best = tdp;
            else break;
        }
        return best;
    }

    @Override
    public int visual(List<Integer> v, int dim) {
        int type = idx(v, TYPE);
        return switch (dim) {
            case 0 -> type;
            case 1 -> (type == TOWER || type == DUAL_TOWER) ? idx(v, FANS) : 0;
            case 2 -> type == AIO ? idx(v, RADIATOR) : 0;
            default -> 0;
        };
    }

    @Override
    public int tint(List<Integer> v, int layer) {
        return switch (layer) {
            case 1 -> color(v, COLOR);
            case 2 -> ledColor(isLedOn(v), color(v, LED_COLOR));
            default -> 0xFFFFFF;
        };
    }

    @Override
    protected boolean hasLed(List<Integer> v) {
        return isLedOn(v);
    }

    @Override
    protected void baseCost(List<Integer> v, Cost cost) {
        int type = idx(v, TYPE);
        cost.add(Items.IRON_INGOT, 2 + type);
        if (type == AIO) {
            cost.add(Items.COPPER_INGOT, 4).add(Items.SLIME_BALL, 1).add(Items.DRIED_KELP, 4);   // герметик и шланги
        } else if (type != BOX) {
            cost.add(Items.COPPER_INGOT, val(v, PIPES));
        }
        cost.add(Items.REDSTONE, 1);
    }
}
