package com.strikerr212.minepc.hardware;

/**
 * Вычисленные характеристики материнской платы.
 *
 * @param maxCpuTier процессоры какого уровня поддерживает чипсет
 * @param channels   каналов памяти (1–4 у обычной, 4–8 у серверной)
 * @param cpuSockets сколько процессоров ставится (у серверной — два)
 * @param server     серверная плата: сокет MS-S, память только ECC, две сетевые по 10 Гбит, встаёт только в серверное шасси
 */
public record MotherboardData(int tier, int socket, int chipset, int maxCpuTier, int ramSlots, int channels,
                              int maxRamMhz, int m2Slots, int sataPorts, int powerDraw, int cpuSockets, boolean server) {

    /** Сокет серверных процессоров. */
    public static final int SOCKET_SERVER = 3;
}
