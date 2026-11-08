package com.strikerr212.minepc.item;

import com.strikerr212.minepc.hardware.RamData;
import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.spec.Specs;
import net.minecraft.world.item.ItemStack;

public class RamItem extends HardwareItem {

    public RamItem(Properties properties) {
        super(properties);
    }

    @Override
    public HardwareType getHardwareType() {
        return HardwareType.RAM;
    }

    /** Вычисленные характеристики конкретного предмета. */
    public static RamData data(ItemStack stack) {
        return Specs.RAM.derive(values(stack, Specs.RAM));
    }
}
