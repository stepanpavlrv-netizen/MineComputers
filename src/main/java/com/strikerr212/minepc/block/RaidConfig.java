package com.strikerr212.minepc.block;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;

/**
 * Массив RAID серверной платы (хранится в плате, как настройки BIOS). Сами данные массива лежат отдельно от дисков
 * (папка мира minepc/drives/raid-номер), а диски помечены, что входят в массив (см. {@link DiskHealth}).
 *
 * @param id      номер массива
 * @param level   0 — полоса (RAID 0), 1 — зеркало (RAID 1), 5 — с чётностью (RAID 5)
 * @param members сколько дисков в массиве
 */
public record RaidConfig(String id, int level, int members) {

    private static final String KEY = "minepc_raid";

    public static RaidConfig of(ItemStack board) {
        if (board == null || board.isEmpty()) return null;
        CustomData data = board.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag root = data.copyTag();
        if (!root.contains(KEY)) return null;
        CompoundTag t = root.getCompound(KEY);
        return new RaidConfig(t.getString("id"), t.getInt("level"), t.getInt("members"));
    }

    public void writeTo(ItemStack board) {
        CompoundTag t = new CompoundTag();
        t.putString("id", id);
        t.putInt("level", level);
        t.putInt("members", members);
        CustomData.update(DataComponents.CUSTOM_DATA, board, root -> root.put(KEY, t));
    }

    public static void clear(ItemStack board) {
        CustomData.update(DataComponents.CUSTOM_DATA, board, root -> root.remove(KEY));
    }

    /** Уровень по названию: stripe/0, mirror/1, raid5/5. -1 — не знаем такого. */
    public static int parseLevel(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "0", "stripe", "raid0", "полоса" -> 0;
            case "1", "mirror", "raid1", "зеркало" -> 1;
            case "5", "raid5" -> 5;
            default -> -1;
        };
    }

    public static int minMembers(int level) {
        return level == 5 ? 3 : 2;
    }

    public String levelName() {
        return switch (level) {
            case 0 -> "RAID 0 (полоса)";
            case 1 -> "RAID 1 (зеркало)";
            default -> "RAID 5 (с чётностью)";
        };
    }

    /** Сколько дисков может не хватать, чтобы массив работал. */
    public int tolerance() {
        return switch (level) {
            case 0 -> 0;
            case 1 -> members - 1;
            default -> 1;
        };
    }

    /** Объём массива по самому маленькому диску. */
    public long capacity(long minDisk) {
        return switch (level) {
            case 0 -> minDisk * members;
            case 1 -> minDisk;
            default -> minDisk * (members - 1);
        };
    }

    /** Скорость массива по самому медленному диску. */
    public long speed(long minSpeed) {
        return switch (level) {
            case 0 -> minSpeed * members;
            case 1 -> minSpeed;
            default -> minSpeed * (members - 1);
        };
    }

    public String folderId() {
        return "raid-" + id;
    }
}
