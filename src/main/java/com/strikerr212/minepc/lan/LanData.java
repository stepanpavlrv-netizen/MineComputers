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
 * Схема сети мира: провода (провода 3.0 — {@link com.strikerr212.minepc.wire.WireLine}), устройства и настройки портов.
 * Хранится в папке мира (data/minepc_lan.dat) и не зависит от загрузки чанков — поэтому связь идёт и через места,
 * где никого нет.
 *
 * Устройство — {вид, скорость, число гнёзд и доп. биты, доп.}. Кабели, проложенные до 0.38 (провода-блоки, старые
 * лотки и корды), не загружаются — их надо проложить заново.
 */
public final class LanData extends SavedData {

    public static final int COMPUTER = 0, DESK_SWITCH = 1, ROUTER = 3, PANEL = 4, RADIO = 5, BRACKET = 6, RACK = 7,
            OUTLET = 8;

    /** Портов у устройства: 16 под кабели (гнёзда по порядку, {@link LanDevice#sockets}) и 8 под патч-корды. */
    public static final int CABLE_PORTS = 16, CORD_PORTS = 8, PORTS = CABLE_PORTS + CORD_PORTS;
    /** Порты шины стойки (по одному на место): только настройки (вкл, VLAN), гнезда у них нет. */
    public static final int BUS_PORT0 = PORTS, CONFIG_PORTS = PORTS + 4;

    /**
     * Устройство в схеме. kbit — скорость, кбит/с; mask — число гнёзд (младшие 8 бит) и доп. биты устройства;
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

    /** Порт устройства. */
    public record Plug(BlockPos pos, int port) {
    }

    private final Map<Long, WifiLink> wifi = new HashMap<>();
    private final Map<Long, Device> devices = new HashMap<>();
    /** Провода 3.0: провод целиком — один объект ({@link com.strikerr212.minepc.wire.WireLine}). */
    private final Map<Long, com.strikerr212.minepc.wire.WireLine> lines = new java.util.LinkedHashMap<>();
    /** Чанк → провода, которые через него проходят (для рассылки игрокам). */
    private final Map<Long, java.util.Set<Long>> lineChunks = new HashMap<>();
    /** Устройство → провода, воткнутые в него. */
    private final Map<Long, java.util.Set<Long>> lineDevices = new HashMap<>();
    private long nextLine = 1;
    /** Настройки портов: бит 0 — порт выключен, биты 1..5 — VLAN (0 — общий, 1..16 — цвет красителя + 1). */
    private final Map<Long, int[]> ports = new HashMap<>();
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

    public static LanData get(ServerLevel level) {
        LanData d = level.getDataStorage().computeIfAbsent(new Factory<>(LanData::new, LanData::load, null), "minepc_lan");
        d.level = level;
        return d;
    }

    private void bump() {
        setDirty();
        if (level != null) LanManager.invalidate(level);
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

    /** Устройство убрали: вместе с ним — его Wi-Fi, клиенты (если это роутер), настройки портов. Провода — {@link #unplugLines}. */
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

    // ======================= Провода 3.0 =======================

    public com.strikerr212.minepc.wire.WireLine line(long id) {
        return lines.get(id);
    }

    public java.util.Collection<com.strikerr212.minepc.wire.WireLine> lines() {
        return lines.values();
    }

    public long newLineId() {
        setDirty();
        return nextLine++;
    }

    /** Провода, воткнутые в устройство. */
    public List<com.strikerr212.minepc.wire.WireLine> linesAt(BlockPos dev) {
        java.util.Set<Long> ids = lineDevices.get(dev.asLong());
        if (ids == null) return List.of();
        List<com.strikerr212.minepc.wire.WireLine> out = new ArrayList<>();
        for (long id : ids) {
            var l = lines.get(id);
            if (l != null) out.add(l);
        }
        return out;
    }

    /** Провод в гнезде port устройства dev (или null). */
    public com.strikerr212.minepc.wire.WireLine lineAt(BlockPos dev, int port) {
        for (var l : linesAt(dev)) if (l.pluggedInto(dev, port)) return l;
        return null;
    }

    public List<com.strikerr212.minepc.wire.WireLine> linesInChunk(long chunk) {
        java.util.Set<Long> ids = lineChunks.get(chunk);
        if (ids == null) return List.of();
        List<com.strikerr212.minepc.wire.WireLine> out = new ArrayList<>();
        for (long id : ids) {
            var l = lines.get(id);
            if (l != null) out.add(l);
        }
        return out;
    }

    private void indexLine(com.strikerr212.minepc.wire.WireLine l, boolean add) {
        for (long c : l.chunks()) {
            if (add) lineChunks.computeIfAbsent(c, k -> new java.util.HashSet<>()).add(l.id);
            else {
                var set = lineChunks.get(c);
                if (set != null && set.remove(l.id) && set.isEmpty()) lineChunks.remove(c);
            }
        }
        for (BlockPos d : new BlockPos[]{l.devA, l.devB}) {
            if (d == null) continue;
            if (add) lineDevices.computeIfAbsent(d.asLong(), k -> new java.util.HashSet<>()).add(l.id);
            else {
                var set = lineDevices.get(d.asLong());
                if (set != null && set.remove(l.id) && set.isEmpty()) lineDevices.remove(d.asLong());
            }
        }
    }

    /** Добавить провод или заменить его новым видом (тот же номер). */
    public void putLine(com.strikerr212.minepc.wire.WireLine l) {
        var old = lines.put(l.id, l);
        if (old != null) indexLine(old, false);
        indexLine(l, true);
        nextLine = Math.max(nextLine, l.id + 1);
        bump();
        if (level != null) com.strikerr212.minepc.wire.WireSync.changed(level, old, l);
    }

    public com.strikerr212.minepc.wire.WireLine removeLine(long id) {
        var old = lines.remove(id);
        if (old != null) {
            indexLine(old, false);
            bump();
            if (level != null) com.strikerr212.minepc.wire.WireSync.removed(level, old);
        }
        return old;
    }

    /** Устройство убрали: воткнутые в него провода остаются лежать, их концы становятся свободными. */
    public void unplugLines(BlockPos dev) {
        for (var l : List.copyOf(linesAt(dev))) {
            var n = l;
            if (dev.equals(n.devA)) n = n.unplugged(false);
            if (dev.equals(n.devB)) n = n.unplugged(true);
            putLine(n);
        }
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

    // ======================= Сохранение =======================

    private static LanData load(CompoundTag tag, HolderLookup.Provider registries) {
        LanData d = new LanData();
        ListTag list = tag.getList("devices", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            int kbit = c.contains("kb") ? c.getInt("kb") : c.getInt("m") * 1000;
            d.devices.put(c.getLong("p"), new Device(c.getInt("t"), kbit, c.getInt("k"), c.getInt("x")));
        }
        list = tag.getList("ports", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            d.ports.put(c.getLong("p"), Arrays.copyOf(c.getIntArray("v"), CONFIG_PORTS));
        }
        list = tag.getList("lines", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var l = com.strikerr212.minepc.wire.WireLine.load(list.getCompound(i));
            if (l == null) continue;
            d.lines.put(l.id, l);
            d.indexLine(l, true);
            d.nextLine = Math.max(d.nextLine, l.id + 1);
        }
        d.nextLine = Math.max(d.nextLine, tag.getLong("next_line"));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
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
        for (var e : ports.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("p", e.getKey());
            c.putIntArray("v", e.getValue());
            list.add(c);
        }
        tag.put("ports", list);
        list = new ListTag();
        for (var l : lines.values()) list.add(l.save());
        tag.put("lines", list);
        tag.putLong("next_line", nextLine);
        return tag;
    }
}
