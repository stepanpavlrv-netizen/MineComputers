package com.strikerr212.minepc.wire;

import com.strikerr212.minepc.lan.CordShape;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Готовые формы проводов: шнур между двумя гнёздами (патч-корд, перевод старых кордов). */
public final class WireLines {

    private WireLines() {
    }

    private static int[] u(Vec3 v) {
        return new int[]{(int) Math.round(v.x * WireLine.UNITS), (int) Math.round(v.y * WireLine.UNITS), (int) Math.round(v.z * WireLine.UNITS)};
    }

    /** Шнур: из каждого гнезда прямо наружу на хвостик вилки, между хвостиками — провисает. */
    public static WireLine cord(BlockGetter level, long id, BlockPos a, int pa, BlockPos b, int pb, int color, String label) {
        Vec3 va = CordShape.anchor(level, a, pa, b), vb = CordShape.anchor(level, b, pb, a);
        Vec3 na = CordShape.outward(level, a, pa), nb = CordShape.outward(level, b, pb);
        List<int[]> nodes = new ArrayList<>();
        List<Boolean> hang = new ArrayList<>();
        nodes.add(u(va));
        if (na.lengthSqr() > 0) {
            nodes.add(u(va.add(na.scale(CordShape.LEAD))));
            hang.add(false);
        }
        if (nb.lengthSqr() > 0) {
            nodes.add(u(vb.add(nb.scale(CordShape.LEAD))));
            hang.add(true);
            nodes.add(u(vb));
            hang.add(false);
        } else {
            nodes.add(u(vb));
            hang.add(true);
        }
        int n = nodes.size();
        int[] xs = new int[n], ys = new int[n], zs = new int[n];
        boolean[] hh = new boolean[n - 1];
        for (int i = 0; i < n; i++) {
            xs[i] = nodes.get(i)[0];
            ys[i] = nodes.get(i)[1];
            zs[i] = nodes.get(i)[2];
            if (i < n - 1) hh[i] = hang.get(i);
        }
        return new WireLine(id, false, color, label, xs, ys, zs, hh, a.immutable(), pa, b.immutable(), pb);
    }
}
