package com.strikerr212.minepc;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Настройки игрока (config/minepc-client.toml): громкость звуков компьютеров, в процентах. */
public final class MineClientConfig {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue PROGRAMS, HUM, KEYS, DRIVES, NETWORK;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Громкость звуков MineComputers, проценты (0 — выключить). Общая громкость берётся из настроек игры.").push("sound");
        PROGRAMS = b.comment("Звук из программ: пищалка и звуковой чип (громкость категории «Музыкальные блоки»)")
                .defineInRange("programs", 100, 0, 100);
        HUM = b.comment("Гул вентиляторов и жёстких дисков работающих компьютеров")
                .defineInRange("hum", 60, 0, 100);
        KEYS = b.comment("Стук клавиатуры")
                .defineInRange("keyboard", 70, 0, 100);
        DRIVES = b.comment("Треск жёстких дисков и дисководов при чтении и записи")
                .defineInRange("drives", 80, 0, 100);
        NETWORK = b.comment("Щелчки коммутаторов и треск радиосвязи")
                .defineInRange("network", 60, 0, 100);
        b.pop();
        SPEC = b.build();
    }

    private MineClientConfig() {
    }

    private static float get(ModConfigSpec.IntValue v, int def) {
        try {
            return (SPEC.isLoaded() ? v.get() : def) / 100f;
        } catch (IllegalStateException e) {
            return def / 100f;
        }
    }

    public static float programs() { return get(PROGRAMS, 100); }
    public static float hum() { return get(HUM, 60); }
    public static float keys() { return get(KEYS, 70); }
    public static float drives() { return get(DRIVES, 80); }
    public static float network() { return get(NETWORK, 60); }
}
