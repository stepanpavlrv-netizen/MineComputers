package com.strikerr212.minepc.hardware;

/**
 * Вычисленные характеристики SSD.
 *
 * @param iface    0 = SATA, 1..3 = NVMe Gen3..Gen5
 * @param speedMbs скорость последовательного чтения
 * @param iops     операций случайного чтения в секунду (мелкие блоки по 4 КБ)
 */
public record SsdData(int tier, int iface, int capacityMb, int speedMbs, int iops) {

    public boolean isNvme() {
        return iface > 0;
    }

    public int powerDraw() {
        return isNvme() ? 4 + speedMbs / 3000 : 3;
    }
}
