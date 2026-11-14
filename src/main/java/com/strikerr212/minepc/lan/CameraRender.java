package com.strikerr212.minepc.lan;

/**
 * Кадр с камеры: лучи из объектива по снимку мира вокруг камеры ({@link Snapshot}). Блоки — настоящими текстурами
 * (таблица {@link Tex}, её собирает игра у игрока; без неё — цветом с карты), с тенью по стороне, солнцем и тенями
 * от блоков, светом факелов; дырявые текстуры (листва, стекло, факелы) пропускают луч; трава и цветы — крестом;
 * плиты, ковры, снег — своей высоты; вода полупрозрачная; существа — головой, телом и ногами цветов своего яйца
 * призыва; вдали — дымка цвета неба, на небе — солнце, луна и звёзды. Работает в любом потоке: снимок только читается
 * (кроме кэша теней — его гонки безвредны).
 */
public final class CameraRender {

    private CameraRender() {
    }

    /** Флаг клетки: вода (полупрозрачная). */
    public static final int WATER = 0x01000000;
    /** Клетка: есть блок (иначе пусто). */
    public static final int SOLID = 0x02000000;
    /** Клетка: растение крестом (трава, цветы) — луч проходит мимо, если не попал в листик. */
    public static final int PLANT = 0x04000000;

    /** Грани текстуры блока. */
    public static final int TOP = 0, SIDE = 1, BOTTOM = 2;

    /**
     * Текстура блока для камеры: верх, бок и низ по 16×16 (ARGB, альфа 0 — дырка) с уменьшенными копиями
     * 8×8 … 1×1 — вдали берётся та, где пиксель текстуры не мельче пикселя кадра.
     */
    public static final class Tex {
        /** faces[грань][уровень] — 16×16, 8×8, 4×4, 2×2, 1×1. */
        public final int[][][] faces = new int[3][][];
        public final boolean cross, cutout;

        /** base — по грани 256 точек ARGB (null — как бок). */
        public Tex(int[][] base, boolean cross) {
            int[] fallback = base[SIDE] != null ? base[SIDE] : base[TOP] != null ? base[TOP] : base[BOTTOM];
            boolean holes = false;
            for (int f = 0; f < 3; f++) {
                int[] b = base[f] != null ? base[f] : fallback;
                int[][] mips = new int[5][];
                mips[0] = b;
                for (int l = 1; l < 5; l++) mips[l] = half(mips[l - 1], 16 >> (l - 1));
                faces[f] = mips;
                for (int c : b) if ((c >>> 24) < 128) holes = true;
            }
            this.cross = cross;
            this.cutout = holes;
        }

        /** Средний цвет бока (вдали и для карты). */
        public int average() {
            return faces[SIDE][4][0] & 0xFFFFFF;
        }

        private static int[] half(int[] src, int size) {
            int n = size / 2;
            int[] out = new int[n * n];
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    int r = 0, g = 0, b = 0, cnt = 0;
                    for (int k = 0; k < 4; k++) {
                        int c = src[(y * 2 + (k >> 1)) * size + x * 2 + (k & 1)];
                        if ((c >>> 24) < 128) continue;
                        r += (c >> 16) & 255;
                        g += (c >> 8) & 255;
                        b += c & 255;
                        cnt++;
                    }
                    out[y * n + x] = cnt == 0 ? 0 : (cnt >= 2 ? 0xFF000000 : 0) | (r / cnt) << 16 | (g / cnt) << 8 | (b / cnt);
                }
            }
            return out;
        }
    }

    /**
     * Снимок мира: клетки (цвет 0xRRGGBB | флаги), блок (номер в реестре + 1, 0 — нет), форма (0 — целый блок,
     * иначе коробка по 1/16) и свет (блок << 4 | небо) в кубе вокруг камеры; существа, время суток и солнце.
     * Сервер обновляет его по слоям, отрисовка читает.
     */
    public static final class Snapshot {
        public final int ox, oy, oz, sx, sy, sz;
        public final int[] cells;
        public final char[] blocks;
        public final int[] shape;
        public final byte[] light;
        /** Кэш: освещена ли клетка солнцем (0 — не считали, 1 — да, 2 — в тени). */
        public final byte[] sunMemo;
        /** Текстуры блоков по номеру в реестре (может быть пустой — тогда цвета с карты). */
        public volatile Tex[] tex = new Tex[0];
        /** Существа: [коробка(6), число частей n] и n частей по 9: коробка(6), цвет, второй цвет, узор. */
        public volatile float[] entities = new float[0];
        public volatile float daylight = 1f;
        public volatile int sky = 0x87B4F0;
        /** Куда светит солнце (единичный вектор к солнцу). */
        public volatile float sunX = 0, sunY = 1, sunZ = 0;

        public Snapshot(int ox, int oy, int oz, int sx, int sy, int sz) {
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            int n = sx * sy * sz;
            cells = new int[n];
            blocks = new char[n];
            shape = new int[n];
            light = new byte[n];
            sunMemo = new byte[n];
        }

        public int index(int x, int y, int z) {
            return (y * sz + z) * sx + x;
        }

        /** Солнце сдвинулось или мир поменялся — тени пересчитать. */
        public void resetShadows() {
            java.util.Arrays.fill(sunMemo, (byte) 0);
        }
    }

    /** Размер записи существа и его части. */
    public static final int ENT_HEAD = 7, ENT_PART = 9;
    /** Узор части: ровный цвет или пятна вторым цветом. */
    public static final int PATTERN_PLAIN = 0, PATTERN_SPOTS = 1;

    /** Форма коробки внутри клетки: x0..x1, y0..y1, z0..z1 в шестнадцатых (включительно). 0 — целый блок. */
    public static int packShape(int x0, int y0, int z0, int x1, int y1, int z1) {
        return 1 << 24 | x0 | y0 << 4 | z0 << 8 | x1 << 12 | y1 << 16 | z1 << 20;
    }

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

    /** Всё, что нужно лучу, кроме направления: чтобы не таскать два десятка параметров. */
    private static final class Ctx {
        Snapshot s;
        Tex[] tex;
        float[] ents;
        double ex, ey, ez, px, py, pz;
        float maxDist, day, pix;
        int skyCol;
        boolean night;
        float sunX, sunY, sunZ;
        boolean sunUp;
        final int[] axis = new int[1];
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
        Ctx c = new Ctx();
        c.s = s;
        c.tex = s.tex;
        c.ents = s.entities;
        c.ex = ex;
        c.ey = ey;
        c.ez = ez;
        c.px = ex - s.ox;
        c.py = ey - s.oy;
        c.pz = ez - s.oz;
        c.maxDist = maxDist;
        c.day = s.daylight;
        c.skyCol = s.sky;
        c.night = night;
        c.pix = (float) (2 * tanY / h);
        c.sunX = s.sunX;
        c.sunY = s.sunY;
        c.sunZ = s.sunZ;
        c.sunUp = c.sunY > 0.06f && c.day > 0.3f;
        for (int j = 0; j < h; j++) {
            double v = (1 - 2.0 * (j + 0.5) / h) * tanY;
            for (int i = 0; i < w; i++) {
                double u = (2.0 * (i + 0.5) / w - 1) * tanX;
                double dx = fx + rx * u + ux * v, dy = fy + ry * u + uy * v, dz = fz + rz * u + uz * v;
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                out[j * w + i] = ray(c, dx / len, dy / len, dz / len);
            }
        }
    }

    private static int ray(Ctx c, double dx, double dy, double dz) {
        Snapshot s = c.s;
        // ближайшее существо: сначала общая коробка, потом части
        double entT = Double.MAX_VALUE;
        int entCol = 0;
        float entK = 1;
        float[] ents = c.ents;
        for (int k = 0; k + ENT_HEAD <= ents.length; ) {
            int n = (int) ents[k + 6];
            double tb = slab(c.ex, c.ey, c.ez, dx, dy, dz, ents[k], ents[k + 1], ents[k + 2], ents[k + 3], ents[k + 4], ents[k + 5], null);
            if (tb >= 0 && tb < entT && tb < c.maxDist) {
                for (int p = 0; p < n; p++) {
                    int b = k + ENT_HEAD + p * ENT_PART;
                    double t = slab(c.ex, c.ey, c.ez, dx, dy, dz, ents[b], ents[b + 1], ents[b + 2], ents[b + 3], ents[b + 4], ents[b + 5], c.axis);
                    if (t >= 0 && t < entT) {
                        entT = t;
                        int col = (int) ents[b + 6];
                        if ((int) ents[b + 8] == PATTERN_SPOTS) {
                            double hx = c.ex + dx * t, hy = c.ey + dy * t, hz = c.ez + dz * t;
                            if (noise((int) Math.floor(hx * 6), (int) Math.floor(hy * 6), (int) Math.floor(hz * 6)) > 0.62f) col = (int) ents[b + 7];
                        }
                        entCol = col;
                        int ax = c.axis[0];
                        int nx = ax == 0 ? (dx > 0 ? -1 : 1) : 0, ny = ax == 1 ? (dy > 0 ? -1 : 1) : 0, nz = ax == 2 ? (dz > 0 ? -1 : 1) : 0;
                        entK = faceLight(nx, ny, nz) * sunLambert(c, nx, ny, nz);
                    }
                }
            }
            k += ENT_HEAD + n * ENT_PART;
        }
        // шаги по клеткам (DDA)
        double px = c.px, py = c.py, pz = c.pz;
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
        int lx = cx, ly = cy, lz = cz;   // последняя пустая клетка (её свет и солнце)
        int waterTint = -1;
        float waterAmount = 0;
        Tex[] tex = c.tex;
        while (t < c.maxDist) {
            if (cx < 0 || cy < 0 || cz < 0 || cx >= s.sx || cy >= s.sy || cz >= s.sz) break;
            if (t > entT) break;
            int idx = s.index(cx, cy, cz);
            int cell = s.cells[idx];
            double tNext = Math.min(tmx, Math.min(tmy, tmz));
            if ((cell & WATER) != 0) {
                if (waterTint < 0) waterTint = cell & 0xFFFFFF;
                waterAmount = Math.min(0.85f, waterAmount + 0.22f);
                lastLight = s.light[idx] & 0xFF;
                lx = cx;
                ly = cy;
                lz = cz;
            } else if ((cell & PLANT) != 0) {
                int col = plant(c, dx, dy, dz, t, tNext, cx, cy, cz, tex, s.blocks[idx]);
                if (col != 0) {
                    int lit = shadeHit(c, col & 0xFFFFFF, 0, 1, 0, s.light[idx] & 0xFF, cx, cy, cz, 0.85f);
                    return finish(c, lit, t, waterTint, waterAmount);
                }
                lastLight = s.light[idx] & 0xFF;
                lx = cx;
                ly = cy;
                lz = cz;
            } else if ((cell & SOLID) != 0) {
                Tex tx = tex.length > 0 && s.blocks[idx] > 0 && s.blocks[idx] - 1 < tex.length ? tex[s.blocks[idx] - 1] : null;
                int sh = s.shape[idx];
                double ht = t;
                int ax = face, nx, ny, nz;
                int lightHere = lastLight, sunCx = lx, sunCy = ly, sunCz = lz;
                boolean hit = true;
                if (sh != 0) {
                    // неполный блок: коробка внутри клетки
                    double bx0 = cx + (sh & 15) / 16.0, by0 = cy + ((sh >> 4) & 15) / 16.0, bz0 = cz + ((sh >> 8) & 15) / 16.0;
                    double bx1 = cx + (((sh >> 12) & 15) + 1) / 16.0, by1 = cy + (((sh >> 16) & 15) + 1) / 16.0, bz1 = cz + (((sh >> 20) & 15) + 1) / 16.0;
                    double tb = slab(px, py, pz, dx, dy, dz, bx0, by0, bz0, bx1, by1, bz1, c.axis);
                    if (tb < 0 || tb > tNext + 1e-6) {
                        hit = false;
                    } else {
                        ht = Math.max(t, tb);
                        ax = c.axis[0];
                        lightHere = s.light[idx] & 0xFF;     // у неполного блока свой свет
                        sunCx = cx;
                        sunCy = cy;
                        sunCz = cz;
                    }
                }
                if (hit) {
                    if (ax < 0) ax = 1;
                    nx = ax == 0 ? -stepX : 0;
                    ny = ax == 1 ? -stepY : 0;
                    nz = ax == 2 ? -stepZ : 0;
                    double hx = px + dx * ht - cx, hy = py + dy * ht - cy, hz = pz + dz * ht - cz;
                    int col;
                    if (tx != null) {
                        int f = ny > 0 ? TOP : ny < 0 ? BOTTOM : SIDE;
                        double u, v;
                        if (ny != 0) {
                            u = hx;
                            v = hz;
                        } else if (nx != 0) {
                            u = nx > 0 ? 1 - hz : hz;
                            v = 1 - hy;
                        } else {
                            u = nz > 0 ? hx : 1 - hx;
                            v = 1 - hy;
                        }
                        int texel = sample(tx, f, u, v, mip(ht * c.pix));
                        if ((texel >>> 24) < 128) {
                            col = 0;     // дырка в текстуре — луч идёт дальше
                        } else {
                            col = texel & 0xFFFFFF;
                        }
                    } else {
                        col = (cell & 0xFFFFFF);
                        float k = 0.93f + 0.14f * noise(cx + s.ox, cy + s.oy, cz + s.oz);
                        double a = ny != 0 ? hx : nx != 0 ? hy : hx, b = ny != 0 ? hz : nx != 0 ? hz : hy;
                        if ((a < 0.06 || a > 0.94 || b < 0.06 || b > 0.94) && sh == 0 && edgeOpen(s, cx, cy, cz, ax, a, b, dx, dy, dz)) k *= 0.8f;
                        col = shade(col, k);
                        if (col == 0) col = 0x010101;     // 0 — «дырка», чёрный блок так не считается
                    }
                    if (col != 0) {
                        int lit = shadeHit(c, col, nx, ny, nz, lightHere, sunCx, sunCy, sunCz, faceLight(nx, ny, nz));
                        if (ht > entT) break;
                        return finish(c, lit, ht, waterTint, waterAmount);
                    }
                }
            } else {
                lastLight = s.light[idx] & 0xFF;
                lx = cx;
                ly = cy;
                lz = cz;
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
        if (entT < Double.MAX_VALUE && entT < c.maxDist) {
            int bl = (lastLight >> 4) & 15, sk = lastLight & 15;
            float b = Math.max(bl / 15f, sk / 15f * c.day * entK);
            float lit = c.night ? Math.max(0.8f, b) : 0.12f + 0.88f * b;
            int col = shade(entCol, lit);
            if (waterTint >= 0) col = mix(col, waterTint, waterAmount);
            return post(col, c.night);
        }
        int sky = skyColor(c, dx, dy, dz);
        if (waterTint >= 0) sky = mix(sky, waterTint, waterAmount);
        return post(sky, c.night);
    }

    /** Дымка вдали, вода перед точкой, ночное видение. */
    private static int finish(Ctx c, int rgb, double t, int waterTint, float waterAmount) {
        if (waterTint >= 0) rgb = mix(rgb, shade(waterTint, Math.max(0.2f, c.day)), waterAmount);
        float fog = (float) Math.max(0, (t - c.maxDist * 0.55) / (c.maxDist * 0.45));
        int fogCol = c.night ? 0x0C1810 : shade(c.skyCol, Math.max(0.12f, c.day));
        return post(mix(rgb, fogCol, fog), c.night);
    }

    /** Освещённость грани по её стороне: верх светлее, низ темнее, бока между. */
    private static float faceLight(int nx, int ny, int nz) {
        return ny > 0 ? 1.0f : ny < 0 ? 0.5f : nx != 0 ? 0.76f : 0.87f;
    }

    /** Насколько грань повёрнута к солнцу (днём): 0,75 … 1,15. */
    private static float sunLambert(Ctx c, int nx, int ny, int nz) {
        if (!c.sunUp) return 1f;
        float lam = Math.max(0, nx * c.sunX + ny * c.sunY + nz * c.sunZ);
        return 0.78f + 0.37f * lam;
    }

    /** Цвет точки с учётом стороны, солнца (и тени от блоков), неба и факелов. */
    private static int shadeHit(Ctx c, int col, int nx, int ny, int nz, int lightByte, int sx, int sy, int sz, float face) {
        int bl = (lightByte >> 4) & 15, sk = lightByte & 15;
        float skyK = sk / 15f * c.day * face;
        if (c.sunUp && sk >= 10) {
            boolean lit = sunVisible(c, sx, sy, sz);
            float lam = Math.max(0, nx * c.sunX + ny * c.sunY + nz * c.sunZ);
            skyK *= lit ? 0.78f + 0.42f * lam : 0.64f;
        }
        float b = Math.max(bl / 15f * face, skyK);
        float lit = c.night ? Math.max(0.75f, b) : 0.07f + 0.93f * b;
        return shade(col, lit);
    }

    /** Видно ли солнце из клетки: луч к солнцу по снимку (с кэшем). */
    private static boolean sunVisible(Ctx c, int x, int y, int z) {
        Snapshot s = c.s;
        if (x < 0 || y < 0 || z < 0 || x >= s.sx || y >= s.sy || z >= s.sz) return true;
        int idx = s.index(x, y, z);
        byte m = s.sunMemo[idx];
        if (m != 0) return m == 1;
        double px = x + 0.5, py = y + 0.5, pz = z + 0.5;
        double dx = c.sunX, dy = c.sunY, dz = c.sunZ;
        int cx = x, cy = y, cz = z;
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
        double tdx = Math.abs(dx) < 1e-6 ? Double.MAX_VALUE : Math.abs(1 / dx);
        double tdy = Math.abs(dy) < 1e-6 ? Double.MAX_VALUE : Math.abs(1 / dy);
        double tdz = Math.abs(dz) < 1e-6 ? Double.MAX_VALUE : Math.abs(1 / dz);
        double tmx = tdx * 0.5, tmy = tdy * 0.5, tmz = tdz * 0.5;
        boolean lit = true;
        for (int step = 0; step < 48; step++) {
            if (tmx < tmy && tmx < tmz) {
                tmx += tdx;
                cx += stepX;
            } else if (tmy < tmz) {
                tmy += tdy;
                cy += stepY;
            } else {
                tmz += tdz;
                cz += stepZ;
            }
            if (cx < 0 || cy < 0 || cz < 0 || cx >= s.sx || cy >= s.sy || cz >= s.sz) break;
            int cell = s.cells[s.index(cx, cy, cz)];
            if ((cell & SOLID) != 0 && (cell & WATER) == 0) {
                lit = false;
                break;
            }
        }
        s.sunMemo[idx] = (byte) (lit ? 1 : 2);
        return lit;
    }

    /** Уровень уменьшенной текстуры: сколько пикселей текстуры приходится на пиксель кадра. */
    private static int mip(double blocksPerPixel) {
        double q = blocksPerPixel * 16;
        return q < 1.5 ? 0 : q < 3 ? 1 : q < 6 ? 2 : q < 12 ? 3 : 4;
    }

    private static int sample(Tex tx, int face, double u, double v, int level) {
        int size = 16 >> level;
        int iu = (int) (u * size), iv = (int) (v * size);
        if (iu < 0) iu = 0;
        if (iv < 0) iv = 0;
        if (iu >= size) iu = size - 1;
        if (iv >= size) iv = size - 1;
        return tx.faces[face][level][iv * size + iu];
    }

    /** Растение крестом: две диагональные плоскости в клетке. 0 — мимо, иначе цвет | 0xFF000000. */
    private static int plant(Ctx c, double dx, double dy, double dz, double t0, double t1, int cx, int cy, int cz, Tex[] tex, char block) {
        if (block == 0 || block - 1 >= tex.length || tex[block - 1] == null) return 0;
        Tex tx = tex[block - 1];
        double ox = c.px + dx * t0 - cx, oy = c.py + dy * t0 - cy, oz = c.pz + dz * t0 - cz;
        double span = t1 - t0;
        double best = Double.MAX_VALUE;
        int col = 0;
        for (int k = 0; k < 2; k++) {
            // k=0: x - z = 0, k=1: x + z = 1
            double den = k == 0 ? dx - dz : dx + dz;
            if (Math.abs(den) < 1e-9) continue;
            double sHit = k == 0 ? (oz - ox) / den : (1 - ox - oz) / den;
            if (sHit < 0 || sHit > span || sHit >= best) continue;
            double hx = ox + dx * sHit, hy = oy + dy * sHit;
            if (hx < 0 || hx > 1 || hy < 0 || hy > 1) continue;
            int texel = sample(tx, SIDE, hx, 1 - hy, mip((t0 + sHit) * c.pix));
            if ((texel >>> 24) < 128) continue;
            best = sHit;
            col = texel | 0xFF000000;
        }
        return col;
    }

    /** Небо: светлее к горизонту, солнце, луна, ночью звёзды. */
    private static int skyColor(Ctx c, double dx, double dy, double dz) {
        if (c.night) return 0x0C1810;
        float day = c.day;
        int sky = shade(c.skyCol, Math.max(0.08f, day));
        sky = mix(sky, shade(0xDCE8F4, Math.max(0.12f, day)), (float) Math.max(0, 1 - Math.abs(dy) * 3) * 0.5f);
        double dot = dx * c.sunX + dy * c.sunY + dz * c.sunZ;
        if (c.sunY > -0.1f) {
            if (dot > 0.9992) return 0xFFF7DC;
            if (dot > 0.993) sky = mix(sky, 0xFFF0C8, (float) ((dot - 0.993) / 0.0062) * 0.55f);
        }
        if (c.sunY < 0.1f && -dot > 0.9995) return 0xD6DAE6;
        if (day < 0.5f && dy > 0) {
            int qx = (int) Math.floor(dx * 220), qy = (int) Math.floor(dy * 220), qz = (int) Math.floor(dz * 220);
            if (noise(qx, qy, qz) > 0.985f) sky = mix(sky, 0xFFFFFF, (0.5f - day) * 2 * 0.9f);
        }
        return sky;
    }

    /** У грани блока рядом с кромкой нет соседа в плоскости грани (или над ним что-то есть) — рисуем кромку. */
    private static boolean edgeOpen(Snapshot s, int cx, int cy, int cz, int face, double a, double b,
                                    double dx, double dy, double dz) {
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

    /** Пересечение луча с коробкой: расстояние или -1; axis[0] — ось грани входа (если axis не null). */
    private static double slab(double ox, double oy, double oz, double dx, double dy, double dz,
                               double x0, double y0, double z0, double x1, double y1, double z1, int[] axis) {
        double tmin = 0, tmax = Double.MAX_VALUE;
        int ax = -1;
        if (Math.abs(dx) < 1e-9) {
            if (ox < x0 || ox > x1) return -1;
        } else {
            double t1 = (x0 - ox) / dx, t2 = (x1 - ox) / dx;
            double lo = Math.min(t1, t2);
            if (lo > tmin) {
                tmin = lo;
                ax = 0;
            }
            tmax = Math.min(tmax, Math.max(t1, t2));
        }
        if (Math.abs(dy) < 1e-9) {
            if (oy < y0 || oy > y1) return -1;
        } else {
            double t1 = (y0 - oy) / dy, t2 = (y1 - oy) / dy;
            double lo = Math.min(t1, t2);
            if (lo > tmin) {
                tmin = lo;
                ax = 1;
            }
            tmax = Math.min(tmax, Math.max(t1, t2));
        }
        if (Math.abs(dz) < 1e-9) {
            if (oz < z0 || oz > z1) return -1;
        } else {
            double t1 = (z0 - oz) / dz, t2 = (z1 - oz) / dz;
            double lo = Math.min(t1, t2);
            if (lo > tmin) {
                tmin = lo;
                ax = 2;
            }
            tmax = Math.min(tmax, Math.max(t1, t2));
        }
        if (axis != null) axis[0] = ax < 0 ? 1 : ax;
        return tmin <= tmax ? tmin : -1;
    }
}
