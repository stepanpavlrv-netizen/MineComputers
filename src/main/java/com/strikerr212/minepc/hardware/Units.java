package com.strikerr212.minepc.hardware;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Красивый вывод чисел с единицами измерения (МБ/ГБ, МГц/ГГц, Вт...). */
public final class Units {

    private Units() {
    }

    public static Component size(int mb) {
        if (mb >= 1024) {
            String gb = mb % 1024 == 0
                    ? String.valueOf(mb / 1024)
                    : String.format(Locale.ROOT, "%.1f", mb / 1024.0);
            return Component.translatable("unit.minepc.gb", gb);
        }
        return Component.translatable("unit.minepc.mb", mb);
    }

    public static Component frequency(int mhz) {
        if (mhz >= 1000) {
            return Component.translatable("unit.minepc.ghz",
                    String.format(Locale.ROOT, "%.1f", mhz / 1000.0));
        }
        return Component.translatable("unit.minepc.mhz", mhz);
    }

    public static Component watts(int w) {
        return Component.translatable("unit.minepc.watts", w);
    }

    public static Component speed(int mbs) {
        return Component.translatable("unit.minepc.mbs", mbs);
    }

    /** Число с точностью, которая имеет смысл: 0.84, 8.4, 84, 840. */
    public static String num(double x) {
        double a = Math.abs(x);
        if (a >= 100) return String.valueOf(Math.round(x));
        if (a >= 10) return String.format(Locale.ROOT, "%.1f", x);
        return String.format(Locale.ROOT, "%.2f", x);
    }

    /** Операций в секунду: миллионы, миллиарды или триллионы. */
    public static Component ops(double gips) {
        if (gips < 1) return Component.translatable("unit.minepc.mops", num(gips * 1000));
        if (gips >= 1000) return Component.translatable("unit.minepc.tops", num(gips / 1000));
        return Component.translatable("unit.minepc.gops", num(gips));
    }

    public static Component tflops(double tflops) {
        if (tflops < 1) return Component.translatable("unit.minepc.gflops", num(tflops * 1000));
        return Component.translatable("unit.minepc.tflops", num(tflops));
    }

    public static Component bandwidth(double gbs) {
        return Component.translatable("unit.minepc.gbs", num(gbs));
    }

    /** Операций ввода-вывода в секунду: у HDD сотни, у SSD сотни тысяч. */
    public static Component iops(int iops) {
        if (iops >= 10_000) return Component.translatable("unit.minepc.kiops", Math.round(iops / 1000.0));
        return Component.translatable("unit.minepc.iops", iops);
    }

    public static Component seconds(double s) {
        return Component.translatable("unit.minepc.seconds", s < 10 ? String.format(Locale.ROOT, "%.1f", s)
                : String.valueOf(Math.round(s)));
    }

    public static Component bits(int bits) {
        return Component.translatable("unit.minepc.bits", bits);
    }

    public static Component percent(double fraction) {
        return Component.literal(Math.round(fraction * 100) + "%");
    }

    /** «≈ ур. 3.4» — на какой заводской уровень похожа скорость. */
    public static Component level(double lv) {
        return Component.translatable("unit.minepc.level", String.format(Locale.ROOT, "%.1f", lv));
    }
}
