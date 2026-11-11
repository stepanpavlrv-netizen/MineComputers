package com.strikerr212.minepc.hardware.spec;

import com.strikerr212.minepc.hardware.Perf;
import com.strikerr212.minepc.hardware.Units;
import net.minecraft.network.chat.Component;

/**
 * Один настраиваемый параметр детали.
 *
 * В дизайне детали хранится не само значение, а его НОМЕР в списке вариантов
 * (например, ядра {1, 2, 4, 8}: номер 2 → 4 ядра).
 *
 * @param key    ключ, по нему берётся перевод "param.minepc.<key>"
 * @param kind   вид параметра (число, вариант из списка, цвет, вкл/выкл)
 * @param unit   единица измерения для чисел
 * @param values значения для чисел (для остальных видов не используются)
 * @param tiers  уровень технологии для каждого варианта (null = все уровня 1)
 * @param count  количество вариантов
 * @param page   страница в сборочном столе (у видеокарты: 0 — плата, 1 — охлаждение)
 */
public record Param(String key, Kind kind, Unit unit, int[] values, int[] tiers, int count, int page) {

    public enum Kind { NUMBER, OPTION, METAL, LED, PCB, TOGGLE }

    /**
     * Единицы. Все «компьютерные» величины показываются в игровых единицах — тех, с которыми компьютер
     * реально работает: KHZ — частота в кГц (показывается как «3,5 МГц»: одна команда Lua — один такт),
     * KB — объём в игре, RAM — скорость памяти (хранится МТ/с, показывается как МБ/с на канал),
     * VRAM — чип видеопамяти (хранится в «МБ» детали, в игре это четверть килобайта на мегабайт).
     */
    public enum Unit { NONE, MHZ, MB, NM, WATTS, RPM, MM, KHZ, KB, RAM, VRAM }

    // ---------- Фабрики ----------

    public static Param number(String key, int page, Unit unit, int[] values, int[] tiers) {
        return new Param(key, Kind.NUMBER, unit, values, tiers, values.length, page);
    }

    /** Выбор из списка; количество вариантов = длина массива уровней. */
    public static Param option(String key, int page, int... tiers) {
        return new Param(key, Kind.OPTION, Unit.NONE, null, tiers, tiers.length, page);
    }

    public static Param metal(String key, int page) {
        return new Param(key, Kind.METAL, Unit.NONE, null, null, Palette.METAL.length, page);
    }

    public static Param led(String key, int page) {
        return new Param(key, Kind.LED, Unit.NONE, null, null, Palette.LED.length, page);
    }

    public static Param pcb(String key, int page) {
        return new Param(key, Kind.PCB, Unit.NONE, null, null, Palette.PCB.length, page);
    }

    public static Param toggle(String key, int page) {
        return new Param(key, Kind.TOGGLE, Unit.NONE, null, null, 2, page);
    }

    // ---------- Значения ----------

    public int clamp(int index) {
        return Math.max(0, Math.min(count - 1, index));
    }

    /** Настоящее значение варианта (для чисел — число, для остальных — сам номер). */
    public int value(int index) {
        index = clamp(index);
        return kind == Kind.NUMBER ? values[index] : index;
    }

    public int tierAt(int index) {
        return tiers == null ? 1 : tiers[clamp(index)];
    }

    /** RGB-цвет для цветовых параметров. */
    public int color(int index) {
        return switch (kind) {
            case METAL -> Palette.metal(index);
            case LED -> Palette.led(index);
            case PCB -> Palette.pcb(index);
            default -> 0xFFFFFF;
        };
    }

    public boolean isColor() {
        return kind == Kind.METAL || kind == Kind.LED || kind == Kind.PCB;
    }

    public Component label() {
        return Component.translatable("param.minepc." + key);
    }

    /** Как показать вариант в интерфейсе и подсказке. */
    public Component display(int index) {
        index = clamp(index);
        return switch (kind) {
            case NUMBER -> formatNumber(values[index]);
            case OPTION -> Component.translatable("param.minepc." + key + "." + index);
            case METAL -> Component.translatable("color.minepc.metal." + index);
            case LED -> Component.translatable("color.minepc.led." + index);
            case PCB -> Component.translatable("color.minepc.pcb." + index);
            case TOGGLE -> Component.translatable(index == 1 ? "gui.minepc.on" : "gui.minepc.off");
        };
    }

    private Component formatNumber(int v) {
        return switch (unit) {
            case MHZ -> Units.frequency(v);
            case MB -> Units.size(v);
            case KHZ -> Units.gameClock(v);
            case KB -> Units.gameSize(v);
            case RAM -> Units.gameBandwidth(Perf.ramChannelMbs(v));
            case VRAM -> Units.gameSize(Perf.vramKb(v));
            case NM -> Component.translatable("unit.minepc.nm", v);
            case WATTS -> Units.watts(v);
            case RPM -> Component.translatable("unit.minepc.rpm", v);
            case MM -> Component.translatable("unit.minepc.mm", v);
            case NONE -> Component.literal(String.valueOf(v));
        };
    }
}
