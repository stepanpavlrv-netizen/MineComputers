package com.strikerr212.minepc.hardware.spec;

import com.strikerr212.minepc.hardware.HardwareType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Все спецификации в одном месте. */
public final class Specs {

    public static final CpuSpec CPU = new CpuSpec();
    public static final CoolerSpec COOLER = new CoolerSpec();
    public static final MotherboardSpec MOTHERBOARD = new MotherboardSpec();
    public static final RamSpec RAM = new RamSpec();
    public static final GpuSpec GPU = new GpuSpec();
    public static final PsuSpec PSU = new PsuSpec();
    public static final HddSpec HDD = new HddSpec();
    public static final SsdSpec SSD = new SsdSpec();

    /** Порядок вкладок в сборочном столе. */
    public static final List<PartSpec> ALL = List.of(MOTHERBOARD, CPU, COOLER, RAM, GPU, PSU, HDD, SSD);

    private static final Map<HardwareType, PartSpec> BY_TYPE = new EnumMap<>(HardwareType.class);

    static {
        for (PartSpec spec : ALL) {
            BY_TYPE.put(spec.type(), spec);
        }
    }

    /** Самое большое число параметров у одной детали (нужно меню сборочного стола). */
    public static final int MAX_PARAMS = ALL.stream().mapToInt(s -> s.params().size()).max().orElse(0);

    private Specs() {
    }

    public static PartSpec of(HardwareType type) {
        return BY_TYPE.get(type);
    }
}
