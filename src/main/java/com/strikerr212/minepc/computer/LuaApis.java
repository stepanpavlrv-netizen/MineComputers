package com.strikerr212.minepc.computer;

import com.strikerr212.minepc.computer.fs.FileSystem;
import com.strikerr212.minepc.computer.fs.FsException;
import com.strikerr212.minepc.computer.fs.Mount;
import org.squiddev.cobalt.*;
import org.squiddev.cobalt.function.RegisteredFunction;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.squiddev.cobalt.ValueFactory.valueOf;
import static org.squiddev.cobalt.ValueFactory.varargsOf;

/**
 * Функции, которые компьютер даёт программам на Lua: term (экран), fs (файлы), os (время, события),
 * computer (сведения о железе). Остальное (print, read, shell...) написано на Lua в BIOS и ПЗУ.
 */
final class LuaApis {

    private LuaApis() {
    }

    // ======================= Преобразования =======================

    static LuaString str(String s) {
        return LuaString.valueOf(Text.encode(s));
    }

    static byte[] bytes(LuaValue v) {
        if (v.isString()) {
            LuaString s = (LuaString) v.toLuaString();
            byte[] b = new byte[s.length()];
            s.copyTo(0, b, 0, b.length);
            return b;
        }
        return Text.encode(v.toString());
    }

    static String text(LuaValue v) {
        return Text.decode(bytes(v));
    }

    static LuaError error(String message) {
        return new LuaError(str(message));
    }

    static String errorText(LuaError e) {
        LuaValue v = e.getValue();
        String msg = v != null && v.isString() ? text(v) : e.getMessage();
        return msg == null ? "неизвестная ошибка" : msg;
    }

    static LuaValue toLua(Object o) {
        if (o == null) return Constants.NIL;
        if (o instanceof String s) return str(s);
        if (o instanceof Integer i) return valueOf(i);
        if (o instanceof Long l) return valueOf((double) l);
        if (o instanceof Number n) return valueOf(n.doubleValue());
        if (o instanceof Boolean b) return valueOf(b);
        if (o instanceof byte[] b) return LuaString.valueOf(b);
        if (o instanceof Key k) return LuaString.valueOf(k.bytes());
        if (o instanceof Map<?, ?> m) {
            LuaTable t = new LuaTable();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                LuaValue k = toLua(e.getKey());
                if (!k.isNil()) {
                    try {
                        t.rawset(k, toLua(e.getValue()));
                    } catch (LuaError ignored) {
                    }
                }
            }
            return t;
        }
        return str(o.toString());
    }

    /** Сколько значений и байт можно передать другому процессу одним сообщением. */
    private static final int COPY_MAX_VALUES = 100_000;

    /**
     * Скопировать значение для другого процесса (у каждого процесса своя машина Lua, общих таблиц нет):
     * числа, строки (байт в байт), логические, таблицы (вложенные). Функции и сопрограммы не передаются.
     */
    static Object copyOut(LuaValue v) throws LuaError {
        return copyOut(v, 0, new int[]{0}, new java.util.IdentityHashMap<>());
    }

    private static Object copyOut(LuaValue v, int depth, int[] count, java.util.IdentityHashMap<LuaValue, Boolean> seen) throws LuaError {
        if (++count[0] > COPY_MAX_VALUES) throw error("Слишком большое сообщение");
        if (v.isNil()) return null;
        if (v instanceof LuaBoolean) return v.toBoolean();
        if (v.isNumber()) return v.toDouble();
        if (v.isString()) return bytes(v);
        if (v instanceof LuaTable t) {
            if (depth > 32 || seen.put(t, Boolean.TRUE) != null) throw error("Таблица в сообщении ссылается сама на себя");
            Map<Object, Object> out = new java.util.LinkedHashMap<>();
            LuaValue k = Constants.NIL;
            while (true) {
                Varargs n = t.next(k);
                k = n.first();
                if (k.isNil()) break;
                Object key = copyOut(k, depth + 1, count, seen);
                if (key instanceof byte[] b) key = new Key(b);
                if (key != null) out.put(key, copyOut(n.arg(2), depth + 1, count, seen));
            }
            seen.remove(t);
            return out;
        }
        return null;
    }

    /** Строка-ключ таблицы в сообщении (у byte[] нет сравнения по содержимому). */
    record Key(byte[] bytes) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && java.util.Arrays.equals(bytes, k.bytes);
        }

        @Override
        public int hashCode() {
            return java.util.Arrays.hashCode(bytes);
        }
    }

    static Varargs toVarargs(Object[] values) {
        LuaValue[] v = new LuaValue[values.length];
        for (int i = 0; i < values.length; i++) v[i] = toLua(values[i]);
        return varargsOf(v);
    }

    private static Object fromLua(LuaValue v) {
        if (v.isNil()) return null;
        if (v instanceof LuaBoolean) return v.toBoolean();
        if (v.isNumber()) return v.toDouble();
        if (v.isString()) return text(v);
        return null;   // таблицы и функции в события не передаём
    }

    // ======================= Установка =======================

    static void install(LuaState state, LuaTable g, Computer c, LuaMachine m) {
        g.rawset("term", term(c));
        g.rawset("fs", fs(c, m));
        g.rawset("os", os(c, m));
        g.rawset("computer", computer(c, m));
        g.rawset("unicode", OcApis.unicode());
        g.rawset("data", OcApis.data(m));
        g.rawset("net", net(c));
        g.rawset("sound", SoundApis.sound(c));
        g.rawset("switch", switchApi(c));
        g.rawset("bios", biosApi(c));
        g.rawset("_HOST", str("MineComputers 0.11 (Lua 5.2 / Cobalt)"));
    }

    // ======================= net (сеть) =======================

    /** Примерный размер значения в сообщении, байт: так считается и скорость, и предел 8 КБ. */
    static int netSize(Object o) {
        if (o == null || o instanceof Boolean) return 1;
        if (o instanceof Number) return 8;
        if (o instanceof byte[] b) return b.length + 2;
        if (o instanceof Key k) return k.bytes().length + 2;
        if (o instanceof Map<?, ?> m) {
            int n = 2;
            for (Map.Entry<?, ?> e : m.entrySet()) n += netSize(e.getKey()) + netSize(e.getValue());
            return n;
        }
        return 8;
    }

    private static int port(Varargs a, int i) throws LuaError {
        int p = a.arg(i).checkInteger();
        if (p < 1 || p > 65535) throw error("Порт — число от 1 до 65535");
        return p;
    }

    private static Varargs netSend(Computer c, String target, int port, Varargs a, int first) throws LuaError {
        Object[] payload = new Object[Math.max(0, a.count() - first + 1)];
        int size = 16;
        for (int i = 0; i < payload.length; i++) {
            payload[i] = copyOut(a.arg(first + i));
            size += netSize(payload[i]);
        }
        if (size > Computer.NET_MAX_PACKET) throw error("Сообщение больше 8 КБ (" + size + " байт) — разбейте на части");
        return valueOf(c.netSend(target, port, payload, size));
    }

    private static LuaTable net(Computer c) {
        LuaTable t = new LuaTable();
        RegisteredFunction.bind(t, new RegisteredFunction[]{
                RegisteredFunction.ofV("open", (s, a) -> valueOf(c.openPort(port(a, 1)))),
                RegisteredFunction.ofV("close", (s, a) -> {
                    if (a.arg(1).isNil()) { c.closeAllPorts(); return Constants.TRUE; }
                    return valueOf(c.closePort(port(a, 1)));
                }),
                RegisteredFunction.ofV("isOpen", (s, a) -> valueOf(c.isPortOpen(port(a, 1)))),
                RegisteredFunction.ofV("send", (s, a) -> netSend(c, text(a.arg(1).checkLuaString()), port(a, 2), a, 3)),
                RegisteredFunction.ofV("broadcast", (s, a) -> netSend(c, null, port(a, 1), a, 2)),
                RegisteredFunction.ofV("ping", (s, a) -> {
                    int id = c.netPing(text(a.arg(1).checkLuaString()));
                    return id < 0 ? Constants.NIL : valueOf(id);
                }),
                RegisteredFunction.ofV("address", (s, a) -> str(c.address())),
                // Контроллер ввода-вывода: net.io(кому, команда, ...) → номер запроса; ответ — событие io_state / io_error
                RegisteredFunction.ofV("io", (s, a) -> {
                    String target = text(a.arg(1).checkLuaString());
                    String cmd = text(a.arg(2).checkLuaString());
                    Object[] rest = new Object[Math.max(0, a.count() - 2)];
                    for (int i = 0; i < rest.length; i++) rest[i] = copyOut(a.arg(3 + i));
                    int id = c.netIo(target, cmd, rest);
                    return id < 0 ? Constants.NIL : valueOf(id);
                }),
                // BMC: net.bmc(сервер, пароль, команда [, аргумент]) → ok, сообщение, данные (ждёт ответа)
                RegisteredFunction.ofV("bmc", (s, a) -> {
                    Object[] r = c.host().bmc(text(a.arg(1).checkLuaString()), a.arg(2).isNil() ? "" : text(a.arg(2).checkLuaString()),
                            text(a.arg(3).checkLuaString()), a.arg(4).isNil() ? "" : text(a.arg(4).checkLuaString()));
                    if (r == null) return varargsOf(new LuaValue[]{Constants.FALSE, str("Нет проводной сети")});
                    return varargsOf(new LuaValue[]{toLua(r[0]), toLua(r[1]), toLua(r[2])});
                }),
                RegisteredFunction.ofV("bmcInfo", (s, a) -> toLua(c.host().bmcLocal())),
                RegisteredFunction.ofV("bmcPassword", (s, a) -> valueOf(c.host().bmcSetPassword(text(a.arg(1).checkLuaString())))),
                RegisteredFunction.ofV("hostname", (s, a) -> {
                    if (!a.arg(1).isNil()) {
                        // Имя видят другие компьютеры сети и окно корпуса — сообщаем корпусу (как label)
                        String name = text(a.arg(1).checkLuaString());
                        if (name.length() > 32) throw error("Имя — не длиннее 32 знаков");
                        c.setLabel(name);
                        c.host().onLabelChanged(name);
                    }
                    return str(c.label());
                }),
                RegisteredFunction.ofV("speed", (s, a) -> valueOf((double) c.host().netBytesPerSecond())),
                RegisteredFunction.ofV("maxPacket", (s, a) -> valueOf(Computer.NET_MAX_PACKET)),
                RegisteredFunction.ofV("stats", (s, a) -> {
                    LuaTable r = new LuaTable();
                    r.rawset("sent", valueOf((double) c.netSent()));
                    r.rawset("received", valueOf((double) c.netReceived()));
                    return r;
                }),
                RegisteredFunction.ofV("peers", (s, a) -> {
                    LuaTable r = new LuaTable();
                    int i = 1;
                    for (Computer.Peer p : c.host().netPeers()) {
                        LuaTable e = new LuaTable();
                        e.rawset("address", str(p.address()));
                        e.rawset("name", str(p.name()));
                        e.rawset("hops", valueOf(p.hops()));
                        r.rawset(i++, e);
                    }
                    return r;
                }),
        });
        t.rawset("wifi", wifi(c));
        return t;
    }

    /** Настройки BIOS, если плата их не хранит (тесты): в памяти, до выключения. */
    private static final java.util.Map<Computer, java.util.Map<String, Object>> LOCAL_BIOS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static java.util.Map<String, Object> localBios(Computer c) {
        return LOCAL_BIOS.computeIfAbsent(c, k -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            java.util.Map<Object, Object> order = new java.util.LinkedHashMap<>();
            String[] ids = {"nvme", "sata", "hdd", "usb", "floppy"};
            for (int i = 0; i < ids.length; i++) order.put(i + 1, ids[i]);
            m.put("bootOrder", order);
            m.put("cores", 0);
            m.put("lan", true);
            m.put("usb", true);
            m.put("wifi", true);
            m.put("biosPassword", false);
            m.put("bootPassword", false);
            m.put("powerLoss", 0);
            m.put("redstone", false);
            m.put("fastBoot", false);
            return m;
        });
    }

    private static String keyText(Object k) {
        if (k instanceof Key key) return new String(key.bytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (k instanceof byte[] b) return new String(b, java.nio.charset.StandardCharsets.UTF_8);
        return String.valueOf(k);
    }

    /** bios: настройки, пароли, сведения о плате и устройства загрузки. */
    private static LuaTable biosApi(Computer c) {
        LuaTable t = new LuaTable();
        RegisteredFunction.bind(t, new RegisteredFunction[]{
                RegisteredFunction.ofV("get", (s, a) -> {
                    java.util.Map<String, Object> m = c.host().biosSettings();
                    return toLua(m != null ? m : localBios(c));
                }),
                RegisteredFunction.ofV("set", (s, a) -> {
                    Object changes = copyOut(a.arg(1).checkTable());
                    if (!(changes instanceof java.util.Map<?, ?> map)) throw error("Нужна таблица настроек");
                    if (c.host().biosSettings() == null) {
                        java.util.Map<String, Object> local = localBios(c);
                        for (var e : map.entrySet()) {
                            String k = keyText(e.getKey());
                            Object v = e.getValue();
                            if (k.endsWith("Password")) v = v instanceof byte[] b && b.length > 0;
                            if (v instanceof byte[] b) v = new String(b, java.nio.charset.StandardCharsets.UTF_8);
                            if (v instanceof Double d && d == Math.rint(d)) v = d.intValue();
                            local.put(k, v);
                        }
                        return Constants.TRUE;
                    }
                    // ключи-строки из Lua приходят байтами: переводим в String
                    java.util.Map<Object, Object> norm = new java.util.LinkedHashMap<>();
                    for (var e : map.entrySet()) {
                        Object k = e.getKey() instanceof Key || e.getKey() instanceof byte[] ? keyText(e.getKey()) : e.getKey();
                        norm.put(k, e.getValue());
                    }
                    String err = c.host().biosSave(norm);
                    return err == null ? Constants.TRUE : varargsOf(Constants.NIL, str(err));
                }),
                RegisteredFunction.ofV("check", (s, a) -> valueOf(c.host().biosCheckPassword(
                        text(a.arg(1).checkLuaString()), text(a.arg(2).checkLuaString())))),
                RegisteredFunction.ofV("info", (s, a) -> toLua(c.host().biosInfo())),
                // RAID-контроллер серверной платы
                RegisteredFunction.ofV("raid", (s, a) -> toLua(c.host().raidInfo())),
                RegisteredFunction.ofV("raidCreate", (s, a) -> {
                    String err = c.host().raidCreate(text(a.arg(1).checkLuaString()));
                    return err == null ? Constants.TRUE : varargsOf(new LuaValue[]{Constants.NIL, str(err)});
                }),
                RegisteredFunction.ofV("raidDelete", (s, a) -> {
                    String err = c.host().raidDelete();
                    return err == null ? Constants.TRUE : varargsOf(new LuaValue[]{Constants.NIL, str(err)});
                }),
                RegisteredFunction.ofV("devices", (s, a) -> listToLua(c.host().biosDevices())),
        });
        return t;
    }

    private static LuaValue listToLua(java.util.List<java.util.Map<String, Object>> list) {
        LuaTable r = new LuaTable();
        int i = 1;
        for (java.util.Map<String, Object> m : list) r.rawset(i++, toLua(m));
        return r;
    }

    /** switch: управляемые коммутаторы в сети — список, порты, включить/выключить, VLAN. */
    private static LuaTable switchApi(Computer c) {
        LuaTable t = new LuaTable();
        RegisteredFunction.bind(t, new RegisteredFunction[]{
                RegisteredFunction.ofV("list", (s, a) -> listToLua(c.host().switchList())),
                RegisteredFunction.ofV("ports", (s, a) -> {
                    var ports = c.host().switchPorts(text(a.arg(1).checkLuaString()));
                    return ports == null ? varargsOf(Constants.NIL, str("Нет такого коммутатора")) : listToLua(ports);
                }),
                RegisteredFunction.ofV("set", (s, a) -> {
                    String id = text(a.arg(1).checkLuaString());
                    int port = a.arg(2).checkInteger();
                    boolean enabled = a.arg(3).isNil() || a.arg(3).toBoolean();
                    LuaValue v = a.arg(4);
                    int vlan;
                    if (v.isNil()) vlan = -2;                                   // не менять
                    else if (v.isNumber()) vlan = v.checkInteger();
                    else vlan = com.strikerr212.minepc.computer.LuaApis.vlanByName(text(v.checkLuaString()));
                    if (vlan == -1 || vlan > 16) throw error("VLAN — число 0..16 или цвет: red, blue, lime…");
                    String err = c.host().switchSet(id, port, enabled, vlan);
                    return err == null ? Constants.TRUE : varargsOf(Constants.NIL, str(err));
                }),
        });
        return t;
    }

    private static final String[] DYES = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

    /** Цвет VLAN по имени красителя: 0 — общий, 1..16, -1 — непонятно. */
    static int vlanByName(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT).trim();
        if (n.isEmpty() || n.equals("none") || n.equals("0")) return 0;
        for (int i = 0; i < DYES.length; i++) if (DYES[i].equals(n)) return i + 1;
        return -1;
    }

    static String vlanName(int vlan) {
        return vlan <= 0 || vlan > 16 ? "" : DYES[vlan - 1];
    }

    /** net.wifi: USB Wi-Fi адаптер. */
    private static LuaTable wifi(Computer c) {
        LuaTable t = new LuaTable();
        RegisteredFunction.bind(t, new RegisteredFunction[]{
                RegisteredFunction.ofV("status", (s, a) -> {
                    Computer.WifiStatus st = c.host().wifiStatus();
                    if (st == null) return Constants.NIL;
                    LuaTable r = new LuaTable();
                    r.rawset("adapter", str(st.adapter()));
                    r.rawset("ssid", str(st.ssid()));
                    r.rawset("connected", valueOf(st.connected()));
                    r.rawset("signal", valueOf(st.signal()));
                    r.rawset("speed", valueOf(st.mbit()));
                    if (!st.error().isEmpty()) r.rawset("error", str(st.error()));
                    return r;
                }),
                RegisteredFunction.ofV("scan", (s, a) -> {
                    if (c.host().wifiStatus() == null) return varargsOf(Constants.NIL, str("Нет Wi-Fi адаптера"));
                    LuaTable r = new LuaTable();
                    int i = 1;
                    for (Computer.WifiNet n : c.host().wifiScan()) {
                        LuaTable e = new LuaTable();
                        e.rawset("ssid", str(n.ssid()));
                        e.rawset("signal", valueOf(n.signal()));
                        e.rawset("secured", valueOf(n.secured()));
                        e.rawset("speed", valueOf(n.mbit()));
                        r.rawset(i++, e);
                    }
                    return r;
                }),
                RegisteredFunction.ofV("connect", (s, a) -> {
                    String ssid = text(a.arg(1).checkLuaString());
                    String password = a.arg(2).isNil() ? "" : text(a.arg(2).checkLuaString());
                    if (ssid.isEmpty() || ssid.length() > 24) throw error("Имя сети — от 1 до 24 знаков");
                    if (!c.host().wifiConnect(ssid, password)) return varargsOf(Constants.NIL, str("Нет Wi-Fi адаптера"));
                    return Constants.TRUE;
                }),
                RegisteredFunction.ofV("disconnect", (s, a) -> {
                    c.host().wifiDisconnect();
                    return Constants.NONE;
                }),
        });
        return t;
    }

    private static LuaTable stats(Computer c) throws LuaError {
        LuaTable t = new LuaTable();
        Computer.Limits l = c.limits();
        double perCore = Math.max(1, (double) l.opsPerTick() * Computer.TICKS_PER_SECOND);
        t.rawset("cpu", valueOf(Math.round(c.cpuLoad() * 1000) / 10.0));
        LuaTable cores = new LuaTable();
        double[] loads = c.coreLoads();
        for (int i = 0; i < loads.length; i++) cores.rawset(i + 1, valueOf(Math.round(loads[i] * 1000) / 10.0));
        t.rawset("cores", cores);
        // текущая частота ядра, кГц (с бустом или сбросом от перегрева)
        t.rawset("clock", valueOf(Math.round(c.info().clockKhz() * c.clockFactor())));
        t.rawset("opsPerSecond", valueOf((double) c.opsLastSecond()));
        t.rawset("opsMax", valueOf(perCore * Math.max(1, l.cores())));
        LuaTable procs = new LuaTable();
        int i = 1;
        int cores_ = Math.max(1, l.cores());
        for (LuaMachine p : c.machines()) {
            LuaTable e = new LuaTable();
            e.rawset("pid", valueOf(p.pid));
            e.rawset("parent", valueOf(p.parentPid));
            e.rawset("path", str(p.isMain() ? "/bios.lua" : p.programPath));
            e.rawset("status", str(p.status()));
            e.rawset("cpu", valueOf(Math.round(p.opsLastSecond / (perCore * cores_) * 1000) / 10.0));
            e.rawset("ops", valueOf((double) p.opsLastSecond));
            e.rawset("memory", valueOf((double) p.liveBytes()));
            procs.rawset(i++, e);
        }
        t.rawset("processes", procs);
        LuaTable mem = new LuaTable();
        mem.rawset("used", valueOf((double) c.memoryUsed()));
        mem.rawset("total", valueOf((double) c.memoryLimit()));
        t.rawset("memory", mem);
        t.rawset("gpu", valueOf(Math.round(c.gpuLoad() * 1000) / 10.0));
        LuaTable disks = new LuaTable();
        i = 1;
        for (Map.Entry<String, com.strikerr212.minepc.computer.fs.Mount> e : c.fileSystem().mounts().entrySet()) {
            com.strikerr212.minepc.computer.fs.Mount mount = e.getValue();
            if (e.getKey().equals("rom")) continue;
            long[] d = c.diskStats(mount);
            LuaTable dt = new LuaTable();
            dt.rawset("path", str("/" + e.getKey()));
            dt.rawset("label", str(mount.label()));
            dt.rawset("read", valueOf((double) d[2]));
            dt.rawset("write", valueOf((double) d[3]));
            dt.rawset("readTotal", valueOf((double) d[4]));
            dt.rawset("writeTotal", valueOf((double) d[5]));
            long speed = mount.bytesPerSecond();
            dt.rawset("speed", valueOf((double) speed));
            dt.rawset("active", valueOf(speed <= 0 ? 0 : Math.min(100, Math.round((d[2] + d[3]) * 1000.0 / speed) / 10.0)));
            dt.rawset("capacity", valueOf((double) mount.capacity()));
            dt.rawset("used", valueOf((double) mount.used()));
            disks.rawset(i++, dt);
        }
        t.rawset("disks", disks);
        LuaTable net = new LuaTable();
        net.rawset("up", valueOf((double) c.netUpPerSecond()));
        net.rawset("down", valueOf((double) c.netDownPerSecond()));
        net.rawset("sent", valueOf((double) c.netSent()));
        net.rawset("received", valueOf((double) c.netReceived()));
        net.rawset("speed", valueOf((double) c.host().netBytesPerSecond()));
        t.rawset("net", net);
        Computer.Thermal th = c.thermal();
        t.rawset("cpuTemp", valueOf(Math.round(c.cpuTemp() * 10) / 10.0));
        if (th.hasGpu()) t.rawset("gpuTemp", valueOf(Math.round(c.gpuTemp() * 10) / 10.0));
        t.rawset("fan", valueOf(c.fanRpm()));
        t.rawset("power", valueOf(c.powerNow()));
        t.rawset("psu", valueOf(th.psuWatts()));
        t.rawset("uptime", valueOf(c.uptimeSeconds()));
        return t;
    }

    // ======================= term =======================

    private static LuaTable term(Computer c) {
        Terminal t = c.terminal();
        LuaTable table = new LuaTable();
        RegisteredFunction.bind(table, new RegisteredFunction[]{
                RegisteredFunction.ofV("write", (s, a) -> {
                    String text = Text.bmp(text(a.arg(1)));
                    c.chargeGpu(text.length());
                    t.write(text);
                    return Constants.NONE;
                }),
                RegisteredFunction.ofV("clear", (s, a) -> { c.chargeGpu(t.cells() / 16); t.clear(); return Constants.NONE; }),
                RegisteredFunction.ofV("clearLine", (s, a) -> { c.chargeGpu(t.width() / 8); t.clearLine(); return Constants.NONE; }),
                RegisteredFunction.ofV("getCursorPos", (s, a) -> varargsOf(valueOf(t.cursorX() + 1), valueOf(t.cursorY() + 1))),
                RegisteredFunction.ofV("setCursorPos", (s, a) -> {
                    t.setCursor(a.arg(1).checkInteger() - 1, a.arg(2).checkInteger() - 1);
                    return Constants.NONE;
                }),
                RegisteredFunction.ofV("setCursorBlink", (s, a) -> { t.setCursorBlink(a.arg(1).toBoolean()); return Constants.NONE; }),
                RegisteredFunction.ofV("getSize", (s, a) -> varargsOf(valueOf(t.width()), valueOf(t.height()))),
                RegisteredFunction.ofV("scroll", (s, a) -> { c.chargeGpu(t.cells() / 8); t.scroll(a.arg(1).checkInteger()); return Constants.NONE; }),
                RegisteredFunction.ofV("getResolution", (s, a) -> varargsOf(valueOf(t.width()), valueOf(t.height()))),
                RegisteredFunction.ofV("maxResolution", (s, a) -> varargsOf(valueOf(t.maxWidth()), valueOf(t.maxHeight()))),
                RegisteredFunction.ofV("setResolution", (s, a) -> {
                    int w = a.arg(1).checkInteger(), h = a.arg(2).checkInteger();
                    if (w < 1 || h < 1 || w > t.maxWidth() || h > t.maxHeight()) throw error("Такое разрешение не поддерживается");
                    boolean changed = t.resize(w, h);
                    c.chargeGpu(t.cells() / 16);
                    if (changed) c.queueEvent("screen_resized", w, h);
                    return valueOf(changed);
                }),
                RegisteredFunction.ofV("setTextColor", (s, a) -> { t.setTextIndex(a.arg(1).checkInteger()); return Constants.NONE; }),
                RegisteredFunction.ofV("setBackgroundColor", (s, a) -> { t.setBackIndex(a.arg(1).checkInteger()); return Constants.NONE; }),
                RegisteredFunction.ofV("getTextColor", (s, a) -> t.textIndex() < 0 ? Constants.NIL : valueOf(t.textIndex())),
                RegisteredFunction.ofV("getBackgroundColor", (s, a) -> t.backIndex() < 0 ? Constants.NIL : valueOf(t.backIndex())),
                // Любой цвет числом 0xRRGGBB (сколько оттенков видно — зависит от видеокарты)
                RegisteredFunction.ofV("setTextColorRGB", (s, a) -> { t.setTextRgb(a.arg(1).checkInteger()); return Constants.NONE; }),
                RegisteredFunction.ofV("setBackgroundColorRGB", (s, a) -> { t.setBackRgb(a.arg(1).checkInteger()); return Constants.NONE; }),
                RegisteredFunction.ofV("getTextColorRGB", (s, a) -> valueOf(t.textRgb())),
                RegisteredFunction.ofV("getBackgroundColorRGB", (s, a) -> valueOf(t.backRgb())),
                RegisteredFunction.ofV("setPaletteColor", (s, a) -> {
                    t.setPaletteColor(a.arg(1).checkInteger(), a.arg(2).checkInteger());
                    return Constants.NONE;
                }),
                RegisteredFunction.ofV("getPaletteColor", (s, a) -> valueOf(t.paletteColor(a.arg(1).checkInteger()))),
                RegisteredFunction.ofV("getDepth", (s, a) -> valueOf(t.depth())),
                RegisteredFunction.ofV("isColor", (s, a) -> valueOf(t.isColor())),
        });
        return table;
    }

    // ======================= fs =======================

    private static String path(Varargs a, int i) throws LuaError {
        return FileSystem.normalize(text(a.arg(i).checkLuaString()));
    }

    private interface FsCall {
        Varargs call(FileSystem fs, Varargs a) throws FsException, LuaError;
    }

    private static RegisteredFunction fsFn(String name, Computer c, FsCall fn) {
        return RegisteredFunction.ofV(name, (s, a) -> {
            try {
                return fn.call(c.fileSystem(), a);
            } catch (FsException e) {
                throw error(e.getMessage());
            }
        });
    }

    private static LuaTable fs(Computer c, LuaMachine m) {
        LuaTable table = new LuaTable();
        RegisteredFunction.bind(table, new RegisteredFunction[]{
                fsFn("list", c, (fs, a) -> {
                    List<String> names = fs.list(path(a, 1));
                    LuaTable out = new LuaTable();
                    for (int i = 0; i < names.size(); i++) out.rawset(i + 1, str(names.get(i)));
                    return out;
                }),
                fsFn("exists", c, (fs, a) -> valueOf(fs.exists(path(a, 1)))),
                fsFn("isDir", c, (fs, a) -> valueOf(fs.isDirectory(path(a, 1)))),
                fsFn("isReadOnly", c, (fs, a) -> valueOf(fs.isReadOnly(path(a, 1)))),
                fsFn("getSize", c, (fs, a) -> valueOf((double) fs.size(path(a, 1)))),
                fsFn("getFreeSpace", c, (fs, a) -> valueOf((double) fs.freeSpace(path(a, 1)))),
                fsFn("getCapacity", c, (fs, a) -> valueOf((double) fs.capacity(path(a, 1)))),
                fsFn("getDrive", c, (fs, a) -> {
                    String d = fs.drive(path(a, 1));
                    return d == null ? Constants.NIL : str("/" + d);
                }),
                fsFn("getLabel", c, (fs, a) -> str(fs.driveLabel(path(a, 1)))),
                fsFn("lastModified", c, (fs, a) -> valueOf((double) fs.lastModified(path(a, 1)))),
                fsFn("makeDir", c, (fs, a) -> { fs.makeDirectory(path(a, 1)); return Constants.NONE; }),
                fsFn("delete", c, (fs, a) -> { fs.delete(path(a, 1)); return Constants.NONE; }),
                fsFn("move", c, (fs, a) -> { fs.move(path(a, 1), path(a, 2)); return Constants.NONE; }),
                fsFn("copy", c, (fs, a) -> { fs.copy(path(a, 1), path(a, 2)); return Constants.NONE; }),
                fsFn("mounts", c, (fs, a) -> {
                    LuaTable out = new LuaTable();
                    int i = 1;
                    for (Map.Entry<String, Mount> e : fs.mounts().entrySet()) {
                        LuaTable mt = new LuaTable();
                        mt.rawset("path", str("/" + e.getKey()));
                        mt.rawset("label", str(e.getValue().label()));
                        mt.rawset("capacity", valueOf((double) e.getValue().capacity()));
                        mt.rawset("used", valueOf((double) e.getValue().used()));
                        mt.rawset("readOnly", valueOf(e.getValue().isReadOnly()));
                        mt.rawset("speed", valueOf((double) e.getValue().bytesPerSecond()));
                        out.rawset(i++, mt);
                    }
                    return out;
                }),
                RegisteredFunction.ofV("open", (s, a) -> open(c, m, a)),
        });
        return table;
    }

    /** fs.open(путь, режим): "r" — чтение, "w" — запись, "a" — дописывание; "b" в режиме ничего не меняет. */
    private static Varargs open(Computer c, LuaMachine m, Varargs a) throws LuaError {
        String path = path(a, 1);
        String mode = a.arg(2).optString("r").replace("b", "");
        FileSystem fs = c.fileSystem();
        try {
            return switch (mode) {
                case "r" -> {
                    // Файл читается в память целиком — он должен в неё поместиться
                    if (fs.exists(path) && !fs.isDirectory(path) && !m.reserve(fs.size(path) * 2)) {
                        throw new FsException("Недостаточно памяти, чтобы открыть файл");
                    }
                    yield readHandle(fs.read(path));
                }
                case "w", "a" -> {
                    if (fs.isDirectory(path)) throw new FsException("Это папка");
                    if (fs.isReadOnly(path)) throw new FsException("Только для чтения");
                    yield writeHandle(fs, path, mode.equals("a"));
                }
                default -> throw error("Неизвестный режим: " + mode);
            };
        } catch (FsException e) {
            return varargsOf(Constants.NIL, str(e.getMessage()));
        }
    }

    private static LuaTable readHandle(byte[] data) {
        int[] pos = {0};
        boolean[] closed = {false};
        LuaTable h = new LuaTable();
        RegisteredFunction.bind(h, new RegisteredFunction[]{
                RegisteredFunction.ofV("readLine", (s, a) -> {
                    if (closed[0]) throw error("Файл закрыт");
                    if (pos[0] >= data.length) return Constants.NIL;
                    int start = pos[0], end = start;
                    while (end < data.length && data[end] != '\n') end++;
                    pos[0] = Math.min(data.length, end + 1);
                    int len = end - start;
                    if (len > 0 && data[start + len - 1] == '\r') len--;
                    return LuaString.valueOf(data, start, len);
                }),
                RegisteredFunction.ofV("readAll", (s, a) -> {
                    if (closed[0]) throw error("Файл закрыт");
                    int start = pos[0];
                    pos[0] = data.length;
                    return LuaString.valueOf(data, start, data.length - start);
                }),
                RegisteredFunction.ofV("read", (s, a) -> {
                    if (closed[0]) throw error("Файл закрыт");
                    if (pos[0] >= data.length) return Constants.NIL;
                    int n = a.arg(1).optInteger(1);
                    int start = pos[0];
                    pos[0] = Math.min(data.length, start + Math.max(0, n));
                    return LuaString.valueOf(data, start, pos[0] - start);
                }),
                RegisteredFunction.ofV("close", (s, a) -> { closed[0] = true; return Constants.NONE; }),
        });
        return h;
    }

    /** Запись копится в памяти и уходит на диск порциями по 64 КБ (и при flush/close). */
    private static final int AUTO_FLUSH = 64 * 1024;

    private static LuaTable writeHandle(FileSystem fs, String path, boolean append) throws FsException {
        // Файл создаётся сразу (как в обычных системах), содержимое пишется при flush/close
        if (!append) fs.write(path, new byte[0], false);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        boolean[] closed = {false};
        LuaTable h = new LuaTable();
        RegisteredFunction.bind(h, new RegisteredFunction[]{
                RegisteredFunction.ofV("write", (s, a) -> {
                    if (closed[0]) throw error("Файл закрыт");
                    byte[] b = bytes(a.arg(1));
                    buf.write(b, 0, b.length);
                    if (buf.size() >= AUTO_FLUSH) flush(fs, path, buf);
                    return Constants.NONE;
                }),
                RegisteredFunction.ofV("writeLine", (s, a) -> {
                    if (closed[0]) throw error("Файл закрыт");
                    byte[] b = bytes(a.arg(1));
                    buf.write(b, 0, b.length);
                    buf.write('\n');
                    if (buf.size() >= AUTO_FLUSH) flush(fs, path, buf);
                    return Constants.NONE;
                }),
                RegisteredFunction.ofV("flush", (s, a) -> { flush(fs, path, buf); return Constants.NONE; }),
                RegisteredFunction.ofV("close", (s, a) -> {
                    if (closed[0]) return Constants.NONE;
                    closed[0] = true;
                    flush(fs, path, buf);
                    return Constants.NONE;
                }),
        });
        return h;
    }

    private static void flush(FileSystem fs, String path, ByteArrayOutputStream buf) throws LuaError {
        if (buf.size() == 0) return;
        try {
            fs.write(path, buf.toByteArray(), true);
            buf.reset();
        } catch (FsException e) {
            throw error(e.getMessage());
        }
    }

    // ======================= os =======================

    private static LuaTable os(Computer c, LuaMachine m) {
        LuaTable table = new LuaTable();
        RegisteredFunction.bind(table, new RegisteredFunction[]{
                RegisteredFunction.ofV("startTimer", (s, a) -> valueOf(c.startTimer(a.arg(1).checkNumber().toDouble(), m.events))),
                RegisteredFunction.ofV("cancelTimer", (s, a) -> { c.cancelTimer(a.arg(1).checkInteger()); return Constants.NONE; }),
                RegisteredFunction.ofV("clock", (s, a) -> valueOf(c.uptimeSeconds())),
                RegisteredFunction.ofV("time", (s, a) -> OcApis.time(a)),
                RegisteredFunction.ofV("date", (s, a) -> OcApis.date(a)),
                RegisteredFunction.ofV("gameTime", (s, a) -> valueOf(c.host().timeOfDay())),
                RegisteredFunction.ofV("day", (s, a) -> valueOf(c.host().day())),
                RegisteredFunction.ofV("epoch", (s, a) -> valueOf((double) System.currentTimeMillis())),
                RegisteredFunction.ofV("queueEvent", (s, a) -> {
                    String name = text(a.arg(1).checkLuaString());
                    Object[] rest = new Object[Math.max(0, a.count() - 1)];
                    for (int i = 0; i < rest.length; i++) rest[i] = fromLua(a.arg(i + 2));
                    m.events.offer(Computer.event(name, rest));
                    return Constants.NONE;
                }),
                RegisteredFunction.ofV("shutdown", (s, a) -> { c.requestShutdown(); return Constants.NONE; }),
                RegisteredFunction.ofV("reboot", (s, a) -> { c.requestReboot(); return Constants.NONE; }),
                RegisteredFunction.ofV("getComputerLabel", (s, a) -> c.label().isEmpty() ? Constants.NIL : str(c.label())),
                RegisteredFunction.ofV("setComputerLabel", (s, a) -> {
                    String label = a.arg(1).isNil() ? "" : text(a.arg(1).checkLuaString());
                    if (label.length() > 32) label = label.substring(0, 32);
                    c.setLabel(label);
                    c.host().onLabelChanged(label);
                    return Constants.NONE;
                }),
        });
        return table;
    }

    // ======================= computer =======================

    private static LuaTable computer(Computer c, LuaMachine m) {
        LuaTable table = new LuaTable();
        RegisteredFunction.bind(table, new RegisteredFunction[]{
                RegisteredFunction.ofV("info", (s, a) -> {
                    Computer.Info i = c.info();
                    LuaTable t = new LuaTable();
                    t.rawset("cpu", str(i.cpu()));
                    t.rawset("cores", valueOf(i.cores()));
                    t.rawset("clock", valueOf(i.clockKhz()));
                    t.rawset("architecture", str(i.architecture()));
                    t.rawset("ram", valueOf(i.ramKb()));
                    t.rawset("opsPerSecond", valueOf(i.opsPerSecond()));
                    t.rawset("gpu", str(i.gpu()));
                    t.rawset("score", valueOf(i.score()));
                    return t;
                }),
                RegisteredFunction.ofV("uptime", (s, a) -> valueOf(c.uptimeSeconds())),
                // Сколько событий уже ждёт в очереди: можно забрать их все, не засыпая (event.pull(0))
                RegisteredFunction.ofV("pending", (s, a) -> valueOf(m.events.size())),
                // ----- Как в OpenComputers -----
                RegisteredFunction.ofV("address", (s, a) -> str(c.address())),
                RegisteredFunction.ofV("componentAddress", (s, a) -> str(OcApis.address(c, text(a.arg(1).checkLuaString())))),
                RegisteredFunction.ofV("gpu", (s, a) -> OcApis.gpu(c, m, OcApis.address(c, "gpu"), OcApis.address(c, "screen"))),
                RegisteredFunction.ofV("totalMemory", (s, a) -> valueOf((double) c.memoryLimit())),
                // Занятая память — по последнему подсчёту (он происходит сам, когда программа много выделяет)
                RegisteredFunction.ofV("freeMemory", (s, a) -> valueOf((double) Math.max(0, c.memoryLimit() - c.memoryUsed()))),
                RegisteredFunction.ofV("energy", (s, a) -> valueOf(10000)),
                RegisteredFunction.ofV("maxEnergy", (s, a) -> valueOf(10000)),
                RegisteredFunction.ofV("getArchitecture", (s, a) -> str("Lua 5.2")),
                RegisteredFunction.ofV("getArchitectures", (s, a) -> {
                    LuaTable t = new LuaTable();
                    t.rawset(1, str("Lua 5.2"));
                    return t;
                }),
                RegisteredFunction.ofV("isRobot", (s, a) -> Constants.FALSE),
                RegisteredFunction.ofV("beep", (s, a) -> SoundApis.beep(c, a)),
                RegisteredFunction.ofV("users", (s, a) -> Constants.NONE),
                RegisteredFunction.ofV("tmpAddress", (s, a) -> Constants.NIL),
                RegisteredFunction.ofV("pushSignal", (s, a) -> {
                    String name = text(a.arg(1).checkLuaString());
                    Object[] rest = new Object[Math.max(0, a.count() - 1)];
                    for (int i = 0; i < rest.length; i++) rest[i] = fromLua(a.arg(i + 2));
                    return valueOf(m.events.offer(Computer.event(name, rest)));
                }),
                // ----- Процессы: каждый работает на своём ядре процессора -----
                RegisteredFunction.ofV("spawn", (s, a) -> {
                    String path = path(a, 1);
                    if (!c.fileSystem().exists(path) || c.fileSystem().isDirectory(path)) throw error("Нет такой программы: /" + path);
                    Object[] args = new Object[Math.max(0, a.count() - 1)];
                    for (int i = 0; i < args.length; i++) args[i] = copyOut(a.arg(i + 2));
                    try {
                        return valueOf(c.spawn(m, "/" + path, args));
                    } catch (LuaMachine.Crash e) {
                        throw error(e.getMessage());
                    }
                }),
                RegisteredFunction.ofV("send", (s, a) -> {
                    LuaMachine target = c.machine(a.arg(1).checkInteger());
                    if (target == null || target.isFinished()) return Constants.FALSE;
                    Object[] ev = new Object[a.count() + 1];
                    ev[0] = "process_message";
                    ev[1] = m.pid;
                    for (int i = 2; i <= a.count(); i++) ev[i] = copyOut(a.arg(i));
                    return valueOf(target.events.offer(ev));
                }),
                RegisteredFunction.ofV("kill", (s, a) -> {
                    int pid = a.arg(1).checkInteger();
                    LuaMachine target = c.machine(pid);
                    if (target == null || target.isMain()) return Constants.FALSE;
                    target.kill();
                    return Constants.TRUE;
                }),
                RegisteredFunction.ofV("processes", (s, a) -> {
                    LuaTable out = new LuaTable();
                    int i = 1;
                    for (LuaMachine p : c.machines()) {
                        LuaTable t = new LuaTable();
                        t.rawset("pid", valueOf(p.pid));
                        t.rawset("parent", valueOf(p.parentPid));
                        t.rawset("path", str(p.isMain() ? "/bios.lua" : p.programPath));
                        t.rawset("status", str(p.status()));
                        t.rawset("memory", valueOf((double) p.liveBytes()));
                        t.rawset("ops", valueOf((double) p.opsTotal()));
                        out.rawset(i++, t);
                    }
                    return out;
                }),
                // Нагрузка, как в диспетчере задач: ядра, процессы, память, диски, видеокарта, сеть, температура
                RegisteredFunction.ofV("stats", (s, a) -> stats(c)),
                RegisteredFunction.ofV("pid", (s, a) -> valueOf(m.pid)),
                RegisteredFunction.ofV("parentPid", (s, a) -> valueOf(m.parentPid)),
                RegisteredFunction.ofV("limits", (s, a) -> {
                    Computer.Limits l = c.limits();
                    Terminal t = c.terminal();
                    LuaTable out = new LuaTable();
                    out.rawset("opsPerSecond", valueOf((double) l.opsPerTick() * Computer.TICKS_PER_SECOND));
                    out.rawset("cores", valueOf(l.cores()));
                    out.rawset("memory", valueOf((double) l.memoryBytes()));
                    out.rawset("gpuCellsPerSecond", valueOf((double) l.gpuCellsPerTick() * Computer.TICKS_PER_SECOND));
                    out.rawset("vram", valueOf((double) l.vramCells()));
                    out.rawset("maxWidth", valueOf(t.maxWidth()));
                    out.rawset("maxHeight", valueOf(t.maxHeight()));
                    out.rawset("depth", valueOf(t.maxDepth()));
                    out.rawset("maxProcesses", valueOf(l.maxProcesses()));
                    out.rawset("bootSeconds", valueOf(l.bootTicks() / (double) Computer.TICKS_PER_SECOND));
                    return out;
                }),
                // Списать команды за работу встроенных функций (используют обёртки BIOS; отрицательное нельзя)
                RegisteredFunction.ofV("__charge", (s, a) -> {
                    double n = a.arg(1).optNumber(valueOf(0)).toDouble();
                    if (n > 0) m.spend((long) Math.min(n, 1e12));
                    return Constants.NONE;
                }),
                RegisteredFunction.ofV("shutdown", (s, a) -> {
                    if (a.arg(1).toBoolean()) c.requestReboot();
                    else c.requestShutdown();
                    return Constants.NONE;
                }),
                RegisteredFunction.ofV("opsUsed", (s, a) -> valueOf((double) c.opsLastSecond())),
                RegisteredFunction.ofV("opsLimit", (s, a) -> valueOf((double) c.opsPerTick() * Computer.TICKS_PER_SECOND)),
        });
        return table;
    }
}
