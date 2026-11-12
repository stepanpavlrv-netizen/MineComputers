package com.strikerr212.minepc.hardware.spec;

import com.strikerr212.minepc.hardware.HardwareType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Спецификация вида детали: какие параметры можно настроить и
 * как из выбранных вариантов получаются характеристики и внешний вид.
 *
 * Каждый вид детали (процессор, память...) — свой наследник этого класса.
 */
public abstract class PartSpec {

    private final HardwareType type;
    private final List<Param> params;
    private final List<Integer> defaults;

    protected PartSpec(HardwareType type, List<Param> params, int... defaults) {
        if (defaults.length != params.size()) {
            throw new IllegalArgumentException(type + ": defaults size " + defaults.length + " != params " + params.size());
        }
        this.type = type;
        this.params = List.copyOf(params);
        List<Integer> d = new ArrayList<>();
        for (int v : defaults) d.add(v);
        this.defaults = List.copyOf(d);
    }

    public HardwareType type() {
        return type;
    }

    public List<Param> params() {
        return params;
    }

    public List<Integer> defaults() {
        return defaults;
    }

    /** Количество страниц в сборочном столе. */
    public int pages() {
        int max = 0;
        for (Param p : params) max = Math.max(max, p.page());
        return max + 1;
    }

    public Component pageName(int page) {
        return Component.translatable("page.minepc." + type.name().toLowerCase() + "." + page);
    }

    /** Привести список вариантов к правильной длине и допустимым номерам. */
    public List<Integer> sanitize(List<Integer> values) {
        List<Integer> out = new ArrayList<>(params.size());
        for (int i = 0; i < params.size(); i++) {
            int v = values != null && i < values.size() ? values.get(i) : defaults.get(i);
            out.add(params.get(i).clamp(v));
        }
        return out;
    }

    // ---------- Чтение значений ----------

    protected int idx(List<Integer> v, int param) {
        return params.get(param).clamp(param < v.size() ? v.get(param) : defaults.get(param));
    }

    /** Настоящее значение параметра (число, а не номер варианта). */
    protected int val(List<Integer> v, int param) {
        return params.get(param).value(idx(v, param));
    }

    protected int color(List<Integer> v, int param) {
        return params.get(param).color(idx(v, param));
    }

    /** Участвует ли параметр в текущей конфигурации (например, размер радиатора — только у СВО). */
    public boolean isActive(int param, List<Integer> v) {
        return true;
    }

    /**
     * Показывать ли параметр в сборочном столе. Скрываются параметры другого вида детали: у серверной платы —
     * обычные слоты памяти, у обычной — серверные. Скрытый параметр и в уровень не считается.
     */
    public boolean isShown(int param, List<Integer> v) {
        return true;
    }

    /** Порядок параметров в сборочном столе (новые параметры дописываются в конец списка, а показать их бывает нужно выше). */
    public int[] order() {
        int[] o = new int[params.size()];
        for (int i = 0; i < o.length; i++) o[i] = i;
        return o;
    }

    // ---------- Характеристики ----------

    /** Уровень технологии = уровень самого продвинутого выбранного варианта. */
    public int tier(List<Integer> v) {
        int tier = 1;
        for (int i = 0; i < params.size(); i++) {
            if (isActive(i, v) && isShown(i, v)) tier = Math.max(tier, params.get(i).tierAt(idx(v, i)));
        }
        return tier;
    }

    /** Потребление энергии, Вт. */
    public abstract int powerDraw(List<Integer> v);

    /** Тепловыделение, Вт (по умолчанию вся энергия уходит в тепло). */
    public int heat(List<Integer> v) {
        return powerDraw(v);
    }

    /** Ключ перевода для названия детали. */
    public abstract String nameKey(List<Integer> v);

    /** Ключ подписи к надписи: "Гравировка" у процессора, "Модель" у остальных. */
    public String labelKey() {
        return "tooltip.minepc.model";
    }

    /** Строки характеристик для подсказки и сборочного стола. */
    public abstract void describe(List<Integer> v, List<Component> lines);

    // ---------- Внешний вид ----------

    /** Вариант модели по измерению 0..2 (должен совпадать с генератором моделей). */
    public abstract int visual(List<Integer> v, int dim);

    /** Цвет слоя: 1 — основной, 2 — подсветка/наклейка, 3 — текстолит. */
    public abstract int tint(List<Integer> v, int layer);

    /** Значения для предпросмотра на странице page (видеокарта показывает голую плату). */
    public List<Integer> previewValues(List<Integer> v, int page) {
        return v;
    }

    protected static int ledColor(boolean on, int rgb) {
        return on ? rgb : Palette.LED_OFF;
    }

    // ---------- Стоимость ----------

    public final Cost cost(List<Integer> v) {
        Cost cost = new Cost();
        baseCost(v, cost);
        int tier = tier(v);
        if (tier >= 2) cost.add(Items.GOLD_INGOT, tier - 1);
        if (tier >= 3) cost.add(Items.QUARTZ, 2 * (tier - 1));
        if (tier >= 4) cost.add(Items.DIAMOND, tier - 3);
        if (tier >= 5) cost.add(Items.AMETHYST_SHARD, 4);
        if (hasLed(v)) cost.add(Items.GLOWSTONE_DUST, 2);
        return cost;
    }

    /** Материалы, нужные всегда (зависят от вида детали). */
    protected abstract void baseCost(List<Integer> v, Cost cost);

    /** Есть ли включённая подсветка (добавляет светокамень в стоимость). */
    protected boolean hasLed(List<Integer> v) {
        return false;
    }

    // ---------- Помощники для describe ----------

    protected static Component line(String key, Object value) {
        Object shown = value instanceof Component c
                ? c.copy().withStyle(ChatFormatting.WHITE)
                : Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE);
        return Component.translatable(key, shown).withStyle(ChatFormatting.GRAY);
    }

    /** Строка про производительность: выделена бирюзовым, чтобы её было видно среди параметров. */
    protected static Component perfLine(String key, Object... values) {
        Object[] shown = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            shown[i] = values[i] instanceof Component c
                    ? c.copy().withStyle(ChatFormatting.AQUA)
                    : Component.literal(String.valueOf(values[i])).withStyle(ChatFormatting.AQUA);
        }
        return Component.translatable(key, shown).withStyle(ChatFormatting.DARK_AQUA);
    }

    protected Component paramLine(List<Integer> v, int param) {
        Param p = params.get(param);
        return Component.translatable("tooltip.minepc.param", p.label(), p.display(idx(v, param)).copy()
                .withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY);
    }
}
