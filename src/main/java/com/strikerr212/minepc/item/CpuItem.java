package com.strikerr212.minepc.item;

import com.strikerr212.minepc.hardware.CpuData;
import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.spec.Specs;
import net.minecraft.world.item.ItemStack;

public class CpuItem extends HardwareItem {

    public CpuItem(Properties properties) {
        super(properties);
    }

    @Override
    public HardwareType getHardwareType() {
        return HardwareType.CPU;
    }

    /** Вычисленные характеристики конкретного предмета. */
    public static CpuData data(ItemStack stack) {
        return Specs.CPU.derive(values(stack, Specs.CPU));
    }
}
