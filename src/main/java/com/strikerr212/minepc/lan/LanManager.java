package com.strikerr212.minepc.lan;

import com.strikerr212.minepc.block.PcCaseBlockEntity;
import com.strikerr212.minepc.computer.Computer;
import com.strikerr212.minepc.block.HostRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Сеть одного мира: кто с кем соединён и доставка сообщений.
 *
 * Провода, соединённые между собой (и патч-корды, и патч-панели — они пассивные), образуют «отрезок»: все порты
 * устройств на нём слышат друг друга. Медный отрезок длиннее 100 блоков не работает (как 100 метров у Ethernet),
 * оптический — до 2000. Коммутатор, роутер и радиомодем соединяют отрезки и добавляют 1 тик задержки; у портов
 * коммутатора есть VLAN (цвет) — порты разных цветов друг друга не слышат — и их можно выключать.
 *
 * Схема ({@link LanData}) хранится отдельно от блоков, поэтому путь идёт и через незагруженные чанки;
 * пересчитывается только когда что-то меняется. Программы отправляют сообщения из своих потоков — они только встают
 * в очередь; доставка идёт в тике сервера.
 */
public final class LanManager {

    /** Самый длинный медный отрезок без коммутатора, блоков. */
    public static final int MAX_SEGMENT = 100;
    /** То же для оптоволокна. */
    public static final int MAX_FIBER = 2000;

    private static final Map<Level, LanManager> BY_LEVEL = new WeakHashMap<>();

    public static synchronized LanManager get(Level level) {
        return BY_LEVEL.computeIfAbsent(level, l -> new LanManager());
    }

    /** Кабели или устройства изменились — схему надо пересчитать. */
    public static void invalidate(Level level) {
        get(level).version++;
    }

    /** До кого можно достучаться: компьютер → переходов и самая медленная связь на пути (бит/с). */
    public record Reach(BlockPos pos, int slot, int hops, long bits) {
        public HostRef host() {
            return new HostRef(pos, slot);
        }
    }

    /** Что видно из компьютера: компьютеры и коммутаторы (роутеры, радиомодемы) на пути. */
    public record Result(List<Reach> computers, List<BlockPos> switches) {
        static final Result EMPTY = new Result(List.of(), List.of());
    }

    /** Отрезок у порта устройства: порты других устройств на нём; ok — не длиннее предела; length — блоков. */
    public record Segment(int port, Set<LanData.Plug> plugs, boolean ok, int length) {
    }

    private record Outgoing(HostRef from, String target, int port, Object[] payload, int bytes) {
    }

    private record Delivery(long tick, long seq, HostRef to, String from, int port, int hops, Object[] payload, int bytes) {
    }

    /** Линия между двумя компьютерами: до какого тика (дробного) она занята уже отправленными сообщениями. */
    private record Pipe(HostRef from, HostRef to) {
    }

    /** Больше этого (в тиках) очередь в линии не копится — лишнее теряется, как в настоящей сети. */
    private static final int MAX_BACKLOG = 30 * Computer.TICKS_PER_SECOND;
    private final Map<Pipe, Double> pipes = new HashMap<>();
    private long deliverySeq;

    private volatile long version;
    private long cachedVersion = -1;
    private final Map<HostRef, Result> reachCache = new HashMap<>();
    private final ConcurrentLinkedQueue<Outgoing> outbox = new ConcurrentLinkedQueue<>();
    /** По тику доставки, а при равенстве — в порядке отправки: сообщения не обгоняют друг друга. */
    private final PriorityQueue<Delivery> pending = new PriorityQueue<>((a, b) ->
            a.tick != b.tick ? Long.compare(a.tick, b.tick) : Long.compare(a.seq, b.seq));

    // ======================= Устройства =======================

    /** Устройство поставили, сломали или поменялась его скорость: занести в схему сети. */
    public static void updateDevice(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        LanData data = LanData.get(server);
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof LanDevice d) {
            int mask = Math.min(LanData.CABLE_PORTS, d.sockets(level, pos, s).size())   // сколько гнёзд
                    | d.lanMaskExtra(level, pos, s) << 8;                                 // у стойки — модули и сторона
            int kbit = (int) Math.min(Integer.MAX_VALUE, d.lanBitsPerSecond(level, pos, s) / 1000L);
            data.putDevice(pos, new LanData.Device(d.lanType(s), kbit, mask, d.lanExtra(level, pos, s)));
        } else {
            // Устройства больше нет: патч-корды — в добычу, у других концов — убрать
            for (LanData.Cord c : data.removeCords(pos)) {
                net.minecraft.world.level.block.Block.popResource(level, pos, CableLabels.cordDrop(level, c));
                CordList.refresh(server, c.a().equals(pos) ? c.b() : c.a());
            }
            data.removeDevice(pos);
        }
    }

    // ======================= Схема =======================

    /** Все компьютеры, до которых можно дойти из корпуса casePos (сам он не входит). Чанки грузить не нужно. */
    public List<Reach> reach(ServerLevel level, BlockPos casePos) {
        return reach(level, HostRef.of(casePos));
    }

    public List<Reach> reach(ServerLevel level, HostRef host) {
        return result(level, host).computers();
    }

    /** Коммутаторы, роутеры и радиомодемы, через которые проходит связь компьютера (для программы switch). */
    public List<BlockPos> switches(ServerLevel level, HostRef host) {
        return result(level, host).switches();
    }

    private Result result(ServerLevel level, HostRef host) {
        if (cachedVersion != version) {
            reachCache.clear();
            cachedVersion = version;
        }
        return reachCache.computeIfAbsent(host, h -> computeReach(LanData.get(level), h));
    }

    /** Порт компьютера подключён (хотя бы к одному работающему отрезку). */
    public boolean linked(ServerLevel level, HostRef host) {
        LanData data = LanData.get(level);
        for (Segment s : hostSegments(data, host)) if (s.ok()) return true;
        // Сервер в стойке с коммутатором-модулем подключён шиной
        return busPortOn(data, host);
    }

    /** Сервер стойки подключён к шине: в ряду есть коммутатор-модуль и порт шины не выключен. */
    static boolean busPortOn(LanData data, HostRef host) {
        LanData.Device d = data.device(host.pos());
        if (d == null || d.type() != LanData.RACK || !RackBus.isServer(d.slotKind(host.slot()))) return false;
        return busAnchor(data, host.pos()) != null && LanData.portEnabled(data.portConfig(host.pos(), LanData.BUS_PORT0 + host.slot()));
    }

    // ======================= Шина серверной стойки =======================

    /** Стойки одного ряда (колонна и соседи бок о бок, смотрящие туда же) — по схеме, без мира. */
    public static List<BlockPos> rackGroup(LanData data, BlockPos start) {
        List<BlockPos> out = new ArrayList<>();
        LanData.Device d0 = data.device(start);
        if (d0 == null || d0.type() != LanData.RACK) return out;
        Direction f = Direction.from2DDataValue(d0.facing2d());
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        q.add(start.immutable());
        seen.add(start.immutable());
        while (!q.isEmpty() && out.size() < 64) {
            BlockPos p = q.poll();
            out.add(p);
            for (Direction dir : new Direction[]{Direction.UP, Direction.DOWN, f.getClockWise(), f.getCounterClockWise()}) {
                BlockPos n = p.relative(dir).immutable();
                if (seen.contains(n)) continue;
                LanData.Device o = data.device(n);
                if (o != null && o.type() == LanData.RACK && o.facing2d() == d0.facing2d()) {
                    seen.add(n);
                    q.add(n);
                }
            }
        }
        return out;
    }

    /** Точка шины ряда (стойка с наименьшим номером), если в ряду есть коммутатор-модуль; иначе null. */
    public static BlockPos busAnchor(LanData data, BlockPos rack) {
        List<BlockPos> g = rackGroup(data, rack);
        boolean sw = false;
        BlockPos anchor = null;
        for (BlockPos p : g) {
            LanData.Device d = data.device(p);
            for (int i = 0; i < 4; i++) if (d.slotKind(i) == RackBus.SWITCH) sw = true;
            if (anchor == null || p.asLong() < anchor.asLong()) anchor = p;
        }
        return sw ? anchor : null;
    }

    /** Константы видов модулей стойки (как в RackBlockEntity, но без Minecraft-классов блока). */
    static final class RackBus {
        static final int SERVER = 1, SERVER_2U = 3, SWITCH = 5, PATCH = 6, ROUTER = 7;

        static boolean isServer(int k) {
            return k == SERVER || k == SERVER_2U;
        }

        static boolean isNetwork(int k) {
            return k == SWITCH || k == PATCH || k == ROUTER;
        }

        /** Какие порты гнёзд у модуля на месте slot: главный и три дополнительных. */
        static int[] ports(int slot) {
            return new int[]{slot, 4 + 3 * slot, 5 + 3 * slot, 6 + 3 * slot};
        }
    }

    /**
     * Порт компьютера — в каком гнезде он в схеме: у системного блока — любое его гнездо, у серверной стойки —
     * гнездо своего модуля (номер гнезда = номер модуля).
     */
    public static int hostSlot(LanData.Device d, int port) {
        if (d == null || d.type() != LanData.RACK || port >= LanData.CABLE_PORTS) return 0;
        return port < 4 ? port : (port - 4) / 3;
    }

    /** Отрезки, к которым подключён компьютер. */
    static List<Segment> hostSegments(LanData data, HostRef host) {
        LanData.Device d = data.device(host.pos());
        List<Segment> all = segmentsAt(data, host.pos());
        if (d == null || d.type() != LanData.RACK) return all;
        List<Segment> out = new ArrayList<>();
        for (Segment s : all) if (s.port() == host.slot()) out.add(s);
        return out;
    }

    private record Node(BlockPos pos, int hops, long bits, int vlan) {
    }

    private record VKey(BlockPos pos, int vlan) {
    }

    static Result computeReach(LanData data, HostRef start) {
        LanData.Device self = data.device(start.pos());
        if (self == null) return Result.EMPTY;
        Map<HostRef, Reach> best = new HashMap<>();
        Set<VKey> visited = new HashSet<>();
        List<BlockPos> switches = new ArrayList<>();
        ArrayDeque<Node> queue = new ArrayDeque<>();
        Visitor v = new Visitor(data, start, best, visited, switches, queue);
        long selfBits = self.bitsFor(start.slot());
        if (selfBits > 0) {
            for (Segment s : hostSegments(data, start)) {
                if (s.ok()) for (LanData.Plug p : s.plugs()) v.plug(p, 0, selfBits);
            }
            // Сервер стойки — в шину ряда (если там есть коммутатор-модуль)
            if (busPortOn(data, start)) {
                int cfg = data.portConfig(start.pos(), LanData.BUS_PORT0 + start.slot());
                v.enter(busAnchor(data, start.pos()), LanData.portVlan(cfg), 1, selfBits);
            }
        }
        // По Wi-Fi: роутер — как коммутатор, к которому мы подключены (общий VLAN)
        LanData.WifiLink own = start.slot() == 0 ? data.wifiOf(start.pos()) : null;
        if (own != null) v.enter(own.router(), 0, 1, own.bits());
        while (!queue.isEmpty()) {
            Node cur = queue.poll();
            LanData.Device dev = data.device(cur.pos);
            if (dev == null) continue;
            if (dev.type() == LanData.RACK) {          // шина ряда стоек
                visitBus(data, cur, v, start);
                continue;
            }
            if (dev.type() == LanData.ROUTER) {
                for (LanData.WifiLink l : data.wifiClients(cur.pos)) {
                    if (!HostRef.of(l.computer()).equals(start)) v.computer(HostRef.of(l.computer()), cur.hops, Math.min(cur.bits, l.bits()));
                }
            }
            if (dev.type() == LanData.RADIO) {
                for (RadioLink l : radioLinks(data, cur.pos, dev)) v.enter(l.pos(), cur.vlan, cur.hops + 1, Math.min(cur.bits, l.bits()));
            }
            for (Segment s : segmentsAt(data, cur.pos)) {
                if (!s.ok()) continue;
                int cfg = data.portConfig(cur.pos, s.port());
                if (!LanData.portEnabled(cfg) || LanData.portVlan(cfg) != cur.vlan) continue;
                for (LanData.Plug p : s.plugs()) v.plug(p, cur.hops, cur.bits);
            }
        }
        return new Result(List.copyOf(best.values()), List.copyOf(switches));
    }

    /**
     * Шина ряда стоек — коммутатор: серверы ряда (по своим портам шины), гнёзда сетевых модулей и патч-корды.
     * Роутер-модуль соединяет VLAN шины между собой (+1 переход).
     */
    private static void visitBus(LanData data, Node cur, Visitor v, HostRef start) {
        boolean router = false;
        Set<Integer> vlans = new HashSet<>();
        for (BlockPos r : rackGroup(data, cur.pos)) {
            LanData.Device d = data.device(r);
            List<Integer> netPorts = new ArrayList<>();
            for (int s = 0; s < 4; s++) {
                int k = d.slotKind(s);
                if (k == RackBus.ROUTER) router = true;
                if (RackBus.isServer(k)) {
                    int cfg = data.portConfig(r, LanData.BUS_PORT0 + s);
                    vlans.add(LanData.portVlan(cfg));
                    HostRef h = new HostRef(r, s);
                    long b = Math.min(cur.bits, d.bitsFor(s));
                    if (!h.equals(start) && b > 0 && LanData.portEnabled(cfg) && LanData.portVlan(cfg) == cur.vlan) v.computer(h, cur.hops, b);
                } else if (RackBus.isNetwork(k)) {
                    for (int p : RackBus.ports(s)) netPorts.add(p);
                }
            }
            for (int p = LanData.CABLE_PORTS; p < LanData.PORTS; p++) netPorts.add(p);   // патч-корды в стойку — тоже в шину
            for (Segment seg : segmentsAt(data, r)) {
                if (!seg.ok() || !netPorts.contains(seg.port())) continue;
                int cfg = data.portConfig(r, seg.port());
                vlans.add(LanData.portVlan(cfg));
                if (!LanData.portEnabled(cfg) || LanData.portVlan(cfg) != cur.vlan) continue;
                boolean wan = seg.port() < LanData.CABLE_PORTS && d.slotKind(hostSlot(d, seg.port())) == RackBus.ROUTER;
                for (LanData.Plug pl : seg.plugs()) v.plug(pl, cur.hops + (wan ? 1 : 0), cur.bits);
            }
        }
        if (router) {
            for (int vl : vlans) if (vl != cur.vlan) v.enter(cur.pos, vl, cur.hops + 1, cur.bits);
        }
    }

    /** Обход: что делать с найденным портом устройства. */
    private record Visitor(LanData data, HostRef start, Map<HostRef, Reach> best, Set<VKey> visited,
                           List<BlockPos> switches, ArrayDeque<Node> queue) {

        void plug(LanData.Plug p, int hops, long bits) {
            LanData.Device d = data.device(p.pos());
            if (d == null) return;
            HostRef host = new HostRef(p.pos(), hostSlot(d, p.port()));
            if (d.type() == LanData.RACK) {
                // Гнездо сетевого модуля или патч-корд в стойку — в шину ряда; гнездо сервера — сам сервер
                int k = d.slotKind(host.slot());
                boolean toBus = p.port() >= LanData.CABLE_PORTS || RackBus.isNetwork(k);
                BlockPos anchor = toBus ? busAnchor(data, p.pos()) : null;
                if (anchor != null) {
                    int cfg = data.portConfig(p.pos(), p.port());
                    long b = Math.min(bits, 10_000_000_000L);
                    if (LanData.portEnabled(cfg)) enter(anchor, LanData.portVlan(cfg), hops + 1 + (k == RackBus.ROUTER ? 1 : 0), b);
                    return;
                }
                if (toBus && p.port() < LanData.CABLE_PORTS) return;   // модуль без коммутатора в ряду — тупик
            }
            if (d.computer() && host.equals(start)) return;
            long b = Math.min(bits, d.computer() ? d.bitsFor(host.slot()) : d.bits());
            if (b <= 0) return;
            if (d.computer()) {
                computer(host, hops, b);
            } else {
                int cfg = data.portConfig(p.pos(), p.port());
                if (LanData.portEnabled(cfg)) enter(p.pos(), LanData.portVlan(cfg), hops + 1, b);
            }
        }

        void computer(HostRef host, int hops, long bits) {
            Reach r = new Reach(host.pos(), host.slot(), hops, bits);
            Reach old = best.get(host);
            if (old == null || r.hops < old.hops || (r.hops == old.hops && r.bits > old.bits)) best.put(host, r);
        }

        void enter(BlockPos sw, int vlan, int hops, long bits) {
            for (BlockPos m : switchGroup(data, sw)) {
                if (visited.add(new VKey(m, vlan))) {
                    queue.add(new Node(m, hops, bits, vlan));
                    if (!switches.contains(m)) switches.add(m);
                }
            }
        }
    }

    /** Радиомодемы того же канала, до которых достаёт сигнал. */
    public static List<BlockPos> radioPeers(LanData data, BlockPos pos, LanData.Device dev) {
        List<BlockPos> out = new ArrayList<>();
        for (RadioLink l : radioLinks(data, pos, dev)) out.add(l.pos());
        return out;
    }

    /**
     * Радиосвязь с другим модемом: куда, скорость (бит/с), через что (pin — штырь или антенна шкафа/мачта,
     * sector — секторы, dish — тарелки) и прямая видимость (clear, weak — холм на пути, связь медленнее; off — не проверяем).
     */
    public record RadioLink(BlockPos pos, long bits, String via, String los) {
    }

    /** Скорость радио: штырь — как у модема (2 КБ/с), секторы — ×4, тарелки — ×16. */
    public static final int SECTOR_SPEEDUP = 4, DISH_SPEEDUP = 16;

    public static List<RadioLink> radioLinks(LanData data, BlockPos pos, LanData.Device dev) {
        List<RadioLink> out = new ArrayList<>();
        long now = data.level() == null ? 0 : data.level().getGameTime();
        if (data.radioDownUntil(pos) > now) return out;
        int channel = dev.extra() >>> 16;
        for (BlockPos other : data.devicesOfType(LanData.RADIO)) {
            if (other.equals(pos) || data.radioDownUntil(other) > now) continue;
            LanData.Device od = data.device(other);
            if (od == null || (od.extra() >>> 16) != channel) continue;
            RadioLink l = radioLink(data, pos, dev, other, od);
            if (l != null) out.add(l);
        }
        return out;
    }

    /** В какую четверть круга смотрит направление от a к b (Direction.get2DDataValue). */
    static int quadrant(BlockPos a, BlockPos b) {
        return Direction.getNearest(b.getX() - a.getX(), 0, b.getZ() - a.getZ()).get2DDataValue();
    }

    private static RadioLink radioLink(LanData data, BlockPos a, LanData.Device da, BlockPos b, LanData.Device db) {
        double dist = Math.sqrt(a.distSqr(b));
        double weather = data.rain && com.strikerr212.minepc.MineConfig.radioWeather() ? 0.85 : 1.0;
        int ma = da.radio(), mb = db.radio();
        int qa = quadrant(a, b), qb = quadrant(b, a);
        long bits = 0;
        String via = "";
        // Тарелки, повёрнутые друг на друга: вдвое дальше вышки и в 16 раз быстрее
        if ((Tower.dishes(ma) & 1 << qa) != 0 && (Tower.dishes(mb) & 1 << qb) != 0) {
            int r = 2 * Math.min(Tower.range(Tower.sections(ma)), Tower.range(Tower.sections(mb)));
            if (dist <= r * weather) {
                bits = RadioBlock.BITS * DISH_SPEEDUP;
                via = "dish";
            }
        }
        // Штырь или секторы (или антенна шкафа): дальность — меньшая из двух; секторы с обеих сторон — в 4 раза быстрее
        if (bits == 0) {
            boolean aOk = omni(ma) || (Tower.sectors(ma) & 1 << qa) != 0;
            boolean bOk = omni(mb) || (Tower.sectors(mb) & 1 << qb) != 0;
            int r = Math.min(da.extra() & 0xFFFF, db.extra() & 0xFFFF);
            if (aOk && bOk && dist <= r * weather) {
                boolean sectors = (Tower.sectors(ma) & 1 << qa) != 0 && (Tower.sectors(mb) & 1 << qb) != 0;
                bits = RadioBlock.BITS * (sectors ? SECTOR_SPEEDUP : 1);
                via = sectors ? "sector" : "pin";
            }
        }
        if (bits == 0) return null;
        int mode = com.strikerr212.minepc.MineConfig.radioLineOfSight();
        if (mode == 0) return new RadioLink(b, bits, via, "off");
        int los = lineOfSight(data, a, antennaHeight(ma), b, antennaHeight(mb));
        if (los == 2 && mode == 2) return null;
        if (los > 0) return new RadioLink(b, bits / 4, via, "weak");
        return new RadioLink(b, bits, via, "clear");
    }

    /** Связь во все стороны: штырь на вышке, или вышка без штыря и секторов (антенна шкафа), или модем без вышки. */
    private static boolean omni(int m) {
        return !Tower.isTower(m) || Tower.hasPin(m) || (Tower.sectors(m) == 0 && Tower.dishes(m) == 0);
    }

    /** Высота антенн над модемом, блоков. */
    private static int antennaHeight(int m) {
        return Math.max(1, Tower.sections(m) + (Tower.hasPin(m) ? 2 : 1));
    }

    /**
     * Прямая видимость между антеннами: рельеф по прямой между ними (раз в 8 блоков). 0 — видно, 1 — холм чуть
     * выше линии (до 6 блоков), 2 — закрыто. Где чанк загружен — настоящая высота (с постройками), где нет — рельеф
     * из генератора мира (чанки не грузим). Результат помнится 2 минуты.
     */
    public static int lineOfSight(LanData data, BlockPos a, int ha, BlockPos b, int hb) {
        ServerLevel level = data.level();
        if (level == null) return 0;
        boolean swap = a.asLong() > b.asLong();
        BlockPos p = swap ? b : a, q = swap ? a : b;
        int hp = swap ? hb : ha, hq = swap ? ha : hb;
        String key = p.asLong() + ":" + hp + ":" + q.asLong() + ":" + hq;
        long now = level.getGameTime();
        long[] cached = data.losCache().get(key);
        if (cached != null && now - cached[1] < 2400) return (int) cached[0];
        if (data.losCache().size() > 4096) data.losCache().clear();
        double ya = p.getY() + hp + 0.5, yb = q.getY() + hq + 0.5;
        double dx = q.getX() - p.getX(), dz = q.getZ() - p.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        int steps = Math.max(2, (int) Math.ceil(len / 8));
        double worst = Double.NEGATIVE_INFINITY;
        var gen = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            if (t * len < 4 || (1 - t) * len < 4) continue;          // у самих вышек — их же постройки
            int x = (int) Math.floor(p.getX() + 0.5 + dx * t), z = (int) Math.floor(p.getZ() + 0.5 + dz * t);
            int h = level.hasChunk(x >> 4, z >> 4)
                    ? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
                    : gen.getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, random);
            worst = Math.max(worst, h - (ya + (yb - ya) * t));
        }
        int result = worst <= 0 ? 0 : worst <= 6 ? 1 : 2;
        data.losCache().put(key, new long[]{result, now});
        return result;
    }

    // ======================= Погода и молнии =======================

    /** Раз в 2 секунды: дождь (дальность радио), конец «выбитости» модемов; в грозу — молнии по вышкам без громоотвода. */
    private void weather(ServerLevel level, long now) {
        LanData data = LanData.get(level);
        boolean on = com.strikerr212.minepc.MineConfig.radioWeather();
        boolean rain = on && level.isRaining();
        if (rain != data.rain) {
            data.rain = rain;
            version++;
        }
        if (data.radioDown().values().removeIf(t -> t <= now)) version++;
        if (on && level.isThundering() && (now / 40) % 5 == 0) {          // раз в 10 секунд
            for (BlockPos radio : data.devicesOfType(LanData.RADIO)) {
                if (level.random.nextInt(16) == 0) strike(level, radio);
            }
        }
    }

    /**
     * Молния в вышку модема. С громоотводом — разряд уходит в землю (модем работает), без него — модем выбит на
     * минуту. true — выбит.
     */
    public static boolean strike(ServerLevel level, BlockPos radio) {
        LanData data = LanData.get(level);
        LanData.Device d = data.device(radio);
        if (d == null || d.type() != LanData.RADIO) return false;
        boolean rod = Tower.hasRod(d.radio());
        if (level.isLoaded(radio)) {
            Tower t = Tower.near(level, radio);
            BlockPos top = t != null ? t.top().above(t.pin() ? 2 : 1) : radio.above(RadioBlock.masts(level, radio) + 1);
            net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(top));
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }
        if (rod) return false;
        data.radioDown().put(radio.asLong(), level.getGameTime() + 1200);
        invalidate(level);
        return true;
    }

    /** Коммутатор и стоечные коммутаторы, стоящие с ним вплотную (они работают как один). */
    static List<BlockPos> switchGroup(LanData data, BlockPos start) {
        List<BlockPos> out = new ArrayList<>();
        out.add(start);
        LanData.Device s = data.device(start);
        if (s == null || s.type() != LanData.RACK_SWITCH) return out;
        ArrayDeque<BlockPos> q = new ArrayDeque<>(out);
        Set<BlockPos> seen = new HashSet<>(out);
        while (!q.isEmpty() && seen.size() < 256) {
            BlockPos p = q.poll();
            for (Direction dir : Direction.values()) {
                BlockPos n = p.relative(dir);
                if (seen.contains(n)) continue;
                LanData.Device o = data.device(n);
                if (o != null && o.type() == LanData.RACK_SWITCH) {
                    seen.add(n);
                    q.add(n);
                    out.add(n);
                }
            }
        }
        return out;
    }

    // ======================= Порты и отрезки =======================

    /** Отрезки на всех занятых портах устройства. */
    public static List<Segment> segmentsAt(LanData data, BlockPos dev) {
        List<Segment> out = new ArrayList<>();
        LanData.Device d = data.device(dev);
        if (d == null) return out;
        for (int port : usedPorts(data, dev, d)) out.add(explore(data, dev, port));
        return out;
    }

    /** Провод с вилкой в устройство dev: клетка кабеля, номер провода в ней, порт. */
    private record PlugWire(BlockPos cell, int index, int port) {
    }

    /** Все провода, воткнутые в устройство (из клеток кабеля по четырём сторонам). */
    private static List<PlugWire> plugWires(LanData data, BlockPos dev) {
        List<PlugWire> out = new ArrayList<>();
        for (Direction face : Direction.values()) {
            BlockPos cell = dev.relative(face);
            long[] here = data.cable(cell.asLong());
            if (here == null) continue;
            Direction toward = face.getOpposite();
            for (int j = 0; j < here.length; j++) {
                Wire w = Wire.unpack(here[j]);
                int k = w.index(toward);
                if (k >= 0 && w.port(k) >= 0) out.add(new PlugWire(cell.immutable(), j, w.port(k)));
            }
        }
        return out;
    }

    /** Бирка того, что воткнуто в порт: провода с вилкой или патч-корда (0 — без бирки). */
    public static int portLabel(LanData data, BlockPos dev, int port) {
        if (port < LanData.CABLE_PORTS) {
            for (PlugWire pw : plugWires(data, dev)) {
                if (pw.port() != port) continue;
                long[] here = data.cable(pw.cell().asLong());
                if (here != null && pw.index() < here.length) {
                    int l = Wire.unpack(here[pw.index()]).label;
                    if (l != 0) return l;
                }
            }
        }
        for (LanData.Cord c : data.cords(dev)) if (c.other(dev, port) != null) return c.label();
        return 0;
    }

    /** Порты, в которые что-то воткнуто: кабель с вилкой или патч-корд. */
    public static List<Integer> usedPorts(LanData data, BlockPos dev, LanData.Device d) {
        List<Integer> out = new ArrayList<>();
        for (PlugWire pw : plugWires(data, dev)) if (pw.port() < LanData.CABLE_PORTS && !out.contains(pw.port())) out.add(pw.port());
        // корды: и в своих портах, и прямо в гнёздах (у коммутатора корд втыкается в гнездо на модели)
        for (int port = 0; port < LanData.PORTS; port++) if (!out.contains(port) && data.cordAt(dev, port)) out.add(port);
        java.util.Collections.sort(out);
        return out;
    }

    private static long key(BlockPos p, int wire) {
        return p.asLong() * 64 + wire;
    }

    /** Куда ведёт соединение провода w клетки p в сторону i: клетка и грань провода на том конце (null — никуда). */
    public static Object[] target(BlockPos p, Wire w, int i) {
        Direction d = w.dirs()[i];
        return switch (w.conn[i]) {
            case Wire.FLAT -> new Object[]{p.relative(d), w.face};
            case Wire.INNER -> new Object[]{p, d};
            case Wire.OUTER -> new Object[]{p.relative(d).relative(w.face), d.getOpposite()};
            default -> null;
        };
    }

    /**
     * Пройти от порта устройства по проводам, лоткам, патч-кордам и сквозь патч-панели; собрать порты других устройств.
     * Длина — клетки кабеля (корд считается за 2): для меди до 100, для чистой оптики до 2000.
     */
    public static Segment explore(LanData data, BlockPos dev, int port) {
        Set<LanData.Plug> out = new HashSet<>();
        Set<LanData.Plug> seenPlugs = new HashSet<>();
        Set<Long> done = new HashSet<>();
        Set<Long> cells = new HashSet<>();
        ArrayDeque<long[]> wires = new ArrayDeque<>();
        ArrayDeque<long[]> trayq = new ArrayDeque<>();
        Set<Long> trayDone = new HashSet<>();
        ArrayDeque<LanData.Plug> plugs = new ArrayDeque<>();
        int[] cordCells = {0};
        boolean[] copper = {false};
        seenPlugs.add(new LanData.Plug(dev, port));
        seed(data, dev, port, wires, plugs, done, cordCells);
        while (!wires.isEmpty() || !plugs.isEmpty() || !trayq.isEmpty()) {
            while (!trayq.isEmpty()) {
                // Провод в лотке: соседние лотки во все стороны и провода, которые входят в лоток
                long[] cur = trayq.poll();
                BlockPos p = BlockPos.of(cur[0]);
                cells.add(cur[0]);
                if (cells.size() + cordCells[0] > MAX_FIBER + 1) return new Segment(port, out, false, cells.size() + cordCells[0]);
                int tw = (int) cur[1];
                if ((tw & 16) == 0) copper[0] = true;
                for (Direction d : Direction.values()) {
                    BlockPos n = p.relative(d);
                    int[] tray = data.tray(n.asLong());
                    if (tray != null) {
                        for (int t : tray) if (t == tw && trayDone.add(n.asLong() * 32 + t)) trayq.add(new long[]{n.asLong(), t});
                        continue;
                    }
                    long[] other = data.cable(n.asLong());
                    if (other == null) continue;
                    for (int j = 0; j < other.length; j++) {
                        Wire o = Wire.unpack(other[j]);
                        int k = o.index(d.getOpposite());
                        if (k >= 0 && o.conn[k] == Wire.FLAT && o.color == (tw & 15) && o.fiber == ((tw & 16) != 0)
                                && done.add(key(n, j))) {
                            wires.add(new long[]{n.asLong(), j});
                        }
                    }
                }
            }
            while (!wires.isEmpty()) {
                long[] cur = wires.poll();
                BlockPos p = BlockPos.of(cur[0]);
                cells.add(cur[0]);
                if (cells.size() + cordCells[0] > MAX_FIBER + 1) return new Segment(port, out, false, cells.size() + cordCells[0]);
                long[] here = data.cable(cur[0]);
                if (here == null || cur[1] >= here.length) continue;
                Wire w = Wire.unpack(here[(int) cur[1]]);
                if (!w.fiber) copper[0] = true;
                Direction[] ds = w.dirs();
                for (int i = 0; i < 4; i++) {
                    Direction d = ds[i];
                    if (w.port(i) >= 0) {
                        plugs.add(new LanData.Plug(p.relative(d).immutable(), w.port(i)));
                        continue;
                    }
                    Object[] t = target(p, w, i);
                    if (t == null) continue;
                    BlockPos n = (BlockPos) t[0];
                    Direction face = (Direction) t[1];
                    if (w.conn[i] == Wire.FLAT) {
                        int[] tray = data.tray(n.asLong());
                        if (tray != null) {
                            int tw = w.color | (w.fiber ? 16 : 0);
                            for (int tt : tray) if (tt == tw && trayDone.add(n.asLong() * 32 + tt)) trayq.add(new long[]{n.asLong(), tt});
                            continue;
                        }
                    }
                    long[] other = data.cable(n.asLong());
                    if (other == null) continue;
                    int edge = Wire.edgeAxis(w.face, d).ordinal();
                    for (int j = 0; j < other.length; j++) {
                        Wire o = Wire.unpack(other[j]);
                        if (o.face == face && o.sameKind(w) && o.lane[edge] == w.lane[edge] && done.add(key(n, j))) {
                            wires.add(new long[]{n.asLong(), j});
                        }
                    }
                }
            }
            while (!plugs.isEmpty()) {
                LanData.Plug pl = plugs.poll();
                if (!seenPlugs.add(pl)) continue;
                LanData.Device d = data.device(pl.pos());
                if (d == null) continue;
                if (d.passive()) {
                    // Патч-панель: гнездо k спереди = гнездо k сзади = корд k; кронштейн — все гнёзда и линии вместе;
                    // розетка — гнёзда своей линии и та же линия розетки на другой стороне стены
                    for (LanData.Plug other : passiveSides(data, pl, d)) {
                        if (!seenPlugs.add(other)) continue;
                        seed(data, other.pos(), other.port(), wires, plugs, done, cordCells);
                    }
                } else {
                    out.add(pl);
                }
            }
        }
        int length = cells.size() + cordCells[0];
        return new Segment(port, out, length <= (copper[0] ? MAX_SEGMENT : MAX_FIBER), length);
    }

    /** Пассивное устройство: какие ещё порты (свои и соседа) соединены с портом pl. */
    static List<LanData.Plug> passiveSides(LanData data, LanData.Plug pl, LanData.Device d) {
        List<LanData.Plug> out = new ArrayList<>();
        if (d.type() == LanData.BRACKET) {
            for (int k = 0; k < LanData.PORTS; k++) out.add(new LanData.Plug(pl.pos(), k));
        } else if (d.type() == LanData.OUTLET) {
            int line = OutletBlock.line(pl.port());
            outletLine(out, pl.pos(), d, line);
            Direction a = Direction.from3DDataValue(d.attached());
            BlockPos partner = pl.pos().relative(a, 2);
            LanData.Device pd = data.device(partner);
            if (pd != null && pd.type() == LanData.OUTLET && pd.attached() == a.getOpposite().get3DDataValue()) {
                outletLine(out, partner.immutable(), pd, line);
            }
        } else {
            int ch = pl.port() < LanData.CABLE_PORTS ? pl.port() % PatchPanelBlock.CHANNELS : pl.port() - LanData.CABLE_PORTS;
            for (int side : new int[]{ch, PatchPanelBlock.CHANNELS + ch, LanData.CABLE_PORTS + ch}) out.add(new LanData.Plug(pl.pos(), side));
        }
        return out;
    }

    /** Все гнёзда и места для кордов одной линии розетки. */
    private static void outletLine(List<LanData.Plug> out, BlockPos pos, LanData.Device d, int line) {
        int n = Math.min(LanData.CABLE_PORTS, d.mask() & 255);
        for (int p = line; p < n; p += 2) out.add(new LanData.Plug(pos, p));
        for (int p = LanData.CABLE_PORTS + line; p < LanData.PORTS; p += 2) out.add(new LanData.Plug(pos, p));
    }

    /** Розетка на другой стороне стены, соединённая с этой (или null). */
    public static BlockPos outletPartner(LanData data, BlockPos pos) {
        LanData.Device d = data.device(pos);
        if (d == null || d.type() != LanData.OUTLET) return null;
        Direction a = Direction.from3DDataValue(d.attached());
        BlockPos partner = pos.relative(a, 2);
        LanData.Device pd = data.device(partner);
        return pd != null && pd.type() == LanData.OUTLET && pd.attached() == a.getOpposite().get3DDataValue() ? partner : null;
    }

    /**
     * Прозвонка кабельным тестером: всё, что соединено с портом устройства (или с проводом), — провода с их клетками,
     * лотки, корды, пассивные устройства по дороге (патч-панели, розетки, кронштейны), устройства на концах и места,
     * где провод обрывается (конец провода ни с чем не соединён). Идёт по схеме — и через незагруженные места.
     */
    public record Trace(List<long[]> wires, Set<Long> trays, List<LanData.Cord> cords, Set<BlockPos> passives,
                        List<LanData.Plug> ends, List<BlockPos> open, int length, boolean copper) {
    }

    public static Trace trace(LanData data, BlockPos startDev, int startPort, BlockPos startCell, int startWire) {
        List<long[]> wireList = new ArrayList<>();
        Set<Long> trayCells = new HashSet<>();
        List<LanData.Cord> cordList = new ArrayList<>();
        Set<BlockPos> passives = new HashSet<>();
        List<LanData.Plug> ends = new ArrayList<>();
        List<BlockPos> open = new ArrayList<>();
        Set<LanData.Plug> seenPlugs = new HashSet<>();
        Set<Long> done = new HashSet<>();
        Set<Long> cells = new HashSet<>();
        Set<Long> trayDone = new HashSet<>();
        ArrayDeque<long[]> wires = new ArrayDeque<>();
        ArrayDeque<long[]> trayq = new ArrayDeque<>();
        ArrayDeque<LanData.Plug> plugs = new ArrayDeque<>();
        int[] cordCells = {0};
        boolean copper = false;
        java.util.function.BiConsumer<BlockPos, Integer> seedPort = (dev, port) -> {
            seed(data, dev, port, wires, plugs, done, cordCells);
            for (LanData.Cord c : data.cords(dev)) if (c.other(dev, port) != null && !cordList.contains(c)) cordList.add(c);
        };
        if (startDev != null) {
            seenPlugs.add(new LanData.Plug(startDev, startPort));
            seedPort.accept(startDev, startPort);
        } else if (startCell != null && done.add(key(startCell, startWire))) {
            wires.add(new long[]{startCell.asLong(), startWire});
        }
        int guard = 0;
        while ((!wires.isEmpty() || !plugs.isEmpty() || !trayq.isEmpty()) && guard++ < 20000) {
            while (!trayq.isEmpty()) {
                long[] cur = trayq.poll();
                BlockPos p = BlockPos.of(cur[0]);
                trayCells.add(cur[0]);
                if (trayCells.size() + cells.size() > MAX_FIBER + 100) {
                    wires.clear();
                    trayq.clear();
                    plugs.clear();
                    break;
                }
                int tw = (int) cur[1];
                if ((tw & 16) == 0) copper = true;
                for (Direction d : Direction.values()) {
                    BlockPos n = p.relative(d);
                    int[] tray = data.tray(n.asLong());
                    if (tray != null) {
                        for (int t : tray) if (t == tw && trayDone.add(n.asLong() * 32 + t)) trayq.add(new long[]{n.asLong(), t});
                        continue;
                    }
                    long[] other = data.cable(n.asLong());
                    if (other == null) continue;
                    for (int j = 0; j < other.length; j++) {
                        Wire o = Wire.unpack(other[j]);
                        int k = o.index(d.getOpposite());
                        if (k >= 0 && o.conn[k] == Wire.FLAT && o.color == (tw & 15) && o.fiber == ((tw & 16) != 0) && done.add(key(n, j))) {
                            wires.add(new long[]{n.asLong(), j});
                        }
                    }
                }
            }
            while (!wires.isEmpty()) {
                long[] cur = wires.poll();
                BlockPos p = BlockPos.of(cur[0]);
                long[] here = data.cable(cur[0]);
                if (here == null || cur[1] >= here.length) continue;
                cells.add(cur[0]);
                if (cells.size() > MAX_FIBER + 100) {
                    wires.clear();
                    trayq.clear();
                    plugs.clear();
                    break;
                }
                Wire w = Wire.unpack(here[(int) cur[1]]);
                wireList.add(new long[]{cur[0], here[(int) cur[1]]});
                if (!w.fiber) copper = true;
                Direction[] ds = w.dirs();
                int links = 0;
                for (int i = 0; i < 4; i++) {
                    Direction d = ds[i];
                    if (w.port(i) >= 0) {
                        links++;
                        plugs.add(new LanData.Plug(p.relative(d).immutable(), w.port(i)));
                        continue;
                    }
                    Object[] t = target(p, w, i);
                    if (t == null) continue;
                    links++;
                    BlockPos n = (BlockPos) t[0];
                    Direction face = (Direction) t[1];
                    if (w.conn[i] == Wire.FLAT) {
                        int[] tray = data.tray(n.asLong());
                        if (tray != null) {
                            int tw = w.color | (w.fiber ? 16 : 0);
                            for (int tt : tray) if (tt == tw && trayDone.add(n.asLong() * 32 + tt)) trayq.add(new long[]{n.asLong(), tt});
                            continue;
                        }
                    }
                    long[] other = data.cable(n.asLong());
                    if (other == null) continue;
                    int edge = Wire.edgeAxis(w.face, d).ordinal();
                    for (int j = 0; j < other.length; j++) {
                        Wire o = Wire.unpack(other[j]);
                        if (o.face == face && o.sameKind(w) && o.lane[edge] == w.lane[edge] && done.add(key(n, j))) {
                            wires.add(new long[]{n.asLong(), j});
                        }
                    }
                }
                // Конец провода ни к чему не подходит — обрыв (у отдельного куска — оба конца)
                if (links < 2 && open.size() < 16) open.add(p.immutable());
            }
            while (!plugs.isEmpty()) {
                LanData.Plug pl = plugs.poll();
                if (!seenPlugs.add(pl)) continue;
                LanData.Device d = data.device(pl.pos());
                if (d == null) continue;
                if (d.passive()) {
                    passives.add(pl.pos());
                    for (LanData.Plug other : passiveSides(data, pl, d)) {
                        if (!seenPlugs.add(other)) continue;
                        if (!other.pos().equals(pl.pos())) passives.add(other.pos());
                        seedPort.accept(other.pos(), other.port());
                    }
                } else if (ends.size() < 32) {
                    ends.add(pl);
                }
            }
        }
        int length = cells.size() + trayCells.size() + cordCells[0];
        return new Trace(wireList, trayCells, cordList, passives, ends, open, length, copper || wireList.isEmpty());
    }

    /** Что воткнуто в порт: провода с вилкой в этот порт и патч-корды. */
    private static void seed(LanData data, BlockPos dev, int port, ArrayDeque<long[]> wires, ArrayDeque<LanData.Plug> plugs,
                             Set<Long> done, int[] cordCells) {
        if (port < LanData.CABLE_PORTS) {
            for (PlugWire pw : plugWires(data, dev)) {
                if (pw.port() == port && done.add(key(pw.cell(), pw.index()))) wires.add(new long[]{pw.cell().asLong(), pw.index()});
            }
        }
        for (LanData.Cord c : data.cords(dev)) {
            LanData.Plug other = c.other(dev, port);
            if (other != null) {
                plugs.add(other);
                cordCells[0] += c.length();
            }
        }
    }

    // ======================= Сообщения =======================

    /** Из потока программы: поставить сообщение в очередь (доставит тик сервера). */
    public void post(HostRef from, String target, int port, Object[] payload, int bytes) {
        outbox.add(new Outgoing(from, target, port, payload, bytes));
    }

    /** Подходит ли компьютер под адрес: полный адрес, его начало (от 4 знаков) или имя. */
    public static boolean matches(String target, String address, String name) {
        if (target == null) return true;
        String t = target.toLowerCase(Locale.ROOT);
        if (address.equalsIgnoreCase(t)) return true;
        if (t.length() >= 4 && address.toLowerCase(Locale.ROOT).startsWith(t)) return true;
        return name != null && !name.isEmpty() && name.equalsIgnoreCase(target);
    }

    /** Когда последний раз щёлкал коммутатор или трещала мачта (чтобы не сливалось в шум). */
    private final Map<BlockPos, Long> lastSound = new HashMap<>();
    private final Map<BlockPos, Long> blinks = new HashMap<>();

    private Long lastBlink(BlockPos p) {
        if (blinks.size() > 512) blinks.clear();
        return blinks.get(p);
    }
    private long soundTick;
    private int soundsThisTick;

    /** Сообщение пошло по сети: щёлкают коммутаторы и роутеры на пути, трещат радиомачты. */
    private void networkSounds(ServerLevel level, HostRef from, long now) {
        if (now != soundTick) {
            soundTick = now;
            soundsThisTick = 0;
            if (lastSound.size() > 512) lastSound.values().removeIf(t -> now - t > 200);
        }
        LanData data = LanData.get(level);
        for (BlockPos p : result(level, from).switches()) {
            if (soundsThisTick >= 6) return;
            LanData.Device d = data.device(p);
            if (d == null || !level.isLoaded(p)) continue;
            boolean radio = d.type() == LanData.RADIO;
            // Лампочки гнёзд мигают у всех, кто рядом (не чаще раза в 3 тика на устройство)
            Long lastBlink = lastBlink(p);
            if (lastBlink == null || now - lastBlink >= 3) {
                blinks.put(p, now);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(level, null, p.getX() + 0.5, p.getY() + 0.5,
                        p.getZ() + 0.5, 32, new com.strikerr212.minepc.network.LanActivityPayload(p));
            }
            Long last = lastSound.get(p);
            if (last != null && now - last < (radio ? 30 : 5)) continue;
            lastSound.put(p, now);
            soundsThisTick++;
            level.playSound(null, p, (radio ? com.strikerr212.minepc.registry.ModSounds.LAN_RADIO
                            : com.strikerr212.minepc.registry.ModSounds.LAN_RELAY).get(), net.minecraft.sounds.SoundSource.BLOCKS,
                    radio ? 0.6f : 0.25f, 0.9f + level.random.nextFloat() * 0.2f);
        }
    }

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now % 40 == 0) CordGuard.check(level);
        if (now % 40 == 20) weather(level, now);
        Outgoing o;
        while ((o = outbox.poll()) != null) {
            NetEndpoint src = level.isLoaded(o.from.pos()) ? com.strikerr212.minepc.block.Hosts.endpoint(level, o.from) : null;
            if (src == null) continue;
            String fromAddr = src.address();
            networkSounds(level, o.from, now);
            String target = o.target;
            if (DnsRegistry.isName(target)) {             // Майннет: shop.mine → адрес владельца имени
                target = DnsRegistry.get(level).lookup(target);
                if (target == null) continue;
            }
            for (Reach r : reach(level, o.from)) {
                // Компьютер в незагруженном чанке выключен — ему некому ответить (провода по дороге — неважно)
                NetEndpoint dst = level.isLoaded(r.pos) ? com.strikerr212.minepc.block.Hosts.endpoint(level, r.host()) : null;
                if (dst == null) continue;
                if (!matches(target, dst.address(), dst.label())) continue;
                // Сообщения между двумя компьютерами идут по линии друг за другом: следующее начинает передаваться,
                // когда ушло предыдущее (по самой медленной связи на пути), плюс по тику на каждый коммутатор.
                // Так большие куски не отстают от маленьких, а скорость линии — настоящая.
                double bytesPerTick = Math.max(1.0, r.bits / 800.0 / Computer.TICKS_PER_SECOND);
                Pipe pipe = new Pipe(o.from, r.host());
                double start = Math.max(now, pipes.getOrDefault(pipe, 0.0));
                if (start - now > MAX_BACKLOG) continue;          // линия забита — сообщение теряется
                double end = start + o.bytes / bytesPerTick;
                pipes.put(pipe, end);
                pending.add(new Delivery((long) Math.floor(end) + r.hops, deliverySeq++, r.host(), fromAddr, o.port, r.hops,
                        o.payload, o.bytes));
            }
        }
        if (now % 200 == 0 && !pipes.isEmpty()) pipes.values().removeIf(t -> t < now);
        while (!pending.isEmpty() && pending.peek().tick <= now) {
            Delivery d = pending.poll();
            NetEndpoint dst = level.isLoaded(d.to.pos()) ? com.strikerr212.minepc.block.Hosts.endpoint(level, d.to) : null;
            if (dst != null) {
                dst.netReceive(d.from, d.port, d.hops, d.payload, d.bytes);
            }
        }
    }
}
