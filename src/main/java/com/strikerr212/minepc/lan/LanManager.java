package com.strikerr212.minepc.lan;

import com.strikerr212.minepc.block.PcCaseBlockEntity;
import com.strikerr212.minepc.computer.Computer;
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
    public record Reach(BlockPos pos, int hops, long bits) {
    }

    /** Что видно из компьютера: компьютеры и коммутаторы (роутеры, радиомодемы) на пути. */
    public record Result(List<Reach> computers, List<BlockPos> switches) {
        static final Result EMPTY = new Result(List.of(), List.of());
    }

    /** Отрезок у порта устройства: порты других устройств на нём; ok — не длиннее предела; length — блоков. */
    public record Segment(int port, Set<LanData.Plug> plugs, boolean ok, int length) {
    }

    private record Outgoing(BlockPos from, String target, int port, Object[] payload, int bytes) {
    }

    private record Delivery(long tick, BlockPos to, String from, int port, int hops, Object[] payload, int bytes) {
    }

    private volatile long version;
    private long cachedVersion = -1;
    private final Map<BlockPos, Result> reachCache = new HashMap<>();
    private final ConcurrentLinkedQueue<Outgoing> outbox = new ConcurrentLinkedQueue<>();
    private final PriorityQueue<Delivery> pending = new PriorityQueue<>((a, b) -> Long.compare(a.tick, b.tick));

    // ======================= Устройства =======================

    /** Устройство поставили, сломали или поменялась его скорость: занести в схему сети. */
    public static void updateDevice(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        LanData data = LanData.get(server);
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof LanDevice d) {
            int mask = Math.min(LanData.CABLE_PORTS, d.sockets(s).size());   // сколько гнёзд
            int kbit = (int) Math.min(Integer.MAX_VALUE, d.lanBitsPerSecond(level, pos, s) / 1000L);
            data.putDevice(pos, new LanData.Device(d.lanType(s), kbit, mask, d.lanExtra(level, pos, s)));
        } else {
            // Устройства больше нет: патч-корды — в добычу, у других концов — убрать
            for (LanData.Cord c : data.removeCords(pos)) {
                net.minecraft.world.level.block.Block.popResource(level, pos, c.length() > 4
                        ? new net.minecraft.world.item.ItemStack(com.strikerr212.minepc.registry.ModItems.CABLE.get(), c.length())
                        : new net.minecraft.world.item.ItemStack(com.strikerr212.minepc.registry.ModItems.PATCH_CORD.get()));
                CordList.refresh(server, c.a().equals(pos) ? c.b() : c.a());
            }
            data.removeDevice(pos);
        }
    }

    // ======================= Схема =======================

    /** Все компьютеры, до которых можно дойти из корпуса casePos (сам он не входит). Чанки грузить не нужно. */
    public List<Reach> reach(ServerLevel level, BlockPos casePos) {
        return result(level, casePos).computers();
    }

    /** Коммутаторы, роутеры и радиомодемы, через которые проходит связь компьютера (для программы switch). */
    public List<BlockPos> switches(ServerLevel level, BlockPos casePos) {
        return result(level, casePos).switches();
    }

    private Result result(ServerLevel level, BlockPos casePos) {
        if (cachedVersion != version) {
            reachCache.clear();
            cachedVersion = version;
        }
        return reachCache.computeIfAbsent(casePos.immutable(), p -> computeReach(LanData.get(level), p));
    }

    /** Порт корпуса подключён (хотя бы к одному работающему отрезку). */
    public boolean linked(ServerLevel level, BlockPos casePos) {
        for (Segment s : segmentsAt(LanData.get(level), casePos)) if (s.ok()) return true;
        return false;
    }

    private record Node(BlockPos pos, int hops, long bits, int vlan) {
    }

    private record VKey(BlockPos pos, int vlan) {
    }

    static Result computeReach(LanData data, BlockPos start) {
        LanData.Device self = data.device(start);
        if (self == null) return Result.EMPTY;
        Map<BlockPos, Reach> best = new HashMap<>();
        Set<VKey> visited = new HashSet<>();
        List<BlockPos> switches = new ArrayList<>();
        ArrayDeque<Node> queue = new ArrayDeque<>();
        Visitor v = new Visitor(data, start, best, visited, switches, queue);
        if (self.kbit() > 0) {
            for (Segment s : segmentsAt(data, start)) {
                if (s.ok()) for (LanData.Plug p : s.plugs()) v.plug(p, 0, self.bits());
            }
        }
        // По Wi-Fi: роутер — как коммутатор, к которому мы подключены (общий VLAN)
        LanData.WifiLink own = data.wifiOf(start);
        if (own != null) v.enter(own.router(), 0, 1, own.bits());
        while (!queue.isEmpty()) {
            Node cur = queue.poll();
            LanData.Device dev = data.device(cur.pos);
            if (dev == null) continue;
            if (dev.type() == LanData.ROUTER) {
                for (LanData.WifiLink l : data.wifiClients(cur.pos)) {
                    if (!l.computer().equals(start)) v.computer(l.computer(), cur.hops, Math.min(cur.bits, l.bits()));
                }
            }
            if (dev.type() == LanData.RADIO) {
                for (BlockPos other : radioPeers(data, cur.pos, dev)) {
                    LanData.Device od = data.device(other);
                    v.enter(other, cur.vlan, cur.hops + 1, Math.min(cur.bits, od.bits()));
                }
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

    /** Обход: что делать с найденным портом устройства. */
    private record Visitor(LanData data, BlockPos start, Map<BlockPos, Reach> best, Set<VKey> visited,
                           List<BlockPos> switches, ArrayDeque<Node> queue) {

        void plug(LanData.Plug p, int hops, long bits) {
            if (p.pos().equals(start)) return;
            LanData.Device d = data.device(p.pos());
            if (d == null) return;
            long b = Math.min(bits, d.bits());
            if (b <= 0) return;
            if (d.computer()) {
                computer(p.pos(), hops, b);
            } else {
                int cfg = data.portConfig(p.pos(), p.port());
                if (LanData.portEnabled(cfg)) enter(p.pos(), LanData.portVlan(cfg), hops + 1, b);
            }
        }

        void computer(BlockPos pos, int hops, long bits) {
            Reach r = new Reach(pos, hops, bits);
            Reach old = best.get(pos);
            if (old == null || r.hops < old.hops || (r.hops == old.hops && r.bits > old.bits)) best.put(pos, r);
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

    /** Радиомодемы того же канала, до которых достаёт сигнал (дальность — меньшая из двух). */
    public static List<BlockPos> radioPeers(LanData data, BlockPos pos, LanData.Device dev) {
        List<BlockPos> out = new ArrayList<>();
        int channel = dev.extra() >>> 16, range = dev.extra() & 0xFFFF;
        for (BlockPos other : data.devicesOfType(LanData.RADIO)) {
            if (other.equals(pos)) continue;
            LanData.Device od = data.device(other);
            if (od == null || (od.extra() >>> 16) != channel) continue;
            int r = Math.min(range, od.extra() & 0xFFFF);
            if (other.distSqr(pos) <= (double) r * r) out.add(other);
        }
        return out;
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

    /** Порты, в которые что-то воткнуто: кабель с вилкой или патч-корд. */
    public static List<Integer> usedPorts(LanData data, BlockPos dev, LanData.Device d) {
        List<Integer> out = new ArrayList<>();
        for (PlugWire pw : plugWires(data, dev)) if (pw.port() < LanData.CABLE_PORTS && !out.contains(pw.port())) out.add(pw.port());
        for (int port = LanData.CABLE_PORTS; port < LanData.PORTS; port++) if (data.cordAt(dev, port)) out.add(port);
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
                    // Патч-панель: гнездо k спереди = гнездо k сзади = корд k; кронштейн — все гнёзда и линии вместе
                    int[] sides;
                    if (d.type() == LanData.BRACKET) {
                        sides = new int[LanData.PORTS];
                        for (int k = 0; k < sides.length; k++) sides[k] = k;
                    } else {
                        int ch = pl.port() < LanData.CABLE_PORTS ? pl.port() % PatchPanelBlock.CHANNELS : pl.port() - LanData.CABLE_PORTS;
                        sides = new int[]{ch, PatchPanelBlock.CHANNELS + ch, LanData.CABLE_PORTS + ch};
                    }
                    for (int side : sides) {
                        LanData.Plug other = new LanData.Plug(pl.pos(), side);
                        if (seenPlugs.add(other)) seed(data, pl.pos(), side, wires, plugs, done, cordCells);
                    }
                } else {
                    out.add(pl);
                }
            }
        }
        int length = cells.size() + cordCells[0];
        return new Segment(port, out, length <= (copper[0] ? MAX_SEGMENT : MAX_FIBER), length);
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
    public void post(BlockPos from, String target, int port, Object[] payload, int bytes) {
        outbox.add(new Outgoing(from.immutable(), target, port, payload, bytes));
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
    private void networkSounds(ServerLevel level, BlockPos from, long now) {
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
        Outgoing o;
        while ((o = outbox.poll()) != null) {
            if (!level.isLoaded(o.from) || !(level.getBlockEntity(o.from) instanceof PcCaseBlockEntity src)) continue;
            String fromAddr = src.address();
            networkSounds(level, o.from, now);
            for (Reach r : reach(level, o.from)) {
                // Компьютер в незагруженном чанке выключен — ему некому ответить (провода по дороге — неважно)
                if (!level.isLoaded(r.pos) || !(level.getBlockEntity(r.pos) instanceof PcCaseBlockEntity dst)) continue;
                if (!matches(o.target, dst.address(), dst.label())) continue;
                // Задержка: по тику на коммутатор, плюс время передачи по самой медленной связи
                long bytesPerTick = Math.max(1, r.bits / 800 / Computer.TICKS_PER_SECOND);
                long delay = r.hops + o.bytes / bytesPerTick;
                pending.add(new Delivery(now + delay, r.pos, fromAddr, o.port, r.hops, o.payload, o.bytes));
            }
        }
        while (!pending.isEmpty() && pending.peek().tick <= now) {
            Delivery d = pending.poll();
            if (level.isLoaded(d.to) && level.getBlockEntity(d.to) instanceof PcCaseBlockEntity dst) {
                dst.netReceive(d.from, d.port, d.hops, d.payload, d.bytes);
            }
        }
    }
}
