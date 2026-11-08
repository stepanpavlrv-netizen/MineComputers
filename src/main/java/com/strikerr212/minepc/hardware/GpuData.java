package com.strikerr212.minepc.hardware;

/**
 * Вычисленные характеристики видеокарты.
 *
 * @param vramChips чипов видеопамяти: каждый подключён по 32-битному каналу, вместе они дают ширину шины
 * @param vramType  0 = GDDR5, 1 = GDDR6, 2 = GDDR6X, 3 = GDDR7
 * @param coolingW  сколько тепла отводит её собственное охлаждение (0 — голая плата)
 */
public record GpuData(int tier, int shaders, int clockMhz, int vramChips, int vramMb, int vramType, int tdp,
                      int coolingW) {

    public boolean hasCooler() {
        return coolingW > 0;
    }
}
