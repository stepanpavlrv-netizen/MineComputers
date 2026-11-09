package com.strikerr212.minepc.lan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Схема сети мира: где лежат провода, где стоят устройства, патч-корды и настройки портов коммутаторов.
 * Хранится в папке мира (data/minepc_lan.dat) и не зависит от загрузки чанков — поэтому связь идёт и через места,
 * где никого нет.
 *
 * Провода — те же числа, что у блока кабеля ({@link Wire#packed()}). Устройство — {вид, скорость, стороны с гнёздами, доп.}.
 */
public final class LanData extends SavedData {

    public static final int COMPUTER = 0, DESK_SWITCH = 1, RACK_SWITCH = 2, ROUTER = 3, PANEL = 4, RADIO = 5;

    /** Портов у устройства: 16 под кабели (4 стороны × 4 дорожки) и 8 под патч-корды. */
    public static final int CABLE_PORTS = 16, CORD_PORTS = 8, PORTS = CABLE_PORTS + CORD_PORTS;

    /**
     * Устройство в схеме. kbit — скорость, кбит/с; mask — биты сторон (Wire.N/S/W/E), куда можно воткнуть кабель;
     * extra — у радиомодема: дальность (младшие 16 бит) и канал (цвет, старшие).
     */
    public record Device(int type, int kbit, int mask, int extra) {
        public long bits() {
            return kbit * 1000L;
        }

        public boolean computer() {
            return type == COMPUTER;
        }

        /** Пассивное устройство: сигнал проходит насквозь (патч-панель). */
        public boolean passive() {
            return type == PANEL;
        }
    }

    /** Компьютер подключён по Wi-Fi к роутеру: скорость (Мбит/с) и сигнал (%). Не сохраняется — компьютер переподключится сам. */
    public record WifiLink(BlockPos computer, BlockPos router, int mbit, int signal) {
        public long bits() {
            return mbit * 1_000_000L;
        }
    }

    /** Патч-корд: короткий провод между портами двух устройств (порт 16+ у коммутатора, 0..3 у патч-панели). */
    public record Cord(BlockPos a, int portA, BlockPos b, int portB, int color) {
        public boolean touches(BlockPos p) {
            return a.equals(p) || b.equals(p);
        }

        /** Другой конец: {устройство, порт}. */
        public Plug other(BlockPos p, int port) {
            if (a.equals(p) && portA == port) return new Plug(b, portB);
            if (b.equals(p) && portB == port) return new Plug(a, portA);
            return null;
        }
    }

    /** Порт устройства. */
    public record Plug(BlockPos pos, int port) {
    }

    private final Map<Long, WifiLink> wifi = new HashMap<>();
    private final Map<Long, int[]> cables = new HashMap<>();
    /** Лотки: провода (цвет | оптика << 4). */
    private final Map<Long, int[]> trays = new HashMap<>();
    private final Map<Long, Device> devices = new HashMap<>();
    private final List<Cord> cords = new ArrayList<>();
    /** Настройки портов: бит 0 — порт выключен, биты 1..5 — VLAN (0 — общий, 1..16 — цвет красителя + 1). */
    private final Map<Long, int[]> ports = new HashMap<>();
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

    public int[] tray(long pos) {
        return trays.get(pos);
    }

    public void putTray(BlockPos pos, int[] wires) {
        int[] old = trays.put(pos.asLong(), wires.clone());
        if (old == null || !Arrays.equals(old, wires)) bump();
    }

    public void removeTray(BlockPos pos) {
        if (trays.remove(pos.asLong()) != null) bump();
    }

    // ======================= Устройства =======================

    public Device device(long pos) {
        return devices.get(pos);
    }

    public Device device(BlockPos pos) {
        return devices.get(pos.asLong());
    }

    public void putDevice(BlockPos pos, Device d) {
        Device old = devices.put(pos.asLong(), d);
        if (!d.equals(old)) bump();
    }

    /** Устройство убрали: вместе с ним — его Wi-Fi, клиенты (если это роутер), настройки портов. Патч-корды снимает {@link #removeCords}. */
    public void removeDevice(BlockPos pos) {
        Device old = devices.remove(pos.asLong());
        if (old != null) {
            if (old.type() == ROUTER) wifi.values().removeIf(l -> l.router().equals(pos));
            wifi.remove(pos.asLong());
            ports.remove(pos.asLong());
            bump();
        }
    }

    /** Устройства вида type не дальше r блоков от точки. */
    public List<BlockPos> devicesNear(int type, BlockPos at, int r) {
        List<BlockPos> out = new ArrayList<>();
        for (var e : devices.entrySet()) {
            if (e.getValue().type() != type) continue;
            BlockPos p = BlockPos.of(e.getKey());
            if (p.distSqr(at) <= (double) r * r) out.add(p);
        }
        return out;
    }

    public List<BlockPos> routersNear(BlockPos at, int r) {
        return devicesNear(ROUTER, at, r);
    }

    /** Все устройства вида type (радиомодемы для связи между собой). */
    public List<BlockPos> devicesOfType(int type) {
        List<BlockPos> out = new ArrayList<>();
        for (var e : devices.entrySet()) if (e.getValue().type() == type) out.add(BlockPos.of(e.getKey()));
        return out;
    }

    // ======================= Порты коммутаторов =======================

    public int portConfig(BlockPos dev, int port) {
        int[] cfg = ports.get(dev.asLong());
        return cfg == null || port < 0 || port >= cfg.length ? 0 : cfg[port];
    }

    public static boolean portEnabled(int cfg) {
        return (cfg & 1) == 0;
    }

    public static int portVlan(int cfg) {
        return (cfg >> 1) & 31;
    }

    public void setPort(BlockPos dev, int port, boolean enabled, int vlan) {
        if (port < 0 || port >= PORTS) return;
        int[] cfg = ports.computeIfAbsent(dev.asLong(), k -> new int[PORTS]);
        int v = (enabled ? 0 : 1) | Math.max(0, Math.min(16, vlan)) << 1;
        if (cfg[port] != v) {
            cfg[port] = v;
            bump();
        }
    }

    // ======================= Патч-корды =======================

    public List<Cord> cords(BlockPos dev) {
        List<Cord> out = new ArrayList<>();
        for (Cord c : cords) if (c.touches(dev)) out.add(c);
        return out;
    }

    public List<Cord> allCords() {
        return cords;
    }

    /** Занят ли порт патч-кордом. */
    public boolean cordAt(BlockPos dev, int port) {
        for (Cord c : cords) if (c.other(dev, port) != null) return true;
        return false;
    }

    public void addCord(Cord c) {
        cords.add(c);
        bump();
    }

    /** Снять все корды устройства; вернуть, сколько снято. */
    public List<Cord> removeCords(BlockPos dev) {
        List<Cord> removed = new ArrayList<>();
        cords.removeIf(c -> {
            if (c.touches(dev)) {
                removed.add(c);
                return true;
            }
            return false;
        });
        if (!removed.isEmpty()) bump();
        return removed;
    }

    // ======================= Wi-Fi =======================

    public WifiLink wifiOf(BlockPos computer) {
        return wifi.get(computer.asLong());
    }

    public List<WifiLink> wifiClients(BlockPos router) {
        List<WifiLink> out = new ArrayList<>();
        for (WifiLink l : wifi.values()) if (l.router().equals(router)) out.add(l);
        return out;
    }

    /** Подключить (или обновить скорость). Схема пересчитывается, только если поменялся роутер или скорость. */
    public void putWifi(WifiLink link) {
        WifiLink old = wifi.put(link.computer().asLong(), link);
        if (old == null || !old.router().equals(link.router()) || old.mbit() != link.mbit()) bump();
    }

    public void removeWifi(BlockPos computer) {
        if (wifi.remove(computer.asLong()) != null && level != null) LanManager.invalidate(level);
    }

    public int cableCount() {
        return cables.size();
    }

    // ======================= Сохранение =======================

    private static BlockPos pos(CompoundTag t, String key) {
        return BlockPos.of(t.getLong(key));
    }

    private static LanData load(CompoundTag tag, HolderLookup.Provider registries) {
        LanData d = new LanData();
        ListTag list = tag.getList("cables", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            int[] w = c.getIntArray("w");
            if (w.length > 0) d.cables.put(c.getLong("p"), w);
        }
        list = tag.getList("trays", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            d.trays.put(c.getLong("p"), c.getIntArray("w"));
        }
        list = tag.getList("devices", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            int kbit = c.contains("kb") ? c.getInt("kb") : c.getInt("m") * 1000;
            d.devices.put(c.getLong("p"), new Device(c.getInt("t"), kbit, c.getInt("k"), c.getInt("x")));
        }
        list = tag.getList("cords", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            d.cords.add(new Cord(pos(c, "a"), c.getInt("pa"), pos(c, "b"), c.getInt("pb"), c.getInt("c")));
        }
        list = tag.getList("ports", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            d.ports.put(c.getLong("p"), Arrays.copyOf(c.getIntArray("v"), PORTS));
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
        for (var e : trays.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("p", e.getKey());
            c.putIntArray("w", e.getValue());
            list.add(c);
        }
        tag.put("trays", list);
        list = new ListTag();
        for (var e : devices.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("p", e.getKey());
            c.putInt("t", e.getValue().type());
            c.putInt("kb", e.getValue().kbit());
            c.putInt("k", e.getValue().mask());
            c.putInt("x", e.getValue().extra());
            list.add(c);
        }
        tag.put("devices", list);
        list = new ListTag();
        for (Cord cord : cords) {
            CompoundTag c = new CompoundTag();
            c.putLong("a", cord.a().asLong());
            c.putInt("pa", cord.portA());
            c.putLong("b", cord.b().asLong());
            c.putInt("pb", cord.portB());
            c.putInt("c", cord.color());
            list.add(c);
        }
        tag.put("cords", list);
        list = new ListTag();
        for (var e : ports.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("p", e.getKey());
            c.putIntArray("v", e.getValue());
            list.add(c);
        }
        tag.put("ports", list);
        return tag;
    }
}
