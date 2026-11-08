package com.strikerr212.minepc.item;

import com.strikerr212.minepc.hardware.CoolerData;
import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.spec.Specs;
import net.minecraft.world.item.ItemStack;

public class CoolerItem extends HardwareItem {

    public CoolerItem(Properties properties) {
        super(properties);
    }

    @Override
    public HardwareType getHardwareType() {
        return HardwareType.COOLER;
    }

    /** Вычисленные характеристики конкретного предмета. */
    public static CoolerData data(ItemStack stack) {
        return Specs.COOLER.derive(values(stack, Specs.COOLER));
    }
}
