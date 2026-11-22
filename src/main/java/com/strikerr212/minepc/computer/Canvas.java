package com.strikerr212.minepc.computer;

import org.squiddev.cobalt.*;
import org.squiddev.cobalt.function.RegisteredFunction;

import static org.squiddev.cobalt.ValueFactory.valueOf;

/**
 * Кадровый буфер видеокарты: картинка из точек (до ширины экрана × двойной высоты), в которую программа
 * рисует быстрыми командами на Java, а потом одной командой present() выводит на экран полублоками «▀».
 *
 * Ускорители для игр и графики: заливка, градиент, столбец стены с текстурой (как в Doom и Wolfenstein),
 * горизонтальная полоса пола с текстурой, спрайт с прозрачностью и буфером глубины.
 * Координаты точек — с нуля (как в графических библиотеках), цвета — 0xRRGGBB.
 */
final class Canvas {

    static final int TRANSPARENT = -1;

    final int w, h;
    final int[] px;
    /** Глубина по столбцам: что ближе, то закрывает спрайты (column() записывает, sprite() проверяет). */
    final float[] depth;
    boolean freed;
    /**
     * Во сколько раз точка этого буфера дешевле обычной: буфер для картинки (cv:image) мельче полублоков —
     * на клетку экрана в нём 8 точек, а не 2, — поэтому тот же экран стоит видеокарте и процессору столько же.
     */
    int costDiv = 1;
    /** 3D: появляется при первой 3D-команде (буфер глубины по точкам — только тем, кому нужен). */
    private Raster3D r3d;

    /** Закрасить накопленные 3D-треугольники (перед любой другой работой с кадром). */
    void flush3d() {
        if (r3d != null && r3d.pending()) r3d.flush();
    }

    Raster3D r3d() {
        if (r3d == null) r3d = new Raster3D(this);
        return r3d;
    }

    Canvas(int w, int h) {
        this.w = w;
        this.h = h;
        px = new int[w * h];
        depth = new float[w];
        java.util.Arrays.fill(depth, Float.MAX_VALUE);
    }

    long cells() {
        return (long) w * ((h + 1) / 2);
    }

    /** Текстура: точки RGB, TRANSPARENT — прозрачная. */
    record Texture(int w, int h, int[] px) {
        long cells() {
            return (long) w * ((h + 1) / 2);
        }
    }

    // ======================= Рисование =======================

    private static int shade(int rgb, float k) {
        if (k >= 0.999f) return rgb;
        if (k <= 0) return 0;
        int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
        return r << 16 | g << 8 | b;
    }

    private static int wrap(int v, int n) {
        int m = v % n;
        return m < 0 ? m + n : m;
    }

    int fill(int x, int y, int fw, int fh, int rgb) {
        int x0 = Math.max(0, x), y0 = Math.max(0, y), x1 = Math.min(w, x + fw), y1 = Math.min(h, y + fh);
        if (x0 >= x1 || y0 >= y1) return 0;
        for (int yy = y0; yy < y1; yy++) java.util.Arrays.fill(px, yy * w + x0, yy * w + x1, rgb & 0xFFFFFF);
        return (x1 - x0) * (y1 - y0);
    }

    /** Вертикальный градиент (небо, пол с затуханием). */
    int gradient(int x, int y, int fw, int fh, int top, int bottom) {
        int x0 = Math.max(0, x), y0 = Math.max(0, y), x1 = Math.min(w, x + fw), y1 = Math.min(h, y + fh);
        if (x0 >= x1 || y0 >= y1) return 0;
        for (int yy = y0; yy < y1; yy++) {
            float t = fh <= 1 ? 0 : (yy - y) / (float) (fh - 1);
            int r = (int) (((top >> 16) & 255) * (1 - t) + ((bottom >> 16) & 255) * t);
            int g = (int) (((top >> 8) & 255) * (1 - t) + ((bottom >> 8) & 255) * t);
            int b = (int) ((top & 255) * (1 - t) + (bottom & 255) * t);
            java.util.Arrays.fill(px, yy * w + x0, yy * w + x1, r << 16 | g << 8 | b);
        }
        return (x1 - x0) * (y1 - y0);
    }

    /**
     * Столбец стены: экранный столбец x от y0 до y1 (могут выходить за экран), столбец текстуры tx,
     * строки текстуры от ty0 (вверху) до ty1 (внизу), яркость shade, глубина d (для спрайтов).
     */
    int column(int x, double y0, double y1, Texture t, int tx, double ty0, double ty1, float k, float d) {
        if (x < 0 || x >= w || y1 <= y0) return 0;
        if (d >= 0) depth[x] = d;
        int a = (int) Math.max(0, Math.ceil(y0 - 0.5)), b = (int) Math.min(h - 1, Math.floor(y1 - 0.5));
        if (a > b) return 0;
        int col = wrap(tx, t.w);
        double step = (ty1 - ty0) / (y1 - y0);
        double ty = ty0 + (a + 0.5 - y0) * step;
        int n = 0;
        for (int y = a; y <= b; y++, ty += step) {
            int c = t.px[wrap((int) Math.floor(ty), t.h) * t.w + col];
            if (c != TRANSPARENT) px[y * w + x] = shade(c, k);
            n++;
        }
        return n;
    }

    /** Горизонтальная полоса (пол, потолок): строка y от x0 до x1, координаты текстуры от (u0,v0) до (u1,v1). */
    int span(int y, double x0, double x1, Texture t, double u0, double v0, double u1, double v1, float k) {
        if (y < 0 || y >= h || x1 <= x0) return 0;
        int a = (int) Math.max(0, Math.ceil(x0 - 0.5)), b = (int) Math.min(w - 1, Math.floor(x1 - 0.5));
        if (a > b) return 0;
        double du = (u1 - u0) / (x1 - x0), dv = (v1 - v0) / (x1 - x0);
        double u = u0 + (a + 0.5 - x0) * du, v = v0 + (a + 0.5 - x0) * dv;
        int row = y * w;
        for (int x = a; x <= b; x++, u += du, v += dv) {
            int c = t.px[wrap((int) Math.floor(v), t.h) * t.w + wrap((int) Math.floor(u), t.w)];
            if (c != TRANSPARENT) px[row + x] = shade(c, k);
        }
        return b - a + 1;
    }

    /**
     * Спрайт: текстура, растянутая в прямоугольник (x, y, sw, sh) с прозрачностью.
     * d ≥ 0 — глубина: столбцы, где стена ближе (depth[x] < d), не рисуются. flip — зеркально.
     */
    int sprite(Texture t, double x, double y, double sw, double sh, float k, float d, boolean flip) {
        if (sw <= 0 || sh <= 0) return 0;
        int a = (int) Math.max(0, Math.ceil(x - 0.5)), b = (int) Math.min(w - 1, Math.floor(x + sw - 0.5));
        int top = (int) Math.max(0, Math.ceil(y - 0.5)), bot = (int) Math.min(h - 1, Math.floor(y + sh - 0.5));
        if (a > b || top > bot) return 0;
        int n = 0;
        for (int cx = a; cx <= b; cx++) {
            if (d >= 0 && depth[cx] < d) continue;
            int tx = (int) ((cx + 0.5 - x) / sw * t.w);
            tx = Math.max(0, Math.min(t.w - 1, flip ? t.w - 1 - tx : tx));
            for (int cy = top; cy <= bot; cy++) {
                int ty = Math.max(0, Math.min(t.h - 1, (int) ((cy + 0.5 - y) / sh * t.h)));
                int c = t.px[ty * t.w + tx];
                if (c != TRANSPARENT) px[cy * w + cx] = shade(c, k);
                n++;
            }
        }
        return n;
    }

    // ======================= Lua =======================

    /** Первый аргумент — сам объект (вызов через двоеточие) или сразу параметры (через точку). */
    private static int base(Varargs a, LuaTable self) {
        return a.arg(1) == self ? 1 : 0;
    }

    private static Texture tex(Gpu gpu, LuaValue v) throws LuaError {
        Texture t = gpu.texture(v.checkInteger());
        if (t == null) throw LuaApis.error("нет такой текстуры");
        return t;
    }

    /**
     * Нарисовать 3D-сетку. Оплата: процессор — за вершины, треугольники и точки (работа драйвера),
     * видеокарта — за треугольники и закрашенные точки, так что слабая видеокарта даёт меньше кадров.
     */
    private static Varargs draw3d(Computer c, LuaMachine m, Gpu gpu, Canvas cv, Varargs a, int o, boolean onCamera) throws LuaError {
        Raster3D.Mesh mesh = gpu.mesh(a.arg(o + 1).checkInteger());
        if (mesh == null) throw LuaApis.error("нет такой сетки");
        Raster3D.Work work = new Raster3D.Work();
        cv.r3d().mesh(mesh, gpu::texture, a.arg(o + 2).checkDouble(), a.arg(o + 3).checkDouble(), a.arg(o + 4).checkDouble(),
                a.arg(o + 5).optDouble(0), a.arg(o + 6).optDouble(0), a.arg(o + 7).optDouble(1), a.arg(o + 8).optDouble(1),
                a.arg(o + 9).optInteger(0), onCamera, work, 4L * cv.px.length);
        long px = work.pixels / cv.costDiv;
        m.spend(work.verts / 4 + work.tris / 2 + px / 32 + 8);
        c.chargeGpu(px / 8 + work.tris / 2);
        return valueOf((int) work.pixels);
    }

    /** Объект для программ: таблица функций, работающих с этим буфером. */
    static LuaTable lua(Computer c, LuaMachine m, Gpu gpu, Canvas cv) {
        LuaTable t = new LuaTable();
        t.rawset("width", valueOf(cv.w));
        t.rawset("height", valueOf(cv.h));
        // Команды — дешевле, чем Lua: примерно 1 команда на 8 точек
        RegisteredFunction.bind(t, new RegisteredFunction[]{
                RegisteredFunction.ofV("clear", (s, a) -> {
                    int o = base(a, t); cv.flush3d();
                    java.util.Arrays.fill(cv.px, a.arg(o + 1).optInteger(0) & 0xFFFFFF);
                    java.util.Arrays.fill(cv.depth, Float.MAX_VALUE);
                    if (cv.r3d != null) cv.r3d.clearDepth();
                    m.spend(cv.px.length / cv.costDiv / 16 + 8);
                    return Constants.NONE;
                }),
                RegisteredFunction.ofV("clearDepth", (s, a) -> {
                    cv.flush3d();
                    java.util.Arrays.fill(cv.depth, Float.MAX_VALUE);
                    if (cv.r3d != null) {
                        cv.r3d.clearDepth();
                        m.spend(cv.px.length / cv.costDiv / 64 + 4);
                    }
                    return Constants.NONE;
                }),
                // ----- 3D: камера, туман, лампы, сетки (gpu.newMesh), спрайты в мире -----
                // view(x, y, z, yaw, pitch, [угол обзора = 90], [сколько точка шире, чем выше = 4/3])
                RegisteredFunction.ofV("view", (s, a) -> {
                    int o = base(a, t); cv.flush3d();
                    cv.r3d().view(a.arg(o + 1).checkDouble(), a.arg(o + 2).checkDouble(), a.arg(o + 3).checkDouble(),
                            a.arg(o + 4).optDouble(0), a.arg(o + 5).optDouble(0), a.arg(o + 6).optDouble(90),
                            a.arg(o + 7).optDouble(4.0 / 3.0));
                    return Constants.NONE;
                }),
                // fog(цвет, начало, конец) — даль растворяется в цвете; fog() — без тумана
                RegisteredFunction.ofV("fog", (s, a) -> {
                    int o = base(a, t); cv.flush3d();
                    if (a.arg(o + 1).isNil()) cv.r3d().fogOff();
                    else cv.r3d().fog(a.arg(o + 1).checkInteger(), a.arg(o + 2).checkDouble(), a.arg(o + 3).checkDouble());
                    return Constants.NONE;
                }),
                // light(x, y, z, радиус, цвет, [сила = 1]) — лампа на этот кадр (до 16); clearLights() — убрать все
                RegisteredFunction.ofV("light", (s, a) -> {
                    int o = base(a, t); cv.flush3d();
                    return valueOf(cv.r3d().light(a.arg(o + 1).checkDouble(), a.arg(o + 2).checkDouble(), a.arg(o + 3).checkDouble(),
                            a.arg(o + 4).checkDouble(), a.arg(o + 5).optInteger(0xFFFFFF), a.arg(o + 6).optDouble(1)));
                }),
                RegisteredFunction.ofV("clearLights", (s, a) -> {
                    cv.r3d().clearLights();
                    return Constants.NONE;
                }),
                // mesh(сетка, x, y, z, [yaw], [pitch], [масштаб], [яркость], [вспышка 0xRRGGBB]) → закрашено точек
                RegisteredFunction.ofV("mesh", (s, a) -> draw3d(c, m, gpu, cv, a, base(a, t), false)),
                // held(сетка, вправо, вверх, вперёд, ...) — то же в координатах камеры (оружие в руках)
                RegisteredFunction.ofV("held", (s, a) -> draw3d(c, m, gpu, cv, a, base(a, t), true)),
                // billboard(текстура, x, y, z, ширина, высота, [яркость], [вспышка], [флаги: 1 светится, 2 не закрывает])
                RegisteredFunction.ofV("billboard", (s, a) -> {
                    int o = base(a, t); cv.flush3d();
                    int n = cv.r3d().billboard(tex(gpu, a.arg(o + 1)), a.arg(o + 2).checkDouble(), a.arg(o + 3).checkDouble(),
                            a.arg(o + 4).checkDouble(), a.arg(o + 5).checkDouble(), a.arg(o + 6).checkDouble(),
                            a.arg(o + 7).optDouble(1), a.arg(o + 8).optInteger(0), a.arg(o + 9).optInteger(0));
                    m.spend(n / cv.costDiv / 16 + 8);
                    c.chargeGpu(n / cv.costDiv / 8 + 1);
                    return valueOf(n);
                }),
                // project(x, y, z) → x, y на кадре (с нуля) и расстояние, или nil — точка позади
                RegisteredFunction.ofV("project", (s, a) -> {
                    int o = base(a, t); cv.flush3d();
                    double[] p = cv.r3d().project(a.arg(o + 1).checkDouble(), a.arg(o + 2).checkDouble(), a.arg(o + 3).checkDouble());
                    if (p == null) return Constants.NIL;
                    return org.squiddev.cobalt.ValueFactory.varargsOf(valueOf(p[0]), valueOf(p[1]), valueOf(p[2]));
                }),
                // tint(цвет, доля 0..1) — смешать весь кадр с цветом
                RegisteredFunction.ofV("tint", (s, a) -> {
                    int o = base(a, t); cv.flush3d();
                    int n = cv.r3d().tint(a.arg(o + 1).checkInteger(), a.arg(o + 2).checkDouble());
                    m.spend(n / cv.costDiv / 32 + 4);
                    return Constants.NONE;
                }),
                RegisteredFunction.ofV("pixel", (s, a) -> {
                    int o = base(a, t); cv.flush3d();
                    int x = a.arg(o + 1).checkInteger(), y = a.arg(o + 2).checkInteger();
                    if (x >= 0 && y >= 0 && x < cv.w && y < cv.h) cv.px[y * cv.w + x] = a.arg(o + 3).checkInteger() & 0xFFFFFF;
                    return Constants.NONE;
                }),
                // Текст пиксельным шрифтом прямо в кадр: text(x, y, строка, цвет, [масштаб], [цвет обводки]) → ширина в точках
                RegisteredFunction.ofV("text", (s, a) -> {
                    int o = base(a, t); cv.flush3d();
                    String str = LuaApis.text(a.arg(o + 3).checkLuaString());
                    if (str.length() > 400) str = str.substring(0, 400);
                    int outline = a.arg(o + 6).isNil() ? -1 : a.arg(o + 6).checkInteger() & 0xFFFFFF;
                    int scale = a.arg(o + 5).optInteger(1);
                    int wpx = PixelFont.draw(cv.px, cv.w, cv.h, a.arg(o + 1).checkInteger(), a.arg(o + 2).checkInteger(), str,
                            a.arg(o + 4).checkInteger() & 0xFFFFFF, scale, outline);
                    m.spend((long) str.length() * scale * scale / 2 + 4);
                    return valueOf(wpx);
                }),
                // Ширина строки пиксельным шрифтом: textWidth(строка, [масштаб])
                RegisteredFunction.ofV("textWidth", (s, a) -> {
                    int o = base(a, t); cv.flush3d();
                    int scale = Math.max(1, a.arg(o + 2).optInteger(1));
                    return valueOf(PixelFont.width(LuaApis.text(a.arg(o + 1).checkLuaString())) * scale);
                }),
                RegisteredFunction.ofV("get", (s, a) -> {
                    int o = base(a, t); cv.flush3d();
                    int x = a.arg(o + 1).checkInteger(), y = a.arg(o + 2).checkInteger();
                    if (x < 0 || y < 0 || x >= cv.w || y >= cv.h) return Constants.NIL;
                    return valueOf(cv.px[y * cv.w + x]);
                }),
                RegisteredFunction.ofV("fill", (s, a) -> {
                    int o = base(a, t); cv.flush3d();
                    int n = cv.fill(a.arg(o + 1).checkInteger(), a.arg(o + 2).checkInteger(), a.arg(o + 3).checkInteger(),
                            a.arg(o + 4).checkInteger(), a.arg(o + 5).checkInteger());
                    m.spend(n / cv.costDiv / 16 + 4);
                    return Constants.NONE;
                }),
                RegisteredFunction.ofV("gradient", (s, a) -> {
                    int o = base(a, t); cv.flush3d();
                    int n = cv.gradient(a.arg(o + 1).checkInteger(), a.arg(o + 2).checkInteger(), a.arg(o + 3).checkInteger(),
                            a.arg(o + 4).checkInteger(), a.arg(o + 5).checkInteger(), a.arg(o + 6).checkInteger());
                    m.spend(n / cv.costDiv / 16 + 4);
                    return Constants.NONE;
                }),
                RegisteredFunction.ofV("column", (s, a) -> {
                    int o = base(a, t); cv.flush3d();
                    int n = cv.column(a.arg(o + 1).checkInteger(), a.arg(o + 2).checkDouble(), a.arg(o + 3).checkDouble(),
                            tex(gpu, a.arg(o + 4)), (int) Math.floor(a.arg(o + 5).checkDouble()), a.arg(o + 6).checkDouble(),
                            a.arg(o + 7).checkDouble(), (float) a.arg(o + 8).optDouble(1), (float) a.arg(o + 9).optDouble(-1));
                    m.spend(n / cv.costDiv / 8 + 4);
                    return Constants.NONE;
                }),
                RegisteredFunction.ofV("span", (s, a) -> {
                    int o = base(a, t); cv.flush3d();
                    int n = cv.span(a.arg(o + 1).checkInteger(), a.arg(o + 2).checkDouble(), a.arg(o + 3).checkDouble(),
                            tex(gpu, a.arg(o + 4)), a.arg(o + 5).checkDouble(), a.arg(o + 6).checkDouble(),
                            a.arg(o + 7).checkDouble(), a.arg(o + 8).checkDouble(), (float) a.arg(o + 9).optDouble(1));
                    m.spend(n / cv.costDiv / 8 + 4);
                    return Constants.NONE;
                }),
                RegisteredFunction.ofV("sprite", (s, a) -> {
                    int o = base(a, t); cv.flush3d();
                    int n = cv.sprite(tex(gpu, a.arg(o + 1)), a.arg(o + 2).checkDouble(), a.arg(o + 3).checkDouble(),
                            a.arg(o + 4).checkDouble(), a.arg(o + 5).checkDouble(), (float) a.arg(o + 6).optDouble(1),
                            (float) a.arg(o + 7).optDouble(-1), a.arg(o + 8).toBoolean());
                    m.spend(n / cv.costDiv / 8 + 4);
                    return Constants.NONE;
                }),
                RegisteredFunction.ofV("present", (s, a) -> {
                    int o = base(a, t); cv.flush3d();
                    if (cv.freed) throw LuaApis.error("буфер освобождён");
                    int x = a.arg(o + 1).optInteger(1) - 1, y = a.arg(o + 2).optInteger(1) - 1;
                    // В активный буфер видеокарты (так программа с задним буфером, например Wired, рисует кадр к себе)
                    Terminal dst = gpu.target();
                    // необязательная обрезка: present(x, y, x1, y1, x2, y2) — клетки, куда можно рисовать
                    int changed = a.arg(o + 3).isNil() ? dst.putPixels(x, y, cv.px, cv.w, cv.h)
                            : dst.putPixels(x, y, cv.px, cv.w, cv.h, a.arg(o + 3).checkInteger() - 1, a.arg(o + 4).checkInteger() - 1,
                            a.arg(o + 5).checkInteger() - 1, a.arg(o + 6).checkInteger() - 1);
                    if (gpu.targetIsScreen()) c.chargeGpu(changed / 4);
                    m.spend(cv.px.length / 32 + 8);
                    return valueOf(changed);
                }),
                // Показать кадр картинкой (вдвое шире и вчетверо выше полублоков): image(x, y) — клетка левого верхнего угла.
                // Только на экран компьютера (не в задний буфер). Возвращает true или nil, причина.
                RegisteredFunction.ofV("image", (s, a) -> {
                    int o = base(a, t); cv.flush3d();
                    if (cv.freed) throw LuaApis.error("буфер освобождён");
                    int x = a.arg(o + 1).optInteger(1) - 1, y = a.arg(o + 2).optInteger(1) - 1;
                    if (!Terminal.imagesAllowed) return org.squiddev.cobalt.ValueFactory.varargsOf(Constants.NIL, LuaApis.str("картинки выключены на сервере"));
                    if (!gpu.targetIsScreen()) return org.squiddev.cobalt.ValueFactory.varargsOf(Constants.NIL, LuaApis.str("картинка — только на экран"));
                    Terminal.Image im = c.terminal().setImage(x, y, cv.w, cv.h, cv.px);
                    if (im == null) return org.squiddev.cobalt.ValueFactory.varargsOf(Constants.NIL, LuaApis.str("картинка не помещается на экран"));
                    // как вывод полублоками той же площади экрана (клетка = 2 точки полублоками)
                    c.chargeGpu(cv.px.length / cv.costDiv / 8);
                    m.spend(cv.px.length / cv.costDiv / 32 + 8);
                    return Constants.TRUE;
                }),
                // Кадр камеры наблюдения: canvas:camera(имя, [x, y, w, h]) → частота или nil, ошибка
                RegisteredFunction.ofV("camera", (s, a) -> {
                    int o = base(a, t); cv.flush3d();
                    String target = LuaApis.text(a.arg(o + 1).checkLuaString());
                    int x = a.arg(o + 2).optInteger(0), y = a.arg(o + 3).optInteger(0);
                    int fw = a.arg(o + 4).optInteger(cv.w - x), fh = a.arg(o + 5).optInteger(cv.h - y);
                    Object r = c.cameraDraw(target, cv.px, cv.w, cv.h, x, y, Math.max(1, fw), Math.max(1, fh));
                    m.spend(Math.max(1, fw) * Math.max(1, fh) / 16 + 16);
                    if (r instanceof String err) return org.squiddev.cobalt.ValueFactory.varargsOf(Constants.NIL, LuaApis.str(err));
                    return valueOf((Integer) r);
                }),
                RegisteredFunction.ofV("free", (s, a) -> {
                    gpu.freeCanvas(cv);
                    return Constants.NONE;
                }),
        });
        return t;
    }
}
