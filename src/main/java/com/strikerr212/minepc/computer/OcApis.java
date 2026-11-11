package com.strikerr212.minepc.computer;

import org.squiddev.cobalt.*;
import org.squiddev.cobalt.function.RegisteredFunction;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.UUID;

import static org.squiddev.cobalt.ValueFactory.valueOf;
import static org.squiddev.cobalt.ValueFactory.varargsOf;

/**
 * Функции для совместимости с OpenComputers (OpenOS), которые удобнее и быстрее сделать на Java:
 * библиотека unicode, видеокарта (component.gpu), дата и время, адреса компонентов.
 * Остальная часть OpenOS (require, event, filesystem, io, shell...) — на Lua в /rom/openos.
 */
final class OcApis {

    private OcApis() {
    }

    private static String text(LuaValue v) throws LuaError {
        return LuaApis.text(v.checkLuaString());
    }

    private static LuaString str(String s) {
        return LuaApis.str(s);
    }

    /** Индексы Lua (с 1, отрицательные — с конца) → границы подстроки Java. */
    private static String sub(String s, int i, int j) {
        int[] cps = s.codePoints().toArray();
        int n = cps.length;
        if (i < 0) i = Math.max(1, n + i + 1);
        else if (i == 0) i = 1;
        if (j < 0) j = n + j + 1;
        else if (j > n) j = n;
        if (i > j) return "";
        return new String(cps, i - 1, j - i + 1);
    }

    // ======================= unicode =======================

    static LuaTable unicode() {
        LuaTable t = new LuaTable();
        RegisteredFunction.bind(t, new RegisteredFunction[]{
                RegisteredFunction.ofV("char", (s, a) -> {
                    StringBuilder b = new StringBuilder();
                    for (int i = 1; i <= a.count(); i++) b.appendCodePoint(a.arg(i).checkInteger());
                    return str(b.toString());
                }),
                RegisteredFunction.ofV("len", (s, a) -> valueOf((int) text(a.arg(1)).codePoints().count())),
                RegisteredFunction.ofV("wlen", (s, a) -> valueOf((int) text(a.arg(1)).codePoints().count())),
                RegisteredFunction.ofV("sub", (s, a) -> str(sub(text(a.arg(1)), a.arg(2).checkInteger(), a.arg(3).optInteger(-1)))),
                RegisteredFunction.ofV("lower", (s, a) -> str(text(a.arg(1)).toLowerCase(Locale.ROOT))),
                RegisteredFunction.ofV("upper", (s, a) -> str(text(a.arg(1)).toUpperCase(Locale.ROOT))),
                RegisteredFunction.ofV("reverse", (s, a) -> str(new StringBuilder(text(a.arg(1))).reverse().toString())),
                RegisteredFunction.ofV("isWide", (s, a) -> Constants.FALSE),
                RegisteredFunction.ofV("charWidth", (s, a) -> valueOf(1)),
                RegisteredFunction.ofV("wtrunc", (s, a) -> {
                    String v = text(a.arg(1));
                    int count = a.arg(2).checkInteger();
                    return str(sub(v, 1, Math.max(0, count - 1)));
                }),
        });
        return t;
    }

    // ======================= Видеокарта (component.gpu) =======================

    private static String depthName(int bits) {
        return switch (bits) {
            case 1 -> "OneBit";
            case 4 -> "FourBit";
            case 8 -> "EightBit";
            default -> "TwentyFourBit";
        };
    }

    static LuaTable gpu(Computer c, LuaMachine m, String address, String screen) {
        Gpu gpu = c.gpu();
        LuaTable g = new LuaTable();
        g.rawset("address", str(address));
        g.rawset("type", str("gpu"));
        RegisteredFunction.bind(g, new RegisteredFunction[]{
                RegisteredFunction.ofV("bind", (s, a) -> Constants.TRUE),
                RegisteredFunction.ofV("getScreen", (s, a) -> str(screen)),
                RegisteredFunction.ofV("getBackground", (s, a) -> {
                    Terminal t = gpu.target();
                    return varargsOf(t.backIndex() >= 0 ? valueOf(t.backIndex()) : valueOf(t.backRgb()), valueOf(t.backIndex() >= 0));
                }),
                RegisteredFunction.ofV("getForeground", (s, a) -> {
                    Terminal t = gpu.target();
                    return varargsOf(t.textIndex() >= 0 ? valueOf(t.textIndex()) : valueOf(t.textRgb()), valueOf(t.textIndex() >= 0));
                }),
                RegisteredFunction.ofV("setBackground", (s, a) -> {
                    Terminal t = gpu.target();
                    int old = t.backRgb();
                    if (a.arg(2).toBoolean()) t.setBackIndex(a.arg(1).checkInteger());
                    else t.setBackRgb(a.arg(1).checkInteger());
                    return valueOf(old);
                }),
                RegisteredFunction.ofV("setForeground", (s, a) -> {
                    Terminal t = gpu.target();
                    int old = t.textRgb();
                    if (a.arg(2).toBoolean()) t.setTextIndex(a.arg(1).checkInteger());
                    else t.setTextRgb(a.arg(1).checkInteger());
                    return valueOf(old);
                }),
                RegisteredFunction.ofV("getPaletteColor", (s, a) -> valueOf(gpu.target().paletteColor(a.arg(1).checkInteger()))),
                RegisteredFunction.ofV("setPaletteColor", (s, a) -> {
                    Terminal t = gpu.target();
                    int i = a.arg(1).checkInteger();
                    int old = t.paletteColor(i);
                    t.setPaletteColor(i, a.arg(2).checkInteger());
                    return valueOf(old);
                }),
                RegisteredFunction.ofV("maxDepth", (s, a) -> valueOf(c.terminal().maxDepth())),
                RegisteredFunction.ofV("getDepth", (s, a) -> valueOf(gpu.target().depth())),
                RegisteredFunction.ofV("setDepth", (s, a) -> {
                    Terminal t = gpu.target();
                    String old = depthName(t.depth());
                    if (!t.setDepth(a.arg(1).checkInteger())) throw LuaApis.error("unsupported depth");
                    return str(old);
                }),
                RegisteredFunction.ofV("maxResolution", (s, a) -> varargsOf(valueOf(c.terminal().maxWidth()), valueOf(c.terminal().maxHeight()))),
                RegisteredFunction.ofV("getResolution", (s, a) -> {
                    Terminal t = gpu.target();
                    return varargsOf(valueOf(t.width()), valueOf(t.height()));
                }),
                RegisteredFunction.ofV("setResolution", (s, a) -> {
                    Terminal t = c.terminal();
                    int w = a.arg(1).checkInteger(), h = a.arg(2).checkInteger();
                    if (w < 1 || h < 1 || w > t.maxWidth() || h > t.maxHeight()) throw LuaApis.error("unsupported resolution");
                    boolean changed = t.resize(w, h);
                    c.chargeGpu(t.cells() / 16);
                    if (changed) c.queueEvent("screen_resized", w, h);
                    return valueOf(changed);
                }),
                RegisteredFunction.ofV("getViewport", (s, a) -> {
                    Terminal t = gpu.target();
                    return varargsOf(valueOf(t.width()), valueOf(t.height()));
                }),
                RegisteredFunction.ofV("setViewport", (s, a) -> Constants.TRUE),
                RegisteredFunction.ofV("get", (s, a) -> {
                    int[] cell = gpu.target().get(a.arg(1).checkInteger() - 1, a.arg(2).checkInteger() - 1);
                    if (cell == null) throw LuaApis.error("index out of bounds");
                    return varargsOf(new LuaValue[]{str(String.valueOf((char) cell[0])), valueOf(cell[1]), valueOf(cell[2]),
                            Constants.NIL, Constants.NIL});
                }),
                RegisteredFunction.ofV("set", (s, a) -> {
                    String text = Text.bmp(LuaApis.text(a.arg(3)));
                    if (gpu.targetIsScreen()) c.chargeGpu(text.length());
                    gpu.target().set(a.arg(1).checkInteger() - 1, a.arg(2).checkInteger() - 1, text, a.arg(4).toBoolean());
                    return Constants.TRUE;
                }),
                RegisteredFunction.ofV("copy", (s, a) -> {
                    int w = a.arg(3).checkInteger(), h = a.arg(4).checkInteger();
                    if (gpu.targetIsScreen()) c.chargeGpu(area(w, h) / 8);
                    gpu.target().copy(a.arg(1).checkInteger() - 1, a.arg(2).checkInteger() - 1, w, h,
                            a.arg(5).checkInteger(), a.arg(6).checkInteger());
                    return Constants.TRUE;
                }),
                RegisteredFunction.ofV("fill", (s, a) -> {
                    String ch = Text.bmp(text(a.arg(5)));
                    int w = a.arg(3).checkInteger(), h = a.arg(4).checkInteger();
                    if (gpu.targetIsScreen()) c.chargeGpu(area(w, h) / 8);
                    gpu.target().fill(a.arg(1).checkInteger() - 1, a.arg(2).checkInteger() - 1, w, h,
                            ch.isEmpty() ? ' ' : ch.charAt(0));
                    return Constants.TRUE;
                }),
                // ----- Кадровый буфер и текстуры (ускорители MineComputers) -----
                RegisteredFunction.ofV("canvas", (s, a) -> {
                    Terminal scr = c.terminal();
                    int w = a.arg(1).optInteger(scr.width()), h = a.arg(2).optInteger(scr.height() * 2);
                    Canvas cv = gpu.newCanvas(w, h);
                    if (cv == null) return varargsOf(Constants.NIL, str("не хватает видеопамяти или слишком большой"));
                    m.spend((long) w * h / 64 + 16);
                    return Canvas.lua(c, m, gpu, cv);
                }),
                RegisteredFunction.ofV("newTexture", (s, a) -> {
                    int w = a.arg(1).checkInteger(), h = a.arg(2).checkInteger();
                    if (w < 1 || h < 1 || w > 1024 || h > 1024) throw LuaApis.error("текстура от 1×1 до 1024×1024");
                    int[] px = new int[w * h];
                    LuaValue d = a.arg(3);
                    if (d.isString()) {
                        byte[] b = LuaApis.bytes(d.checkLuaString());
                        for (int i = 0; i < px.length; i++) {
                            int o = i * 3;
                            int rgb = o + 2 < b.length ? (b[o] & 255) << 16 | (b[o + 1] & 255) << 8 | (b[o + 2] & 255) : 0;
                            px[i] = rgb == 0xFF00FF ? Canvas.TRANSPARENT : rgb;     // пурпурный — прозрачный
                        }
                    } else {
                        LuaTable tbl = d.checkTable();
                        for (int i = 0; i < px.length; i++) {
                            LuaValue v = tbl.rawget(i + 1);
                            px[i] = v.isNumber() && v.toInteger() >= 0 ? v.toInteger() & 0xFFFFFF : Canvas.TRANSPARENT;
                        }
                    }
                    m.spend(px.length / 4 + 16);
                    int id = gpu.newTexture(w, h, px);
                    return id < 0 ? varargsOf(Constants.NIL, str("не хватает видеопамяти")) : valueOf(id);
                }),
                RegisteredFunction.ofV("freeTexture", (s, a) -> valueOf(gpu.freeTexture(a.arg(1).checkInteger()))),
                // ----- Видеопамять (как в OpenComputers 1.8) -----
                RegisteredFunction.ofV("getActiveBuffer", (s, a) -> valueOf(gpu.active())),
                RegisteredFunction.ofV("setActiveBuffer", (s, a) -> {
                    int old = gpu.setActive(a.arg(1).checkInteger());
                    return old < 0 ? varargsOf(Constants.NIL, str("invalid buffer index")) : valueOf(old);
                }),
                RegisteredFunction.ofV("buffers", (s, a) -> {
                    LuaTable out = new LuaTable();
                    int i = 1;
                    for (int idx : gpu.indexes()) out.rawset(i++, valueOf(idx));
                    return out;
                }),
                RegisteredFunction.ofV("allocateBuffer", (s, a) -> {
                    Terminal t = c.terminal();
                    int w = a.arg(1).optInteger(t.maxWidth()), h = a.arg(2).optInteger(t.maxHeight());
                    int idx = gpu.allocate(w, h);
                    return idx < 0 ? varargsOf(Constants.NIL, str("not enough video memory")) : valueOf(idx);
                }),
                RegisteredFunction.ofV("freeBuffer", (s, a) -> valueOf(gpu.free(a.arg(1).optInteger(gpu.active())))),
                RegisteredFunction.ofV("freeAllBuffers", (s, a) -> { gpu.freeAll(); return Constants.NONE; }),
                RegisteredFunction.ofV("totalMemory", (s, a) -> valueOf((double) gpu.totalCells())),
                RegisteredFunction.ofV("freeMemory", (s, a) -> valueOf((double) Math.max(0, gpu.totalCells() - gpu.usedCells()))),
                RegisteredFunction.ofV("getBufferSize", (s, a) -> {
                    Terminal t = gpu.get(a.arg(1).optInteger(gpu.active()));
                    if (t == null) return varargsOf(Constants.NIL, str("invalid buffer index"));
                    return varargsOf(valueOf(t.width()), valueOf(t.height()));
                }),
                // bitblt(куда, столбец, строка, ширина, высота, откуда, столбец источника, строка источника)
                RegisteredFunction.ofV("bitblt", (s, a) -> {
                    int dstIdx = a.arg(1).optInteger(0);
                    int srcIdx = a.arg(6).optInteger(gpu.active());
                    Terminal dst = gpu.get(dstIdx), src = gpu.get(srcIdx);
                    if (dst == null || src == null) throw LuaApis.error("invalid buffer index");
                    int col = a.arg(2).optInteger(1), row = a.arg(3).optInteger(1);
                    int w = a.arg(4).optInteger(src.width()), h = a.arg(5).optInteger(src.height());
                    int fromCol = a.arg(7).optInteger(1), fromRow = a.arg(8).optInteger(1);
                    Object[] region = src.region(fromCol - 1, fromRow - 1, w, h);
                    int changed = dst.paste(region, col - 1, row - 1);
                    if (dstIdx == 0) c.chargeGpu(changed / 8);
                    return Constants.TRUE;
                }),
        });
        return g;
    }

    private static long area(int w, int h) {
        return Math.max(0, (long) Math.min(w, 4096) * Math.min(h, 4096));
    }

    // ======================= Библиотека data (как карта данных в OpenComputers) =======================

    private static byte[] digest(String algorithm, byte[] data) throws LuaError {
        try {
            return java.security.MessageDigest.getInstance(algorithm).digest(data);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw LuaApis.error("Нет алгоритма " + algorithm);
        }
    }

    /**
     * Хеши, base64, сжатие и случайные байты на Java — в сотни раз быстрее, чем то же самое на Lua.
     * Каждая функция списывает команды по объёму данных (как остальные встроенные функции).
     */
    private static Varargs aes(LuaMachine m, java.security.SecureRandom random, Varargs a, boolean encrypt) throws LuaError {
        byte[] d = LuaApis.bytes(a.arg(1).checkLuaString());
        byte[] key = LuaApis.bytes(a.arg(2).checkLuaString());
        if (key.length != 16) key = java.util.Arrays.copyOf(digest("SHA-256", key), 16);
        byte[] iv = a.arg(3).isNil() ? null : LuaApis.bytes(a.arg(3).checkLuaString());
        if (iv != null && iv.length != 16) throw LuaApis.error("iv — ровно 16 байт");
        if (!m.reserve(d.length + 64L)) throw LuaApis.error("Недостаточно памяти");
        m.spend(d.length / 4 + 16);
        boolean ivInside = iv == null;
        try {
            if (encrypt) {
                if (ivInside) {
                    iv = new byte[16];
                    random.nextBytes(iv);
                }
            } else if (ivInside) {
                if (d.length < 32) return varargsOf(Constants.NIL, str("Это не зашифрованные данные"));
                iv = java.util.Arrays.copyOf(d, 16);
                d = java.util.Arrays.copyOfRange(d, 16, d.length);
            }
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(encrypt ? javax.crypto.Cipher.ENCRYPT_MODE : javax.crypto.Cipher.DECRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(key, "AES"), new javax.crypto.spec.IvParameterSpec(iv));
            byte[] out = cipher.doFinal(d);
            if (encrypt && ivInside) {
                byte[] withIv = new byte[16 + out.length];
                System.arraycopy(iv, 0, withIv, 0, 16);
                System.arraycopy(out, 0, withIv, 16, out.length);
                out = withIv;
            }
            return LuaString.valueOf(out);
        } catch (javax.crypto.BadPaddingException | javax.crypto.IllegalBlockSizeException e) {
            return varargsOf(Constants.NIL, str("Неверный ключ или данные повреждены"));
        } catch (java.security.GeneralSecurityException e) {
            throw LuaApis.error("Шифрование недоступно: " + e.getMessage());
        }
    }

    static LuaTable data(LuaMachine m) {
        LuaTable t = new LuaTable();
        java.security.SecureRandom random = new java.security.SecureRandom();
        RegisteredFunction.bind(t, new RegisteredFunction[]{
                RegisteredFunction.ofV("crc32", (s, a) -> {
                    byte[] d = LuaApis.bytes(a.arg(1).checkLuaString());
                    m.spend(d.length / LuaMachine.BYTES_PER_OP);
                    java.util.zip.CRC32 crc = new java.util.zip.CRC32();
                    crc.update(d);
                    long v = crc.getValue();
                    return LuaString.valueOf(new byte[]{(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v});
                }),
                RegisteredFunction.ofV("md5", (s, a) -> {
                    byte[] d = LuaApis.bytes(a.arg(1).checkLuaString());
                    m.spend(d.length / 8);
                    return LuaString.valueOf(digest("MD5", d));
                }),
                RegisteredFunction.ofV("sha256", (s, a) -> {
                    byte[] d = LuaApis.bytes(a.arg(1).checkLuaString());
                    m.spend(d.length / 8);
                    return LuaString.valueOf(digest("SHA-256", d));
                }),
                RegisteredFunction.ofV("encode64", (s, a) -> {
                    byte[] d = LuaApis.bytes(a.arg(1).checkLuaString());
                    if (!m.reserve(d.length * 2L)) throw LuaApis.error("Недостаточно памяти");
                    m.spend(d.length / LuaMachine.BYTES_PER_OP);
                    return LuaString.valueOf(java.util.Base64.getEncoder().encode(d));
                }),
                RegisteredFunction.ofV("decode64", (s, a) -> {
                    byte[] d = LuaApis.bytes(a.arg(1).checkLuaString());
                    m.spend(d.length / LuaMachine.BYTES_PER_OP);
                    try {
                        return LuaString.valueOf(java.util.Base64.getMimeDecoder().decode(d));
                    } catch (IllegalArgumentException e) {
                        return varargsOf(Constants.NIL, str("invalid base64"));
                    }
                }),
                RegisteredFunction.ofV("deflate", (s, a) -> {
                    byte[] d = LuaApis.bytes(a.arg(1).checkLuaString());
                    m.spend(d.length / 4);
                    java.util.zip.Deflater def = new java.util.zip.Deflater();
                    def.setInput(d);
                    def.finish();
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(d.length / 2 + 16);
                    byte[] buf = new byte[8192];
                    while (!def.finished()) out.write(buf, 0, def.deflate(buf));
                    def.end();
                    return LuaString.valueOf(out.toByteArray());
                }),
                RegisteredFunction.ofV("inflate", (s, a) -> {
                    byte[] d = LuaApis.bytes(a.arg(1).checkLuaString());
                    java.util.zip.Inflater inf = new java.util.zip.Inflater();
                    inf.setInput(d);
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(d.length * 3 + 16);
                    byte[] buf = new byte[8192];
                    try {
                        while (!inf.finished()) {
                            int k = inf.inflate(buf);
                            if (k == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                            out.write(buf, 0, k);
                            if (out.size() % (1 << 20) < k && !m.reserve(out.size() * 2L)) throw LuaApis.error("Недостаточно памяти");
                        }
                    } catch (java.util.zip.DataFormatException e) {
                        return varargsOf(Constants.NIL, str("invalid data"));
                    } finally {
                        inf.end();
                    }
                    m.spend(out.size() / LuaMachine.BYTES_PER_OP);
                    return LuaString.valueOf(out.toByteArray());
                }),
                // Шифрование AES-128-CBC (как data.encrypt в OpenComputers). Ключ — 16 байт; любая другая строка
                // превращается в ключ через SHA-256. Без iv — случайный, кладётся в начало результата (decrypt его найдёт)
                RegisteredFunction.ofV("encrypt", (s, a) -> aes(m, random, a, true)),
                RegisteredFunction.ofV("decrypt", (s, a) -> aes(m, random, a, false)),
                RegisteredFunction.ofV("random", (s, a) -> {
                    int n = Math.max(0, Math.min(1 << 20, a.arg(1).checkInteger()));
                    m.spend(n / LuaMachine.BYTES_PER_OP);
                    byte[] b = new byte[n];
                    random.nextBytes(b);
                    return LuaString.valueOf(b);
                }),
                RegisteredFunction.ofV("toHex", (s, a) -> {
                    byte[] d = LuaApis.bytes(a.arg(1).checkLuaString());
                    if (!m.reserve(d.length * 2L)) throw LuaApis.error("Недостаточно памяти");
                    m.spend(d.length / LuaMachine.BYTES_PER_OP);
                    return str(java.util.HexFormat.of().formatHex(d));
                }),
                RegisteredFunction.ofV("getLimit", (s, a) -> valueOf(1 << 30)),
        });
        return t;
    }

    // ======================= Время и дата =======================

    private static ZonedDateTime when(Varargs a, int i, boolean utc) throws LuaError {
        long t = a.arg(i).isNil() ? System.currentTimeMillis() / 1000 : (long) a.arg(i).checkNumber().toDouble();
        return Instant.ofEpochSecond(t).atZone(utc ? ZoneId.of("UTC") : ZoneId.systemDefault());
    }

    /** os.date как в обычном Lua: формат "%d.%m.%Y %H:%M" и т. п., "*t" — таблица. */
    static LuaValue date(Varargs a) throws LuaError {
        String fmt = a.arg(1).isNil() ? "%c" : text(a.arg(1));
        boolean utc = fmt.startsWith("!");
        if (utc) fmt = fmt.substring(1);
        ZonedDateTime d = when(a, 2, utc);
        if (fmt.startsWith("*t")) {
            LuaTable t = new LuaTable();
            t.rawset("year", valueOf(d.getYear()));
            t.rawset("month", valueOf(d.getMonthValue()));
            t.rawset("day", valueOf(d.getDayOfMonth()));
            t.rawset("hour", valueOf(d.getHour()));
            t.rawset("min", valueOf(d.getMinute()));
            t.rawset("sec", valueOf(d.getSecond()));
            t.rawset("wday", valueOf(d.getDayOfWeek().getValue() % 7 + 1));
            t.rawset("yday", valueOf(d.getDayOfYear()));
            t.rawset("isdst", Constants.FALSE);
            return t;
        }
        Locale ru = Locale.forLanguageTag("ru");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < fmt.length(); i++) {
            char ch = fmt.charAt(i);
            if (ch != '%' || i + 1 >= fmt.length()) {
                b.append(ch);
                continue;
            }
            char f = fmt.charAt(++i);
            switch (f) {
                case 'Y' -> b.append(d.getYear());
                case 'y' -> b.append(String.format("%02d", d.getYear() % 100));
                case 'm' -> b.append(String.format("%02d", d.getMonthValue()));
                case 'd' -> b.append(String.format("%02d", d.getDayOfMonth()));
                case 'H' -> b.append(String.format("%02d", d.getHour()));
                case 'I' -> b.append(String.format("%02d", (d.getHour() + 11) % 12 + 1));
                case 'M' -> b.append(String.format("%02d", d.getMinute()));
                case 'S' -> b.append(String.format("%02d", d.getSecond()));
                case 'p' -> b.append(d.getHour() < 12 ? "AM" : "PM");
                case 'A' -> b.append(d.getDayOfWeek().getDisplayName(TextStyle.FULL, ru));
                case 'a' -> b.append(d.getDayOfWeek().getDisplayName(TextStyle.SHORT, ru));
                case 'B' -> b.append(d.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, ru));
                case 'b' -> b.append(d.getMonth().getDisplayName(TextStyle.SHORT_STANDALONE, ru));
                case 'j' -> b.append(String.format("%03d", d.getDayOfYear()));
                case 'c' -> b.append(String.format("%02d.%02d.%d %02d:%02d:%02d", d.getDayOfMonth(), d.getMonthValue(),
                        d.getYear(), d.getHour(), d.getMinute(), d.getSecond()));
                case 'x' -> b.append(String.format("%02d.%02d.%02d", d.getDayOfMonth(), d.getMonthValue(), d.getYear() % 100));
                case 'X' -> b.append(String.format("%02d:%02d:%02d", d.getHour(), d.getMinute(), d.getSecond()));
                case '%' -> b.append('%');
                default -> b.append('%').append(f);
            }
        }
        return str(b.toString());
    }

    /** os.time(): секунды с 1970 года; os.time{year=..., month=..., day=...} — для указанной даты. */
    static LuaValue time(Varargs a) throws LuaError {
        if (a.arg(1).isNil()) return valueOf((double) (System.currentTimeMillis() / 1000));
        LuaTable t = a.arg(1).checkTable();
        ZonedDateTime d = ZonedDateTime.of(t.rawget("year").checkInteger(), t.rawget("month").checkInteger(),
                t.rawget("day").checkInteger(), t.rawget("hour").optInteger(12), t.rawget("min").optInteger(0),
                t.rawget("sec").optInteger(0), 0, ZoneId.systemDefault());
        return valueOf((double) d.toEpochSecond());
    }

    /** Постоянный адрес компонента (как UUID в OpenComputers): из адреса компьютера и имени. */
    static String address(Computer c, String kind) {
        if (kind.isEmpty()) return c.address();
        return UUID.nameUUIDFromBytes((c.address() + ":" + kind).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
}
