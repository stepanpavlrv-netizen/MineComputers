package com.strikerr212.minepc.hardware;

/**
 * Вычисленные характеристики видеокарты.
 *
 * @param coolingW сколько тепла отводит её собственное охлаждение (0 — голая плата)
 */
public record GpuData(int tier, int shaders, int clockMhz, int vramMb, int vramType, int tdp, int coolingW) {

    public boolean hasCooler() {
        return coolingW > 0;
    }
}
