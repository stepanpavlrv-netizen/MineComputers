package com.strikerr212.minepc.item;

import com.strikerr212.minepc.hardware.PsuData;
import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.spec.Specs;
import net.minecraft.world.item.ItemStack;

public class PsuItem extends HardwareItem {

    public PsuItem(Properties properties) {
        super(properties);
    }

    @Override
    public HardwareType getHardwareType() {
        return HardwareType.PSU;
    }

    /** Вычисленные характеристики конкретного предмета. */
    public static PsuData data(ItemStack stack) {
        return Specs.PSU.derive(values(stack, Specs.PSU));
    }
}
