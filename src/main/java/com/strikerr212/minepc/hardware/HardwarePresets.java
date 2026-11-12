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

    // сокет, чипсет, слоты ОЗУ, каналы, частота ОЗУ, M.2, SATA | вид, текстолит, цвет, подсветка, цвет подсветки | серверная, слоты и каналы серверной
    private static final int[][] MOTHERBOARD = {
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 1, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0},
            {1, 2, 1, 1, 2, 2, 1, 2, 2, 2, 0, 0, 0, 0, 0},
            {1, 3, 1, 1, 4, 2, 2, 3, 2, 3, 1, 1, 0, 0, 0},
            {2, 4, 1, 2, 6, 2, 2, 4, 5, 0, 1, 6, 0, 0, 0},
    };
    // сокет, ядра, частота, техпроцесс, кэш | крышка, размер крышки, цвет крышки, подложка, логотип | видеоядро, ядра серверного
    private static final int[][] CPU = {
            {0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0},
            {0, 1, 3, 2, 2, 1, 1, 0, 0, 0, 0, 0},
            {1, 4, 5, 4, 4, 2, 1, 0, 0, 0, 0, 0},
            {1, 6, 7, 6, 6, 3, 1, 0, 2, 0, 0, 0},
            {2, 8, 10, 9, 7, 4, 2, 1, 3, 3, 0, 0},
    };
    // тип, трубки, вентиляторы, радиатор СВО, цвет, подсветка, цвет подсветки
    private static final int[][] COOLER = {
            {0, 0, 0, 0, 2, 0, 0},
            {1, 2, 0, 0, 0, 0, 0},
            {2, 4, 0, 0, 0, 0, 0},
            {3, 5, 1, 0, 2, 1, 0},
            {4, 0, 0, 2, 2, 1, 6},
    };
    // чипов, объём чипа, частота, радиатор, текстолит, цвет радиатора, подсветка, цвет подсветки, ECC
    private static final int[][] RAM = {
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {1, 1, 1, 1, 2, 0, 0, 0, 0},
            {1, 2, 3, 2, 2, 2, 0, 0, 0},
            {1, 3, 5, 2, 2, 2, 1, 6, 0},
            {2, 4, 6, 3, 2, 0, 1, 8, 0},
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

    /** Варианты заводской детали уровня tier — для шаблонов сборочного стола. */
    public static List<Integer> stockValues(HardwareType type, int tier) {
        int[][] table = switch (type) {
            case MOTHERBOARD -> MOTHERBOARD;
            case CPU -> CPU;
            case COOLER -> COOLER;
            case RAM -> RAM;
            case GPU -> GPU;
            case PSU -> PSU;
            case HDD -> HDD;
            case SSD -> SSD;
        };
        List<Integer> list = new ArrayList<>();
        for (int v : table[Math.max(1, Math.min(MAX_TIER, tier)) - 1]) list.add(v);
        return Specs.of(type).sanitize(list);
    }

    /**
     * Серверный шаблон: много ядер на умеренной частоте, много памяти, большой и надёжный диск.
     * null — для этой детали серверного варианта нет.
     */
    public static List<Integer> serverValues(HardwareType type) {
        int[] v = switch (type) {
            // Сокет MS-S, 48 ядер, 2.5 МГц, 7 нм, 64 КБ кэша — много программ сразу, умеренное тепло
            case CPU -> new int[]{3, 7, 4, 7, 6, 3, 1, 1, 2, 0, 0, 3};
            // ECC: 16 чипов по 1 МБ на 3200
            case RAM -> new int[]{2, 4, 3, 1, 2, 2, 0, 0, 1};
            // 3.5", 32 МБ, 7200 об/мин, большой кэш
            case HDD -> new int[]{0, 8, 2, 5, 1, 0, 0};
            // Серверная: два сокета MS-S, 12 слотов, 6 каналов, много SATA
            case MOTHERBOARD -> new int[]{1, 3, 1, 2, 3, 1, 2, 0, 0, 1, 0, 0, 1, 1, 1};
            default -> null;
        };
        if (v == null) return null;
        List<Integer> list = new ArrayList<>();
        for (int x : v) list.add(x);
        return Specs.of(type).sanitize(list);
    }

    /** Серверная деталь (плата, процессор MS-S, память ECC, диск) — как серверный шаблон стола. */
    public static ItemStack server(HardwareType type) {
        List<Integer> v = serverValues(type);
        return v == null ? ItemStack.EMPTY : build(type, type == HardwareType.CPU ? "MINECORE S" : "", v);
    }

    /** Примеры деталей "собранных игроком". */
    public static List<ItemStack> customExamples() {
        return List.of(
                build(HardwareType.CPU, "MINECORE X", 2, 7, 9, 8, 6, 4, 2, 4, 6, 1, 0, 0),
                build(HardwareType.CPU, "MINECORE G", 1, 3, 7, 7, 4, 2, 1, 1, 0, 0, 3, 0),   // с сильным видеоядром
                build(HardwareType.MOTHERBOARD, "", 1, 3, 1, 1, 4, 2, 2, 3, 3, 3, 1, 1, 0, 0, 0),
                build(HardwareType.RAM, "", 2, 3, 5, 3, 3, 6, 1, 9, 0),
                build(HardwareType.GPU, "", 3, 5, 3, 3, 2, 2, 4, 0, 0, 0, 0, 0),           // голая плата
                build(HardwareType.GPU, "", 4, 6, 4, 4, 3, 2, 5, 4, 2, 0, 1, 9),
                build(HardwareType.COOLER, "", 4, 0, 0, 1, 0, 1, 1),
                // Серверные: плата на два MS-S, процессор MS-S, память ECC
                build(HardwareType.MOTHERBOARD, "", 1, 3, 1, 2, 3, 1, 2, 0, 0, 1, 0, 0, 1, 1, 1),
                build(HardwareType.CPU, "MINECORE S", 3, 7, 4, 7, 6, 3, 1, 1, 2, 0, 0, 3),
                build(HardwareType.RAM, "", 2, 4, 3, 1, 2, 2, 0, 0, 1)
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
