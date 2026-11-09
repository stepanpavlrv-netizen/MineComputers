package com.strikerr212.minepc.lan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Схема сети мира: где лежат провода и где стоят устройства. Хранится в папке мира (data/minepc_lan.dat)
 * и не зависит от загрузки чанков — поэтому связь идёт и через места, где никого нет.
 *
 * Провода — те же числа, что у блока кабеля ({@link Wire#packed()}). Устройство — {вид, Мбит/с, стороны с гнёздами}.
 * Вид: 0 — компьютер, 1 — настольный коммутатор, 2 — стоечный (соседние стоечные работают как один), 3 — роутер.
 */
public final class LanData extends SavedData {

    public static final int COMPUTER = 0, DESK_SWITCH = 1, RACK_SWITCH = 2, ROUTER = 3;

    /** Устройство в схеме. mask — биты сторон (Wire.N/S/W/E), куда можно воткнуть кабель. */
    public record Device(int type, int mbit, int mask) {
        public long bits() {
            return mbit * 1_000_000L;
        }

        public boolean computer() {
            return type == COMPUTER;
        }
    }

    private final Map<Long, int[]> cables = new HashMap<>();
    private final Map<Long, Device> devices = new HashMap<>();
    private ServerLevel level;

    public static LanData get(ServerLevel level) {
        LanData d = level.getDataStorage().computeIfAbsent(new Factory<>(LanData::new, LanData::load, null), "minepc_lan");
        d.level = level;
        return d;
    }

    private void bump() {
        setDirty();
        if (level != null) LanManager.invalidate(level);
    }

    // ======================= Провода =======================

    public int[] cable(long pos) {
        return cables.get(pos);
    }

    public void putCable(BlockPos pos, int[] wires) {
        if (wires.length == 0) {
            removeCable(pos);
            return;
        }
        int[] old = cables.put(pos.asLong(), wires.clone());
        if (old == null || !Arrays.equals(old, wires)) bump();
    }

    public void removeCable(BlockPos pos) {
        if (cables.remove(pos.asLong()) != null) bump();
    }

    // ======================= Устройства =======================

    public Device device(long pos) {
        return devices.get(pos);
    }

    public void putDevice(BlockPos pos, Device d) {
        Device old = devices.put(pos.asLong(), d);
        if (!d.equals(old)) bump();
    }

    public void removeDevice(BlockPos pos) {
        if (devices.remove(pos.asLong()) != null) bump();
    }

    public int cableCount() {
        return cables.size();
    }

    // ======================= Сохранение =======================

    private static LanData load(CompoundTag tag, HolderLookup.Provider registries) {
        LanData d = new LanData();
        ListTag list = tag.getList("cables", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            int[] w = c.getIntArray("w");
            if (w.length > 0) d.cables.put(c.getLong("p"), w);
        }
        list = tag.getList("devices", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            d.devices.put(c.getLong("p"), new Device(c.getInt("t"), c.getInt("m"), c.getInt("k")));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var e : cables.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("p", e.getKey());
            c.putIntArray("w", e.getValue());
            list.add(c);
        }
        tag.put("cables", list);
        list = new ListTag();
        for (var e : devices.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("p", e.getKey());
            c.putInt("t", e.getValue().type());
            c.putInt("m", e.getValue().mbit());
            c.putInt("k", e.getValue().mask());
            list.add(c);
        }
        tag.put("devices", list);
        return tag;
    }
}
