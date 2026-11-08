package com.strikerr212.minepc.client;

/**
 * Псевдографика, которую рисуем ровными прямоугольниками, а не буквами шрифта:
 * полублоки и блоки (▀ ▄ █ ▌ ▐ ░ ▒ ▓ ▖ ▗ ...) и линии рамок (─ │ ┌ ╔ ...).
 * Так «пиксельные» картинки и окна выглядят без щелей между клетками.
 */
final class CellArt {

    /** Куда рисовать: прямоугольник в долях клетки (0..1) и цвет RGB. */
    interface Painter {
        void rect(float x0, float y0, float x1, float y1, int rgb);
    }

    private CellArt() {
    }

    /** Нарисовать символ, если это псевдографика. Возвращает false, если это обычная буква. */
    static boolean draw(char c, int fg, int bg, Painter p) {
        if (c >= 0x2580 && c <= 0x259F) return block(c, fg, bg, p);
        if (c >= 0x2500 && c <= 0x257F) return box(c, fg, p);
        return false;
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    private static boolean block(char c, int fg, int bg, Painter p) {
        switch (c) {
            case 0x2580 -> p.rect(0, 0, 1, 0.5f, fg);
            case 0x2588 -> p.rect(0, 0, 1, 1, fg);
            case 0x2590 -> p.rect(0.5f, 0, 1, 1, fg);
            case 0x2591 -> p.rect(0, 0, 1, 1, mix(bg, fg, 0.25f));
            case 0x2592 -> p.rect(0, 0, 1, 1, mix(bg, fg, 0.5f));
            case 0x2593 -> p.rect(0, 0, 1, 1, mix(bg, fg, 0.75f));
            case 0x2594 -> p.rect(0, 0, 1, 0.125f, fg);
            case 0x2595 -> p.rect(0.875f, 0, 1, 1, fg);
            default -> {
                if (c >= 0x2581 && c <= 0x2587) {          // нижние 1/8 .. 7/8
                    p.rect(0, 1 - (c - 0x2580) / 8f, 1, 1, fg);
                } else if (c >= 0x2589 && c <= 0x258F) {   // левые 7/8 .. 1/8
                    p.rect(0, 0, (8 - (c - 0x2588)) / 8f, 1, fg);
                } else {
                    // Четвертинки ▖ ▗ ▘ ▙ ▚ ▛ ▜ ▝ ▞ ▟: биты — верх-лево, верх-право, низ-лево, низ-право
                    int[] q = {0b0010, 0b0001, 0b1000, 0b1011, 0b1001, 0b1110, 0b1101, 0b0100, 0b0110, 0b0111};
                    int m = q[c - 0x2596];
                    if ((m & 0b1000) != 0) p.rect(0, 0, 0.5f, 0.5f, fg);
                    if ((m & 0b0100) != 0) p.rect(0.5f, 0, 1, 0.5f, fg);
                    if ((m & 0b0010) != 0) p.rect(0, 0.5f, 0.5f, 1, fg);
                    if ((m & 0b0001) != 0) p.rect(0.5f, 0.5f, 1, 1, fg);
                }
            }
        }
        return true;
    }

    // Линии рамок: для каждого символа — толщина «плеч» влево, вправо, вверх, вниз (0 нет, 1 тонкая, 2 толстая, 3 двойная)
    private static final java.util.Map<Character, int[]> ARMS = new java.util.HashMap<>();

    private static void arms(String chars, int l, int r, int u, int d) {
        for (char ch : chars.toCharArray()) ARMS.put(ch, new int[]{l, r, u, d});
    }

    static {
        arms("─", 1, 1, 0, 0); arms("━", 2, 2, 0, 0); arms("│", 0, 0, 1, 1); arms("┃", 0, 0, 2, 2);
        arms("┌╭", 0, 1, 0, 1); arms("┐╮", 1, 0, 0, 1); arms("└╰", 0, 1, 1, 0); arms("┘╯", 1, 0, 1, 0);
        arms("┏", 0, 2, 0, 2); arms("┓", 2, 0, 0, 2); arms("┗", 0, 2, 2, 0); arms("┛", 2, 0, 2, 0);
        arms("├", 0, 1, 1, 1); arms("┤", 1, 0, 1, 1); arms("┬", 1, 1, 0, 1); arms("┴", 1, 1, 1, 0); arms("┼", 1, 1, 1, 1);
        arms("┣", 0, 2, 2, 2); arms("┫", 2, 0, 2, 2); arms("┳", 2, 2, 0, 2); arms("┻", 2, 2, 2, 0); arms("╋", 2, 2, 2, 2);
        arms("═", 3, 3, 0, 0); arms("║", 0, 0, 3, 3);
        arms("╔", 0, 3, 0, 3); arms("╗", 3, 0, 0, 3); arms("╚", 0, 3, 3, 0); arms("╝", 3, 0, 3, 0);
        arms("╠", 0, 3, 3, 3); arms("╣", 3, 0, 3, 3); arms("╦", 3, 3, 0, 3); arms("╩", 3, 3, 3, 0); arms("╬", 3, 3, 3, 3);
        arms("╴", 1, 0, 0, 0); arms("╶", 0, 1, 0, 0); arms("╵", 0, 0, 1, 0); arms("╷", 0, 0, 0, 1);
    }

    private static boolean box(char c, int fg, Painter p) {
        int[] a = ARMS.get(c);
        if (a == null) return false;
        // Толщина линий в долях клетки (клетка 6×9 точек: тонкая линия — 1 точка)
        float th = 1 / 9f, tv = 1 / 6f, cy = 0.5f, cx = 0.5f;
        for (int side = 0; side < 4; side++) {
            int w = a[side];
            if (w == 0) continue;
            boolean horizontal = side < 2;
            float t = (horizontal ? th : tv) * (w == 2 ? 2 : 1);
            float x0, x1, y0, y1;
            if (horizontal) {
                x0 = side == 0 ? 0 : cx - tv / 2;
                x1 = side == 0 ? cx + tv / 2 : 1;
                if (w == 3) {           // двойная: две линии
                    p.rect(x0, cy - 2 * th, x1, cy - th, fg);
                    p.rect(x0, cy + th, x1, cy + 2 * th, fg);
                    continue;
                }
                y0 = cy - t / 2;
                y1 = cy + t / 2;
            } else {
                y0 = side == 2 ? 0 : cy - th / 2;
                y1 = side == 2 ? cy + th / 2 : 1;
                if (w == 3) {
                    p.rect(cx - 1.5f * tv, y0, cx - 0.5f * tv, y1, fg);
                    p.rect(cx + 0.5f * tv, y0, cx + 1.5f * tv, y1, fg);
                    continue;
                }
                x0 = cx - t / 2;
                x1 = cx + t / 2;
            }
            p.rect(x0, y0, x1, y1, fg);
        }
        return true;
    }
}
