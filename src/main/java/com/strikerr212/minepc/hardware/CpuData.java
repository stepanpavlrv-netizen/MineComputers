package com.strikerr212.minepc.hardware;

/**
 * Вычисленные характеристики процессора.
 *
 * @param igpu встроенное видеоядро: 0 — нет, 1 — слабое, 2 — среднее, 3 — сильное
 * @param tdp  тепло всего кристалла, вместе с видеоядром
 */
public record CpuData(int tier, int socket, int cores, int clockMhz, int processNm, int cacheMb, int tdp, int igpu) {
}
