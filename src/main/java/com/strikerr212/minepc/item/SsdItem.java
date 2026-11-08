package com.strikerr212.minepc.item;

import com.strikerr212.minepc.hardware.SsdData;
import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.spec.Specs;
import net.minecraft.world.item.ItemStack;

public class SsdItem extends HardwareItem {

    public SsdItem(Properties properties) {
        super(properties);
    }

    @Override
    public HardwareType getHardwareType() {
        return HardwareType.SSD;
    }

    /** Вычисленные характеристики конкретного предмета. */
    public static SsdData data(ItemStack stack) {
        return Specs.SSD.derive(values(stack, Specs.SSD));
    }
}
