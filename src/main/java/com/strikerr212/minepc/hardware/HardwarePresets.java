package com.strikerr212.minepc.hardware;

import com.strikerr212.minepc.hardware.spec.PartDesign;
import com.strikerr212.minepc.hardware.spec.PartSpec;
import com.strikerr212.minepc.hardware.spec.Specs;
import com.strikerr212.minepc.registry.ModDataComponents;
import com.strikerr212.minepc.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.List;

/**
 * Заводские детали пяти уровней и примеры "самосборных".
 *
 * Каждый пресет — это просто номера вариантов для параметров спецификации,
 * в том же порядке, что и в ней (подсказки по порядку — в комментариях).
 */
public final class HardwarePresets {

    public static final int MAX_TIER = 5;

    private HardwarePresets() {
    }

    // сокет, чипсет, слоты ОЗУ, каналы, частота ОЗУ, M.2, SATA | вид, текстолит, цвет, подсветка, цвет подсветки
    private static final int[][] MOTHERBOARD = {
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 1, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0},
            {1, 2, 1, 1, 2, 2, 1, 2, 2, 2, 0, 0},
            {1, 3, 1, 1, 3, 2, 2, 3, 2, 3, 1, 1},
            {2, 4, 1, 2, 6, 2, 2, 4, 5, 0, 1, 6},
    };
    // сокет, ядра, частота, техпроцесс, кэш | крышка, размер крышки, цвет крышки, подложка, логотип
    private static final int[][] CPU = {
            {0, 0, 0, 0, 0, 0, 1, 0, 0, 0},
            {0, 1, 3, 2, 2, 1, 1, 0, 0, 0},
            {1, 4, 5, 4, 4, 2, 1, 0, 0, 0},
            {1, 6, 7, 6, 6, 3, 1, 0, 2, 0},
            {2, 8, 10, 9, 7, 4, 2, 1, 3, 3},
    };
    // тип, трубки, вентиляторы, радиатор СВО, цвет, подсветка, цвет подсветки
    private static final int[][] COOLER = {
            {0, 0, 0, 0, 2, 0, 0},
            {1, 2, 0, 0, 0, 0, 0},
            {2, 4, 0, 0, 0, 0, 0},
            {3, 5, 1, 0, 2, 1, 0},
            {4, 0, 0, 2, 2, 1, 6},
    };
    // чипов, объём чипа, частота, радиатор, текстолит, цвет радиатора, подсветка, цвет подсветки
    private static final int[][] RAM = {
            {0, 0, 0, 0, 0, 0, 0, 0},
            {1, 1, 1, 1, 2, 0, 0, 0},
            {1, 2, 3, 2, 2, 2, 0, 0},
            {1, 3, 5, 2, 2, 2, 1, 6},
            {2, 4, 6, 3, 2, 0, 1, 8},
    };
    // чип, частота, чипов памяти, объём чипа, тип памяти, длина, текстолит | охлаждение, толщина, цвет, подсветка, цвет
    private static final int[][] GPU = {
            {0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0},
            {1, 2, 1, 1, 1, 1, 2, 2, 0, 2, 0, 0},
            {2, 4, 2, 2, 1, 1, 2, 3, 1, 1, 0, 0},
            {3, 5, 3, 3, 2, 2, 2, 4, 2, 3, 1, 0},
            {4, 6, 4, 4, 3, 2, 2, 4, 2, 0, 1, 4},
    };
    // мощность, сертификат, модульность, боковина, цвет, цвет подсветки
    private static final int[][] PSU = {
            {0, 0, 0, 0, 0, 0},
            {1, 1, 0, 0, 2, 0},
            {3, 2, 1, 1, 2, 0},
            {5, 3, 2, 0, 3, 0},
            {7, 4, 2, 2, 3, 9},
    };
    // форм-фактор, объём, обороты, кэш, крышка, цвет крышки, цвет наклейки
    private static final int[][] HDD = {
            {0, 1, 0, 0, 0, 0, 0},
            {1, 3, 1, 2, 0, 0, 0},
            {1, 5, 2, 3, 0, 2, 0},
            {1, 6, 2, 5, 1, 0, 0},
            {1, 7, 3, 6, 2, 0, 6},
    };
    // интерфейс, объём, тип памяти, корпус/охлаждение, цвет, акцент, текстолит
    private static final int[][] SSD = {
            {0, 1, 1, 0, 2, 0, 2},
            {0, 3, 2, 1, 0, 1, 2},
            {1, 4, 1, 0, 0, 0, 2},
            {2, 6, 2, 1, 2, 0, 2},
            {3, 7, 3, 2, 2, 1, 2},
    };

    public static ItemStack motherboard(int tier) { return stock(HardwareType.MOTHERBOARD, MOTHERBOARD, tier); }
    public static ItemStack cpu(int tier) { return stock(HardwareType.CPU, CPU, tier); }
    public static ItemStack cooler(int tier) { return stock(HardwareType.COOLER, COOLER, tier); }
    public static ItemStack ram(int tier) { return stock(HardwareType.RAM, RAM, tier); }
    public static ItemStack gpu(int tier) { return stock(HardwareType.GPU, GPU, tier); }
    public static ItemStack psu(int tier) { return stock(HardwareType.PSU, PSU, tier); }
    public static ItemStack hdd(int tier) { return stock(HardwareType.HDD, HDD, tier); }
    public static ItemStack ssd(int tier) { return stock(HardwareType.SSD, SSD, tier); }

    /** Примеры деталей "собранных игроком". */
    public static List<ItemStack> customExamples() {
        return List.of(
                build(HardwareType.CPU, "MINECORE X", 2, 7, 9, 8, 6, 4, 2, 4, 6, 1),
                build(HardwareType.MOTHERBOARD, "", 1, 3, 1, 1, 4, 2, 2, 3, 3, 3, 1, 1),
                build(HardwareType.RAM, "", 2, 3, 5, 3, 3, 6, 1, 9),
                build(HardwareType.GPU, "", 3, 5, 3, 3, 2, 2, 4, 0, 0, 0, 0, 0),           // голая плата
                build(HardwareType.GPU, "", 4, 6, 4, 4, 3, 2, 5, 4, 2, 0, 1, 9),
                build(HardwareType.COOLER, "", 4, 0, 0, 1, 0, 1, 1)
        );
    }

    // ============ Вспомогательное ============

    private static ItemStack stock(HardwareType type, int[][] table, int tier) {
        int i = Math.max(1, Math.min(MAX_TIER, tier)) - 1;
        return build(type, "", table[i]);
    }

    /** Собрать предмет по номерам вариантов. Используется и сборочным столом. */
    public static ItemStack build(HardwareType type, String label, int... values) {
        List<Integer> list = new ArrayList<>();
        for (int v : values) list.add(v);
        return build(type, label, list);
    }

    public static ItemStack build(HardwareType type, String label, List<Integer> values) {
        PartSpec spec = Specs.of(type);
        List<Integer> clean = spec.sanitize(values);
        ItemStack stack = new ItemStack(ModItems.forType(type).get());
        stack.set(ModDataComponents.DESIGN.get(), new PartDesign(clean, label));
        stack.set(DataComponents.RARITY, rarityFor(spec.tier(clean)));   // цвет названия
        return stack;
    }

    public static Rarity rarityFor(int tier) {
        return switch (tier) {
            case 3 -> Rarity.UNCOMMON;  // жёлтый
            case 4 -> Rarity.RARE;      // голубой
            case 5 -> Rarity.EPIC;      // фиолетовый
            default -> Rarity.COMMON;   // белый
        };
    }
}
