package com.strikerr212.minepc.lan;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Бирки на кабелях: моток или патч-корд, переименованный на наковальне, кладёт провода с этим именем.
 * Имя видно у вилок (белая бирка), в подсказке порта, в окне коммутатора и в программе switch.
 */
public final class CableLabels {

    private CableLabels() {
    }

    /** Имя предмета, если его переименовали ("" — нет). */
    public static String of(ItemStack stack) {
        Component c = stack.get(DataComponents.CUSTOM_NAME);
        if (c == null) return "";
        String s = c.getString().strip();
        return s.length() > LanData.MAX_LABEL ? s.substring(0, LanData.MAX_LABEL) : s;
    }

    /** Предмет с именем бирки (провод вернулся в инвентарь — имя мотка сохраняется). */
    public static ItemStack named(ItemStack stack, String name) {
        if (name != null && !name.isEmpty()) stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }
}
