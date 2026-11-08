package com.strikerr212.minepc.item;

import com.strikerr212.minepc.hardware.HddData;
import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.spec.Specs;
import net.minecraft.world.item.ItemStack;

public class HddItem extends HardwareItem {

    public HddItem(Properties properties) {
        super(properties);
    }

    @Override
    public HardwareType getHardwareType() {
        return HardwareType.HDD;
    }

    /** Вычисленные характеристики конкретного предмета. */
    public static HddData data(ItemStack stack) {
        return Specs.HDD.derive(values(stack, Specs.HDD));
    }
}
