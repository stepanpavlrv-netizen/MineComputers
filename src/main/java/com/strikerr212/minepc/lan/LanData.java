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

    public static final int COMPUTER = 0, DESK_SWITCH = 1, RACK_SWITCH = 2, ROUTER = 3, PANEL = 4, RADIO = 5, BRACKET = 6, RACK = 7,
            OUTLET = 8;

    /** Портов у устройства: 16 под кабели (гнёзда по порядку, {@link LanDevice#sockets}) и 8 под патч-корды. */
    public static final int CABLE_PORTS = 16, CORD_PORTS = 8, PORTS = CABLE_PORTS + CORD_PORTS;
    /** Порты шины стойки (по одному на место): только настройки (вкл, VLAN), гнезда у них нет. */
    public static final int BUS_PORT0 = PORTS, CONFIG_PORTS = PORTS + 4;

    /**
     * Устройство в схеме. kbit — скорость, кбит/с; mask — биты сторон (Wire.N/S/W/E), куда можно воткнуть кабель;
     * extra — у радиомодема: дальность (младшие 16 бит) и канал (цвет, старшие).
     */
    public record Device(int type, int kbit, int mask, int extra) {
        public long bits() {
            return kbit * 1000L;
        }

        /** Компьютер или серверная стойка (в стойке — несколько компьютеров, по одному на гнездо). */
        public boolean computer() {
            return type == COMPUTER || type == RACK;
        }

        /**
         * Скорость порта компьютера в месте slot, бит/с. У серверной стойки у каждого модуля своя плата:
         * extra — по 8 бит на модуль, скорость в сотнях Мбит/с (0 — нет платы).
         */
        public long bitsFor(int slot) {
            if (type != RACK) return bits();
            return ((extra >>> (8 * Math.max(0, Math.min(3, slot)))) & 255) * 100_000_000L;
        }

        /** Серверная стойка: вид модуля на месте slot (RackBlockEntity.SERVER и т.д.). */
        public int slotKind(int slot) {
            return type == RACK ? (mask >>> (8 + 4 * Math.max(0, Math.min(3, slot)))) & 15 : 0;
        }

        /** Серверная стойка: куда смотрит (Direction.get2DDataValue). */
        public int facing2d() {
            return (mask >>> 24) & 3;
        }

        /** Пассивное устройство: сигнал проходит насквозь (патч-панель, кронштейн воздушной линии). */
        public boolean passive() {
            return type == PANEL || type == BRACKET || type == OUTLET;
        }

        /** Радиомодем: вышка и антенны ({@link Tower#mask}). */
        public int radio() {
            return mask >>> 8;
        }

        /** Розетка: сторона, к которой она прикручена (Direction.get3DDataValue). */
        public int attached() {
            return (mask >>> 8) & 7;
        }
    }

    /** Компьютер подключён по Wi-Fi к роутеру: скорость (Мбит/с) и сигнал (%). Не сохраняется — компьютер переподключится сам. */
    public record WifiLink(BlockPos computer, BlockPos router, int mbit, int signal) {
        public long bits() {
            return mbit * 1_000_000L;
        }
    }

    /** Патч-корд: короткий провод между портами двух устройств (порт 16+); label — бирка (0 — нет). */
    public record Cord(BlockPos a, int portA, BlockPos b, int portB, int color, int label) {
        public Cord(BlockPos a, int portA, BlockPos b, int portB, int color) {
            this(a, portA, b, portB, color, 0);
        }

        public boolean touches(BlockPos p) {
            return a.equals(p) || b.equals(p);
        }

        /** Длина в блоках (патч-корд считается за 2, воздушная линия — по расстоянию). */
        public int length() {
            return Math.max(2, (int) Math.ceil(Math.sqrt(a.distSqr(b))));
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
    private final Map<Long, long[]> cables = new HashMap<>();
    /** Лотки: провода (цвет | оптика << 4). */
    private final Map<Long, int[]> trays = new HashMap<>();
    private final Map<Long, Device> devices = new HashMap<>();
    private final List<Cord> cords = new ArrayList<>();
    /** Настройки портов: бит 0 — порт выключен, биты 1..5 — VLAN (0 — общий, 1..16 — цвет красителя + 1). */
    private final Map<Long, int[]> ports = new HashMap<>();
    /** Бирки на проводах и кордах: номер бирки - 1 → имя. */
    private final List<String> labels = new ArrayList<>();
    private ServerLevel level;

    // ----- Радио: погода, выбитые молнией модемы, прямая видимость (не сохраняется) -----
    /** Идёт дождь (дальность радио меньше). */
    public boolean rain;
    private final Map<Long, Long> radioDown = new HashMap<>();
    private final Map<String, long[]> los = new HashMap<>();

    public ServerLevel level() {
        return level;
    }

    /** До какого тика модем выбит молнией (0 — работает). */
    public long radioDownUntil(BlockPos pos) {
        return radioDown.getOrDefault(pos.asLong(), 0L);
    }

    public Map<Long, Long> radioDown() {
        return radioDown;
    }

    /** Кэш прямой видимости: ключ → {результат, тик}. */
    public Map<String, long[]> losCache() {
        return los;
    }

    /** Самая длинная бирка, знаков. */
    public static final int MAX_LABEL = 24;

    /** Номер бирки для имени (новое имя — в словарь); пустое имя — 0. */
    public int labelId(String name) {
        if (name == null) return 0;
        name = name.strip();
        if (name.isEmpty()) return 0;
        if (name.length() > MAX_LABEL) name = name.substring(0, MAX_LABEL);
        int i = labels.indexOf(name);
        if (i >= 0) return i + 1;
        if (labels.size() >= 0xFFFE) return 0;
        labels.add(name);
        setDirty();
        return labels.size();
    }

    /** Имя бирки по номеру ("" — нет такой). */
    public String labelName(int id) {
        return id <= 0 || id > labels.size() ? "" : labels.get(id - 1);
    }

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

    public long[] cable(long pos) {
        return cables.get(pos);
    }

    public void putCable(BlockPos pos, long[] wires) {
        if (wires.length == 0) {
            removeCable(pos);
            return;
        }
        long[] old = cables.put(pos.asLong(), wires.clone());
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
        if (port < 0 || port >= CONFIG_PORTS) return;
        int[] cfg = ports.computeIfAbsent(dev.asLong(), k -> new int[CONFIG_PORTS]);
        if (cfg.length < CONFIG_PORTS) {
            cfg = Arrays.copyOf(cfg, CONFIG_PORTS);
            ports.put(dev.asLong(), cfg);
        }
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
            long[] w;
            if (c.contains("l")) {
                w = c.getLongArray("l");
            } else {
                // Схема мира до 0.22: старые провода (только пол) → новые; порты выберутся при загрузке чанка
                List<Long> conv = new ArrayList<>();
                for (int old : c.getIntArray("w")) for (Wire nw : Wire.fromLegacy(old)) conv.add(nw.packed());
                w = conv.stream().mapToLong(Long::longValue).toArray();
            }
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
            d.cords.add(new Cord(pos(c, "a"), c.getInt("pa"), pos(c, "b"), c.getInt("pb"), c.getInt("c"), c.getInt("n")));
        }
        list = tag.getList("ports", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            d.ports.put(c.getLong("p"), Arrays.copyOf(c.getIntArray("v"), CONFIG_PORTS));
        }
        list = tag.getList("labels", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) d.labels.add(list.getString(i));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var e : cables.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("p", e.getKey());
            c.putLongArray("l", e.getValue());
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
            if (cord.label() != 0) c.putInt("n", cord.label());
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
        list = new ListTag();
        for (String l : labels) list.add(net.minecraft.nbt.StringTag.valueOf(l));
        tag.put("labels", list);
        return tag;
    }
}
