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
 * Провода, соединённые между собой, образуют «отрезок»: все устройства, в которые воткнуты его вилки, слышат друг друга.
 * Отрезок длиннее 100 блоков не работает (как 100 метров у Ethernet). Коммутатор соединяет отрезки во всех своих гнёздах
 * и добавляет 1 тик задержки. Схема пересчитывается только когда меняются кабели, коммутаторы или корпуса.
 *
 * Программы отправляют сообщения из своих потоков — они только встают в очередь; доставка идёт в тике сервера.
 */
public final class LanManager {

    /** Самый длинный отрезок кабеля без коммутатора, блоков. */
    public static final int MAX_SEGMENT = 100;

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

    private record Outgoing(BlockPos from, String target, int port, Object[] payload, int bytes) {
    }

    private record Delivery(long tick, BlockPos to, String from, int port, int hops, Object[] payload, int bytes) {
    }

    private volatile long version;
    private long cachedVersion = -1;
    private final Map<BlockPos, List<Reach>> reachCache = new HashMap<>();
    private final ConcurrentLinkedQueue<Outgoing> outbox = new ConcurrentLinkedQueue<>();
    private final PriorityQueue<Delivery> pending = new PriorityQueue<>((a, b) -> Long.compare(a.tick, b.tick));

    // ======================= Схема =======================

    /** Все компьютеры, до которых можно дойти из корпуса casePos (сам он не входит). */
    public List<Reach> reach(ServerLevel level, BlockPos casePos) {
        if (cachedVersion != version) {
            reachCache.clear();
            cachedVersion = version;
        }
        return reachCache.computeIfAbsent(casePos.immutable(), p -> computeReach(level, p));
    }

    /** Порт корпуса подключён (хотя бы к одному работающему отрезку). */
    public boolean linked(ServerLevel level, BlockPos casePos) {
        return !segmentsAt(level, casePos, level.getBlockState(casePos)).isEmpty();
    }

    private List<Reach> computeReach(ServerLevel level, BlockPos start) {
        BlockState startState = level.getBlockState(start);
        if (!(startState.getBlock() instanceof LanDevice self)) return List.of();
        long startBits = self.lanBitsPerSecond(level, start, startState);
        if (startBits <= 0) return List.of();
        Map<BlockPos, Reach> best = new HashMap<>();
        Set<BlockPos> visitedSwitches = new HashSet<>();
        ArrayDeque<Reach> queue = new ArrayDeque<>();
        queue.add(new Reach(start, 0, startBits));
        while (!queue.isEmpty()) {
            Reach cur = queue.poll();
            BlockState cs = level.getBlockState(cur.pos);
            for (Set<BlockPos> segment : segmentsAt(level, cur.pos, cs)) {
                for (BlockPos dev : segment) {
                    if (dev.equals(cur.pos) || dev.equals(start)) continue;
                    BlockState ds = level.getBlockState(dev);
                    if (!(ds.getBlock() instanceof LanDevice d)) continue;
                    long bits = Math.min(cur.bits, d.lanBitsPerSecond(level, dev, ds));
                    if (bits <= 0) continue;
                    if (d.lanKind() == LanDevice.Kind.COMPUTER) {
                        Reach r = new Reach(dev, cur.hops, bits);
                        Reach old = best.get(dev);
                        if (old == null || r.hops < old.hops || (r.hops == old.hops && r.bits > old.bits)) best.put(dev, r);
                    } else {
                        for (BlockPos sw : switchGroup(level, dev)) {
                            if (visitedSwitches.add(sw)) queue.add(new Reach(sw, cur.hops + 1, bits));
                        }
                    }
                }
            }
        }
        return new ArrayList<>(best.values());
    }

    /** Коммутатор и стоечные коммутаторы, стоящие с ним вплотную (они работают как один). */
    private static List<BlockPos> switchGroup(ServerLevel level, BlockPos start) {
        List<BlockPos> out = new ArrayList<>();
        out.add(start);
        BlockState s = level.getBlockState(start);
        if (!(s.getBlock() instanceof LanDevice d) || !d.joinsNeighbours()) return out;
        ArrayDeque<BlockPos> q = new ArrayDeque<>(out);
        Set<BlockPos> seen = new HashSet<>(out);
        while (!q.isEmpty() && seen.size() < 256) {
            BlockPos p = q.poll();
            for (Direction dir : Direction.values()) {
                BlockPos n = p.relative(dir);
                if (!level.isLoaded(n) || seen.contains(n)) continue;
                if (level.getBlockState(n).getBlock() == s.getBlock()) {
                    seen.add(n);
                    q.add(n);
                    out.add(n);
                }
            }
        }
        return out;
    }

    /**
     * Отрезки кабеля, вставленные в гнёзда устройства: для каждого — устройства на нём.
     * Отрезок длиннее {@link #MAX_SEGMENT} блоков пропускается (сигнал не доходит).
     */
    private static List<Set<BlockPos>> segmentsAt(ServerLevel level, BlockPos dev, BlockState state) {
        List<Set<BlockPos>> out = new ArrayList<>();
        if (!(state.getBlock() instanceof LanDevice d)) return out;
        Set<Long> done = new HashSet<>();
        for (Direction face : Direction.Plane.HORIZONTAL) {
            if (!d.acceptsCable(state, face)) continue;
            BlockPos cp = dev.relative(face);
            if (!level.isLoaded(cp) || !(level.getBlockEntity(cp) instanceof CableBlockEntity cable)) continue;
            for (int i = 0; i < cable.wires().size(); i++) {
                Wire w = cable.wires().get(i);
                if ((w.plugs & Wire.bit(face.getOpposite())) == 0 || done.contains(key(cp, i))) continue;
                Set<BlockPos> devices = new HashSet<>();
                int cells = walk(level, cp, i, done, devices);
                if (cells <= MAX_SEGMENT) out.add(devices);
            }
        }
        return out;
    }

    private static long key(BlockPos p, int wire) {
        return p.asLong() * 4 + wire;
    }

    /** Пройти по соединённым проводам: собрать устройства с вилками; вернуть длину (блоков). */
    private static int walk(ServerLevel level, BlockPos startPos, int startWire, Set<Long> done, Set<BlockPos> devices) {
        ArrayDeque<long[]> q = new ArrayDeque<>();
        q.add(new long[]{startPos.asLong(), startWire});
        done.add(key(startPos, startWire));
        Set<Long> cells = new HashSet<>();
        while (!q.isEmpty()) {
            long[] cur = q.poll();
            BlockPos p = BlockPos.of(cur[0]);
            cells.add(cur[0]);
            if (cells.size() > MAX_SEGMENT + 1) return cells.size();
            if (!(level.getBlockEntity(p) instanceof CableBlockEntity be) || cur[1] >= be.wires().size()) continue;
            Wire w = be.wires().get((int) cur[1]);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                int bit = Wire.bit(d);
                if ((w.dirs & bit) == 0) continue;
                BlockPos n = p.relative(d);
                if (!level.isLoaded(n)) continue;
                if ((w.plugs & bit) != 0) {
                    devices.add(n.immutable());
                    continue;
                }
                if (!(level.getBlockEntity(n) instanceof CableBlockEntity other)) continue;
                int lane = w.laneToward(d);
                for (int j = 0; j < other.wires().size(); j++) {
                    Wire o = other.wires().get(j);
                    if (o.color == w.color && o.laneToward(d.getOpposite()) == lane && done.add(key(n, j))) {
                        q.add(new long[]{n.asLong(), j});
                    }
                }
            }
        }
        return cells.size();
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

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        Outgoing o;
        while ((o = outbox.poll()) != null) {
            if (!(level.getBlockEntity(o.from) instanceof PcCaseBlockEntity src)) continue;
            String fromAddr = src.address();
            for (Reach r : reach(level, o.from)) {
                if (!(level.getBlockEntity(r.pos) instanceof PcCaseBlockEntity dst)) continue;
                if (!matches(o.target, dst.address(), dst.label())) continue;
                // Задержка: по тику на коммутатор, плюс время передачи по самой медленной связи
                long bytesPerTick = Math.max(1, r.bits / 800 / Computer.TICKS_PER_SECOND);
                long delay = Math.max(1, r.hops) + o.bytes / bytesPerTick;
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
