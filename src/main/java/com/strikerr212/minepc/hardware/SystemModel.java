package com.strikerr212.minepc.hardware;

import java.util.ArrayList;
import java.util.List;

/**
 * Оценка собранного компьютера целиком: что реально выдаст каждая деталь в этой сборке,
 * где узкие места и сколько очков набирает система.
 *
 * Как и {@link Perf}, ничего не знает о Minecraft: на вход — паспорта деталей, на выход — числа и список замечаний.
 * Системный блок только переводит их в сообщения.
 */
public final class SystemModel {

    private SystemModel() {
    }

    /** Что установлено в корпусе. Пустой слот — null (или пустой список). */
    public record Input(MotherboardData board, CpuData cpu, CoolerData cooler, List<RamData> ram, GpuData gpu,
                        PsuData psu, List<HddData> hdds, List<SsdData> ssds, int powerDraw) {
    }

    public enum Issue {
        // ----- без этого компьютер не запустится -----
        NO_BOARD(true), NO_CPU(true), NO_RAM(true), NO_PSU(true), PSU_OVERLOAD(true), NO_STORAGE(true),
        // ----- запустится, но теряет скорость -----
        /** a = сколько процентов теряет процессор из-за памяти */
        MEMORY_BOUND(false),
        /** a = каналов задействовано, b = каналов на плате */
        CHANNELS(false),
        /** a = до какой частоты плата ограничила память */
        RAM_LIMITED(false),
        /** a = во сколько раз медленнее работает чужая память, если планки разные */
        MIXED_RAM(false),
        NO_GPU(false),
        /** a = уровень процессора, b = уровень видеокарты */
        CPU_BOTTLENECK(false),
        /** a = уровень видеокарты, b = уровень процессора */
        GPU_BOTTLENECK(false),
        /** a = процентов нагрузки на блок питания */
        PSU_HIGH(false),
        /** a = секунд загрузки */
        SLOW_BOOT(false);

        public final boolean critical;

        Issue(boolean critical) {
            this.critical = critical;
        }
    }

    public record Note(Issue issue, double a, double b) {
    }

    /**
     * Итог оценки.
     *
     * @param cpuThrottle  доля частоты процессора, которую держит кулер (1 — без троттлинга)
     * @param cpuMemory    доля скорости процессора, которую успевает прокормить память
     * @param cpuGips      реальная скорость процессора в этой сборке, млрд оп/с
     * @param gpuThrottle  доля частоты видеокарты, которую держит её охлаждение
     * @param gpuTflops    реальная скорость видеокарты, TFLOPS (0 — нет видеокарты)
     * @param ramMts       на какой скорости работает память (с учётом платы и самой медленной планки)
     * @param channels     сколько каналов задействовано
     * @param ramGbs       итоговая пропускная способность памяти
     * @param bootSeconds  время загрузки с самого быстрого накопителя (0 — накопителей нет)
     * @param bootFromSsd  загрузка идёт с SSD
     * @param wallWatts    сколько берётся из розетки (0 — нет блока питания)
     * @param score        оценка системы: заводская сборка ур. 3 ≈ 1000
     */
    public record Report(boolean bootable, double cpuThrottle, double cpuMemory, double cpuGips, double cpuLevel,
                         double gpuThrottle, double gpuTflops, double gpuLevel,
                         int ramMb, int ramMts, int channels, double ramGbs,
                         double bootSeconds, boolean bootFromSsd, int wallWatts, int score, List<Note> notes) {
    }

    /** Чем отличаются уровни процессора и видеокарты, чтобы считать одно из них узким местом. */
    private static final double BOTTLENECK_LEVELS = 1.5;
    /** Скорость, которую даёт простейший видеовыход без видеокарты, TFLOPS. */
    private static final double NO_GPU_TFLOPS = 0.05;
    /** Опорные значения оценки — заводская сборка ур. 3. */
    private static final double REF_CPU = 58.5, REF_GPU = 9.0, REF_RAM = 51.2, REF_BOOT = 3.5;

    public static Report evaluate(Input in) {
        List<Note> notes = new ArrayList<>();
        List<RamData> ram = in.ram() == null ? List.of() : in.ram();
        List<HddData> hdds = in.hdds() == null ? List.of() : in.hdds();
        List<SsdData> ssds = in.ssds() == null ? List.of() : in.ssds();

        if (in.board() == null) notes.add(new Note(Issue.NO_BOARD, 0, 0));
        if (in.cpu() == null) notes.add(new Note(Issue.NO_CPU, 0, 0));
        if (ram.isEmpty()) notes.add(new Note(Issue.NO_RAM, 0, 0));
        if (hdds.isEmpty() && ssds.isEmpty()) notes.add(new Note(Issue.NO_STORAGE, 0, 0));

        // ----- Питание -----
        int wall = 0;
        if (in.psu() == null) {
            notes.add(new Note(Issue.NO_PSU, 0, 0));
        } else {
            int load = in.powerDraw() * 100 / Math.max(1, in.psu().wattage());
            if (in.powerDraw() > in.psu().wattage()) notes.add(new Note(Issue.PSU_OVERLOAD, load, 0));
            else if (load > 85) notes.add(new Note(Issue.PSU_HIGH, load, 0));
            wall = Perf.wallWatts(in.powerDraw(), in.psu().efficiencyPercent());
        }

        // ----- Память -----
        int ramMb = 0, minMts = Integer.MAX_VALUE, maxMts = 0;
        for (RamData r : ram) {
            ramMb += r.capacityMb();
            minMts = Math.min(minMts, r.speedMhz());
            maxMts = Math.max(maxMts, r.speedMhz());
        }
        int ramMts = 0, channels = 0;
        double ramGbs = 0;
        if (!ram.isEmpty()) {
            ramMts = minMts;
            if (maxMts > minMts) notes.add(new Note(Issue.MIXED_RAM, minMts, maxMts));
            if (in.board() != null) {
                if (in.board().maxRamMhz() < ramMts) {
                    ramMts = in.board().maxRamMhz();
                    notes.add(new Note(Issue.RAM_LIMITED, ramMts, 0));
                }
                channels = Math.min(in.board().channels(), ram.size());
                if (channels < in.board().channels()) {
                    notes.add(new Note(Issue.CHANNELS, channels, in.board().channels()));
                }
            } else {
                channels = 1;
            }
            ramGbs = Perf.ramChannelGbs(ramMts) * channels;
        }

        // ----- Процессор -----
        double cpuThrottle = 1, cpuMemory = 1, cpuGips = 0, cpuLevel = 0;
        if (in.cpu() != null) {
            int cooling = in.cooler() != null ? in.cooler().coolingW() : Perf.PASSIVE_COOLING;
            cpuThrottle = Perf.throttle(in.cpu().tdp(), cooling);
            double need = Perf.cpuMemoryDemand(in.cpu()) * cpuThrottle;
            cpuMemory = ramGbs > 0 ? Perf.memoryFactor(ramGbs / need) : Perf.memoryFactor(0);
            cpuGips = Perf.cpuGips(in.cpu()) * cpuThrottle * cpuMemory;
            cpuLevel = Perf.level(cpuGips, Perf.STOCK_CPU_GIPS);
            // Предупреждаем, когда память отнимает заметно больше, чем при хорошем запасе
            if (ramGbs > 0 && ramGbs < need) {
                notes.add(new Note(Issue.MEMORY_BOUND, Math.round((1 - cpuMemory) * 100), 0));
            }
        }

        // ----- Видеокарта -----
        double gpuThrottle = 1, gpuTflops = 0, gpuLevel = 0;
        if (in.gpu() != null) {
            gpuThrottle = Perf.throttle(in.gpu().tdp(), in.gpu().hasCooler() ? in.gpu().coolingW() : Perf.PASSIVE_COOLING);
            gpuTflops = Perf.gpuEffectiveTflops(in.gpu());
            gpuLevel = Perf.level(gpuTflops, Perf.STOCK_GPU_TFLOPS);
        } else {
            notes.add(new Note(Issue.NO_GPU, 0, 0));
        }
        if (in.cpu() != null && in.gpu() != null) {
            if (gpuLevel - cpuLevel >= BOTTLENECK_LEVELS) notes.add(new Note(Issue.CPU_BOTTLENECK, cpuLevel, gpuLevel));
            if (cpuLevel - gpuLevel >= BOTTLENECK_LEVELS) notes.add(new Note(Issue.GPU_BOTTLENECK, gpuLevel, cpuLevel));
        }

        // ----- Накопители: система грузится с самого быстрого -----
        double boot = 0;
        boolean fromSsd = false;
        for (SsdData s : ssds) {
            double t = Perf.bootSeconds(s);
            if (boot == 0 || t < boot) {
                boot = t;
                fromSsd = true;
            }
        }
        for (HddData h : hdds) {
            double t = Perf.bootSeconds(h);
            if (boot == 0 || t < boot) {
                boot = t;
                fromSsd = false;
            }
        }
        if (boot > 20) notes.add(new Note(Issue.SLOW_BOOT, boot, 0));

        // ----- Итог -----
        boolean bootable = notes.stream().noneMatch(n -> n.issue().critical);
        int score = 0;
        if (in.cpu() != null && ramGbs > 0) {
            double gpu = in.gpu() != null ? gpuTflops : NO_GPU_TFLOPS;
            double s = 1000
                    * Math.pow(cpuGips / REF_CPU, 0.45)
                    * Math.pow(gpu / REF_GPU, 0.35)
                    * Math.pow(ramGbs / REF_RAM, 0.10)
                    * Math.pow(boot > 0 ? REF_BOOT / boot : 0.1, 0.10);
            score = (int) Math.round(s);
        }
        return new Report(bootable, cpuThrottle, cpuMemory, cpuGips, cpuLevel, gpuThrottle, gpuTflops, gpuLevel,
                ramMb, ramMts, channels, ramGbs, boot, fromSsd, wall, score, notes);
    }
}
