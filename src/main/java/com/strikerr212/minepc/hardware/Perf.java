package com.strikerr212.minepc.hardware;

/**
 * Модель производительности: сколько реальной работы может сделать каждая деталь.
 *
 * Формулы упрощены, но устроены как в настоящем железе:
 * <ul>
 *   <li><b>Процессор.</b> Операций в секунду = частота × IPC (сколько команд ядро выполняет за такт) × ядра.
 *       IPC растёт с каждым техпроцессом (новые поколения архитектур) и немного — с кэшем.
 *       Ядра складываются не идеально: часть работы не делится, плюс ядра делят кэш и память.</li>
 *   <li><b>Память.</b> Пропускная способность канала = скорость (МТ/с) × 8 байт. Каналы складываются.
 *       Процессору нужен поток данных пропорционально его скорости; кэш снижает эту потребность.</li>
 *   <li><b>Видеокарта.</b> TFLOPS = шейдеры × частота × 2 (умножение и сложение за такт).
 *       Шина памяти = чипы × 32 бита, пропускная способность = шина × скорость памяти.</li>
 *   <li><b>Нагрев.</b> Если охлаждение не справляется, чип снижает частоту. Тепловыделение растёт
 *       примерно как куб частоты (вместе с частотой поднимается напряжение), поэтому частота падает
 *       как кубический корень из «отвод / нагрев».</li>
 *   <li><b>Накопители.</b> Загрузка системы = прочитать файлы подряд + много мелких случайных чтений.
 *       У жёсткого диска случайное чтение в сотни раз медленнее, чем у SSD.</li>
 * </ul>
 *
 * Здесь нет ничего из Minecraft, поэтому класс проверяется тестом без запуска игры
 * ({@code tools/spectest/run.py}). Позже эти же числа станут настоящими лимитами для программ.
 */
public final class Perf {

    private Perf() {
    }

    // ===================== Процессор =====================

    private static final int[] PROCESS_NM = {90, 65, 45, 32, 22, 14, 10, 7, 5, 3};
    /** Команд за такт на ядро для архитектуры каждого техпроцесса. */
    private static final double[] PROCESS_IPC = {1.0, 1.3, 1.7, 2.1, 2.6, 3.0, 3.5, 4.0, 4.5, 5.0};
    /** Какая доля работы мешает ядрам друг другу (общий кэш, память, синхронизация). */
    private static final double CORE_OVERHEAD = 0.02;
    /** ГБ/с данных на каждый миллиард операций в секунду, если бы кэша не было. */
    private static final double MEMORY_PER_GIPS = 0.4;

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }

    public static double cpuIpc(int processNm, int cacheMb) {
        double base = PROCESS_IPC[PROCESS_IPC.length - 1];
        for (int i = 0; i < PROCESS_NM.length; i++) {
            if (PROCESS_NM[i] <= processNm) {
                base = PROCESS_IPC[i];
                break;
            }
        }
        return base * (1 + 0.04 * log2(Math.max(1, cacheMb)));
    }

    /** Во сколько раз N ядер быстрее одного. */
    public static double coreScaling(int cores) {
        return cores / (1 + CORE_OVERHEAD * (cores - 1));
    }

    /** Миллиардов операций в секунду на одном ядре. */
    public static double cpuSingleGips(CpuData d) {
        return d.clockMhz() / 1000.0 * cpuIpc(d.processNm(), d.cacheMb());
    }

    /** Миллиардов операций в секунду всеми ядрами. */
    public static double cpuGips(CpuData d) {
        return cpuSingleGips(d) * coreScaling(d.cores());
    }

    /** Сколько ГБ/с памяти нужно процессору на полной частоте. Большой кэш снижает потребность. */
    public static double cpuMemoryDemand(CpuData d) {
        return cpuGips(d) * MEMORY_PER_GIPS / (1 + 0.1 * log2(Math.max(1, d.cacheMb())));
    }

    // ===================== Память =====================

    /** ГБ/с одного канала: за каждую передачу уходит 64 бита = 8 байт. */
    public static double ramChannelGbs(int mts) {
        return mts * 8 / 1000.0;
    }

    /**
     * Какая доля скорости остаётся, если данных приходит в {@code ratio} раз больше, чем нужно.
     * Плавная кривая: даже с запасом немного теряется на задержках, при нехватке потери быстро растут.
     */
    public static double memoryFactor(double ratio) {
        return 1 - 0.5 * Math.exp(-1.2 * Math.max(0, ratio));
    }

    // ===================== Видеокарта =====================

    /** Гбит/с на контакт для GDDR5, GDDR6, GDDR6X, GDDR7. */
    private static final int[] VRAM_GBPS = {8, 16, 21, 28};
    /** ГБ/с видеопамяти, которые нужны на каждый TFLOPS. */
    private static final double VRAM_PER_TFLOPS = 16;

    public static int vramGbps(int vramType) {
        return VRAM_GBPS[Math.max(0, Math.min(VRAM_GBPS.length - 1, vramType))];
    }

    public static int gpuBusBits(GpuData d) {
        return d.vramChips() * 32;
    }

    public static double gpuTflops(GpuData d) {
        return d.shaders() * (double) d.clockMhz() * 2 / 1_000_000.0;
    }

    public static double gpuMemoryGbs(GpuData d) {
        return gpuBusBits(d) / 8.0 * vramGbps(d.vramType());
    }

    /** Доля скорости чипа, которую успевает «прокормить» видеопамять. */
    public static double gpuMemoryFactor(GpuData d) {
        return memoryFactor(gpuMemoryGbs(d) / (gpuTflops(d) * VRAM_PER_TFLOPS));
    }

    /** Скорость видеокарты с учётом её собственного охлаждения и видеопамяти. */
    public static double gpuEffectiveTflops(GpuData d) {
        return gpuTflops(d) * throttle(d.tdp(), d.hasCooler() ? d.coolingW() : PASSIVE_COOLING) * gpuMemoryFactor(d);
    }

    // ===================== Нагрев =====================

    /** Сколько тепла чип рассеивает сам, без радиатора и вентилятора. */
    public static final int PASSIVE_COOLING = 15;
    /** Ниже этой доли частоты чип не опускается — дальше он бы просто выключился. */
    public static final double MIN_THROTTLE = 0.25;

    /** Доля частоты, на которой чип может работать долго, не перегреваясь. */
    public static double throttle(int heatW, int coolingW) {
        if (heatW <= coolingW || heatW <= 0) return 1;
        return Math.max(MIN_THROTTLE, Math.cbrt((double) coolingW / heatW));
    }

    // ===================== Накопители =====================

    /** Сколько данных система читает при загрузке подряд (МБ) и сколько мелких случайных чтений. */
    private static final int BOOT_SEQ_MB = 1500;
    private static final int BOOT_RANDOM_READS = 4000;
    /** Время на проверку оборудования до начала загрузки. */
    private static final double BOOT_POST_SECONDS = 3;

    public static double bootSeconds(int seqMbs, int iops) {
        return BOOT_POST_SECONDS + (double) BOOT_SEQ_MB / Math.max(1, seqMbs)
                + (double) BOOT_RANDOM_READS / Math.max(1, iops);
    }

    public static double bootSeconds(HddData d) {
        return bootSeconds(d.speedMbs(), d.iops());
    }

    public static double bootSeconds(SsdData d) {
        return bootSeconds(d.speedMbs(), d.iops());
    }

    // ===================== Питание =====================

    /** Сколько блок питания берёт из розетки, чтобы выдать {@code loadW}. Разница уходит в тепло. */
    public static int wallWatts(int loadW, int efficiencyPercent) {
        return Math.round(loadW * 100f / Math.max(1, efficiencyPercent));
    }

    // ===================== Лимиты в игре =====================

    /**
     * Сколько команд Lua в секунду компьютер реально получает в игре. Программы выполняет процессор
     * сервера, поэтому шкала сжата: ур. 1 ≈ 200 тыс., ур. 3 ≈ 4 млн, ур. 5 ≈ 20 млн команд/с
     * (вместо разницы в сотни раз между настоящими процессорами).
     */
    public static int gameOpsPerSecond(double gips) {
        if (gips <= 0) return 0;
        return (int) Math.min(50_000_000, Math.round(200_000 * Math.pow(gips / STOCK_CPU_GIPS[0], 0.69)));
    }

    /**
     * Размер текстового экрана (символов в строке, строк) по уровню видеокарты; 0 — без видеокарты
     * (чёрно-белый текст). Больше видеокарта — больше символов помещается на экран.
     */
    public static int[] screenSize(int gpuTier) {
        return switch (gpuTier) {
            case 0 -> new int[]{40, 12};
            case 1, 2 -> new int[]{50, 16};
            case 3 -> new int[]{80, 25};
            case 4 -> new int[]{120, 40};
            default -> new int[]{160, 50};
        };
    }

    /** Сколько байт файлов помещается на накопитель в игре: «гигабайт» детали = мегабайт в игре. */
    public static long gameStorageBytes(int capacityMb) {
        return capacityMb * 1024L;
    }

    /** Память для программ в игре: «гигабайт» планки = мегабайт в игре. */
    public static int gameMemoryKb(int capacityMb) {
        return capacityMb;
    }

    // ===================== Сравнение с заводскими уровнями =====================

    /** Скорость заводских процессоров ур. 1–5, млрд оп/с (проверяется тестом). */
    public static final double[] STOCK_CPU_GIPS = {0.8, 7.2, 59.3, 192.3, 632.1};
    /** Скорость заводских видеокарт ур. 1–5, TFLOPS. */
    public static final double[] STOCK_GPU_TFLOPS = {0.31, 2.46, 9.22, 25.8, 59.0};
    /** Пропускная способность памяти в заводских сборках ур. 1–5, ГБ/с. */
    public static final double[] STOCK_RAM_GBS = {6.4, 25.6, 51.2, 76.8, 192};

    /**
     * На какой заводской уровень похожа скорость {@code value}: 3.0 — как заводская деталь ур. 3,
     * 3.5 — посередине между ур. 3 и 4 (в логарифмической шкале). Может быть меньше 1 и больше 5.
     */
    public static double level(double value, double[] stock) {
        if (value <= 0) return 0;
        int n = stock.length;
        int i;
        if (value <= stock[1]) i = 0;
        else if (value >= stock[n - 2]) i = n - 2;
        else {
            i = 1;
            while (value > stock[i + 1]) i++;
        }
        double lv = i + 1 + Math.log(value / stock[i]) / Math.log(stock[i + 1] / stock[i]);
        return Math.max(0, lv);
    }
}
