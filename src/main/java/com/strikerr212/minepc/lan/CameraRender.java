package com.strikerr212.minepc.lan;

/**
 * Кадр с камеры: лучи из объектива по снимку мира вокруг камеры ({@link Snapshot}). Блоки — цветом с карты,
 * с тенью по стороне, светом (днём — небо, ночью — факелы), лёгким шумом и тёмной кромкой у граней; вода
 * полупрозрачная; существа — цветными фигурами; вдали — дымка цвета неба. Работает в любом потоке:
 * снимок только читается.
 */
public final class CameraRender {

    private CameraRender() {
    }

    /** Флаг клетки: вода (полупрозрачная). */
    public static final int WATER = 0x01000000;
    /** Клетка: есть блок (иначе пусто). */
    public static final int SOLID = 0x02000000;

    /**
     * Снимок мира: клетки (цвет 0xRRGGBB | флаги) и свет (блок << 4 | небо) в кубе вокруг камеры,
     * существа и время суток. Сервер обновляет его по слоям, отрисовка читает.
     */
    public static final class Snapshot {
        public final int ox, oy, oz, sx, sy, sz;
        public final int[] cells;
        public final byte[] light;
        public volatile float[] entities = new float[0];   // по 7: x0 y0 z0 x1 y1 z1 вид
        public volatile float daylight = 1f;
        public volatile int sky = 0x87B4F0;

        public Snapshot(int ox, int oy, int oz, int sx, int sy, int sz) {
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            cells = new int[sx * sy * sz];
            light = new byte[sx * sy * sz];
        }

        public int index(int x, int y, int z) {
            return (y * sz + z) * sx + x;
        }
    }

    /** Виды существ (последнее число в записи существа). */
    public static final int ENT_PLAYER = 0, ENT_MONSTER = 1, ENT_ANIMAL = 2, ENT_WATER = 3, ENT_ITEM = 4, ENT_OTHER = 5;

    private static int shade(int rgb, float k) {
        if (k < 0) k = 0;
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * k));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * k));
        int b = Math.min(255, (int) ((rgb & 255) * k));
        return r << 16 | g << 8 | b;
    }

    private static int mix(int a, int b, float t) {
        if (t <= 0) return a;
        if (t >= 1) return b;
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    private static float noise(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return ((h >>> 8) & 255) / 255f;
    }

    private static int entityColor(int kind, float frac) {
        return switch (kind) {
            case ENT_PLAYER -> frac > 0.78f ? 0xC8946A : frac > 0.38f ? 0x3C64C8 : 0x2C3E80;
            case ENT_MONSTER -> frac > 0.75f ? 0x4A8A3A : 0x3A5A8A;
            case ENT_ANIMAL -> frac > 0.6f ? 0xC8A070 : 0x8A6040;
            case ENT_WATER -> 0x3070A0;
            case ENT_ITEM -> 0xE8C840;
            default -> 0x9A9A9A;
        };
    }

    /**
     * Нарисовать кадр w×h. eye — точка объектива (мир), yaw/pitch — градусы (yaw 0 — север, 90 — восток;
     * pitch вверх — плюс), fov — угол по вертикали, night — ночное видение (зелёное, ярче).
     */
    public static void render(Snapshot s, double ex, double ey, double ez, float yaw, float pitch, float fov,
                              boolean night, int[] out, int w, int h, float maxDist) {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double fx = Math.sin(yr) * Math.cos(pr), fy = Math.sin(pr), fz = -Math.cos(yr) * Math.cos(pr);
        // правый и верхний векторы экрана
        double rx = Math.cos(yr), ry = 0, rz = Math.sin(yr);
        double ux = ry * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - ry * fx;
        double tanY = Math.tan(Math.toRadians(fov) / 2), tanX = tanY * w / h;
        float day = s.daylight;
        int skyCol = s.sky;
        float[] ents = s.entities;
        for (int j = 0; j < h; j++) {
            double v = (1 - 2.0 * (j + 0.5) / h) * tanY;
            for (int i = 0; i < w; i++) {
                double u = (2.0 * (i + 0.5) / w - 1) * tanX;
                double dx = fx + rx * u + ux * v, dy = fy + ry * u + uy * v, dz = fz + rz * u + uz * v;
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= len;
                dy /= len;
                dz /= len;
                out[j * w + i] = ray(s, ex, ey, ez, dx, dy, dz, maxDist, day, skyCol, night, ents);
            }
        }
    }

    private static int ray(Snapshot s, double ex, double ey, double ez, double dx, double dy, double dz,
                           float maxDist, float day, int skyCol, boolean night, float[] ents) {
        // ближайшее существо
        double entT = Double.MAX_VALUE;
        int entCol = 0;
        for (int k = 0; k + 6 < ents.length; k += 7) {
            double t = slab(ex, ey, ez, dx, dy, dz, ents[k], ents[k + 1], ents[k + 2], ents[k + 3], ents[k + 4], ents[k + 5]);
            if (t >= 0 && t < entT) {
                entT = t;
                double hy = ey + dy * t;
                float frac = (float) ((hy - ents[k + 1]) / Math.max(0.01, ents[k + 4] - ents[k + 1]));
                entCol = entityColor((int) ents[k + 6], frac);
            }
        }
        // шаги по клеткам (DDA)
        double px = ex - s.ox, py = ey - s.oy, pz = ez - s.oz;
        int cx = (int) Math.floor(px), cy = (int) Math.floor(py), cz = (int) Math.floor(pz);
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
        double tdx = dx == 0 ? Double.MAX_VALUE : Math.abs(1 / dx);
        double tdy = dy == 0 ? Double.MAX_VALUE : Math.abs(1 / dy);
        double tdz = dz == 0 ? Double.MAX_VALUE : Math.abs(1 / dz);
        double tmx = dx == 0 ? Double.MAX_VALUE : ((dx > 0 ? cx + 1 - px : px - cx) * tdx);
        double tmy = dy == 0 ? Double.MAX_VALUE : ((dy > 0 ? cy + 1 - py : py - cy) * tdy);
        double tmz = dz == 0 ? Double.MAX_VALUE : ((dz > 0 ? cz + 1 - pz : pz - cz) * tdz);
        double t = 0;
        int face = -1;              // 0 — x, 1 — y, 2 — z
        int lastLight = 15;
        int waterTint = -1;
        float waterAmount = 0;
        while (t < maxDist) {
            if (cx < 0 || cy < 0 || cz < 0 || cx >= s.sx || cy >= s.sy || cz >= s.sz) break;
            int idx = s.index(cx, cy, cz);
            int c = s.cells[idx];
            if (t > entT) break;
            if ((c & SOLID) != 0) {
                if ((c & WATER) != 0) {
                    if (waterTint < 0) waterTint = c & 0xFFFFFF;
                    waterAmount = Math.min(0.85f, waterAmount + 0.22f);
                    lastLight = s.light[idx] & 0xFF;
                } else {
                    int col = c & 0xFFFFFF;
                    float k = face == 1 ? (dy < 0 ? 1.0f : 0.55f) : face == 0 ? 0.72f : 0.86f;
                    // точка попадания: тёмная кромка у граней
                    double hx = px + dx * t - cx, hy = py + dy * t - cy, hz = pz + dz * t - cz;
                    double a = face == 0 ? hy : hx, b = face == 2 ? hy : hz;
                    if (face == 1) {
                        a = hx;
                        b = hz;
                    }
                    // кромка — только там, где у грани нет соседа (силуэт блока, а не сетка по полу)
                    if (a < 0.06 || a > 0.94 || b < 0.06 || b > 0.94) {
                        if (edgeOpen(s, cx, cy, cz, face, a, b, dx, dy, dz)) k *= 0.8f;
                    }
                    k *= 0.93f + 0.14f * noise(cx + s.ox, cy + s.oy, cz + s.oz);
                    int bl = (lastLight >> 4) & 15, sk = lastLight & 15;
                    float lit = Math.max(bl / 15f, sk / 15f * day);
                    lit = night ? Math.max(0.75f, lit) : 0.08f + 0.92f * lit;
                    int px2 = shade(col, k * lit);
                    if (waterTint >= 0) px2 = mix(px2, shade(waterTint, lit), waterAmount);
                    if (t > entT) break;
                    float fog = (float) Math.max(0, (t - maxDist * 0.55) / (maxDist * 0.45));
                    int fogCol = night ? 0x0C1810 : shade(skyCol, Math.max(0.12f, day));
                    return post(mix(px2, fogCol, fog), night);
                }
            } else {
                lastLight = s.light[idx] & 0xFF;
            }
            if (tmx < tmy && tmx < tmz) {
                t = tmx;
                tmx += tdx;
                cx += stepX;
                face = 0;
            } else if (tmy < tmz) {
                t = tmy;
                tmy += tdy;
                cy += stepY;
                face = 1;
            } else {
                t = tmz;
                tmz += tdz;
                cz += stepZ;
                face = 2;
            }
        }
        if (entT < Double.MAX_VALUE && entT < maxDist) {
            int bl = (lastLight >> 4) & 15, sk = lastLight & 15;
            float lit = Math.max(bl / 15f, sk / 15f * day);
            lit = night ? Math.max(0.8f, lit) : 0.15f + 0.85f * lit;
            int col = shade(entCol, lit);
            if (waterTint >= 0) col = mix(col, waterTint, waterAmount);
            return post(col, night);
        }
        // небо: светлее к горизонту
        int sky = night ? 0x0C1810 : shade(skyCol, Math.max(0.1f, day));
        if (!night) sky = mix(sky, shade(0xDCE8F4, Math.max(0.15f, day)), (float) Math.max(0, 1 - Math.abs(dy) * 3) * 0.5f);
        if (waterTint >= 0) sky = mix(sky, waterTint, waterAmount);
        return post(sky, night);
    }

    /** У грани блока рядом с кромкой нет соседа в плоскости грани (или над ним что-то есть) — рисуем кромку. */
    private static boolean edgeOpen(Snapshot s, int cx, int cy, int cz, int face, double a, double b,
                                    double dx, double dy, double dz) {
        // смещение к наружной стороне грани (откуда пришёл луч)
        int ox = face == 0 ? (dx > 0 ? -1 : 1) : 0, oy = face == 1 ? (dy > 0 ? -1 : 1) : 0, oz = face == 2 ? (dz > 0 ? -1 : 1) : 0;
        int[][] dirs;
        if (face == 0) dirs = new int[][]{{0, a < 0.5 ? -1 : 1, 0}, {0, 0, b < 0.5 ? -1 : 1}};
        else if (face == 1) dirs = new int[][]{{a < 0.5 ? -1 : 1, 0, 0}, {0, 0, b < 0.5 ? -1 : 1}};
        else dirs = new int[][]{{a < 0.5 ? -1 : 1, 0, 0}, {0, b < 0.5 ? -1 : 1, 0}};
        boolean[] near = {a < 0.06 || a > 0.94, b < 0.06 || b > 0.94};
        for (int k = 0; k < 2; k++) {
            if (!near[k]) continue;
            int nx = cx + dirs[k][0], ny = cy + dirs[k][1], nz = cz + dirs[k][2];
            if (!solidAt(s, nx, ny, nz) || solidAt(s, nx + ox, ny + oy, nz + oz)) return true;
        }
        return false;
    }

    private static boolean solidAt(Snapshot s, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= s.sx || y >= s.sy || z >= s.sz) return false;
        int c = s.cells[s.index(x, y, z)];
        return (c & SOLID) != 0 && (c & WATER) == 0;
    }

    /** Ночное видение — оттенки зелёного. */
    private static int post(int rgb, boolean night) {
        if (!night) return rgb;
        int l = (int) (0.3 * ((rgb >> 16) & 255) + 0.59 * ((rgb >> 8) & 255) + 0.11 * (rgb & 255));
        return (l / 4) << 16 | Math.min(255, l + 30) << 8 | (l / 4);
    }

    /** Пересечение луча с коробкой: расстояние или -1. */
    private static double slab(double ox, double oy, double oz, double dx, double dy, double dz,
                               double x0, double y0, double z0, double x1, double y1, double z1) {
        double tmin = 0, tmax = Double.MAX_VALUE;
        if (Math.abs(dx) < 1e-9) {
            if (ox < x0 || ox > x1) return -1;
        } else {
            double t1 = (x0 - ox) / dx, t2 = (x1 - ox) / dx;
            tmin = Math.max(tmin, Math.min(t1, t2));
            tmax = Math.min(tmax, Math.max(t1, t2));
        }
        if (Math.abs(dy) < 1e-9) {
            if (oy < y0 || oy > y1) return -1;
        } else {
            double t1 = (y0 - oy) / dy, t2 = (y1 - oy) / dy;
            tmin = Math.max(tmin, Math.min(t1, t2));
            tmax = Math.min(tmax, Math.max(t1, t2));
        }
        if (Math.abs(dz) < 1e-9) {
            if (oz < z0 || oz > z1) return -1;
        } else {
            double t1 = (z0 - oz) / dz, t2 = (z1 - oz) / dz;
            tmin = Math.max(tmin, Math.min(t1, t2));
            tmax = Math.min(tmax, Math.max(t1, t2));
        }
        return tmin <= tmax ? tmin : -1;
    }
}
