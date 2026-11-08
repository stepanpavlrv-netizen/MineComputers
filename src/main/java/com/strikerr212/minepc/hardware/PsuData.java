package com.strikerr212.minepc.hardware;

/** Вычисленные характеристики блока питания. */
public record PsuData(int tier, int wattage, int efficiency) {

    private static final String[] CERT_KEYS = {"white", "bronze", "gold", "platinum", "titanium"};
    private static final int[] EFFICIENCY_PERCENT = {80, 85, 90, 92, 94};

    private int certIndex() {
        return Math.max(0, Math.min(CERT_KEYS.length - 1, efficiency));
    }

    public String certificateKey() {
        return "tooltip.minepc.cert." + CERT_KEYS[certIndex()];
    }

    public int efficiencyPercent() {
        return EFFICIENCY_PERCENT[certIndex()];
    }
}
