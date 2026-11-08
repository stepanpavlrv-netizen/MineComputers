package com.strikerr212.minepc.item;

import com.strikerr212.minepc.hardware.MotherboardData;
import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.spec.Specs;
import net.minecraft.world.item.ItemStack;

public class MotherboardItem extends HardwareItem {

    public MotherboardItem(Properties properties) {
        super(properties);
    }

    @Override
    public HardwareType getHardwareType() {
        return HardwareType.MOTHERBOARD;
    }

    /** Вычисленные характеристики конкретного предмета. */
    public static MotherboardData data(ItemStack stack) {
        return Specs.MOTHERBOARD.derive(values(stack, Specs.MOTHERBOARD));
    }
}
