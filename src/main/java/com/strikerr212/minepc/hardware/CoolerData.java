package com.strikerr212.minepc.hardware;

/** Вычисленные характеристики процессорного охлаждения. */
public record CoolerData(int tier, int kind, int coolingW, int powerDraw) {

    public static final int BOX = 0;
    public static final int LOW_PROFILE = 1;
    public static final int TOWER = 2;
    public static final int DUAL_TOWER = 3;
    public static final int AIO = 4;
    /** Не деталь: стена вентиляторов серверного шасси. */
    public static final int FAN_WALL = 5;
}
