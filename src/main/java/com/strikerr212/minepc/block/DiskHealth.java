package com.strikerr212.minepc.block;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Состояние накопителя (хранится в самом предмете): износ, отказ и членство в массиве RAID.
 *
 * @param wear   износ 0..1: от 0,9 диск «просит замены» (жёлтая лампа), на 1 — отказывает
 * @param failed диск отказал: не читается, данные на нём недоступны
 * @param raid   номер массива RAID, в который входит диск ("" — сам по себе)
 * @param member номер диска в массиве
 */
public record DiskHealth(double wear, boolean failed, String raid, int member) {

    public static final DiskHealth NEW = new DiskHealth(0, false, "", -1);
    public static final double WARN = 0.9;
    private static final String KEY = "minepc_disk";

    public static DiskHealth of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return NEW;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return NEW;
        CompoundTag root = data.copyTag();
        if (!root.contains(KEY)) return NEW;
        CompoundTag t = root.getCompound(KEY);
        return new DiskHealth(t.getDouble("wear"), t.getBoolean("failed"), t.getString("raid"), t.contains("member") ? t.getInt("member") : -1);
    }

    public void writeTo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        CompoundTag t = new CompoundTag();
        t.putDouble("wear", wear);
        if (failed) t.putBoolean("failed", true);
        if (!raid.isEmpty()) {
            t.putString("raid", raid);
            t.putInt("member", member);
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(KEY, t));
    }

    /** Диск просит замены: износ за 90%, но ещё работает. */
    public boolean warning() {
        return !failed && wear >= WARN;
    }

    public int percent() {
        return (int) Math.min(100, Math.round(wear * 100));
    }

    public DiskHealth withWear(double w) {
        return new DiskHealth(Math.min(1, w), failed || w >= 1, raid, member);
    }

    public DiskHealth withRaid(String id, int index) {
        return new DiskHealth(wear, failed, id, index);
    }

    public DiskHealth withoutRaid() {
        return new DiskHealth(wear, failed, "", -1);
    }
}
