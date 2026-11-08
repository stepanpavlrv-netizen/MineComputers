package com.strikerr212.minepc.hardware.spec;

/**
 * Палитры цветов, из которых игрок выбирает в сборочном столе.
 * Цвет 0xRRGGBB умножается на светлую нейтральную текстуру детали.
 */
public final class Palette {

    private Palette() {
    }

    /** Металл и пластик: крышки, радиаторы, кожухи, корпуса. */
    public static final int[] METAL = {
            0xFFFFFF, // серебристый
            0x7A7F8C, // графитовый
            0x4A4E58, // тёмно-серый
            0x33363D, // чёрный
            0xD4A030, // золотой
            0xC8784A, // медный
            0xC03030, // красный
            0xE08030, // оранжевый
            0xE0C040, // жёлтый
            0x40A060, // зелёный
            0x30B0A0, // бирюзовый
            0x40A0E0, // голубой
            0x3060C0, // синий
            0x8040C0, // фиолетовый
            0xE060A0, // розовый
    };

    /** Подсветка и наклейки. */
    public static final int[] LED = {
            0xFFFFFF, // белый
            0xFF4040, // красный
            0xFF9030, // оранжевый
            0xFFE040, // жёлтый
            0x40FF70, // зелёный
            0x40FFD0, // бирюзовый
            0x40C8FF, // голубой
            0x4060FF, // синий
            0x9A6BFF, // фиолетовый
            0xFF60D0, // розовый
    };

    /** Текстолит печатных плат. */
    public static final int[] PCB = {
            0x3C8A58, // зелёный
            0x3A5A48, // тёмно-зелёный
            0x34383F, // чёрный
            0x33507E, // синий
            0x8E3438, // красный
            0xFFFFFF, // белый
            0x5E3D88, // фиолетовый
            0x9A7A3A, // коричневый
    };

    /** Цвет выключенной подсветки: матовый рассеиватель. */
    public static final int LED_OFF = 0x8C9096;

    public static int metal(int index) {
        return METAL[Math.floorMod(index, METAL.length)];
    }

    public static int led(int index) {
        return LED[Math.floorMod(index, LED.length)];
    }

    public static int pcb(int index) {
        return PCB[Math.floorMod(index, PCB.length)];
    }
}
