package com.strikerr212.minepc.computer;

/**
 * Частота процессора от температуры: холодный процессор разгоняется (буст), горячий сбрасывает частоту.
 * Одна формула для живого компьютера и для оценки сборки на сборочном столе.
 *
 * <p>Тепло растёт быстрее частоты (вместе с частотой поднимается напряжение), поэтому на слабом кулере
 * буст быстро съедается, а под нагрузкой процессор находит частоту, на которой тепло и отвод сходятся.
 */
public final class CpuClock {

    /** До этой температуры держится полный буст. */
    public static final double BOOST_UNTIL = 65;
    /** К этой температуре буст сходит на нет — дальше базовая частота. */
    public static final double BASE_AT = 80;
    /** К этой температуре частота падает до {@link #HOT_FACTOR} от базовой. */
    public static final double HOT_AT = 95;
    public static final double HOT_FACTOR = 0.55;
    /** Ниже этого процессор не опускается (дальше он бы выключился). */
    public static final double MIN_FACTOR = 0.35;

    private CpuClock() {
    }

    /** Температура кристалла, к которой он идёт при нагрузке {@code load} (0..1) и частоте {@code factor} от базовой. */
    public static double temperature(int tdpW, int coolingW, double load, double factor) {
        double heat = tdpW * Math.pow(Math.max(0, factor), 1.5);
        double t = 28 + (0.22 + 0.78 * Math.max(0, Math.min(1, load))) * heat / Math.max(1, coolingW) * 50;
        return Math.max(28, Math.min(105, t));
    }

    /** Какую частоту (долю базовой) процессор выбирает при такой температуре; boost — 0.15 = +15%. */
    public static double factor(double temp, double boost) {
        if (temp <= BOOST_UNTIL) return 1 + boost;
        if (temp <= BASE_AT) return 1 + boost * (BASE_AT - temp) / (BASE_AT - BOOST_UNTIL);
        if (temp <= HOT_AT) return 1 - (1 - HOT_FACTOR) * (temp - BASE_AT) / (HOT_AT - BASE_AT);
        return Math.max(MIN_FACTOR, HOT_FACTOR - (HOT_FACTOR - MIN_FACTOR) * (temp - HOT_AT) / 10);
    }

    /** На какой частоте процессор работает долго при постоянной нагрузке {@code load}. */
    public static double steady(int tdpW, int coolingW, double boost, double load) {
        double f = 1 + boost;
        for (int i = 0; i < 80; i++) {
            f = 0.5 * f + 0.5 * factor(temperature(tdpW, coolingW, load, f), boost);
        }
        return f;
    }
}
