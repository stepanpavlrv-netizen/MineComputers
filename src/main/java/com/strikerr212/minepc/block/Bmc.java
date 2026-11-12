package com.strikerr212.minepc.block;

import com.strikerr212.minepc.hardware.MotherboardData;
import com.strikerr212.minepc.hardware.Perf;
import com.strikerr212.minepc.hardware.SystemModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * BMC серверной платы: маленький отдельный компьютер, который работает от дежурного питания — пока в сервере есть
 * хоть один блок питания, даже если сам сервер выключен или упал. Отвечает по проводной сети (через гнездо модуля):
 * состояние, датчики, журнал событий, экран сервера, включение и выключение. Вход — по паролю (по умолчанию admin).
 */
public final class Bmc {

    public static final String DEFAULT_PASSWORD = "admin";
    private static final int LOG_MAX = 64;
    /** Вентиляторов в стене серверного шасси. */
    public static final int FANS = 6;

    private final PcCaseBlockEntity be;
    private String password = DEFAULT_PASSWORD;
    private final ArrayDeque<String> log = new ArrayDeque<>();

    Bmc(PcCaseBlockEntity be) {
        this.be = be;
    }

    /** BMC есть и работает: серверная плата и дежурное питание (хоть один блок питания). */
    public boolean present() {
        MotherboardData board = be.motherboard();
        return board != null && board.server() && be.psuCount() > 0;
    }

    /** Собственный адрес BMC (постоянный, выводится из адреса сервера). */
    public String address() {
        return UUID.nameUUIDFromBytes(("bmc:" + be.address()).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public boolean defaultPassword() {
        return DEFAULT_PASSWORD.equals(password);
    }

    public boolean checkPassword(String pw) {
        return password.equals(pw);
    }

    public void setPassword(String pw) {
        password = pw;
        be.setChanged();
    }

    // ======================= Журнал =======================

    /** Записать событие (только у серверного шасси — у обычного компьютера BMC нет). */
    public void log(String text) {
        if (!be.serverChassis() || be.getLevel() == null || be.getLevel().isClientSide) return;
        long t = be.getLevel().getDayTime();
        long day = t / 24000 + 1;
        int minutes = (int) ((t % 24000) * 1440 / 24000);
        int hours = (minutes / 60 + 6) % 24;   // в Minecraft день начинается в 6 утра
        log.addLast(String.format(Locale.ROOT, "день %d %02d:%02d  %s", day, hours, minutes % 60, text));
        while (log.size() > LOG_MAX) log.removeFirst();
        be.setChanged();
    }

    public List<String> entries() {
        return new ArrayList<>(log);
    }

    void save(CompoundTag tag) {
        if (!defaultPassword()) tag.putString("bmc_password", password);
        if (!log.isEmpty()) {
            ListTag list = new ListTag();
            for (String s : log) list.add(StringTag.valueOf(s));
            tag.put("bmc_log", list);
        }
    }

    void load(CompoundTag tag) {
        password = tag.contains("bmc_password") ? tag.getString("bmc_password") : DEFAULT_PASSWORD;
        log.clear();
        for (Tag t : tag.getList("bmc_log", Tag.TAG_STRING)) log.addLast(t.getAsString());
    }

    // ======================= Команды =======================

    /** Выполнить команду (пароль уже проверен). Ответ: {ok, сообщение, данные}. */
    Object[] command(ServerLevel level, String cmd, String arg, String from) {
        return switch (cmd.toLowerCase(Locale.ROOT)) {
            case "status" -> status();
            case "sensors" -> sensors();
            case "log" -> log(arg);
            case "screen" -> screen();
            case "power" -> power(level, arg, from);
            case "password" -> password(arg, from);
            case "help", "" -> reply(true, String.join("\n",
                    "Команды BMC:",
                    "  status          — питание, процессоры, вентиляторы, блоки питания, диски",
                    "  sensors         — температура, обороты, потребление",
                    "  log [N|clear]   — журнал событий",
                    "  screen          — что сейчас на экране сервера",
                    "  power on|off|reset — включить, выключить, перезагрузить",
                    "  password новый  — сменить пароль BMC"), null);
            default -> reply(false, "Неизвестная команда BMC: " + cmd + " (bmc имя help — список)", null);
        };
    }

    private static Object[] reply(boolean ok, String message, Map<String, Object> data) {
        return new Object[]{ok, message, data};
    }

    private String name() {
        String l = be.label();
        return l.isEmpty() ? be.address().substring(0, Math.min(8, be.address().length())) : l;
    }

    private String powerText(int state) {
        return state == 1 ? "включён" : state == 2 ? "сбой (сервер упал)" : "выключен";
    }

    private static String duration(double seconds) {
        long s = (long) seconds;
        if (s < 60) return s + " с";
        if (s < 3600) return (s / 60) + " мин " + (s % 60) + " с";
        return (s / 3600) + " ч " + (s % 3600 / 60) + " мин";
    }

    /** Температура процессоров: у работающего — живая, у выключенного — комнатная. */
    private double cpuTemp() {
        var c = be.caseComputer().computer();
        return c != null && be.computerState() == 1 ? c.cpuTemp() : 24;
    }

    /** Обороты стены вентиляторов: растут с температурой. 0 — сервер выключен. */
    private int fanRpm() {
        if (be.computerState() != 1) return 0;
        return (int) Math.min(12000, 3200 + Math.max(0, cpuTemp() - 30) * 140);
    }

    private Object[] status() {
        int state = be.computerState();
        SystemModel.Input in = be.collectInput();
        var c = be.caseComputer().computer();
        Map<String, Object> d = new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder();
        sb.append("Сервер ").append(name()).append(" (").append(be.address(), 0, 8).append("), BMC ").append(address(), 0, 8).append('\n');
        d.put("power", state == 1 ? "on" : state == 2 ? "crashed" : "off");
        sb.append("Питание      ").append(powerText(state));
        if (state == 1 && c != null) {
            d.put("uptime", c.uptimeSeconds());
            sb.append(", ").append(duration(c.uptimeSeconds()));
        }
        sb.append('\n');
        int cpus = (be.getStack(PcCaseBlockEntity.SLOT_CPU).isEmpty() ? 0 : 1) + (be.getStack(PcCaseBlockEntity.SLOT_CPU2).isEmpty() ? 0 : 1);
        d.put("cpus", cpus);
        if (in.cpu() != null) {
            d.put("cores", in.cpu().cores());
            d.put("temp", Math.round(cpuTemp()));
            sb.append("Процессоры   ").append(cpus).append(" × ").append(in.cpu().cores() / Math.max(1, cpus)).append(" ядер, ")
                    .append(Math.round(cpuTemp())).append(" °C\n");
        } else {
            sb.append("Процессоры   нет\n");
        }
        int rpm = fanRpm();
        d.put("fans", FANS);
        d.put("rpm", rpm);
        sb.append("Вентиляторы  ").append(FANS).append(" из ").append(FANS).append(", ").append(rpm == 0 ? "стоят" : rpm + " об/мин").append('\n');
        Map<Object, Object> psu = new LinkedHashMap<>();
        StringBuilder ps = new StringBuilder();
        int[] psuSlots = {PcCaseBlockEntity.SLOT_PSU, PcCaseBlockEntity.SLOT_PSU2};
        for (int k = 0; k < 2; k++) {
            boolean has = !be.getStack(psuSlots[k]).isEmpty();
            psu.put(k + 1, has);
            if (k > 0) ps.append(", ");
            ps.append(k + 1).append(has ? (state == 1 ? " в работе" : " есть") : " вынут");
        }
        d.put("psu", psu);
        sb.append("Блоки пит.   ").append(ps).append(be.psuCount() < 2 ? "  ! нет резерва" : "").append('\n');
        Map<Object, Object> disks = new LinkedHashMap<>();
        StringBuilder ds = new StringBuilder();
        int n = 0;
        for (int i = 0; i < 4; i++) {
            boolean has = !be.getStack(PcCaseBlockEntity.SLOT_HDD + i).isEmpty();
            disks.put(i + 1, has);
            if (has) n++;
            ds.append(has ? '#' : '.');
        }
        d.put("disks", disks);
        sb.append("Диски        ").append(n).append(" из 4  [").append(ds).append("]\n");
        int watts = state == 1 ? in.powerDraw() : 8;   // выключенный: дежурное питание и сам BMC
        d.put("watts", watts);
        sb.append("Потребление  ").append(watts).append(" Вт");
        return reply(true, sb.toString(), d);
    }

    private Object[] sensors() {
        int state = be.computerState();
        SystemModel.Input in = be.collectInput();
        Map<String, Object> d = new LinkedHashMap<>();
        double temp = cpuTemp();
        int rpm = fanRpm();
        int watts = state == 1 ? in.powerDraw() : 8;
        int eff = in.psu() == null ? 90 : in.psu().efficiencyPercent();
        int wall = Perf.wallWatts(watts, eff);
        d.put("temp", Math.round(temp));
        d.put("rpm", rpm);
        d.put("watts", watts);
        d.put("wall", wall);
        String text = String.format(Locale.ROOT, "Процессоры   %d °C%s%nВентиляторы  %s%nНагрузка     %d Вт, из розетки %d Вт%nВоздух       %d Вт отвода",
                Math.round(temp), temp >= 90 ? "  ! перегрев" : "", rpm == 0 ? "стоят" : rpm + " об/мин", watts, wall,
                in.cooler() == null ? 0 : in.cooler().coolingW());
        return reply(true, text, d);
    }

    private Object[] log(String arg) {
        if ("clear".equalsIgnoreCase(arg)) {
            log.clear();
            log("журнал очищен");
            return reply(true, "Журнал очищен", null);
        }
        int count = 15;
        try {
            if (!arg.isEmpty()) count = Math.max(1, Math.min(LOG_MAX, Integer.parseInt(arg)));
        } catch (NumberFormatException ignored) {
        }
        List<String> all = entries();
        List<String> tail = all.subList(Math.max(0, all.size() - count), all.size());
        Map<Object, Object> d = new LinkedHashMap<>();
        for (int i = 0; i < tail.size(); i++) d.put(i + 1, tail.get(i));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entries", d);
        return reply(true, tail.isEmpty() ? "Журнал пуст" : String.join("\n", tail), data);
    }

    private Object[] screen() {
        var c = be.caseComputer().computer();
        if (c == null || be.computerState() != 1) return reply(false, "Сервер выключен — экрана нет", null);
        var t = c.terminal();
        List<String> lines = new ArrayList<>();
        for (int y = 0; y < t.height(); y++) lines.add(t.line(y));
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
        Map<Object, Object> d = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) d.put(i + 1, lines.get(i));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lines", d);
        data.put("width", t.width());
        return reply(true, String.join("\n", lines), data);
    }

    private Object[] power(ServerLevel level, String arg, String from) {
        int state = be.computerState();
        switch (arg.toLowerCase(Locale.ROOT)) {
            case "on" -> {
                if (state == 1) return reply(true, "Сервер уже включён", null);
                log("BMC: включение по сети (" + from + ")");
                var msg = be.togglePower();
                boolean ok = be.computerState() == 1;
                return reply(ok, ok ? "Сервер включается" : "Не включился: " + (msg == null ? "" : msg.getString()), null);
            }
            case "off" -> {
                if (state != 1) return reply(true, "Сервер уже выключен", null);
                log("BMC: выключение по сети (" + from + ")");
                be.togglePower();
                return reply(true, "Сервер выключен", null);
            }
            case "reset", "cycle" -> {
                log("BMC: перезагрузка по сети (" + from + ")");
                if (state == 1) be.togglePower();
                var msg = be.togglePower();
                boolean ok = be.computerState() == 1;
                return reply(ok, ok ? "Сервер перезагружается…" : "Не включился: " + (msg == null ? "" : msg.getString()), null);
            }
            default -> {
                return reply(false, "power on | off | reset", null);
            }
        }
    }

    private Object[] password(String arg, String from) {
        if (arg.length() < 4 || arg.length() > 32) return reply(false, "Пароль — от 4 до 32 знаков", null);
        setPassword(arg);
        log("пароль BMC изменён (" + from + ")");
        return reply(true, "Пароль BMC изменён", null);
    }
}
