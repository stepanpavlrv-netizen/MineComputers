package com.strikerr212.minepc.client;

import com.strikerr212.minepc.block.CaseStyle;

/**
 * Оформление окна системного блока. Раскладка у всех тем одна — меняются только цвета и мелкие детали:
 * заклёпки, полосы, подсветка, вид экрана состояния и кнопки питания.
 */
public final class CaseTheme {

    public enum Deco { NONE, RETRO, OFFICE, GAMING, INDUSTRIAL, SERVER }

    // ----- окно -----
    int winFill, winLight, winDark, winOutline, titleColor, scoreColor;
    // ----- корпус изнутри (утоплен) -----
    int caseFill, caseTL, caseBR;
    int pcb, labelColor, labelShadow;
    // ----- слоты -----
    int slotFill, slotTL, slotBR, slotRing;
    /** Цвет силуэта детали в пустом слоте (ARGB). */
    int ghost;
    // ----- экран состояния -----
    int bezel, glass, fg, dim, ok, bad, warn, barBg, tabOn, tabOff;
    boolean scan, roundCorners;
    // ----- кнопка питания -----
    boolean roundButton;
    int btnFace, btnRing, btnSymbol, ledOn, ledOff;
    // ----- передняя панель (USB и нагрузка) -----
    int portsFill, portsTL, portsBR, portsLabel, graphBg, graphFg;
    Deco deco = Deco.NONE;
    // ----- корпус без крышки (окно 2.0) -----
    /** Задняя стенка, её перфорация, рёбра корпуса, кожух блока питания. */
    int wall, wallDot, frame, shroud;
    /** Цвет пунктира свободного места и подсветки под мышью. */
    int accent;
    /** Боковое стекло (блик) и подсветка вентиляторов. */
    boolean glassPanel, rgbLights;

    private CaseTheme() {
    }

    static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Светлее или темнее: каждый канал × k. */
    static int sh(int c, double k) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * k));
        int g = Math.min(255, (int) (((c >> 8) & 255) * k));
        int b = Math.min(255, (int) ((c & 255) * k));
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    static int lerp(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    public static CaseTheme of(CaseStyle style) {
        return switch (style) {
            case RETRO -> RETRO;
            case OFFICE -> OFFICE;
            case GAMING -> GAMING;
            case INDUSTRIAL -> INDUSTRIAL;
            case SERVER -> SERVER;
            default -> CLASSIC;
        };
    }

    // =============================== КЛАССИКА (как Minecraft) ===============================
    static final CaseTheme CLASSIC = new CaseTheme();
    static {
        CaseTheme t = CLASSIC;
        t.wall = rgb(112, 114, 118); t.wallDot = rgb(96, 98, 102); t.frame = rgb(150, 152, 156); t.shroud = rgb(126, 128, 132); t.accent = rgb(255, 240, 120);
        t.winFill = rgb(198, 198, 198); t.winLight = rgb(255, 255, 255); t.winDark = rgb(85, 85, 85); t.winOutline = rgb(0, 0, 0);
        t.titleColor = rgb(64, 64, 64); t.scoreColor = rgb(64, 64, 64);
        t.caseFill = rgb(139, 139, 139); t.caseTL = rgb(55, 55, 55); t.caseBR = rgb(255, 255, 255);
        t.pcb = rgb(52, 112, 76); t.labelColor = rgb(232, 240, 232); t.labelShadow = rgb(30, 60, 40);
        t.slotFill = rgb(139, 139, 139); t.slotTL = rgb(55, 55, 55); t.slotBR = rgb(255, 255, 255); t.ghost = 0x55000000;
        t.bezel = rgb(55, 55, 55); t.glass = rgb(24, 26, 30); t.fg = rgb(230, 230, 230); t.dim = rgb(130, 130, 130);
        t.ok = rgb(110, 230, 120); t.bad = rgb(255, 96, 96); t.warn = rgb(255, 200, 60);
        t.barBg = rgb(60, 62, 66); t.tabOn = rgb(70, 74, 80); t.tabOff = rgb(40, 42, 46);
        t.btnFace = rgb(120, 120, 120); t.btnRing = rgb(0, 0, 0); t.btnSymbol = rgb(240, 240, 240);
        t.ledOn = rgb(90, 255, 90); t.ledOff = rgb(60, 60, 60);
        t.portsFill = rgb(139, 139, 139); t.portsTL = rgb(255, 255, 255); t.portsBR = rgb(55, 55, 55); t.portsLabel = rgb(64, 64, 64);
        t.graphBg = rgb(24, 26, 30); t.graphFg = rgb(110, 230, 120);
    }

    // =============================== РЕТРО ===============================
    static final int BEIGE = rgb(214, 205, 178);
    static final CaseTheme RETRO = new CaseTheme();
    static {
        CaseTheme t = RETRO;
        t.wall = rgb(186, 178, 152); t.wallDot = rgb(170, 162, 136); t.frame = rgb(214, 205, 178); t.shroud = rgb(200, 192, 166); t.accent = rgb(150, 70, 30);
        t.winFill = BEIGE; t.winLight = rgb(236, 230, 210); t.winDark = rgb(150, 140, 112); t.winOutline = rgb(70, 60, 40);
        t.titleColor = rgb(90, 70, 40); t.scoreColor = rgb(120, 90, 40);
        t.caseFill = rgb(78, 78, 74); t.caseTL = rgb(40, 40, 38); t.caseBR = rgb(150, 146, 132);
        t.pcb = rgb(58, 120, 72); t.labelColor = rgb(236, 240, 220); t.labelShadow = rgb(30, 50, 30);
        t.slotFill = rgb(166, 158, 134); t.slotTL = rgb(96, 88, 66); t.slotBR = rgb(236, 230, 210); t.ghost = 0x50402A10;
        t.bezel = rgb(180, 172, 146); t.glass = rgb(14, 26, 18); t.fg = rgb(110, 255, 140); t.dim = rgb(50, 120, 70);
        t.ok = rgb(150, 255, 170); t.bad = rgb(255, 110, 80); t.warn = rgb(230, 230, 110);
        t.barBg = rgb(24, 52, 32); t.tabOn = rgb(30, 70, 40); t.tabOff = rgb(18, 36, 24);
        t.scan = true; t.roundCorners = true;
        t.btnFace = rgb(206, 197, 170); t.btnRing = rgb(70, 60, 40); t.btnSymbol = rgb(90, 70, 40);
        t.ledOn = rgb(255, 60, 40); t.ledOff = rgb(90, 40, 30);
        t.portsFill = rgb(196, 187, 160); t.portsTL = rgb(236, 230, 210); t.portsBR = rgb(150, 140, 112); t.portsLabel = rgb(90, 70, 40);
        t.graphBg = rgb(14, 26, 18); t.graphFg = rgb(110, 255, 140);
        t.deco = Deco.RETRO;
    }

    // =============================== ОФИС ===============================
    static final int OFFICE_BLUE = rgb(60, 120, 220);
    static final CaseTheme OFFICE = new CaseTheme();
    static {
        CaseTheme t = OFFICE;
        t.wall = rgb(206, 211, 218); t.wallDot = rgb(190, 196, 204); t.frame = rgb(232, 235, 240); t.shroud = rgb(222, 226, 232); t.accent = OFFICE_BLUE;
        t.winFill = rgb(232, 234, 238); t.winLight = rgb(255, 255, 255); t.winDark = rgb(176, 180, 188); t.winOutline = rgb(90, 94, 102);
        t.titleColor = rgb(40, 44, 52); t.scoreColor = OFFICE_BLUE;
        t.caseFill = rgb(246, 247, 249); t.caseTL = rgb(190, 194, 200); t.caseBR = rgb(255, 255, 255);
        t.pcb = rgb(52, 56, 64); t.labelColor = rgb(214, 220, 230);
        t.slotFill = rgb(214, 218, 224); t.slotTL = rgb(150, 156, 166); t.slotBR = rgb(255, 255, 255); t.ghost = 0x48203040;
        t.bezel = rgb(200, 204, 212); t.glass = rgb(252, 252, 253); t.fg = rgb(40, 44, 52); t.dim = rgb(140, 146, 156);
        t.ok = rgb(40, 150, 80); t.bad = rgb(210, 50, 50); t.warn = rgb(200, 130, 0);
        t.barBg = rgb(222, 226, 232); t.tabOn = rgb(220, 232, 250); t.tabOff = rgb(240, 242, 245);
        t.roundButton = true;
        t.btnFace = rgb(240, 242, 245); t.btnRing = OFFICE_BLUE; t.btnSymbol = OFFICE_BLUE;
        t.ledOn = rgb(60, 150, 255); t.ledOff = rgb(180, 184, 190);
        t.portsFill = rgb(246, 247, 249); t.portsTL = rgb(255, 255, 255); t.portsBR = rgb(190, 194, 200); t.portsLabel = rgb(40, 44, 52);
        t.graphBg = rgb(240, 244, 250); t.graphFg = OFFICE_BLUE;
        t.deco = Deco.OFFICE;
    }

    // =============================== ИГРОВАЯ ===============================
    static final CaseTheme GAMING = new CaseTheme();
    static {
        CaseTheme t = GAMING;
        t.wall = rgb(22, 21, 27); t.wallDot = rgb(30, 29, 37); t.frame = rgb(40, 38, 50); t.shroud = rgb(31, 29, 38); t.accent = rgb(255, 79, 208);
        t.glassPanel = true; t.rgbLights = true;
        t.winFill = rgb(26, 26, 32); t.winLight = rgb(56, 56, 66); t.winDark = rgb(10, 10, 12); t.winOutline = rgb(0, 0, 0);
        t.titleColor = rgb(230, 230, 240); t.scoreColor = rgb(255, 90, 210);
        t.caseFill = rgb(14, 14, 18); t.caseTL = rgb(6, 6, 8); t.caseBR = rgb(40, 40, 48);
        t.pcb = rgb(30, 32, 40); t.labelColor = rgb(200, 200, 220);
        t.slotFill = rgb(36, 36, 44); t.slotTL = rgb(12, 12, 16); t.slotBR = rgb(70, 70, 82); t.slotRing = rgb(90, 60, 160);
        t.ghost = 0x40C8B4FF;
        t.bezel = rgb(40, 40, 48); t.glass = rgb(10, 10, 14); t.fg = rgb(120, 230, 255); t.dim = rgb(70, 90, 110);
        t.ok = rgb(255, 90, 210); t.bad = rgb(255, 80, 80); t.warn = rgb(255, 210, 90);
        t.barBg = rgb(30, 30, 40); t.tabOn = rgb(50, 30, 80); t.tabOff = rgb(22, 22, 30);
        t.roundButton = true;
        t.btnFace = rgb(30, 30, 36); t.btnRing = rgb(255, 60, 200); t.btnSymbol = rgb(120, 230, 255);
        t.ledOn = rgb(255, 60, 200); t.ledOff = rgb(60, 40, 60);
        t.portsFill = rgb(22, 22, 28); t.portsTL = rgb(10, 10, 12); t.portsBR = rgb(60, 60, 70); t.portsLabel = rgb(200, 200, 220);
        t.graphBg = rgb(10, 10, 14); t.graphFg = rgb(255, 90, 210);
        t.deco = Deco.GAMING;
    }

    /** Радужная подсветка: розовый → фиолетовый → синий → бирюзовый. */
    static int rgbAt(double t) {
        int[] stops = {rgb(255, 60, 200), rgb(140, 80, 255), rgb(60, 140, 255), rgb(60, 230, 230)};
        t = (t % 1.0 + 1.0) % 1.0 * (stops.length - 1);
        int i = (int) t;
        return lerp(stops[i], stops[Math.min(i + 1, stops.length - 1)], t - i);
    }

    // =============================== ПРОМЫШЛЕННАЯ ===============================
    static final int STEEL = rgb(104, 118, 124);
    static final CaseTheme INDUSTRIAL = new CaseTheme();
    static {
        CaseTheme t = INDUSTRIAL;
        t.wall = rgb(72, 82, 88); t.wallDot = rgb(46, 52, 56); t.frame = rgb(120, 134, 140); t.shroud = rgb(88, 100, 106); t.accent = rgb(255, 176, 40);
        t.winFill = STEEL; t.winLight = rgb(150, 164, 170); t.winDark = rgb(60, 70, 74); t.winOutline = rgb(20, 24, 26);
        t.titleColor = rgb(240, 240, 236); t.scoreColor = rgb(255, 176, 40);
        t.caseFill = rgb(72, 82, 88); t.caseTL = rgb(40, 46, 50); t.caseBR = rgb(130, 144, 150);
        t.pcb = rgb(40, 70, 52); t.labelColor = rgb(240, 230, 200); t.labelShadow = rgb(20, 20, 20);
        t.slotFill = rgb(56, 64, 68); t.slotTL = rgb(24, 28, 30); t.slotBR = rgb(130, 144, 150); t.ghost = 0x38FFFFFF;
        t.bezel = rgb(60, 66, 60); t.glass = rgb(40, 22, 6); t.fg = rgb(255, 176, 40); t.dim = rgb(140, 90, 30);
        t.ok = rgb(255, 210, 90); t.bad = rgb(255, 90, 60); t.warn = rgb(255, 230, 120);
        t.barBg = rgb(70, 40, 10); t.tabOn = rgb(90, 52, 14); t.tabOff = rgb(56, 32, 10);
        t.roundButton = true;
        t.btnFace = rgb(200, 30, 30); t.btnRing = rgb(230, 190, 40); t.btnSymbol = rgb(255, 230, 230);
        t.ledOn = rgb(90, 255, 90); t.ledOff = rgb(60, 60, 60);
        t.portsFill = rgb(84, 96, 102); t.portsTL = rgb(150, 164, 170); t.portsBR = rgb(40, 46, 50); t.portsLabel = rgb(240, 230, 200);
        t.graphBg = rgb(40, 22, 6); t.graphFg = rgb(255, 176, 40);
        t.deco = Deco.INDUSTRIAL;
    }

    // =============================== СЕРВЕРНАЯ ===============================
    static final CaseTheme SERVER = new CaseTheme();
    static {
        CaseTheme t = SERVER;
        t.wall = rgb(30, 33, 38); t.wallDot = rgb(38, 42, 48); t.frame = rgb(64, 68, 78); t.shroud = rgb(44, 48, 54); t.accent = rgb(92, 240, 106);
        t.winFill = rgb(40, 43, 50); t.winLight = rgb(70, 74, 84); t.winDark = rgb(18, 20, 24); t.winOutline = rgb(0, 0, 0);
        t.titleColor = rgb(220, 224, 230); t.scoreColor = rgb(60, 255, 120);
        t.caseFill = rgb(28, 30, 36); t.caseTL = rgb(14, 15, 18); t.caseBR = rgb(64, 68, 78);
        t.pcb = rgb(36, 40, 48); t.labelColor = rgb(170, 176, 186);
        t.slotFill = rgb(62, 66, 76); t.slotTL = rgb(20, 22, 26); t.slotBR = rgb(100, 106, 118); t.ghost = 0x34FFFFFF;
        t.bezel = rgb(58, 62, 70); t.glass = rgb(8, 20, 12); t.fg = rgb(120, 255, 150); t.dim = rgb(50, 110, 70);
        t.ok = rgb(160, 255, 180); t.bad = rgb(255, 100, 90); t.warn = rgb(255, 200, 80);
        t.barBg = rgb(20, 44, 28); t.tabOn = rgb(30, 70, 40); t.tabOff = rgb(16, 34, 22);
        t.btnFace = rgb(70, 74, 84); t.btnRing = rgb(0, 0, 0); t.btnSymbol = rgb(200, 204, 210);
        t.ledOn = rgb(60, 255, 120); t.ledOff = rgb(40, 60, 44);
        t.portsFill = rgb(36, 39, 46); t.portsTL = rgb(70, 74, 84); t.portsBR = rgb(18, 20, 24); t.portsLabel = rgb(170, 176, 186);
        t.graphBg = rgb(8, 20, 12); t.graphFg = rgb(120, 255, 150);
        t.deco = Deco.SERVER;
    }
}
