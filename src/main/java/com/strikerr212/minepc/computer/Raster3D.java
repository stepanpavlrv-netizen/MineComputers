package com.strikerr212.minepc.computer;

import java.util.Arrays;

/**
 * Настоящее 3D для кадрового буфера: треугольники с буфером глубины по точкам, текстуры с правильной
 * перспективой, освещение по вершинам (запечённое + до 16 цветных ламп на кадр), туман.
 * Программа один раз кладёт сетки в видеопамять (gpu.newMesh), а каждый кадр только ставит камеру
 * и говорит, какую сетку где нарисовать: поворот, отсечение и закраска — здесь, на Java.
 *
 * Координаты мира: x — восток, y — вверх, z — юг (как в Minecraft). Поворот yaw — от оси +x к оси +z,
 * pitch — вверх. Координаты текстуры u, v — в «повторах» (1 = вся текстура).
 */
final class Raster3D {

    /** Сетка в видеопамяти: вершины (x, y, z, u, v, свет) и треугольники (a, b, c, текстура). */
    record Mesh(float[] v, int[] t, int nv, int nt, float radius, float cx, float cy, float cz, boolean cull) {
        static final int STRIDE = 6;

        long cells() {
            return ((long) nv * STRIDE + (long) nt * 4) / 16 + 1;
        }

        static Mesh of(float[] v, int[] t, boolean cull) {
            int nv = v.length / STRIDE, nt = t.length / 4;
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (int i = 0; i < nv; i++) {
                float x = v[i * STRIDE], y = v[i * STRIDE + 1], z = v[i * STRIDE + 2];
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            }
            if (nv == 0) minX = minY = minZ = maxX = maxY = maxZ = 0;
            float cx = (minX + maxX) / 2, cy = (minY + maxY) / 2, cz = (minZ + maxZ) / 2;
            float r = 0;
            for (int i = 0; i < nv; i++) {
                float dx = v[i * STRIDE] - cx, dy = v[i * STRIDE + 1] - cy, dz = v[i * STRIDE + 2] - cz;
                r = Math.max(r, dx * dx + dy * dy + dz * dz);
            }
            return new Mesh(v, t, nv, nt, (float) Math.sqrt(r), cx, cy, cz, cull);
        }
    }

    /** Ищет текстуру по номеру (номера — как у gpu.newTexture). */
    interface Textures {
        Canvas.Texture get(int id);
    }

    /** Сколько работы ушло на вызов: для оплаты процессором и видеокартой. */
    static final class Work {
        long verts, tris, pixels;
    }

    static final float NEAR = 0.05f;
    static final int MAX_LIGHTS = 16;

    private final Canvas cv;
    private final int w, h;
    /** 1/глубина каждой точки: больше — ближе, 0 — пусто (бесконечно далеко). */
    final float[] z;

    // камера
    private double camX, camY, camZ;
    private double rx = 0, ry = 0, rz = 1, ux = 0, uy = 1, uz = 0, fx = 1, fy = 0, fz = 0;
    private double focalX, focalY, centerX, centerY;
    // туман
    private boolean fog;
    private int fogColor;
    private double fogStart, fogEnd;
    // лампы на этот кадр: x, y, z, радиус, r, g, b (0..1 — сила по каналам)
    private final double[] lights = new double[MAX_LIGHTS * 7];
    private int nLights;

    // рабочие массивы (растут под самую большую сетку)
    private double[] tv = new double[0];   // вершины в координатах камеры: x, y, z, свет r, g, b
    private final double[][] poly = new double[8][8];
    private final double[][] clip = new double[8][8];

    // ----- очередь треугольников: копятся за кадр и закрашиваются сразу всеми ядрами, каждое — своей полосой -----
    private static final int QS = 24;                       // три вершины по 8 чисел (x, y, 1/z, u/z, v/z, r, g, b)
    private double[] qv = new double[QS * 1024];
    private Canvas.Texture[] qt = new Canvas.Texture[1024];
    private int[] qf = new int[1024];
    private int qn;
    /** Помощники для закраски (на сервере их делят все компьютеры); null — одно ядро. */
    private static final java.util.concurrent.ExecutorService POOL;
    private static final int THREADS;

    static {
        int n = Math.max(0, Math.min(7, Runtime.getRuntime().availableProcessors() - 1));
        THREADS = n + 1;
        if (n == 0) POOL = null;
        else {
            java.util.concurrent.atomic.AtomicInteger k = new java.util.concurrent.atomic.AtomicInteger();
            POOL = java.util.concurrent.Executors.newFixedThreadPool(n, r -> {
                Thread t = new Thread(r, "MineComputers-3D-" + k.incrementAndGet());
                t.setDaemon(true);
                return t;
            });
        }
    }

    Raster3D(Canvas cv) {
        this.cv = cv;
        this.w = cv.w;
        this.h = cv.h;
        z = new float[w * h];
        view(0, 0, 0, 0, 0, 90, 4.0 / 3.0);
    }

    void clearDepth() {
        Arrays.fill(z, 0f);
    }

    // ======================= камера, туман, лампы =======================

    /** fov — угол обзора по горизонтали в градусах; aspect — точка шире, чем выше (4/3 у полублоков экрана). */
    void view(double x, double y, double zc, double yaw, double pitch, double fovDeg, double aspect) {
        camX = x; camY = y; camZ = zc;
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        fx = cp * cy; fy = sp; fz = cp * sy;
        rx = -sy; ry = 0; rz = cy;
        // вверх = вправо × вперёд
        ux = ry * fz - rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy - ry * fx;
        double fov = Math.max(10, Math.min(170, fovDeg)) * Math.PI / 180;
        focalX = (w / 2.0) / Math.tan(fov / 2);
        focalY = focalX * (aspect > 0 ? aspect : 4.0 / 3.0);
        centerX = w / 2.0;
        centerY = h / 2.0;
    }

    void fog(int color, double start, double end) {
        fog = end > start;
        fogColor = color & 0xFFFFFF;
        fogStart = start;
        fogEnd = end;
    }

    void fogOff() {
        fog = false;
    }

    boolean light(double x, double y, double zc, double radius, int color, double power) {
        if (nLights >= MAX_LIGHTS || radius <= 0) return false;
        int o = nLights++ * 7;
        lights[o] = x; lights[o + 1] = y; lights[o + 2] = zc; lights[o + 3] = radius;
        lights[o + 4] = ((color >> 16) & 255) / 255.0 * power;
        lights[o + 5] = ((color >> 8) & 255) / 255.0 * power;
        lights[o + 6] = (color & 255) / 255.0 * power;
        return true;
    }

    void clearLights() {
        nLights = 0;
    }

    /** Точка мира на экране: {x, y, расстояние вдоль взгляда} или null, если она позади камеры. */
    double[] project(double x, double y, double zc) {
        double dx = x - camX, dy = y - camY, dz = zc - camZ;
        double cz = dx * fx + dy * fy + dz * fz;
        if (cz < NEAR) return null;
        double cx = dx * rx + dy * ry + dz * rz, cyy = dx * ux + dy * uy + dz * uz;
        return new double[]{centerX + cx * focalX / cz, centerY - cyy * focalY / cz, cz};
    }

    // ======================= сетки =======================

    /**
     * Нарисовать сетку: масштаб s, поворот yaw (вокруг вертикали) и pitch (вокруг поперечной оси модели),
     * потом сдвиг в (px, py, pz). bright — яркость, flash — добавочный цвет (вспышка попадания).
     * onCamera — координаты в системе камеры (вправо, вверх, вперёд): так рисуют оружие в руках.
     */
    void mesh(Mesh m, Textures tex, double px, double py, double pz, double yaw, double pitch, double s,
              double bright, int flash, boolean onCamera, Work work, long pixelCap) {
        if (m.nt() == 0) return;
        double cyw = Math.cos(yaw), syw = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        // Отсечение целой сетки по сфере вокруг неё
        {
            double mx = m.cx() * s, my = m.cy() * s, mz = m.cz() * s;
            double ax = mx * cp - my * sp, ay = mx * sp + my * cp;
            double rx0 = ax * cyw - mz * syw, rz0 = ax * syw + mz * cyw;
            double wx = rx0 + px, wy = ay + py, wz = rz0 + pz;
            double cx, cyy, cz;
            // в руках: модель (вперёд +x, вправо +z) как перед камерой с поворотом 0; сдвиг — вправо, вверх, вперёд
            if (onCamera) { cx = rz0 + px; cyy = ay + py; cz = rx0 + pz; }
            else {
                double dx = wx - camX, dy = wy - camY, dz = wz - camZ;
                cx = dx * rx + dy * ry + dz * rz;
                cyy = dx * ux + dy * uy + dz * uz;
                cz = dx * fx + dy * fy + dz * fz;
            }
            double r = m.radius() * Math.abs(s);
            if (cz + r < NEAR) return;
            if (fog && cz - r > fogEnd) return;
            // боковые плоскости пирамиды взгляда: |x| ≤ z·(w/2)/focal
            double kx = centerX / focalX, ky = centerY / focalY;
            if (cx - r * Math.sqrt(1 + kx * kx) > cz * kx || -cx - r * Math.sqrt(1 + kx * kx) > cz * kx) return;
            if (cyy - r * Math.sqrt(1 + ky * ky) > cz * ky || -cyy - r * Math.sqrt(1 + ky * ky) > cz * ky) return;
        }
        int nv = m.nv();
        if (tv.length < nv * 6) tv = new double[nv * 6];
        float[] v = m.v();
        // Лампы, которые могут достать до сетки (в координатах мира)
        int[] near = nearLights(m, px, py, pz, s, cyw, syw, cp, sp, onCamera);
        int nNear = near.length;
        for (int i = 0; i < nv; i++) {
            int o = i * Mesh.STRIDE;
            double x = v[o] * s, y = v[o + 1] * s, zz = v[o + 2] * s;
            double ax = x * cp - y * sp, ay = x * sp + y * cp;
            double rx0 = ax * cyw - zz * syw, rz0 = ax * syw + zz * cyw;
            double wx = rx0 + px, wy = ay + py, wz = rz0 + pz;
            double l = v[o + 5] * bright;
            double lr = l, lg = l, lb = l;
            for (int k = 0; k < nNear; k++) {
                int q = near[k] * 7;
                double dx = wx - lights[q], dy = wy - lights[q + 1], dz = wz - lights[q + 2];
                double d2 = dx * dx + dy * dy + dz * dz, r = lights[q + 3];
                if (d2 < r * r) {
                    double a = 1 - Math.sqrt(d2) / r;
                    a *= a;
                    lr += lights[q + 4] * a; lg += lights[q + 5] * a; lb += lights[q + 6] * a;
                }
            }
            int t = i * 6;
            if (onCamera) {
                tv[t] = rz0 + px; tv[t + 1] = ay + py; tv[t + 2] = rx0 + pz;
            } else {
                double dx = wx - camX, dy = wy - camY, dz = wz - camZ;
                tv[t] = dx * rx + dy * ry + dz * rz;
                tv[t + 1] = dx * ux + dy * uy + dz * uz;
                tv[t + 2] = dx * fx + dy * fy + dz * fz;
            }
            tv[t + 3] = lr; tv[t + 4] = lg; tv[t + 5] = lb;
        }
        work.verts += nv;
        int[] tr = m.t();
        int fr = (flash >> 16) & 255, fg = (flash >> 8) & 255, fb = flash & 255;
        Canvas.Texture last = null;
        int lastId = Integer.MIN_VALUE;
        for (int i = 0; i < m.nt(); i++) {
            if (work.pixels > pixelCap) break;
            int a = tr[i * 4], b = tr[i * 4 + 1], c = tr[i * 4 + 2], id = tr[i * 4 + 3];
            if (id != lastId) { last = tex.get(id); lastId = id; }
            if (last == null) continue;
            work.tris++;
            int n = clipNear(a, b, c, v, nv);
            if (n < 3) continue;
            // в экранные координаты
            for (int k = 0; k < n; k++) {
                double[] p = clip[k];
                double iz = 1 / p[2];
                p[0] = centerX + p[0] * focalX * iz;
                p[1] = centerY - p[1] * focalY * iz;
                p[2] = iz;
                p[3] *= iz;
                p[4] *= iz;
            }
            if (m.cull()) {
                double area = 0;
                for (int k = 0; k < n; k++) {
                    double[] p = clip[k], q = clip[(k + 1) % n];
                    area += p[0] * q[1] - q[0] * p[1];
                }
                // лицевая сторона — вершины против часовой стрелки, если смотреть на неё (как в OpenGL);
                // на экране ось y вниз, поэтому у лицевой площадь по формуле шнурков отрицательная
                if (area >= 0) continue;
            }
            for (int k = 1; k + 1 < n; k++) work.pixels += enqueue(clip[0], clip[k], clip[k + 1], last, flash);
        }
    }

    /** Треугольник в очередь; возвращает примерную площадь на экране (для оплаты). */
    private long enqueue(double[] a, double[] b, double[] c, Canvas.Texture t, int flash) {
        if (qn == qt.length) {
            qt = Arrays.copyOf(qt, qn * 2);
            qf = Arrays.copyOf(qf, qn * 2);
            qv = Arrays.copyOf(qv, qn * 2 * QS);
        }
        int o = qn * QS;
        System.arraycopy(a, 0, qv, o, 8);
        System.arraycopy(b, 0, qv, o + 8, 8);
        System.arraycopy(c, 0, qv, o + 16, 8);
        qt[qn] = t;
        qf[qn] = flash;
        qn++;
        double x0 = Math.max(0, Math.min(a[0], Math.min(b[0], c[0]))), x1 = Math.min(w, Math.max(a[0], Math.max(b[0], c[0])));
        double y0 = Math.max(0, Math.min(a[1], Math.min(b[1], c[1]))), y1 = Math.min(h, Math.max(a[1], Math.max(b[1], c[1])));
        return x1 > x0 && y1 > y0 ? (long) ((x1 - x0) * (y1 - y0) / 2) + 1 : 0;
    }

    /** Есть ли незакрашенные треугольники. */
    boolean pending() {
        return qn > 0;
    }

    /** Закрасить очередь: кадр делится на полосы, полосы — на все ядра. */
    void flush() {
        if (qn == 0) return;
        int bands = (POOL == null || h < 48 || qn < 32) ? 1 : Math.min(THREADS * 2, h / 16);
        if (bands <= 1) {
            band(0, h - 1);
        } else {
            java.util.List<java.util.concurrent.Callable<Object>> jobs = new java.util.ArrayList<>(bands);
            for (int i = 0; i < bands; i++) {
                int y0 = h * i / bands, y1 = h * (i + 1) / bands - 1;
                jobs.add(() -> { band(y0, y1); return null; });
            }
            try {
                for (var f : POOL.invokeAll(jobs)) f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (java.util.concurrent.ExecutionException e) {
                throw new RuntimeException(e.getCause());
            }
        }
        qn = 0;
        Arrays.fill(qt, null);
    }

    private void band(int y0, int y1) {
        double[] a = new double[8], b = new double[8], c = new double[8];
        for (int i = 0; i < qn; i++) {
            int o = i * QS;
            System.arraycopy(qv, o, a, 0, 8);
            System.arraycopy(qv, o + 8, b, 0, 8);
            System.arraycopy(qv, o + 16, c, 0, 8);
            int f = qf[i];
            tri(a, b, c, qt[i], (f >> 16) & 255, (f >> 8) & 255, f & 255, y0, y1);
        }
    }

    private int[] nearLights(Mesh m, double px, double py, double pz, double s, double cyw, double syw, double cp, double sp,
                             boolean onCamera) {
        if (nLights == 0 || onCamera) return new int[0];
        double mx = m.cx() * s, my = m.cy() * s, mz = m.cz() * s;
        double ax = mx * cp - my * sp, ay = mx * sp + my * cp;
        double wx = ax * cyw - mz * syw + px, wy = ay + py, wz = ax * syw + mz * cyw + pz;
        double r = m.radius() * Math.abs(s);
        int[] out = new int[nLights];
        int n = 0;
        for (int k = 0; k < nLights; k++) {
            int q = k * 7;
            double dx = wx - lights[q], dy = wy - lights[q + 1], dz = wz - lights[q + 2];
            double reach = r + lights[q + 3];
            if (dx * dx + dy * dy + dz * dz < reach * reach) out[n++] = k;
        }
        return Arrays.copyOf(out, n);
    }

    /**
     * Треугольник в координатах камеры → многоугольник за ближней плоскостью (в clip[], до 4 вершин).
     * Поля вершины: x, y, z, u, v, r, g, b.
     */
    private int clipNear(int a, int b, int c, float[] v, int nv) {
        int n = 0;
        for (int k = 0; k < 3; k++) {
            double[] p = poly[k];
            int i = k == 0 ? a : k == 1 ? b : c;
            if (i < 0 || i >= nv) return 0;
            int t = i * 6, o = i * Mesh.STRIDE;
            p[0] = tv[t]; p[1] = tv[t + 1]; p[2] = tv[t + 2];
            p[3] = v[o + 3]; p[4] = v[o + 4];
            p[5] = tv[t + 3]; p[6] = tv[t + 4]; p[7] = tv[t + 5];
        }
        boolean in0 = poly[0][2] >= NEAR, in1 = poly[1][2] >= NEAR, in2 = poly[2][2] >= NEAR;
        if (in0 && in1 && in2) {
            for (int k = 0; k < 3; k++) System.arraycopy(poly[k], 0, clip[k], 0, 8);
            return 3;
        }
        if (!in0 && !in1 && !in2) return 0;
        for (int k = 0; k < 3; k++) {
            double[] p = poly[k], q = poly[(k + 1) % 3];
            boolean pin = p[2] >= NEAR, qin = q[2] >= NEAR;
            if (pin) System.arraycopy(p, 0, clip[n++], 0, 8);
            if (pin != qin) {
                double t = (NEAR - p[2]) / (q[2] - p[2]);
                double[] o = clip[n++];
                for (int j = 0; j < 8; j++) o[j] = p[j] + (q[j] - p[j]) * t;
                o[2] = NEAR;
            }
        }
        return n;
    }

    /**
     * Закрасить треугольник. Вершины в экранных координатах: x, y, 1/z, u/z, v/z, r, g, b.
     * Точка закрашивается, если её центр внутри (без щелей и двойной закраски на общих рёбрах).
     */
    private int tri(double[] p0, double[] p1, double[] p2, Canvas.Texture t, int fr, int fg, int fb, int ylo, int yhi) {
        // сортировка по y
        if (p1[1] < p0[1]) { double[] s = p0; p0 = p1; p1 = s; }
        if (p2[1] < p0[1]) { double[] s = p0; p0 = p2; p2 = s; }
        if (p2[1] < p1[1]) { double[] s = p1; p1 = p2; p2 = s; }
        double x0 = p0[0], y0 = p0[1], x1 = p1[0], y1 = p1[1], x2 = p2[0], y2 = p2[1];
        double den = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
        if (Math.abs(den) < 1e-9 || y2 - y0 < 1e-9) return 0;
        int ys = (int) Math.max(ylo, Math.ceil(y0 - 0.5)), ye = (int) Math.min(yhi, Math.ceil(y2 - 0.5) - 1);
        if (ys > ye) return 0;
        // градиенты: значение в точке = p0 + dx·(x − x0) + dy·(y − y0)
        double e1y = y1 - y0, e2y = y2 - y0, e1x = x1 - x0, e2x = x2 - x0;
        double izX = ((p1[2] - p0[2]) * e2y - (p2[2] - p0[2]) * e1y) / den, izY = ((p2[2] - p0[2]) * e1x - (p1[2] - p0[2]) * e2x) / den;
        double uX = ((p1[3] - p0[3]) * e2y - (p2[3] - p0[3]) * e1y) / den, uY = ((p2[3] - p0[3]) * e1x - (p1[3] - p0[3]) * e2x) / den;
        double vX = ((p1[4] - p0[4]) * e2y - (p2[4] - p0[4]) * e1y) / den, vY = ((p2[4] - p0[4]) * e1x - (p1[4] - p0[4]) * e2x) / den;
        double rX = ((p1[5] - p0[5]) * e2y - (p2[5] - p0[5]) * e1y) / den, rY = ((p2[5] - p0[5]) * e1x - (p1[5] - p0[5]) * e2x) / den;
        double gX = ((p1[6] - p0[6]) * e2y - (p2[6] - p0[6]) * e1y) / den, gY = ((p2[6] - p0[6]) * e1x - (p1[6] - p0[6]) * e2x) / den;
        double bX = ((p1[7] - p0[7]) * e2y - (p2[7] - p0[7]) * e1y) / den, bY = ((p2[7] - p0[7]) * e1x - (p1[7] - p0[7]) * e2x) / den;
        int tw = t.w(), th = t.h();
        int[] tp = t.px();
        boolean pow2 = (tw & (tw - 1)) == 0 && (th & (th - 1)) == 0;
        int mw = tw - 1, mh = th - 1;
        int[] px = cv.px;
        float[] zb = z;
        boolean fogOn = fog;
        double fs = fogStart, fe = fogEnd, fspan = fogEnd - fogStart;
        int fcr = (fogColor >> 16) & 255, fcg = (fogColor >> 8) & 255, fcb = fogColor & 255;
        boolean add = (fr | fg | fb) != 0;
        int drawn = 0;
        for (int y = ys; y <= ye; y++) {
            double py = y + 0.5;
            double xa = x0 + (py - y0) * e2x / e2y;
            double xb = py < y1 ? (e1y > 1e-12 ? x0 + (py - y0) * e1x / e1y : x1)
                    : (y2 - y1 > 1e-12 ? x1 + (py - y1) * (x2 - x1) / (y2 - y1) : x1);
            double xl = Math.min(xa, xb), xr = Math.max(xa, xb);
            int xs = (int) Math.max(0, Math.ceil(xl - 0.5)), xe = (int) Math.min(w - 1, Math.ceil(xr - 0.5) - 1);
            if (xs > xe) continue;
            double ddx = xs + 0.5 - x0, ddy = py - y0;
            double iz = p0[2] + izX * ddx + izY * ddy;
            double uz = p0[3] + uX * ddx + uY * ddy;
            double vz = p0[4] + vX * ddx + vY * ddy;
            double lr = p0[5] + rX * ddx + rY * ddy;
            double lg = p0[6] + gX * ddx + gY * ddy;
            double lb = p0[7] + bX * ddx + bY * ddy;
            int row = y * w;
            for (int x = xs; x <= xe; x++, iz += izX, uz += uX, vz += vX, lr += rX, lg += gX, lb += bX) {
                int i = row + x;
                if (iz <= zb[i] || iz <= 0) continue;
                double zz = 1 / iz;
                int tx = (int) Math.floor(uz * zz * tw), ty = (int) Math.floor(vz * zz * th);
                int c = pow2 ? tp[(ty & mh) * tw + (tx & mw)] : tp[wrap(ty, th) * tw + wrap(tx, tw)];
                if (c == Canvas.TRANSPARENT) continue;
                int r = (int) (((c >> 16) & 255) * lr), g = (int) (((c >> 8) & 255) * lg), b = (int) ((c & 255) * lb);
                if (add) { r += fr; g += fg; b += fb; }
                if (fogOn && zz > fs) {
                    double f = zz >= fe ? 1 : (zz - fs) / fspan;
                    r = (int) (r + (fcr - r) * f); g = (int) (g + (fcg - g) * f); b = (int) (b + (fcb - b) * f);
                }
                px[i] = (r < 0 ? 0 : r > 255 ? 255 : r) << 16 | (g < 0 ? 0 : g > 255 ? 255 : g) << 8 | (b < 0 ? 0 : b > 255 ? 255 : b);
                zb[i] = (float) iz;
                drawn++;
            }
        }
        return drawn;
    }

    private static int wrap(int v, int n) {
        int m = v % n;
        return m < 0 ? m + n : m;
    }

    // ======================= спрайты в мире =======================

    /** Флаги billboard: 1 — светится (цвет прибавляется к картинке), 2 — не закрывает то, что дальше. */
    static final int GLOW = 1, NO_DEPTH_WRITE = 2;

    /**
     * Плоская картинка, всегда повёрнутая к камере (взрывы, искры, снаряды, предметы).
     * (x, y, z) — центр, sw × sh — размер в мире. Возвращает закрашенные точки.
     */
    int billboard(Canvas.Texture t, double x, double y, double zc, double sw, double sh, double bright, int flash, int flags) {
        double[] p = project(x, y, zc);
        if (p == null) return 0;
        double d = p[2];
        double hw = sw / 2 * focalX / d, hh = sh / 2 * focalY / d;
        double left = p[0] - hw, top = p[1] - hh, wpx = hw * 2, hpx = hh * 2;
        if (wpx < 0.5 || hpx < 0.5) return 0;
        int a = (int) Math.max(0, Math.ceil(left - 0.5)), b = (int) Math.min(w - 1, Math.floor(left + wpx - 0.5));
        int ta = (int) Math.max(0, Math.ceil(top - 0.5)), tb = (int) Math.min(h - 1, Math.floor(top + hpx - 0.5));
        if (a > b || ta > tb) return 0;
        float iz = (float) (1 / d);
        boolean glow = (flags & GLOW) != 0, write = (flags & NO_DEPTH_WRITE) == 0 && !glow;
        double f = 0;
        if (fog && d > fogStart) f = d >= fogEnd ? 1 : (d - fogStart) / (fogEnd - fogStart);
        if (f >= 1 && glow) return 0;
        int fcr = (fogColor >> 16) & 255, fcg = (fogColor >> 8) & 255, fcb = fogColor & 255;
        int fr = (flash >> 16) & 255, fg = (flash >> 8) & 255, fb = flash & 255;
        int n = 0;
        for (int sy = ta; sy <= tb; sy++) {
            int ty = Math.max(0, Math.min(t.h() - 1, (int) ((sy + 0.5 - top) / hpx * t.h())));
            int row = sy * w;
            for (int sx = a; sx <= b; sx++) {
                int i = row + sx;
                if (iz <= z[i]) continue;
                int tx = Math.max(0, Math.min(t.w() - 1, (int) ((sx + 0.5 - left) / wpx * t.w())));
                int c = t.px()[ty * t.w() + tx];
                if (c == Canvas.TRANSPARENT) continue;
                int r = (int) (((c >> 16) & 255) * bright) + fr, g = (int) (((c >> 8) & 255) * bright) + fg,
                        bl = (int) ((c & 255) * bright) + fb;
                if (glow) {
                    // светящееся: туман гасит, цвет прибавляется к тому, что уже нарисовано
                    r = (int) (r * (1 - f)); g = (int) (g * (1 - f)); bl = (int) (bl * (1 - f));
                    int o = cv.px[i];
                    r += (o >> 16) & 255; g += (o >> 8) & 255; bl += o & 255;
                } else if (f > 0) {
                    r = (int) (r + (fcr - r) * f); g = (int) (g + (fcg - g) * f); bl = (int) (bl + (fcb - bl) * f);
                }
                cv.px[i] = Math.min(255, Math.max(0, r)) << 16 | Math.min(255, Math.max(0, g)) << 8 | Math.min(255, Math.max(0, bl));
                if (write) z[i] = iz;
                n++;
            }
        }
        return n;
    }

    /** Смешать весь кадр с цветом (вспышка урона, вода, затемнение): alpha от 0 до 1. */
    int tint(int color, double alpha) {
        if (alpha <= 0) return 0;
        double a = Math.min(1, alpha);
        int cr = (color >> 16) & 255, cg = (color >> 8) & 255, cb = color & 255;
        int[] px = cv.px;
        for (int i = 0; i < px.length; i++) {
            int o = px[i];
            int r = (o >> 16) & 255, g = (o >> 8) & 255, b = o & 255;
            px[i] = (int) (r + (cr - r) * a) << 16 | (int) (g + (cg - g) * a) << 8 | (int) (b + (cb - b) * a);
        }
        return px.length;
    }
}
