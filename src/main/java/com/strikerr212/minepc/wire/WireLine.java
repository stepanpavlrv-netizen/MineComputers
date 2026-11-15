package com.strikerr212.minepc.wire;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Провод — один объект: вид (медь или оптика), цвет, бирка, два конца и путь.
 *
 * <p>Путь — узлы в единицах 1/32 блока (центр пикселя — нечётное число, точки гнёзд — любые), между соседними узлами
 * прямой отрезок. Отрезок либо лежит (вдоль блоков), либо висит ({@link #hang}) — тогда он рисуется дугой.
 * Конец либо воткнут в гнездо устройства (устройство и порт), либо свободный (устройства нет) — такой провод
 * можно продолжить или срастить с другим.
 *
 * <p>Изменяется только заменой целиком: сервер собирает новый объект и рассылает его.
 */
public final class WireLine {

    /** Единиц в блоке. */
    public static final int UNITS = 32;
    /** Больше узлов в одном проводе не бывает. */
    public static final int MAX_NODES = 2048;

    public final long id;
    public final boolean fiber;
    public final int color;
    public final String label;
    public final int[] xs, ys, zs;
    public final boolean[] hang;
    /** Концы: у узла 0 и у последнего узла. null — свободный конец. */
    public final BlockPos devA, devB;
    public final int portA, portB;

    public WireLine(long id, boolean fiber, int color, String label, int[] xs, int[] ys, int[] zs, boolean[] hang,
                    BlockPos devA, int portA, BlockPos devB, int portB) {
        this.id = id;
        this.fiber = fiber;
        this.color = color;
        this.label = label == null ? "" : label;
        this.xs = xs;
        this.ys = ys;
        this.zs = zs;
        this.hang = hang;
        this.devA = devA;
        this.portA = devA == null ? -1 : portA;
        this.devB = devB;
        this.portB = devB == null ? -1 : portB;
    }

    public int nodes() {
        return xs.length;
    }

    public Vec3 node(int i) {
        return new Vec3(xs[i] / (double) UNITS, ys[i] / (double) UNITS, zs[i] / (double) UNITS);
    }

    public Vec3 end(boolean b) {
        return node(b ? xs.length - 1 : 0);
    }

    public BlockPos dev(boolean b) {
        return b ? devB : devA;
    }

    public int port(boolean b) {
        return b ? portB : portA;
    }

    public boolean free(boolean b) {
        return dev(b) == null;
    }

    /** Воткнут ли этот провод в гнездо port устройства pos. */
    public boolean pluggedInto(BlockPos pos, int port) {
        return (devA != null && portA == port && devA.equals(pos)) || (devB != null && portB == port && devB.equals(pos));
    }

    /** Длина, блоков (висящие отрезки — с запасом на провис). */
    public double length() {
        double sum = 0;
        for (int i = 1; i < xs.length; i++) {
            double dx = xs[i] - xs[i - 1], dy = ys[i] - ys[i - 1], dz = zs[i] - zs[i - 1];
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz) / UNITS;
            sum += hang[i - 1] ? d * 1.04 : d;
        }
        return sum;
    }

    /** Сколько кабеля в проводе, м (как в мотке: 1 м — 1 блок). */
    public int meters() {
        return Math.max(1, (int) Math.ceil(length() - 0.01));
    }

    public AABB bounds() {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (int i = 0; i < xs.length; i++) {
            x0 = Math.min(x0, xs[i]);
            y0 = Math.min(y0, ys[i]);
            z0 = Math.min(z0, zs[i]);
            x1 = Math.max(x1, xs[i]);
            y1 = Math.max(y1, ys[i]);
            z1 = Math.max(z1, zs[i]);
        }
        double u = UNITS;
        return new AABB(x0 / u, y0 / u, z0 / u, x1 / u, y1 / u, z1 / u).inflate(0.1, 0.6, 0.1);
    }

    /** Чанки, через которые проходит провод (по ним он рассылается игрокам). */
    public Set<Long> chunks() {
        Set<Long> out = new HashSet<>();
        for (int i = 0; i < xs.length; i++) {
            out.add(chunkKey(xs[i], zs[i]));
            if (i > 0) {
                // длинный отрезок может пересечь чанк, не оставив в нём узла
                int n = (int) (Math.max(Math.abs(xs[i] - xs[i - 1]), Math.abs(zs[i] - zs[i - 1])) / (8.0 * UNITS)) + 1;
                for (int k = 1; k < n; k++) {
                    out.add(chunkKey(xs[i - 1] + (xs[i] - xs[i - 1]) * k / n, zs[i - 1] + (zs[i] - zs[i - 1]) * k / n));
                }
            }
        }
        return out;
    }

    private static long chunkKey(int ux, int uz) {
        return net.minecraft.world.level.ChunkPos.asLong(Math.floorDiv(ux, UNITS * 16), Math.floorDiv(uz, UNITS * 16));
    }

    public WireLine withColor(int color) {
        return new WireLine(id, fiber, color, label, xs, ys, zs, hang, devA, portA, devB, portB);
    }

    /** Тот же провод, но конец b теперь свободный (устройство убрали). */
    public WireLine unplugged(boolean b) {
        return b ? new WireLine(id, fiber, color, label, xs, ys, zs, hang, devA, portA, null, -1)
                : new WireLine(id, fiber, color, label, xs, ys, zs, hang, null, -1, devB, portB);
    }

    /** Тот же провод задом наперёд. */
    public WireLine reversed() {
        int n = xs.length;
        int[] x = new int[n], y = new int[n], z = new int[n];
        boolean[] h = new boolean[Math.max(0, n - 1)];
        for (int i = 0; i < n; i++) {
            x[i] = xs[n - 1 - i];
            y[i] = ys[n - 1 - i];
            z[i] = zs[n - 1 - i];
        }
        for (int i = 0; i < n - 1; i++) h[i] = hang[n - 2 - i];
        return new WireLine(id, fiber, color, label, x, y, z, h, devB, portB, devA, portA);
    }

    // ======================= Хранение =======================

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("id", id);
        if (fiber) t.putBoolean("f", true);
        t.putInt("c", color);
        if (!label.isEmpty()) t.putString("n", label);
        t.putIntArray("x", xs);
        t.putIntArray("y", ys);
        t.putIntArray("z", zs);
        byte[] h = new byte[hang.length];
        for (int i = 0; i < h.length; i++) h[i] = (byte) (hang[i] ? 1 : 0);
        t.putByteArray("h", h);
        if (devA != null) {
            t.putLong("a", devA.asLong());
            t.putInt("pa", portA);
        }
        if (devB != null) {
            t.putLong("b", devB.asLong());
            t.putInt("pb", portB);
        }
        return t;
    }

    public static WireLine load(CompoundTag t) {
        int[] x = t.getIntArray("x"), y = t.getIntArray("y"), z = t.getIntArray("z");
        if (x.length < 2 || y.length != x.length || z.length != x.length) return null;
        byte[] hb = t.getByteArray("h");
        boolean[] h = new boolean[x.length - 1];
        for (int i = 0; i < h.length && i < hb.length; i++) h[i] = hb[i] != 0;
        return new WireLine(t.getLong("id"), t.getBoolean("f"), t.getInt("c"), t.getString("n"), x, y, z, h,
                t.contains("a") ? BlockPos.of(t.getLong("a")) : null, t.getInt("pa"),
                t.contains("b") ? BlockPos.of(t.getLong("b")) : null, t.getInt("pb"));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeLong(id);
        buf.writeBoolean(fiber);
        buf.writeByte(color);
        buf.writeUtf(label, 64);
        buf.writeVarInt(xs.length);
        for (int i = 0; i < xs.length; i++) {
            buf.writeVarInt(xs[i]);
            buf.writeVarInt(ys[i]);
            buf.writeVarInt(zs[i]);
            if (i < hang.length) buf.writeBoolean(hang[i]);
        }
        buf.writeBoolean(devA != null);
        if (devA != null) {
            buf.writeBlockPos(devA);
            buf.writeVarInt(portA);
        }
        buf.writeBoolean(devB != null);
        if (devB != null) {
            buf.writeBlockPos(devB);
            buf.writeVarInt(portB);
        }
    }

    public static WireLine read(FriendlyByteBuf buf) {
        long id = buf.readLong();
        boolean fiber = buf.readBoolean();
        int color = buf.readByte() & 15;
        String label = buf.readUtf(64);
        int n = Math.min(MAX_NODES, buf.readVarInt());
        int[] x = new int[n], y = new int[n], z = new int[n];
        boolean[] h = new boolean[Math.max(0, n - 1)];
        for (int i = 0; i < n; i++) {
            x[i] = buf.readVarInt();
            y[i] = buf.readVarInt();
            z[i] = buf.readVarInt();
            if (i < h.length) h[i] = buf.readBoolean();
        }
        BlockPos a = null, b = null;
        int pa = -1, pb = -1;
        if (buf.readBoolean()) {
            a = buf.readBlockPos();
            pa = buf.readVarInt();
        }
        if (buf.readBoolean()) {
            b = buf.readBlockPos();
            pb = buf.readVarInt();
        }
        return new WireLine(id, fiber, color, label, x, y, z, h, a, pa, b, pb);
    }
}
