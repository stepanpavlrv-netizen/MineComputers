package com.strikerr212.minepc.hardware;

/**
 * Вычисленные характеристики процессора (в игровых единицах: одна команда Lua — один такт).
 *
 * @param clockKhz базовая частота ядра, кГц (3500 — 3,5 МГц, то есть 3,5 млн команд в секунду)
 * @param boostPct насколько ядро разгоняется, пока процессор холодный, %
 * @param cacheKb  кэш, КБ: снижает нагрузку на память
 * @param tdp      тепло всего кристалла на базовой частоте, вместе с видеоядром
 * @param igpu     встроенное видеоядро: 0 — нет, 1 — слабое, 2 — среднее, 3 — сильное
 */
public record CpuData(int tier, int socket, int cores, int clockKhz, int boostPct, int processNm, int cacheKb, int tdp, int igpu) {
}
