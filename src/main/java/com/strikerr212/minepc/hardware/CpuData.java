package com.strikerr212.minepc.hardware;

/** Вычисленные характеристики процессора. */
public record CpuData(int tier, int socket, int cores, int clockMhz, int processNm, int cacheMb, int tdp) {
}
