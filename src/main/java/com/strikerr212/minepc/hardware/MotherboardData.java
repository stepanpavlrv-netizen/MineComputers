package com.strikerr212.minepc.hardware;

/**
 * Вычисленные характеристики материнской платы.
 *
 * @param maxCpuTier процессоры какого уровня поддерживает чипсет
 * @param channels   каналов памяти (1, 2 или 4)
 */
public record MotherboardData(int tier, int socket, int chipset, int maxCpuTier, int ramSlots, int channels,
                              int maxRamMhz, int m2Slots, int sataPorts, int powerDraw) {
}
