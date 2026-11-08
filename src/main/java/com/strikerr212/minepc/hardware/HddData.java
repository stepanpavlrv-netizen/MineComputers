package com.strikerr212.minepc.hardware;

/** Вычисленные характеристики жёсткого диска. */
public record HddData(int tier, boolean small, int capacityMb, int rpm, int cacheMb) {

    public int speedMbs() {
        return rpm / 40 + cacheMb / 8;
    }

    /**
     * Операций случайного чтения в секунду. Головке нужно доехать до дорожки и дождаться,
     * пока нужный сектор подъедет под неё, — поэтому всё упирается в обороты шпинделя.
     */
    public int iops() {
        return rpm / 50 + cacheMb / 16;
    }

    public int powerDraw() {
        return small ? 2 : 5 + rpm / 7200;
    }
}
