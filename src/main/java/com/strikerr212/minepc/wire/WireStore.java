package com.strikerr212.minepc.wire;

import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Копия проводов у игрока (то, что прислал сервер). Лежит в общем коде, чтобы проверки гнёзд работали одинаково,
 * но заполняется только у игрока; на сервере пустая.
 */
public final class WireStore {

    private WireStore() {
    }

    private static final Map<Long, WireLine> lines = new ConcurrentHashMap<>();
    private static final Map<Long, Set<Long>> byDevice = new HashMap<>();

    public static Collection<WireLine> all() {
        return lines.values();
    }

    public static WireLine get(long id) {
        return lines.get(id);
    }

    public static synchronized void put(WireLine l) {
        WireLine old = lines.put(l.id, l);
        if (old != null) index(old, false);
        index(l, true);
    }

    public static synchronized void remove(long id) {
        WireLine old = lines.remove(id);
        if (old != null) index(old, false);
    }

    public static synchronized void clear() {
        lines.clear();
        byDevice.clear();
    }

    private static void index(WireLine l, boolean add) {
        for (BlockPos d : new BlockPos[]{l.devA, l.devB}) {
            if (d == null) continue;
            if (add) byDevice.computeIfAbsent(d.asLong(), k -> new HashSet<>()).add(l.id);
            else {
                Set<Long> s = byDevice.get(d.asLong());
                if (s != null && s.remove(l.id) && s.isEmpty()) byDevice.remove(d.asLong());
            }
        }
    }

    /** Провод в гнезде port устройства dev (у игрока). */
    public static synchronized WireLine at(BlockPos dev, int port) {
        Set<Long> s = byDevice.get(dev.asLong());
        if (s == null) return null;
        for (long id : s) {
            WireLine l = lines.get(id);
            if (l != null && l.pluggedInto(dev, port)) return l;
        }
        return null;
    }
}
