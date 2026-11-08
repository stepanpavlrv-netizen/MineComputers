package com.strikerr212.minepc.hardware;

/** Вычисленные характеристики модуля памяти. */
public record RamData(int tier, int chips, int chipMb, int speedMhz) {

    public int capacityMb() {
        return chips * chipMb;
    }

    public int powerDraw() {
        return 1 + Math.round(chips * speedMhz / 8000f);
    }
}
