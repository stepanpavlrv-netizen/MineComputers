package com.strikerr212.minepc.hardware;

/** Вычисленные характеристики SSD. iface: 0 = SATA, 1..3 = NVMe Gen3..Gen5. */
public record SsdData(int tier, int iface, int capacityMb, int speedMbs) {

    public boolean isNvme() {
        return iface > 0;
    }

    public int powerDraw() {
        return isNvme() ? 4 + speedMbs / 3000 : 3;
    }
}
