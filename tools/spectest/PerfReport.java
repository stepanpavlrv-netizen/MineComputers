package com.strikerr212.minepc.hardware.spec;

import com.strikerr212.minepc.hardware.*;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Часть теста спецификаций: считает производительность заводских сборок ур. 1–5
 * и нескольких «плохих» сборок, печатает подсказки деталей.
 * Таблицы пресетов подставляет run.py (класс StockTables).
 * Вывод — в stderr, ошибки калибровки — исключением.
 */
public class PerfReport {

    static List<Integer> L(int[] a) {
        List<Integer> l = new ArrayList<>();
        for (int x : a) l.add(x);
        return l;
    }

    static int sticks(int tier) {
        return tier == 1 ? 1 : tier == 5 ? 4 : 2;
    }

    /** Заводская сборка уровня t (каждая деталь своего уровня). */
    static SystemModel.Input stock(int t, boolean hddOnly, boolean noGpu, int coolerTier, int ramSticks) {
        int i = t - 1;
        MotherboardData board = Specs.MOTHERBOARD.derive(L(StockTables.MOTHERBOARD[i]));
        CpuData cpu = Specs.CPU.derive(L(StockTables.CPU[i]));
        CoolerData cooler = coolerTier > 0 ? Specs.COOLER.derive(L(StockTables.COOLER[coolerTier - 1])) : null;
        List<RamData> ram = new ArrayList<>();
        for (int n = 0; n < ramSticks; n++) ram.add(Specs.RAM.derive(L(StockTables.RAM[i])));
        GpuData gpu = noGpu ? null : Specs.GPU.derive(L(StockTables.GPU[i]));
        PsuData psu = Specs.PSU.derive(L(StockTables.PSU[i]));
        List<HddData> hdds = new ArrayList<>();
        List<SsdData> ssds = new ArrayList<>();
        if (hddOnly) hdds.add(Specs.HDD.derive(L(StockTables.HDD[i])));
        else ssds.add(Specs.SSD.derive(L(StockTables.SSD[i])));

        int power = Specs.MOTHERBOARD.powerDraw(L(StockTables.MOTHERBOARD[i])) + Specs.CPU.powerDraw(L(StockTables.CPU[i]))
                + (coolerTier > 0 ? Specs.COOLER.powerDraw(L(StockTables.COOLER[coolerTier - 1])) : 0)
                + ramSticks * Specs.RAM.powerDraw(L(StockTables.RAM[i]))
                + (noGpu ? 0 : Specs.GPU.powerDraw(L(StockTables.GPU[i])))
                + (hddOnly ? Specs.HDD.powerDraw(L(StockTables.HDD[i])) : Specs.SSD.powerDraw(L(StockTables.SSD[i])));
        return new SystemModel.Input(board, cpu, cooler, ram, gpu, psu, hdds, ssds, power);
    }

    static String fmt(SystemModel.Report r) {
        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.ROOT,
                "CPU %7.1f GIPS (ур.%.1f, частота %3d%%, память %3d%%) | GPU %6.2f TF (ур.%.1f, %3d%%) | RAM %5.1f ГБ/с x%d | загрузка %5.1f с | розетка %4d Вт | оценка %5d%s",
                r.cpuGips(), r.cpuLevel(), Math.round(r.cpuThrottle() * 100), Math.round(r.cpuMemory() * 100),
                r.gpuTflops(), r.gpuLevel(), Math.round(r.gpuThrottle() * 100), r.ramGbs(), r.channels(),
                r.bootSeconds(), r.wallWatts(), r.score(), r.bootable() ? "" : " НЕ ЗАПУСТИТСЯ"));
        for (SystemModel.Note n : r.notes()) {
            b.append(String.format(Locale.ROOT, "\n      ! %s %.1f %.1f", n.issue(), n.a(), n.b()));
        }
        return b.toString();
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new RuntimeException("Калибровка: " + what);
    }

    static void near(double actual, double expected, String what) {
        check(Math.abs(actual - expected) <= expected * 0.02 + 0.005,
                what + " = " + actual + ", а в Perf записано " + expected + " — обнови константу");
    }

    public static void run() {
        System.err.println("=== Заводские детали ===");
        for (int t = 1; t <= 5; t++) {
            CpuData cpu = Specs.CPU.derive(L(StockTables.CPU[t - 1]));
            GpuData gpu = Specs.GPU.derive(L(StockTables.GPU[t - 1]));
            HddData hdd = Specs.HDD.derive(L(StockTables.HDD[t - 1]));
            SsdData ssd = Specs.SSD.derive(L(StockTables.SSD[t - 1]));
            RamData ram = Specs.RAM.derive(L(StockTables.RAM[t - 1]));
            System.err.printf(Locale.ROOT,
                    "ур.%d  CPU %6.1f GIPS (ядро %5.2f, нужно памяти %5.1f ГБ/с, %3d Вт) | GPU %5.2f TF, шина %3d бит %5.0f ГБ/с, память %3d%% | RAM %5.1f ГБ/с/канал | HDD %3d оп/с %5.1f с | SSD %7d оп/с %4.1f с%n",
                    t, Perf.cpuGips(cpu), Perf.cpuSingleGips(cpu), Perf.cpuMemoryDemand(cpu), cpu.tdp(),
                    Perf.gpuTflops(gpu), Perf.gpuBusBits(gpu), Perf.gpuMemoryGbs(gpu), Math.round(Perf.gpuMemoryFactor(gpu) * 100),
                    Perf.ramChannelGbs(ram.speedMhz()), hdd.iops(), Perf.bootSeconds(hdd), ssd.iops(), Perf.bootSeconds(ssd));
            near(Perf.cpuGips(cpu), Perf.STOCK_CPU_GIPS[t - 1], "STOCK_CPU_GIPS[" + (t - 1) + "]");
            near(Perf.gpuTflops(gpu), Perf.STOCK_GPU_TFLOPS[t - 1], "STOCK_GPU_TFLOPS[" + (t - 1) + "]");
            check(Math.abs(Perf.level(Perf.cpuGips(cpu), Perf.STOCK_CPU_GIPS) - t) < 0.05, "уровень CPU " + t);
        }

        System.err.println("=== Заводские сборки (все детали одного уровня) ===");
        int prev = 0;
        for (int t = 1; t <= 5; t++) {
            SystemModel.Input in = stock(t, false, false, t, sticks(t));
            SystemModel.Report r = SystemModel.evaluate(in);
            near(Perf.ramChannelGbs(r.ramMts()) * r.channels(), Perf.STOCK_RAM_GBS[t - 1], "STOCK_RAM_GBS[" + (t - 1) + "]");
            System.err.printf("ур.%d  %s%n", t, fmt(r));
            check(r.bootable(), "заводская сборка ур." + t + " должна запускаться");
            check(r.score() > prev, "оценка должна расти с уровнем");
            check(r.cpuThrottle() == 1 && r.gpuThrottle() == 1, "заводская сборка ур." + t + " не должна троттлить");
            for (SystemModel.Note n : r.notes()) {
                check(n.issue() != SystemModel.Issue.CPU_BOTTLENECK && n.issue() != SystemModel.Issue.GPU_BOTTLENECK,
                        "в заводской сборке ур." + t + " не должно быть узких мест: " + n.issue());
            }
            prev = r.score();
            if (t == 3) check(Math.abs(r.score() - 1000) < 30, "заводская ур.3 должна давать ≈1000, а даёт " + r.score());
        }

        System.err.println("=== Неудачные сборки ===");
        System.err.println("ур.5 с боксовым кулером:      " + fmt(SystemModel.evaluate(stock(5, false, false, 1, 4))));
        System.err.println("ур.5 без кулера:              " + fmt(SystemModel.evaluate(stock(5, false, false, 0, 4))));
        System.err.println("ур.1 без кулера:              " + fmt(SystemModel.evaluate(stock(1, false, false, 0, 1))));
        System.err.println("ур.5 одна планка памяти:      " + fmt(SystemModel.evaluate(stock(5, false, false, 5, 1))));
        System.err.println("ур.3 одна планка памяти:      " + fmt(SystemModel.evaluate(stock(3, false, false, 3, 1))));
        System.err.println("ур.3 без видеокарты:          " + fmt(SystemModel.evaluate(stock(3, false, true, 3, 2))));
        System.err.println("ур.3 только жёсткий диск:     " + fmt(SystemModel.evaluate(stock(3, true, false, 3, 2))));
        SystemModel.Input s5 = stock(5, false, false, 5, 4);
        SystemModel.Input mixed = new SystemModel.Input(s5.board(), Specs.CPU.derive(L(StockTables.CPU[0])), s5.cooler(), s5.ram(),
                s5.gpu(), s5.psu(), s5.hdds(), s5.ssds(), s5.powerDraw());
        System.err.println("ур.5 с процессором ур.1:      " + fmt(SystemModel.evaluate(mixed)));
        SystemModel.Input s1 = stock(1, false, false, 1, 1);
        SystemModel.Input over = new SystemModel.Input(s1.board(), s1.cpu(), s1.cooler(), s1.ram(), s5.gpu(), s1.psu(),
                s1.hdds(), s1.ssds(), s1.powerDraw() + 500);
        System.err.println("ур.1 с видеокартой ур.5:      " + fmt(SystemModel.evaluate(over)));
        GpuData bare = Specs.GPU.derive(L(new int[]{2, 4, 2, 2, 1, 1, 2, 0, 0, 0, 0, 0}));
        System.err.printf(Locale.ROOT, "голая плата ур.3: %.2f TF из %.2f (частота %.0f%%)%n", Perf.gpuEffectiveTflops(bare),
                Perf.gpuTflops(bare), Perf.throttle(bare.tdp(), Perf.PASSIVE_COOLING) * 100);
        SystemModel.Report none = SystemModel.evaluate(new SystemModel.Input(null, null, null, List.of(), null, null,
                List.of(), List.of(), 0));
        check(!none.bootable(), "пустой корпус не должен запускаться");

        System.err.println("=== Подсказки заводских деталей ур.3 ===");
        int[][][] tables = {StockTables.MOTHERBOARD, StockTables.CPU, StockTables.COOLER, StockTables.RAM, StockTables.GPU,
                StockTables.PSU, StockTables.HDD, StockTables.SSD};
        PartSpec[] specs = {Specs.MOTHERBOARD, Specs.CPU, Specs.COOLER, Specs.RAM, Specs.GPU, Specs.PSU, Specs.HDD, Specs.SSD};
        for (int k = 0; k < specs.length; k++) {
            List<Component> lines = new ArrayList<>();
            specs[k].describe(L(tables[k][2]), lines);
            System.err.println(specs[k].type() + ":");
            for (Component c : lines) System.err.println("    " + c.text());
        }
    }
}
