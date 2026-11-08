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

    static LuaTable gpu(Computer c, String address, String screen) {
        Terminal t = c.terminal();
        LuaTable g = new LuaTable();
        g.rawset("address", str(address));
        g.rawset("type", str("gpu"));
        RegisteredFunction.bind(g, new RegisteredFunction[]{
                RegisteredFunction.ofV("bind", (s, a) -> Constants.TRUE),
                RegisteredFunction.ofV("getScreen", (s, a) -> str(screen)),
                RegisteredFunction.ofV("getBackground", (s, a) -> varargsOf(valueOf(t.backRgb()), Constants.FALSE)),
                RegisteredFunction.ofV("getForeground", (s, a) -> varargsOf(valueOf(t.textRgb()), Constants.FALSE)),
                RegisteredFunction.ofV("setBackground", (s, a) -> {
                    int old = t.backRgb();
                    if (a.arg(2).toBoolean()) t.setBackIndex(a.arg(1).checkInteger());
                    else t.setBackRgb(a.arg(1).checkInteger());
                    return valueOf(old);
                }),
                RegisteredFunction.ofV("setForeground", (s, a) -> {
                    int old = t.textRgb();
                    if (a.arg(2).toBoolean()) t.setTextIndex(a.arg(1).checkInteger());
                    else t.setTextRgb(a.arg(1).checkInteger());
                    return valueOf(old);
                }),
                RegisteredFunction.ofV("getPaletteColor", (s, a) -> valueOf(t.paletteColor(a.arg(1).checkInteger()))),
                RegisteredFunction.ofV("setPaletteColor", (s, a) -> {
                    int i = a.arg(1).checkInteger();
                    int old = t.paletteColor(i);
                    t.setPaletteColor(i, a.arg(2).checkInteger());
                    return valueOf(old);
                }),
                RegisteredFunction.ofV("maxDepth", (s, a) -> valueOf(t.maxDepth())),
                RegisteredFunction.ofV("getDepth", (s, a) -> valueOf(t.depth())),
                RegisteredFunction.ofV("setDepth", (s, a) -> {
                    String old = depthName(t.depth());
                    if (!t.setDepth(a.arg(1).checkInteger())) throw LuaApis.error("unsupported depth");
                    return str(old);
                }),
                RegisteredFunction.ofV("maxResolution", (s, a) -> varargsOf(valueOf(t.maxWidth()), valueOf(t.maxHeight()))),
                RegisteredFunction.ofV("getResolution", (s, a) -> varargsOf(valueOf(t.width()), valueOf(t.height()))),
                RegisteredFunction.ofV("setResolution", (s, a) -> {
                    int w = a.arg(1).checkInteger(), h = a.arg(2).checkInteger();
                    if (w < 1 || h < 1 || w > t.maxWidth() || h > t.maxHeight()) throw LuaApis.error("unsupported resolution");
                    return valueOf(t.resize(w, h));
                }),
                RegisteredFunction.ofV("getViewport", (s, a) -> varargsOf(valueOf(t.width()), valueOf(t.height()))),
                RegisteredFunction.ofV("setViewport", (s, a) -> Constants.TRUE),
                RegisteredFunction.ofV("get", (s, a) -> {
                    int[] cell = t.get(a.arg(1).checkInteger() - 1, a.arg(2).checkInteger() - 1);
                    if (cell == null) throw LuaApis.error("index out of bounds");
                    return varargsOf(new LuaValue[]{str(String.valueOf((char) cell[0])), valueOf(cell[1]), valueOf(cell[2]),
                            Constants.NIL, Constants.NIL});
                }),
                RegisteredFunction.ofV("set", (s, a) -> {
                    t.set(a.arg(1).checkInteger() - 1, a.arg(2).checkInteger() - 1, Text.bmp(LuaApis.text(a.arg(3))),
                            a.arg(4).toBoolean());
                    return Constants.TRUE;
                }),
                RegisteredFunction.ofV("copy", (s, a) -> {
                    t.copy(a.arg(1).checkInteger() - 1, a.arg(2).checkInteger() - 1, a.arg(3).checkInteger(),
                            a.arg(4).checkInteger(), a.arg(5).checkInteger(), a.arg(6).checkInteger());
                    return Constants.TRUE;
                }),
                RegisteredFunction.ofV("fill", (s, a) -> {
                    String ch = Text.bmp(text(a.arg(5)));
                    t.fill(a.arg(1).checkInteger() - 1, a.arg(2).checkInteger() - 1, a.arg(3).checkInteger(),
                            a.arg(4).checkInteger(), ch.isEmpty() ? ' ' : ch.charAt(0));
                    return Constants.TRUE;
                }),
        });
        return g;
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
