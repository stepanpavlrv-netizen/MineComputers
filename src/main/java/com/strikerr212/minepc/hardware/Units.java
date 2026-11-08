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
}
