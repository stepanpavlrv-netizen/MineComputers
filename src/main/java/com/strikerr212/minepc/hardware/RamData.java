package com.strikerr212.minepc.hardware;

/**
 * Вычисленные характеристики модуля памяти.
 *
 * @param ecc серверная память с коррекцией ошибок: девятый чип на каждые восемь, встаёт только в серверные платы
 */
public record RamData(int tier, int chips, int chipMb, int speedMhz, boolean ecc) {

    public int capacityMb() {
        return chips * chipMb;
    }

    public int powerDraw() {
        int physical = ecc ? chips * 9 / 8 : chips;   // проверочные чипы тоже едят
        return 1 + Math.round(physical * speedMhz / 8000f) + (ecc ? 1 : 0);
    }
}
