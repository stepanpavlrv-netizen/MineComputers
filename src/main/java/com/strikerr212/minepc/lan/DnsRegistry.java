package com.strikerr212.minepc.lan;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Майннет: общий на весь мир реестр имён «.mine» — имя → адрес компьютера, кто первый занял. Сообщение на
 * shop.mine идёт компьютеру с этим адресом (если до него есть сеть). Хранится в мире верхнего уровня.
 */
public final class DnsRegistry extends SavedData {

    public static final String ZONE = ".mine";
    public static final int MAX_PER_COMPUTER = 4, MAX_NAME = 32;

    private final Map<String, String> names = new TreeMap<>();

    public static DnsRegistry get(ServerLevel level) {
        ServerLevel top = level.getServer().overworld();
        return top.getDataStorage().computeIfAbsent(new Factory<>(DnsRegistry::new, DnsRegistry::load, null), "minepc_dns");
    }

    /** Приводит имя к виду «shop.mine»; null — имя не подходит. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String n = raw.trim().toLowerCase(Locale.ROOT);
        if (!n.endsWith(ZONE)) n = n + ZONE;
        String base = n.substring(0, n.length() - ZONE.length());
        if (base.isEmpty() || n.length() > MAX_NAME) return null;
        for (char ch : base.toCharArray()) {
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '-' || ch == '.'
                    || (ch >= 'а' && ch <= 'я') || ch == 'ё';
            if (!ok) return null;
        }
        if (base.startsWith("-") || base.startsWith(".") || base.endsWith(".") || base.contains("..")) return null;
        return n;
    }

    /** Похоже ли это на имя Майннета (оканчивается на .mine). */
    public static boolean isName(String target) {
        return target != null && target.toLowerCase(Locale.ROOT).endsWith(ZONE);
    }

    public String lookup(String name) {
        String n = normalize(name);
        return n == null ? null : names.get(n);
    }

    /** Занять имя: null — получилось, иначе причина. */
    public String register(String name, String address) {
        String n = normalize(name);
        if (n == null) return "Имя — латиница, кириллица, цифры, «-» и «.», до " + MAX_NAME + " знаков";
        String owner = names.get(n);
        if (owner != null) return owner.equals(address) ? null : "Имя " + n + " уже занято";
        if (namesOf(address).size() >= MAX_PER_COMPUTER) return "У компьютера уже " + MAX_PER_COMPUTER + " имени — освободите одно";
        names.put(n, address);
        setDirty();
        return null;
    }

    public String unregister(String name, String address) {
        String n = normalize(name);
        if (n == null || !names.containsKey(n)) return "Такого имени нет";
        if (!names.get(n).equals(address)) return "Имя принадлежит другому компьютеру";
        names.remove(n);
        setDirty();
        return null;
    }

    public List<String> namesOf(String address) {
        List<String> out = new ArrayList<>();
        for (var e : names.entrySet()) if (e.getValue().equals(address)) out.add(e.getKey());
        return out;
    }

    public Map<String, String> all() {
        return names;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag n = new CompoundTag();
        for (var e : names.entrySet()) n.putString(e.getKey(), e.getValue());
        tag.put("Names", n);
        return tag;
    }

    private static DnsRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        DnsRegistry d = new DnsRegistry();
        CompoundTag n = tag.getCompound("Names");
        for (String k : n.getAllKeys()) d.names.put(k, n.getString(k));
        return d;
    }
}
