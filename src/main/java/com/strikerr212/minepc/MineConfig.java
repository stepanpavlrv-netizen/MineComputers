package com.strikerr212.minepc;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Настройки сервера (файл serverconfig/minepc-server.toml в папке мира).
 * Администратор сервера решает, сколько мощности отдать компьютерам: на одиночной игре можно
 * разогнать их сильнее, на большом сервере — придержать.
 */
public final class MineConfig {

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.DoubleValue SPEED;
    private static final ModConfigSpec.DoubleValue SINGLEPLAYER_SPEED;
    private static final ModConfigSpec.DoubleValue MEMORY;
    private static final ModConfigSpec.IntValue MAX_MEMORY_MB;
    private static final ModConfigSpec.DoubleValue GPU;
    private static final ModConfigSpec.DoubleValue DISK;
    private static final ModConfigSpec.IntValue MAX_CORES;
    private static final ModConfigSpec.IntValue MAX_PROCESSES;
    private static final ModConfigSpec.IntValue THREADS;
    private static final ModConfigSpec.IntValue SLICE_MS;
    private static final ModConfigSpec.IntValue SCREEN_FPS;
    private static final ModConfigSpec.IntValue SCREEN_KB;
    private static final ModConfigSpec.BooleanValue BOOT_TIME;
    private static final ModConfigSpec.IntValue DISK_WEAR;
    private static final ModConfigSpec.DoubleValue DISK_LIFE_HOURS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Компьютеры MineComputers / MineComputers computers").push("computers");
        SPEED = b.comment("Множитель скорости процессоров на выделенном сервере (команд в секунду)",
                        "CPU speed multiplier on a dedicated server")
                .defineInRange("speedMultiplier", 1.0, 0.1, 20.0);
        SINGLEPLAYER_SPEED = b.comment("Множитель скорости в одиночной игре (компьютер игрока мощнее сервера на одного)",
                        "CPU speed multiplier in singleplayer")
                .defineInRange("singleplayerSpeedMultiplier", 2.0, 0.1, 20.0);
        MEMORY = b.comment("Множитель памяти для программ", "Program memory multiplier")
                .defineInRange("memoryMultiplier", 1.0, 0.1, 16.0);
        MAX_MEMORY_MB = b.comment("Больше этого компьютер не получит, МБ (защита памяти сервера)",
                        "Hard cap of program memory per computer, MB")
                .defineInRange("maxMemoryMb", 256, 1, 4096);
        GPU = b.comment("Множитель скорости отрисовки видеокарт", "GPU fill rate multiplier")
                .defineInRange("gpuMultiplier", 1.0, 0.1, 100.0);
        DISK = b.comment("Множитель скорости накопителей", "Drive speed multiplier")
                .defineInRange("diskMultiplier", 1.0, 0.1, 1000.0);
        MAX_CORES = b.comment("Сколько ядер процессора компьютер может использовать для параллельных процессов",
                        "Max CPU cores a computer may use for parallel processes")
                .defineInRange("maxCores", 8, 1, 64);
        MAX_PROCESSES = b.comment("Сколько процессов можно запустить на одном компьютере",
                        "Max processes per computer")
                .defineInRange("maxProcesses", 32, 1, 256);
        THREADS = b.comment("Потоков сервера для программ (0 — сами решим: до 4, но одно ядро всегда остаётся игре)",
                        "Server threads for computers (0 = auto)")
                .defineInRange("threads", 0, 0, 32);
        SLICE_MS = b.comment("Сколько миллисекунд за тик может работать один процесс (из 50 мс тика)",
                        "Max milliseconds per tick for one process")
                .defineInRange("sliceMs", 20, 2, 45);
        SCREEN_FPS = b.comment("Сколько раз в секунду экран компьютера может обновляться у игроков",
                        "Max screen updates per second sent to players")
                .defineInRange("screenFps", 60, 10, 120);
        SCREEN_KB = b.comment("Сколько КБ/с один компьютер может слать каждому зрителю на сервере (в одиночной игре без ограничения)",
                        "Max screen traffic per computer per viewer, KB/s (unlimited in singleplayer)")
                .defineInRange("screenKbPerSecond", 2048, 64, 65536);
        BOOT_TIME = b.comment("Загрузка системы занимает время (зависит от накопителя: SSD быстрее жёсткого диска)",
                        "System boot takes time depending on the drive")
                .define("bootTime", true);
        DISK_WEAR = b.comment("Износ накопителей: 0 — нет, 1 — только у серверов (по умолчанию), 2 — у всех компьютеров",
                        "Drive wear: 0 = off, 1 = servers only (default), 2 = all computers")
                .defineInRange("diskWear", 1, 0, 2);
        DISK_LIFE_HOURS = b.comment("Сколько часов работы (настоящих) выдерживает жёсткий диск; SSD — в полтора раза дольше",
                        "Real hours of powered-on time a hard drive lasts; SSDs last 1.5 times longer")
                .defineInRange("diskLifeHours", 40.0, 0.01, 100000.0);
        b.pop();
        SPEC = b.build();
    }

    private MineConfig() {
    }

    private static <T> T get(ModConfigSpec.ConfigValue<T> v, T def) {
        try {
            return SPEC.isLoaded() ? v.get() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    /** Клиент: играем ли в одиночной игре (выставляет окно корпуса, чтобы показывать скорость как на сервере). */
    public static volatile boolean clientSingleplayer = true;

    public static double speed(boolean singleplayer) {
        return singleplayer ? get(SINGLEPLAYER_SPEED, 2.0) : get(SPEED, 1.0);
    }

    /** Множитель скорости для мира: на сервере — по его типу; у клиента одиночной игры — как в одиночной; иначе ×1. */
    public static double speedFor(net.minecraft.world.level.Level level) {
        if (level != null && level.getServer() != null) return speed(!level.getServer().isDedicatedServer());
        return SPEC.isLoaded() ? speed(clientSingleplayer) : 1.0;
    }

    public static double memory() { return get(MEMORY, 1.0); }
    public static int maxMemoryMb() { return get(MAX_MEMORY_MB, 256); }
    public static double gpu() { return get(GPU, 1.0); }
    public static double disk() { return get(DISK, 1.0); }
    public static int maxCores() { return get(MAX_CORES, 8); }
    public static int maxProcesses() { return get(MAX_PROCESSES, 32); }
    public static int threads() { return get(THREADS, 0); }
    public static int sliceMs() { return get(SLICE_MS, 20); }
    public static boolean bootTime() { return get(BOOT_TIME, true); }
    public static int diskWear() { return get(DISK_WEAR, 1); }
    public static double diskLifeHours() { return get(DISK_LIFE_HOURS, 40.0); }
    public static int screenFps() { return get(SCREEN_FPS, 60); }
    public static int screenKbPerSecond() { return get(SCREEN_KB, 2048); }
}
