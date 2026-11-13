package com.strikerr212.minepc.lan;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

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

    /** Номер бирки для проводов из этого мотка (только на сервере; 0 — без бирки). */
    public static int id(Level level, ItemStack stack) {
        if (!(level instanceof ServerLevel server)) return 0;
        String name = of(stack);
        return name.isEmpty() ? 0 : LanData.get(server).labelId(name);
    }

    /** Имя бирки по номеру (только на сервере). */
    public static String name(Level level, int id) {
        if (id == 0 || !(level instanceof ServerLevel server)) return "";
        return LanData.get(server).labelName(id);
    }

    /** Предмет с именем бирки (провод вернулся в инвентарь — имя мотка сохраняется). */
    public static ItemStack named(ItemStack stack, String name) {
        if (name != null && !name.isEmpty()) stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    /** Что выпадет из снятого корда: моток (воздушная линия) или патч-корд — с биркой. */
    public static ItemStack cordDrop(Level level, LanData.Cord c) {
        ItemStack s = c.length() > 4
                ? new ItemStack(com.strikerr212.minepc.registry.ModItems.CABLE.get(), c.length())
                : new ItemStack(com.strikerr212.minepc.registry.ModItems.PATCH_CORD.get());
        return named(s, name(level, c.label()));
    }
}
