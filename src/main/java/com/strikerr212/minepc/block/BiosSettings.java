package com.strikerr212.minepc.block;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Настройки BIOS. Хранятся в самой материнской плате (как CMOS): переставил плату — настройки переехали вместе с ней.
 *
 * @param bootOrder    порядок загрузки: nvme, sata, hdd (внутренние), usb, floppy
 * @param cores        сколько ядер включено (0 — все)
 * @param lan          встроенная сетевая карта
 * @param usb          USB-порты (флешки и Wi-Fi адаптеры)
 * @param wifi         Wi-Fi адаптеры (если включён USB)
 * @param biosPassword пароль на вход в настройки ("" — нет)
 * @param bootPassword пароль при включении ("" — нет)
 * @param powerLoss    после пропажи питания (загрузка мира): 0 — как было, 1 — включаться, 2 — оставаться выключенным
 * @param redstone     включаться по сигналу красного камня
 * @param fastBoot     быстрая загрузка: без проверки памяти и паузы на экране самотеста
 */
public record BiosSettings(List<String> bootOrder, int cores, boolean lan, boolean usb, boolean wifi,
                           String biosPassword, String bootPassword, int powerLoss, boolean redstone, boolean fastBoot) {

    public static final List<String> DEVICES = List.of("nvme", "sata", "hdd", "usb", "floppy");
    public static final BiosSettings DEFAULT = new BiosSettings(DEVICES, 0, true, true, true, "", "", 0, false, false);
    private static final String KEY = "minepc_bios";

    /** Порядок: только известные устройства, без повторов; забытые — в конец. */
    static List<String> normalizeOrder(List<String> order) {
        List<String> out = new ArrayList<>();
        for (String s : order) if (DEVICES.contains(s) && !out.contains(s)) out.add(s);
        for (String s : DEVICES) if (!out.contains(s)) out.add(s);
        return List.copyOf(out);
    }

    public static BiosSettings of(ItemStack board) {
        if (board == null || board.isEmpty()) return DEFAULT;
        CustomData data = board.get(DataComponents.CUSTOM_DATA);
        if (data == null) return DEFAULT;
        CompoundTag root = data.copyTag();
        if (!root.contains(KEY)) return DEFAULT;
        CompoundTag t = root.getCompound(KEY);
        List<String> order = new ArrayList<>();
        for (Tag s : t.getList("order", Tag.TAG_STRING)) order.add(s.getAsString());
        return new BiosSettings(normalizeOrder(order), Math.max(0, t.getInt("cores")), !t.getBoolean("noLan"),
                !t.getBoolean("noUsb"), !t.getBoolean("noWifi"), t.getString("biosPass"), t.getString("bootPass"),
                Math.max(0, Math.min(2, t.getInt("powerLoss"))), t.getBoolean("redstone"), t.getBoolean("fastBoot"));
    }

    /** Записать в плату (меняет сам предмет, без события «деталь заменили»). */
    public void writeTo(ItemStack board) {
        if (board == null || board.isEmpty()) return;
        CompoundTag t = new CompoundTag();
        ListTag order = new ListTag();
        for (String s : bootOrder) order.add(StringTag.valueOf(s));
        t.put("order", order);
        t.putInt("cores", cores);
        t.putBoolean("noLan", !lan);
        t.putBoolean("noUsb", !usb);
        t.putBoolean("noWifi", !wifi);
        t.putString("biosPass", biosPassword);
        t.putString("bootPass", bootPassword);
        t.putInt("powerLoss", powerLoss);
        t.putBoolean("redstone", redstone);
        t.putBoolean("fastBoot", fastBoot);
        CustomData.update(DataComponents.CUSTOM_DATA, board, root -> root.put(KEY, t));
    }

    /** Для программ: всё, кроме самих паролей (только есть ли они). */
    public Map<String, Object> toLua() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<Object, Object> order = new LinkedHashMap<>();
        for (int i = 0; i < bootOrder.size(); i++) order.put(i + 1, bootOrder.get(i));
        m.put("bootOrder", order);
        m.put("cores", cores);
        m.put("lan", lan);
        m.put("usb", usb);
        m.put("wifi", wifi);
        m.put("biosPassword", !biosPassword.isEmpty());
        m.put("bootPassword", !bootPassword.isEmpty());
        m.put("powerLoss", powerLoss);
        m.put("redstone", redstone);
        m.put("fastBoot", fastBoot);
        return m;
    }

    /** Изменения из программы: только известные поля; пароль "" — снять. */
    public BiosSettings with(Map<?, ?> changes) {
        List<String> order = bootOrder;
        Object o = changes.get("bootOrder");
        if (o instanceof Map<?, ?> om) {
            List<String> list = new ArrayList<>();
            for (int i = 1; i <= om.size(); i++) {
                Object v = om.get((double) i);
                if (v == null) v = om.get(i);
                if (v != null) list.add(text(v));
            }
            order = normalizeOrder(list);
        }
        return new BiosSettings(order,
                changes.get("cores") instanceof Number n ? Math.max(0, Math.min(64, n.intValue())) : cores,
                changes.get("lan") instanceof Boolean b ? b : lan,
                changes.get("usb") instanceof Boolean b ? b : usb,
                changes.get("wifi") instanceof Boolean b ? b : wifi,
                changes.containsKey("biosPassword") && !(changes.get("biosPassword") instanceof Boolean) ? cap(text(changes.get("biosPassword"))) : biosPassword,
                changes.containsKey("bootPassword") && !(changes.get("bootPassword") instanceof Boolean) ? cap(text(changes.get("bootPassword"))) : bootPassword,
                changes.get("powerLoss") instanceof Number n ? Math.max(0, Math.min(2, n.intValue())) : powerLoss,
                changes.get("redstone") instanceof Boolean b ? b : redstone,
                changes.get("fastBoot") instanceof Boolean b ? b : fastBoot);
    }

    private static String cap(String s) {
        return s.length() > 32 ? s.substring(0, 32) : s;
    }

    static String text(Object v) {
        if (v == null) return "";
        if (v instanceof byte[] b) return new String(b, java.nio.charset.StandardCharsets.UTF_8);
        return v.toString();
    }
}
