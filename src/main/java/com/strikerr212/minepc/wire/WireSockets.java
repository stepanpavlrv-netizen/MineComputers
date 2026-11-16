package com.strikerr212.minepc.wire;

import com.strikerr212.minepc.lan.LanData;
import com.strikerr212.minepc.lan.LanDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Гнёзда устройств для проводов: свободно ли гнездо, в какое целится игрок, где его середина. */
public final class WireSockets {

    private WireSockets() {
    }

    /** Воткнут ли провод в гнездо port устройства dev: на сервере — по схеме мира, у игрока — по его копии. */
    public static boolean plugged(BlockGetter level, BlockPos dev, int port) {
        if (level instanceof net.minecraft.server.level.ServerLevel sl) return LanData.get(sl).lineAt(dev, port) != null;
        return WireStore.at(dev, port) != null;
    }

    /** Свободно ли гнездо port устройства dev (гнездо есть, провод в него не воткнут). */
    public static boolean socketFree(BlockGetter level, BlockPos dev, int port) {
        List<LanDevice.Socket> socks = LanDevice.socketsAt(level, dev);
        if (port < 0 || port >= socks.size()) return false;
        return !plugged(level, dev, port);
    }

    /** Гнездо под прицелом (ближайшее на той стороне, куда щёлкнули; иначе ближайшее вообще), свободное и подходящее. */
    public static int socketAt(BlockGetter level, BlockPos dev, Vec3 hit, Direction side, boolean fiber) {
        List<LanDevice.Socket> socks = LanDevice.socketsAt(level, dev);
        Vec3 at = hit.subtract(dev.getX(), dev.getY(), dev.getZ()).scale(16);
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < socks.size(); i++) {
            LanDevice.Socket s = socks.get(i);
            if (!s.accepts(fiber) || !socketFree(level, dev, i)) continue;
            Vec3 c = socketCenter(s);
            double d = c.distanceToSqr(at) + (s.face() == side ? 0 : 400);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** Середина гнезда в пикселях блока устройства. */
    public static Vec3 socketCenter(LanDevice.Socket s) {
        Direction.Axis[] fa = LanDevice.faceAxes(s.face());
        double[] c = new double[3];
        c[fa[0].ordinal()] = s.along() + 0.5;
        c[fa[1].ordinal()] = s.height() + 0.5;
        int n = s.face().getAxis().ordinal();
        c[n] = s.face().getAxisDirection() == Direction.AxisDirection.POSITIVE ? 16 - s.depth() : s.depth();
        return new Vec3(c[0], c[1], c[2]);
    }
}
