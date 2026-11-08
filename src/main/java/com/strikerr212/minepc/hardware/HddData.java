package com.strikerr212.minepc.hardware;

/** Вычисленные характеристики жёсткого диска. */
public record HddData(int tier, boolean small, int capacityMb, int rpm, int cacheMb) {

    public int speedMbs() {
        return rpm / 40 + cacheMb / 8;
    }

    public int powerDraw() {
        return small ? 2 : 5 + rpm / 7200;
    }
}
