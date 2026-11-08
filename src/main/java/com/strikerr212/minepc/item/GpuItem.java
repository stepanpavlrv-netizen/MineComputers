package com.strikerr212.minepc.item;

import com.strikerr212.minepc.hardware.GpuData;
import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.spec.Specs;
import net.minecraft.world.item.ItemStack;

public class GpuItem extends HardwareItem {

    public GpuItem(Properties properties) {
        super(properties);
    }

    @Override
    public HardwareType getHardwareType() {
        return HardwareType.GPU;
    }

    /** Вычисленные характеристики конкретного предмета. */
    public static GpuData data(ItemStack stack) {
        return Specs.GPU.derive(values(stack, Specs.GPU));
    }
}
