package com.strikerr212.minepc.block;

/**
 * Вид монитора: где на блоке экран и как блоки складываются в большой экран.
 * Экран задан как видит зритель: u — от левого края блока, v — от верхнего (пиксели блока);
 * z — передняя грань экрана у монитора, смотрящего на север. Числа печатает tools/assetgen/monitors.py.
 */
public enum MonitorKind {
    /** Плоский ЖК на подставке: складывается в стену, рамка 1 пиксель, снизу подбородок. */
    LCD(1, 15, 1, 13, 11, true, false, false),
    /** Кинескоп: один блок, глубокий корпус. */
    CRT(3, 13, 3, 12, 1, false, false, false),
    /** Ламповый телевизор. */
    TV(2, 10, 4, 13, 2, false, false, false),
    /** Терминал: экран и клавиатура одним корпусом. */
    TERMINAL(3, 13, 3, 11, 5, false, false, true),
    /** Промышленная панель на стену. */
    PANEL(3, 13, 2, 12, 14, false, false, false),
    /** Блочный монитор: стыкуется без швов, рамка только по краю всей стены. */
    BLOCK(1, 15, 1, 15, 0, true, true, false),
    /** Широкоформатный в два блока (uR — в правом блоке). */
    WIDE(2, 14, 1, 11, 6, false, false, false),
    /** Изогнутый в два блока: края экрана выдвинуты к зрителю (см. {@link #curve(int)}). */
    CURVED(1, 15, 1, 11, 7, false, false, false);

    /** Насколько столбец u изогнутого экрана (0..31 по ширине двух блоков) выдвинут к зрителю, пиксели. */
    private static final int[] CURVE = new int[32];
    static {
        for (int u = 0; u < 32; u++) CURVE[u] = (int) Math.rint((u - 15.5) * (u - 15.5) / 70);
    }

    public int curve(int u) {
        return this == CURVED && u >= 0 && u < 32 ? CURVE[u] : 0;
    }

    /** Монитор в два блока: левая и правая половины — один экран. */
    public boolean pair() {
        return this == WIDE || this == CURVED;
    }

    public final int uL, uR, vT, vB, z;
    /** Соседние блоки складываются в один большой экран. */
    public final boolean groups;
    /** Без швов: рамку рисуют только крайние блоки. */
    public final boolean seamless;
    /** Клавиатура уже встроена. */
    public final boolean keyboard;

    MonitorKind(int uL, int uR, int vT, int vB, int z, boolean groups, boolean seamless, boolean keyboard) {
        this.uL = uL;
        this.uR = uR;
        this.vT = vT;
        this.vB = vB;
        this.z = z;
        this.groups = groups;
        this.seamless = seamless;
        this.keyboard = keyboard;
    }
}
