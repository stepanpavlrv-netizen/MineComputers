import com.strikerr212.minepc.computer.*;
import com.strikerr212.minepc.computer.fs.DirMount;

import java.nio.file.*;
import java.util.*;

/**
 * Проверка компьютера без Minecraft: включаем, «печатаем» команды, смотрим на экран.
 * Запуск: python tools/computer/run_test.py
 */
public class HeadlessTest {

    static Computer pc;
    static int failures = 0;

    static void ticks(int n) throws Exception {
        for (int i = 0; i < n; i++) {
            pc.tick();
            Thread.sleep(3);
        }
    }

    static void type(String text) throws Exception {
        for (char ch : text.toCharArray()) pc.queueEvent("char", String.valueOf(ch));
        ticks(3);
    }

    static void key(String name, boolean ctrl) throws Exception {
        pc.queueEvent("key", name, ctrl, false);
        ticks(3);
    }

    /** Ввести строку и подождать, тикая как настоящий сервер: 20 раз в секунду. */
    static void realLine(String line, int realTicks) throws Exception {
        for (char ch : line.toCharArray()) pc.queueEvent("char", String.valueOf(ch));
        pc.queueEvent("key", "enter", false, false);
        for (int i = 0; i < realTicks; i++) { pc.tick(); Thread.sleep(50); }
    }

    static void command(String line) throws Exception {
        type(line);
        key("enter", false);
        ticks(15);
    }

    static String screen() {
        Terminal t = pc.terminal();
        StringBuilder b = new StringBuilder();
        for (int y = 0; y < t.height(); y++) b.append(t.line(y).replaceAll("\\s+$", "")).append('\n');
        return b.toString();
    }

    static String flat() {
        return screen().replaceAll("\\s+", " ");
    }

    static void show(String title) {
        System.out.println("┌── " + title + " ──");
        for (String l : screen().split("\n")) System.out.println("│" + l);
        System.out.println("└──");
    }

    static Path root;
    static final java.util.List<Object[]> AUDIO = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Собрать звук из пакетов, как у игрока, и найти основную частоту и громкость. */
    static double[] renderAudio(int ms) {
        var synth = new com.strikerr212.minepc.computer.audio.Synth();
        var tl = new com.strikerr212.minepc.computer.audio.Timeline(synth);
        for (Object[] p : AUDIO) tl.receive((Long) p[0], (Long) p[1], (byte[]) p[2]);
        float[] out = new float[ms * 44100 / 1000];
        synth.render(out, out.length);
        double rms = 0;
        int cross = 0, first = -1, last = -1;
        for (int i = 1; i < out.length; i++) {
            rms += out[i] * out[i];
            if (out[i - 1] < 0 && out[i] >= 0) { cross++; if (first < 0) first = i; last = i; }
        }
        double freq = cross > 1 ? (cross - 1) * 44100.0 / (last - first) : 0;
        return new double[]{Math.sqrt(rms / out.length), freq};
    }

    static void expect(String what, boolean ok) {
        System.out.println((ok ? "  ✔ " : "  ✘ ") + what);
        if (!ok) failures++;
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("minepc_drive");
        root = dir;
        pc = new Computer(new Computer.Host() {
            @Override public void audio(long startMs, long nowMs, byte[] commands) {
                AUDIO.add(new Object[]{startMs, nowMs, commands});
            }
        });
        pc.setInfo(new Computer.Info("Тестовый процессор", 4, 2800, "Lua", 2048, 2_000_000, "Видеокарта", 1000));
        pc.setOpsPerTick(Integer.parseInt(args.length > 0 ? args[0] : "100000"));
        pc.fileSystem().mount("rom", ComputerRom.mount());
        pc.fileSystem().mount("", new DirMount(dir, 64 * 1024, "SSD"));
        pc.terminal().resize(50, 16);

        pc.turnOn();
        for (int i = 0; i < 200 && !screen().contains("/>"); i++) ticks(1);
        show("после включения");
        expect("BIOS напечатал заголовок", screen().contains("MineComputers BIOS"));
        expect("оболочка ждёт команду", screen().contains("/>"));

        command("help");
        show("help");
        expect("help показал команды", screen().contains("edit <файл>"));

        command("clear");
        command("mkdir code");
        command("cd code");
        // Пишем программу через редактор
        command("edit hello.lua");
        for (int i = 0; i < 60 && !screen().contains("hello.lua"); i++) { ticks(1); Thread.sleep(5); }
        type("for i = 1, 3 do");
        key("enter", false);
        type("  print(\"Привет, \" .. i)");
        key("enter", false);
        type("end");
        for (int i = 0; i < 60 && !screen().contains("for i = 1, 3 do"); i++) { ticks(1); Thread.sleep(5); }
        show("редактор");
        expect("подсветка: ключевое слово на экране", screen().contains("for i = 1, 3 do"));
        key("s", true);
        for (int i = 0; i < 40 && !screen().contains("Сохранено"); i++) ticks(1);
        expect("файл сохранён", screen().contains("Сохранено"));
        key("q", true);
        ticks(10);
        expect("файл есть на диске", Files.exists(dir.resolve("code/hello.lua")));
        command("hello");
        show("запуск hello");
        expect("программа напечатала кириллицу", screen().contains("Привет, 3"));
        expect("end после блока сдвинут влево", Files.readString(dir.resolve("code/hello.lua"), java.nio.charset.StandardCharsets.UTF_8).endsWith("\nend"));

        command("ls");
        expect("ls видит файл", screen().contains("hello.lua"));

        command("lua");
        command("2 + 2 * 10");
        command("string.rep('ab', 3)");
        show("lua");
        expect("lua посчитал выражение", screen().contains("22"));
        expect("lua вернул строку", screen().contains("ababab"));
        command("exit()");

        command("cd /");
        command("drives");
        show("drives");
        expect("drives показал ПЗУ", screen().contains("/rom"));

        // Бесконечный цикл: компьютер не зависает, лимит тратится, Ctrl+T прерывает
        Files.writeString(dir.resolve("loop.lua"), "local n = 0\nwhile true do n = n + 1 end\n");
        command("loop");
        long t0 = System.nanoTime();
        ticks(40);
        double sec = (System.nanoTime() - t0) / 1e9;
        long used = pc.opsLastSecond();
        System.out.printf("  команд за последнюю секунду игры: %d (лимит %d/с), реальное время 40 тиков: %.2f с%n",
                used, (long) pc.opsPerTick() * 20, sec);
        // Запас на 2 тика (100 мс) можно истратить разом, поэтому за отдельную секунду — до +10%
        expect("бесконечный цикл ест не больше лимита", used <= (long) pc.opsPerTick() * 20 * 111 / 100);
        pc.terminate();
        ticks(40);
        show("после Ctrl+T");
        expect("программа прервана, оболочка снова ждёт", screen().contains("Прервано"));

        command("this_is_not_a_command");
        show("неизвестная команда");
        expect("неизвестная команда", screen().contains("Нет такой команды"));

        // Ошибка в программе показывает строку
        Files.writeString(dir.resolve("bad.lua"), "local x = nil\nprint(x.y)\n");
        command("bad");
        show("ошибка в программе");
        expect("сообщение об ошибке со строкой", screen().contains("bad.lua:2"));

        // Место на диске кончается
        Files.writeString(dir.resolve("fill.lua"), "local f = fs.open('big.txt', 'w')\nf.write(string.rep('x', 100000))\nf.close()\n");
        command("fill");
        show("переполнение диска");
        expect("ошибка о нехватке места", screen().contains("Недостаточно места"));

        // Программа для OpenComputers (OpenOS): библиотеки, видеокарта, события
        command("clear");
        Files.writeString(dir.resolve("oc.lua"), String.join("\n",
                "local component = require('component')",
                "local event = require('event')",
                "local fs = require('filesystem')",
                "local ser = require('serialization')",
                "local term = require('term')",
                "local gpu = component.gpu",
                "gpu.setForeground(0xFF8800)",
                "gpu.set(1, 10, 'ГПУ:' .. gpu.getDepth())",
                "local t = ser.unserialize(ser.serialize({a = 1, b = 'ж'}))",
                "local id = event.timer(0.05, function() end)",
                "event.push('my_signal', 42)",
                "local _, v = event.pull(1, 'my_signal')",
                "fs.makeDirectory('/oc_dir')",
                "local f = io.open('/oc_dir/x.txt', 'w'); f:write('строка\\n'); f:close()",
                "local line = io.lines('/oc_dir/x.txt')()",
                "print('OC:', t.b, v, line, unicode.len('Ёжик'), fs.exists('/oc_dir/x.txt'))",
                ""), java.nio.charset.StandardCharsets.UTF_8);
        command("oc");
        ticks(20);
        show("программа OpenOS");
        expect("OpenOS-программа отработала", screen().contains("OC:\tж\t42\tстрока\t4\ttrue") || screen().contains("OC: ж 42 строка 4 true")
                || screen().replaceAll("\\s+", " ").contains("OC: ж 42 строка 4 true"));
        expect("gpu.set нарисовал текст", screen().contains("ГПУ:"));
        command("sh");
        expect("вложенная оболочка запустилась", screen().contains("exit, чтобы вернуться"));
        command("exit");
        ticks(10);
        expect("exit во вложенной оболочке не выключает компьютер", pc.state() == Computer.State.RUNNING);

        // ===== Память: настоящий лимит =====
        command("clear");
        Computer.Limits base = pc.limits();
        pc.setLimits(new Computer.Limits(base.opsPerTick(), 4, 2L << 20, base.gpuCellsPerTick(), 20_000, 0, 8));
        Files.writeString(dir.resolve("mem.lua"), String.join("\n",
                "local t = {}",
                "local ok, err = pcall(function() for i = 1, 1e7 do t[i] = {i} end end)",
                "local n = #t",
                "t = nil",
                "collectgarbage()",
                "print('MEM', ok, err, n > 1000, computer.freeMemory() > 1e6)",
                "local ok2, err2 = pcall(string.rep, 'x', 1e8)",
                "print('REP', ok2, err2)",
                ""), java.nio.charset.StandardCharsets.UTF_8);
        command("mem");
        for (int i = 0; i < 400 && !screen().contains("REP"); i++) ticks(1);
        show("память 2 МБ");
        expect("большая таблица упирается в память", screen().contains("Недостаточно памяти") && screen().contains("MEM"));
        expect("после ошибки память освобождается", screen().replaceAll("\\s+", " ").contains("true true"));
        expect("string.rep на 100 МБ не выделяет память", screen().replaceAll("\\s+", " ").contains("REP false"));
        expect("компьютер жив после нехватки памяти", pc.state() == Computer.State.RUNNING);
        pc.setLimits(new Computer.Limits(base.opsPerTick(), 4, 64L << 20, base.gpuCellsPerTick(), 20_000, 0, 8));

        // ===== Процессы на ядрах =====
        command("clear");
        Files.writeString(dir.resolve("worker.lua"), String.join("\n",
                "local from, a, b = require('cores').receive()",
                "local s = 0",
                "for i = a, b do s = s + i end",
                "computer.send(from, s, { done = true, name = 'воркер' })",
                ""), java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("par.lua"), String.join("\n",
                "local cores = require('cores')",
                "local p1 = cores.spawn('worker', 1)",
                "local p2 = cores.spawn('worker')",
                "cores.send(p1, 1, 100000)",
                "cores.send(p2, 100001, 200000)",
                "local total = 0",
                "for i = 1, 2 do local from, s, info = cores.receive(10) total = total + s assert(info.done and info.name == 'воркер') end",
                "local ok = cores.wait(p1, 5)",
                "print('PAR', total, ok, cores.count())",
                "local bad = cores.spawn('nope_missing_file_test') ",
                ""), java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("crashy.lua"), "error('сломался')\n");
        Files.writeString(dir.resolve("crashtest.lua"), String.join("\n",
                "local cores = require('cores')",
                "local p = cores.spawn('crashy')",
                "local ok, err = cores.wait(p, 5)",
                "print('CRASHY', ok, err)",
                "local loop = cores.spawn('loop')",
                "os.sleep(0.2)",
                "print('KILL', computer.kill(loop), #computer.processes())",
                "local ok2, err2 = cores.wait(loop, 5)",
                "print('KILLED', ok2, err2, #computer.processes())",
                ""), java.nio.charset.StandardCharsets.UTF_8);
        command("par");
        for (int i = 0; i < 400 && !screen().contains("Нет такой программы"); i++) ticks(1);
        show("процессы");
        expect("два процесса посчитали сумму", screen().replaceAll("\\s+", " ").contains("PAR 20000100000 true 4"));
        expect("запуск несуществующей программы — ошибка", screen().contains("Нет такой программы"));
        command("crashtest");
        for (int i = 0; i < 400 && !screen().contains("KILLED"); i++) ticks(1);
        show("ошибка и остановка процесса");
        expect("ошибка процесса приходит родителю", screen().contains("CRASHY") && screen().contains("сломался"));
        expect("kill останавливает процесс", screen().replaceAll("\\s+", " ").contains("KILLED false Процесс остановлен 1"));
        expect("главный процесс жив", pc.state() == Computer.State.RUNNING);

        // ===== Видеопамять, data, цена встроенных функций =====
        command("clear");
        Files.writeString(dir.resolve("vram.lua"), String.join("\n",
                "local gpu = require('component').gpu",
                "local buf = gpu.allocateBuffer(20, 3)",
                "gpu.setActiveBuffer(buf)",
                "gpu.set(1, 1, 'ИЗ_БУФЕРА')",
                "gpu.setActiveBuffer(0)",
                "gpu.bitblt(0, 5, 12, 20, 1, buf, 1, 1)",
                "local huge = gpu.allocateBuffer(300, 100)",
                "local d = require('component').data",
                "print('HEX', data.toHex(data.sha256('abc')):sub(1, 16), data.inflate(data.deflate(('ab'):rep(1000))) == ('ab'):rep(1000),",
                "  data.decode64(data.encode64('Привет')) == 'Привет', huge == nil, d ~= nil)",
                ""), java.nio.charset.StandardCharsets.UTF_8);
        command("vram");
        ticks(10);
        show("видеопамять и data");
        expect("bitblt скопировал буфер на экран", screen().contains("ИЗ_БУФЕРА"));
        expect("data: sha256, deflate, base64, лимит видеопамяти",
                screen().replaceAll("\\s+", " ").contains("HEX ba7816bf8f01cfea true true true true"));
        Files.writeString(dir.resolve("cost.lua"), String.join("\n",
                "local function myOps() for _, p in ipairs(computer.processes()) do if p.pid == computer.pid() then return p.ops end end end",
                "local s = ('x'):rep(1000000)",
                "local a = myOps()",
                "for i = 1, 20 do local u = s:upper() end",
                "print('COST', myOps() - a)",
                ""), java.nio.charset.StandardCharsets.UTF_8);
        command("cost");
        for (int i = 0; i < 400 && !screen().contains("COST"); i++) ticks(1);
        show("цена встроенных функций");
        java.util.regex.Matcher cm = java.util.regex.Pattern.compile("COST\\s+(\\d+)").matcher(screen());
        long costOps = cm.find() ? Long.parseLong(cm.group(1)) : 0;
        System.out.printf("  20 раз upper() строки 1 МБ стоят %d команд (без цены было бы ~100)%n", costOps);
        expect("встроенные функции платят за объём", costOps > 1_000_000);

        // ===== Скорость видеокарты и накопителя =====
        command("clear");
        pc.setLimits(new Computer.Limits(base.opsPerTick(), 4, 64L << 20, 2_000, 20_000, 0, 8));
        Files.writeString(dir.resolve("draw.lua"), String.join("\n",
                "local t0 = os.clock()",
                "for i = 1, 600 do term.setCursorPos(1, 1) term.write(('#'):rep(40)) end",
                "print('DRAW', os.clock() - t0 >= 0.25)",
                ""), java.nio.charset.StandardCharsets.UTF_8);
        command("draw");
        for (int i = 0; i < 100 && !screen().contains("DRAW"); i++) ticks(1);
        show("медленная видеокарта");
        expect("2000 клеток за тик: 24000 символов рисуются несколько тиков", screen().replaceAll("\\s+", " ").contains("DRAW true"));
        pc.setLimits(new Computer.Limits(base.opsPerTick(), 4, 64L << 20, base.gpuCellsPerTick(), 20_000, 0, 8));

        Path slowDir = Files.createTempDirectory("minepc_slow");
        Files.write(slowDir.resolve("big.bin"), new byte[256 * 1024]);
        pc.fileSystem().mount("slow", new DirMount(slowDir, 1 << 20, "Дискета").withSpeed(128 * 1024, 256));
        Files.writeString(dir.resolve("io.lua"), String.join("\n",
                "local t0 = os.clock()",
                "local f = fs.open('/slow/big.bin', 'r') local d = f.readAll() f.close()",
                "local t1 = os.clock()",
                "local g = fs.open('/slow/big.bin', 'r') g.close()",
                "os.sleep(0)",
                "print('IO', #d, t1 - t0 >= 0, os.clock() - t0 >= 1.5)",
                ""), java.nio.charset.StandardCharsets.UTF_8);
        command("io");
        for (int i = 0; i < 600 && !screen().contains("IO "); i++) ticks(1);
        show("медленная дискета");
        expect("чтение 256 КБ на 128 КБ/с занимает ~2 секунды", screen().replaceAll("\\s+", " ").contains("IO 262144 true true"));
        pc.fileSystem().unmount("slow");

        // Передача экрана: изменения строк и сжатие
        Terminal big = new Terminal(160, 50);
        byte[] f1 = big.snapshot();
        byte[] p1 = TerminalCodec.encode(null, f1);
        byte[] client = TerminalCodec.apply(null, p1);
        big.setCursor(3, 7);
        big.setTextIndex(4);
        big.write("Привет, большой экран!");
        byte[] f2 = big.snapshot();
        byte[] p2 = TerminalCodec.encode(f1, f2);
        client = TerminalCodec.apply(client, p2);
        System.out.printf("  экран 160×50: кадр %d Б, весь кадр в пакете %d Б, изменение одной строки %d Б%n",
                f2.length, p1.length, p2.length);
        expect("кадр после изменений совпадает", java.util.Arrays.equals(client, f2));
        expect("изменения без основы не применяются", TerminalCodec.apply(null, p2) == null);
        expect("пакет изменений маленький", p2.length < 400);

        command("shutdown");
        ticks(30);
        expect("компьютер выключился", pc.state() == Computer.State.OFF);

        testStats();
        testNet();
        testWifi();
        testAuth();
        testWired();

        System.out.println(failures == 0 ? "ВСЁ ХОРОШО" : "ОШИБОК: " + failures);
        System.exit(failures == 0 ? 0 : 1);
    }

    /** Имена .mine в сетевом тесте (общие для двух компьютеров). */
    static final java.util.Map<String, String> DNS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Два компьютера, соединённые «сетью» прямо в тесте: сообщение, таблица, порт, предел 8 КБ, OpenOS modem. */
    static Computer netComputer(String address, Computer[] box, int other, String otherName) throws Exception {
        Path dir = Files.createTempDirectory("minepc_net");
        Computer c = new Computer(new Computer.Host() {
            @Override public String address() { return address; }
            @Override public long netBytesPerSecond() { return 125_000; }
            @Override public List<Computer.Peer> netPeers() {
                return List.of(new Computer.Peer(box[other].address(), otherName, 1));
            }
            @Override public boolean netSend(String target, int port, Object[] payload, int bytes) {
                if (target != null && target.toLowerCase(java.util.Locale.ROOT).endsWith(".mine")) {
                    target = DNS.get(target.toLowerCase(java.util.Locale.ROOT));
                    if (target == null) return true;
                }
                if (target != null && address.startsWith(target)) {          // себе
                    Computer me = box[other == 0 ? 1 : 0];
                    me.netReceive(address, port, 0, payload, bytes);
                    return true;
                }
                if (target == null || target.equalsIgnoreCase(otherName) || box[other].address().startsWith(target)) {
                    box[other].netReceive(address, port, 1, payload, bytes);
                }
                return true;
            }
            @Override public Object[] dns(String command, String name) {
                String n = name == null ? null : name.toLowerCase(java.util.Locale.ROOT);
                if (n != null && !n.endsWith(".mine")) n = n + ".mine";
                switch (command) {
                    case "register" -> {
                        String owner = DNS.get(n);
                        if (owner != null && !owner.equals(address)) return new Object[]{false, "Имя " + n + " уже занято"};
                        DNS.put(n, address);
                        return new Object[]{true, n};
                    }
                    case "unregister" -> { DNS.remove(n); return new Object[]{true, ""}; }
                    case "lookup" -> { String a = DNS.get(n); return new Object[]{a != null, a == null ? "Имя не занято" : a}; }
                    default -> {
                        java.util.Map<Object, Object> t = new java.util.LinkedHashMap<>();
                        int i = 1;
                        for (var e : DNS.entrySet()) {
                            if (command.equals("names") && !e.getValue().equals(address)) continue;
                            t.put(i++, java.util.Map.of("name", e.getKey(), "address", e.getValue()));
                        }
                        return new Object[]{true, t};
                    }
                }
            }
        });
        c.setInfo(new Computer.Info("Тест", 2, 2800, "Lua", 2048, 2_000_000, "ГП", 1000));
        c.setOpsPerTick(100000);
        c.fileSystem().mount("rom", ComputerRom.mount());
        c.fileSystem().mount("", new DirMount(dir, 64 * 1024, "SSD"));
        c.terminal().resize(50, 16);
        return c;
    }

    static void mouse(int x, int y) throws Exception {
        pc.queueEvent("mouse_click", 1, x, y);
        pc.queueEvent("mouse_up", 1, x, y);
        ticks(4);
    }

    static boolean waitFor(String text, int maxTicks) throws Exception {
        for (int i = 0; i < maxTicks && !screen().contains(text); i++) ticks(1);
        return screen().contains(text);
    }

    static int[] findOnScreen(String text) {
        Terminal t = pc.terminal();
        for (int y = 0; y < t.height(); y++) {
            int i = t.line(y).indexOf(text);
            if (i >= 0) return new int[]{i + 1, y + 1};
        }
        return null;
    }

    /** Wired: установка с дискеты, вход, рабочий стол, Проводник, «Пуск», Терминал в окне. */
    static void testWired() throws Exception {
        System.out.println("── Wired ──");
        Path dir = Files.createTempDirectory("minepc_wired");
        Path disk = Files.createTempDirectory("minepc_wdisk");
        Files.delete(disk);
        ProgramDisks.populate(disk, "wired");
        expect("на дискете файлы Wired", Files.exists(disk.resolve("system/boot.lua")) && Files.exists(disk.resolve("install.lua")));
        Computer c = new Computer(new Computer.Host() {});
        c.setInfo(new Computer.Info("Тест", 4, 2800, "Lua", 4096, 4_000_000, "ГП", 1000));
        c.setOpsPerTick(400000);
        c.fileSystem().mount("rom", ComputerRom.mount());
        c.fileSystem().mount("", new DirMount(dir, 2 * 1024 * 1024, "SSD"));
        c.fileSystem().mount("disk", new DirMount(disk, 512 * 1024, "Дискета"));
        c.terminal().setLimit(160, 50);
        c.terminal().resize(160, 50);
        pc = c;
        c.turnOn();
        waitFor("/>", 300);
        command("/disk/install");
        waitFor("Enter — продолжить", 100);
        key("enter", false);
        waitFor("Имя", 60);
        command("admin");
        command("secret1");
        command("secret1");
        boolean done = waitFor("Wired установлена", 400);
        expect("установка: файлы в /wired и /startup.lua", done && Files.exists(dir.resolve("wired/boot.lua")) && Files.exists(dir.resolve("startup.lua")));
        boolean login = waitFor("Пользователь", 600);
        show("Wired: вход");
        expect("после перезагрузки — вход Wired", login && screen().contains("WIRED"));
        type("admin");
        key("enter", false);
        type("secret1");
        key("enter", false);
        boolean desk = waitFor("Проводник", 200);
        expect("рабочий стол: значки и «Пуск»", desk && screen().contains("WIRED") && screen().contains("Терминал"));
        int[] icon = findOnScreen("Проводник");
        if (icon != null) {
            mouse(icon[0] + 2, icon[1] - 2);
            mouse(icon[0] + 2, icon[1] - 2);
        }
        expect("Проводник открылся", waitFor("Проводник — Домой", 100));
        mouse(3, 49);
        expect("«Пуск»: программы и папки", waitFor("ПРОГРАММЫ", 60) && screen().contains("ПАПКИ"));
        int[] term = findOnScreen("> Терминал");
        if (term != null) mouse(term[0] + 3, term[1]);
        boolean shell = waitFor("/home>", 100);
        if (shell) {
            type("ls /");
            key("enter", false);
            ticks(20);
        }
        show("Wired: Проводник и Терминал");
        expect("Терминал в окне: команды работают", shell && screen().contains("wired"));
        c.shutdown();
    }

    /** Пользователи, вход по сети (sshd/connect), sudo, журнал, блокировка после 5 ошибок. */
    static void testAuth() throws Exception {
        System.out.println("── пользователи и вход по сети ──");
        // Блокировка и хранение — прямо на Accounts, со своим временем
        Accounts acc = new Accounts();
        expect("первый пользователь добавлен", acc.add("steve", "pass1", false) == null && acc.isAdmin("steve"));
        int[] alerts = {0};
        long t = 0;
        Accounts.Check r = null;
        for (int i = 0; i < 5; i++) {
            r = acc.login("steve", "bad", "10.6.0.3", "t", t, f -> alerts[0]++);
            t += 7000;
        }
        expect("5 ошибок — адрес закрыт и пришёл сигнал", r != null && r.reason().equals("locked") && alerts[0] == 1);
        expect("закрыт и для верного пароля", acc.login("steve", "pass1", "10.6.0.3", "t", t, null).reason().equals("locked"));
        expect("с другого адреса пускает", acc.login("steve", "pass1", "10.0.0.5", "t", t, null).ok());
        expect("через 10 минут снова пускает", acc.login("steve", "pass1", "10.6.0.3", "t", t + Accounts.LOCK_MS, null).ok());
        Accounts acc2 = new Accounts();
        acc2.load(acc.save());
        expect("пользователи и журнал сохраняются", acc2.matches("steve", "pass1") && !acc2.matches("steve", "bad")
                && acc2.journal(50).stream().anyMatch(l -> l.contains("закрыт")));
        expect("пароль хранится отпечатком", !acc.save().contains("pass1"));

        Computer[] box = new Computer[2];
        box[0] = netComputer("aaaaaaaa-1111-4000-8000-000000000000", box, 1, "beta");
        box[1] = netComputer("bbbbbbbb-2222-4000-8000-000000000000", box, 0, "alpha");
        for (Computer c : box) c.turnOn();
        for (int i = 0; i < 200; i++) {
            for (Computer c : box) c.tick();
            Thread.sleep(2);
            pc = box[1];
            if (screen().contains("/>")) break;
        }
        // beta: пользователь и sshd
        pc = box[1];
        command("users add admin");
        command("secret1");
        command("secret1");
        command("sshd");
        for (int i = 0; i < 20; i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        show("beta: users add, sshd");
        expect("users add: администратор добавлен", flat().contains("добавлен (администратор)"));
        expect("sshd запустился", flat().contains("Вход по сети работает"));
        // alpha: неверный пароль, потом верный
        pc = box[0];
        for (int i = 0; i < 200 && !screen().contains("/>"); i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        command("connect beta admin");
        command("wrong");
        for (int i = 0; i < 60 && !flat().contains("из 5"); i++) { for (Computer c : box) c.tick(); Thread.sleep(3); }
        expect("connect: неверный пароль (1 из 5)", flat().contains("Неверный пароль (1 из 5)"));
        Thread.sleep(2200);                 // следующая попытка — не раньше чем через 2 с
        command("clear");
        command("connect beta admin");
        command("secret1");
        for (int i = 0; i < 60 && !flat().contains("Вы вошли"); i++) { for (Computer c : box) c.tick(); Thread.sleep(3); }
        expect("connect: вошли", flat().contains("Вы вошли на") && flat().contains("как admin"));
        command("users");
        for (int i = 0; i < 60 && !flat().contains("Пользователи:"); i++) { for (Computer c : box) c.tick(); Thread.sleep(3); }
        expect("команда выполнилась там, вывод здесь", flat().contains("Пользователи:") && flat().contains("admin (администратор)"));
        command("sudo journal");
        for (int i = 0; i < 60 && !flat().contains("Пароль admin"); i++) { for (Computer c : box) c.tick(); Thread.sleep(3); }
        command("secret1");
        for (int i = 0; i < 60 && !flat().contains("вход admin с"); i++) { for (Computer c : box) c.tick(); Thread.sleep(3); }
        show("alpha: connect, users, sudo journal");
        expect("sudo по сети: пароль спросили здесь, журнал показали", flat().contains("вход admin с aaaaaaaa — ok"));
        expect("в журнале — неверный пароль", flat().contains("неверный пароль admin"));
        command("exit");
        for (int i = 0; i < 20; i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        expect("exit закрывает сеанс", flat().contains("Сеанс закрыт"));
        // beta: выход из своего сеанса — снова вход
        pc = box[1];
        command("clear");
        command("exit");
        for (int i = 0; i < 40 && !flat().contains("Пользователь:"); i++) { for (Computer c : box) c.tick(); Thread.sleep(3); }
        show("beta: exit");
        expect("exit на компьютере с пользователями — к входу, а не выключение", flat().contains("Пользователь:") && box[1].state() == Computer.State.RUNNING);
        for (Computer c : box) c.shutdown();
    }

    static void testNet() throws Exception {
        System.out.println("── сеть ──");
        Computer[] box = new Computer[2];
        box[0] = netComputer("aaaaaaaa-1111-4000-8000-000000000000", box, 1, "beta");
        box[1] = netComputer("bbbbbbbb-2222-4000-8000-000000000000", box, 0, "alpha");
        Path dirB = Files.createTempDirectory("minepc_netb");
        box[1].fileSystem().mount("", new DirMount(dirB, 64 * 1024, "SSD"));
        Files.writeString(dirB.resolve("listen.lua"), String.join("\n",
                "net.open(7)",
                "local _, from, port, hops, msg, t = os.pullEvent('net_message')",
                "print('GOT', from:sub(1, 8), port, hops, msg, t.x)",
                "local _, _, _, _, m2 = os.pullEvent('net_message')",
                "print('SECOND', m2)",
                "local c = require and nil",
                ""));
        for (Computer c : box) c.turnOn();
        Computer saved = pc;
        for (int i = 0; i < 200; i++) {
            for (Computer c : box) c.tick();
            Thread.sleep(2);
            pc = box[1];
            if (screen().contains("/>")) break;
        }
        pc = box[1];
        command("listen");
        pc = box[0];
        for (int i = 0; i < 200 && !screen().contains("/>"); i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        command("lua");
        command("print('SENT', net.send('beta', 7, 'привет', {x = 42}))");
        command("print('BIG', pcall(net.send, 'beta', 7, ('x'):rep(9000)))");
        command("net.send('beta', 8, 'мимо')");
        command("net.broadcast(7, 'всем')");
        command("print('PEERS', #net.peers(), net.peers()[1].name, net.speed())");
        for (int i = 0; i < 30; i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        show("отправитель");
        expect("send вернул true", screen().contains("SENT") && screen().contains("true"));
        expect("больше 8 КБ — ошибка", screen().contains("BIG") && screen().contains("8 КБ"));
        expect("peers видит соседа", screen().contains("PEERS") && screen().contains("beta"));
        pc = box[1];
        for (int i = 0; i < 20; i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        show("получатель");
        expect("сообщение с таблицей дошло", screen().contains("GOT") && screen().contains("aaaaaaaa") && screen().contains("привет") && screen().contains("42"));
        expect("закрытый порт не слышен, широковещательное дошло", screen().contains("SECOND") && screen().contains("всем"));
        expect("принято больше нуля", box[1].netReceived() > 0 && box[0].netSent() > 0);

        // Эхо-запрос: отвечает сама сетевая карта адресата, программа там не нужна
        pc = box[0];
        command("local id = net.ping('beta'); local e = {os.pullEvent('net_pong')}; print('PONG', e[3] == id, e[2]:sub(1, 8), e[5])");
        for (int i = 0; i < 20; i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        show("net.ping");
        expect("net.ping: пришёл ответ с тем же номером", screen().contains("PONG") && screen().contains("true") && screen().contains("bbbbbbbb"));
        pc = box[1];
        command("ping alpha 2");
        for (int i = 0; i < 80 && !screen().contains("дошли"); i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        command("ping nobody 1");
        for (int i = 0; i < 120 && !screen().contains("0 из 1"); i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        show("ping");
        expect("ping: 2 из 2 дошли", screen().contains("2 из 2 дошли") && screen().contains("alpha"));
        expect("ping: нет ответа от несуществующего", screen().contains("нет ответа от nobody") && screen().contains("0 из 1"));

        // Шифрование
        pc = box[0];
        command("local e = data.encrypt('секретное письмо', 'ключ'); print('ENC', #e, data.decrypt(e, 'ключ'), (data.decrypt(e, 'чужой')))");
        command("local iv = ('\\0'):rep(16); local e = data.encrypt('abc', '0123456789abcdef', iv); print('IV', #e, data.decrypt(e, '0123456789abcdef', iv))");
        for (int i = 0; i < 20; i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        show("data.encrypt");
        expect("encrypt/decrypt: тот же текст обратно, чужой ключ — nil", screen().contains("ENC") && screen().contains("секретное письмо") && screen().contains("nil"));
        expect("encrypt с iv: 16 байт, без iv в начале", screen().contains("IV      16") && screen().contains("abc"));

        // Передача файла: send → recv, бинарный файл 20 КБ
        command("local f = fs.open('big.bin', 'wb'); local t = {}; for i = 1, 20000 do t[i] = string.char((i * 7) % 256) end; f.write(table.concat(t)); f.close()");
        command("exit()");
        for (int i = 0; i < 20; i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        pc = box[1];
        command("recv");
        for (int i = 0; i < 20; i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        pc = box[0];
        command("send beta big.bin");
        for (int i = 0; i < 400 && !screen().contains("Готово") && !screen().contains("Нет"); i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        show("send");
        pc = box[1];
        show("recv");
        byte[] got = Files.exists(dirB.resolve("big.bin")) ? Files.readAllBytes(dirB.resolve("big.bin")) : new byte[0];
        boolean same = got.length == 20000;
        for (int i = 0; same && i < 20000; i++) same = got[i] == (byte) (((i + 1) * 7) % 256);
        expect("send/recv: файл дошёл байт в байт", same);
        pc = box[0];
        expect("send: сообщил о готовности", screen().contains("Готово: 20000 байт"));

        // Сайт, запущенный из программы (как «Опубликовать» в Сайтостроителе): вторая страница по ссылке без .page
        Files.createDirectories(dirB.resolve("www"));
        Files.writeString(dirB.resolve("www/index.page"), "# Главная\n[Вторая](two)\n", java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(dirB.resolve("www/two.page"), "# Вторая страница\n[Назад](/)\n", java.nio.charset.StandardCharsets.UTF_8);
        pc = box[1];
        box[1].terminate();
        for (int i = 0; i < 20; i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        command("lua");
        command("print('SPAWN', computer.spawn('/rom/programs/httpd.lua', 'serve'))");
        for (int i = 0; i < 20; i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        pc = box[0];
        command("lua");
        command("local http = require('http'); local b, code = http.get('beta/' .. http.resolve('beta/', 'two'):match('/(.*)$')); print('SITE', code, b)");
        for (int i = 0; i < 300 && !screen().contains("SITE"); i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        show("сайт из программы");
        expect("httpd, запущенный computer.spawn, отдаёт вторую страницу по ссылке «two»", screen().contains("SITE") && screen().contains("Вторая страница"));

        // Хостинг: beta держит сайты, alpha загружает свой сайт и открывает его по имени .mine
        pc = box[1];
        command("os.run({}, '/rom/programs/hostd.lua', 'add', 'vasya', 'pw', '64')");
        command("print('HOSTD', computer.spawn('/rom/programs/hostd.lua', 'serve'))");
        for (int i = 0; i < 20; i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        pc = box[0];
        command("fs.makeDir('/www') local f = fs.open('/www/index.page', 'w') f.write('# Сайт на хостинге\\n[Ещё](more)') f.close()"
                + " f = fs.open('/www/more.page', 'w') f.write('# Ещё страница') f.close()");
        command("local h = require('hosting'); local s = h.connect('beta', 'vasya', 'pw'); print('DEPLOY', s:upload('/www', 'test'))");
        for (int i = 0; i < 400 && !screen().contains("DEPLOY"); i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        command("local h = require('hosting'); print('BADPW', h.connect('beta', 'vasya', 'нет'):info())");
        for (int i = 0; i < 300 && !screen().contains("BADPW"); i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        command("local b, code = require('http').get('test.mine/more'); print('VHOST', code, b)");
        for (int i = 0; i < 300 && !screen().contains("VHOST"); i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        command("local b = require('http').get('beta/hosting.lua', {login = 'vasya', password = 'pw'}); print('PANEL', b:match('Посещения: [^\\n]*'))");
        for (int i = 0; i < 300 && !screen().contains("PANEL"); i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        show("хостинг");
        expect("панель хостинга: вход и посещения сайта", screen().contains("PANEL") && screen().contains("Посещения: сегодня"));
        expect("deploy: сайт загружен на хостинг как test.mine", screen().contains("DEPLOY") && screen().contains("test.mine"));
        expect("хостинг: неверный пароль — отказ", screen().contains("BADPW") && screen().contains("Неверный"));
        expect("хостинг: test.mine открывается с сервера (своя папка сайта)", screen().contains("VHOST") && screen().contains("Ещё страница"));
        expect("на сервере файлы сайта в /sites/test.mine", Files.exists(dirB.resolve("sites/test.mine/more.page")));
        testSandbox(box, dirB.resolve("sites/test.mine"));

        // Почта на домене хостинга: письмо на info@test.mine ложится на сервер, клиент забирает его по сети
        pc = box[1];
        command("print('MAILD', computer.spawn('/rom/programs/maild.lua', 'serve'))");
        for (int i = 0; i < 20; i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        pc = box[0];
        command("net.open(41000) net.send('test.mine', 25, 'MAIL', 7, 41000, 'info', 'alpha@x', 'Привет хостингу', 'Текст письма', 'test.mine')"
                + " local e repeat e = {os.pullEvent('net_message')} until e[5] == 'MAILOK' print('MAILOK', e[7])");
        for (int i = 0; i < 300 && !screen().contains("MAILOK"); i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        command("local s = require('hosting').connect('beta', 'vasya', 'pw'); local l = s:call('mail_list', 'test.mine', 'info');"
                + " print('MBOX', type(l) == 'table' and #l, l and l[1] and l[1].subject, s:call('mail_get', 'test.mine', 'info', l[1].id))");
        for (int i = 0; i < 400 && !screen().contains("MBOX"); i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        show("почта на хостинге");
        expect("письмо на домен хостинга лежит в /hosting/mail/test.mine/info", Files.isDirectory(dirB.resolve("hosting/mail/test.mine/info")));
        expect("клиент видит письмо в ящике хостинга и читает его", screen().contains("MBOX") && screen().contains("Привет хостингу") && screen().contains("Текст письма"));
        testPauseAndTerm(box);
        pc = saved;
    }

    /** Срок работы и приостановка: владелец (box[1]) приостанавливает сайт или ставит прошедший срок — посетители
     *  получают заглушку с кодом 423; загрузка в приостановленный сайт проходит, но клиент видит, что он приостановлен. */
    static void testPauseAndTerm(Computer[] box) throws Exception {
        pc = box[1];
        command("os.run({}, '/rom/programs/hostd.lua', 'pause', 'test.mine', 'ремонт')");
        for (int i = 0; i < 20; i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        pc = box[0];
        String paused = probe(box, "PAUSED");
        expect("приостановленный сайт: заглушка с кодом 423 и причиной", paused.startsWith("423") && paused.contains("stub") && paused.contains("reason"));
        command("local _, _, _, st = require('hosting').connect('beta', 'vasya', 'pw'):upload('/www', 'test') print('UPST' .. '=' .. tostring(st and st.state))");
        for (int i = 0; i < 400 && !screen().contains("UPST="); i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        expect("загрузка в приостановленный сайт проходит, клиент видит состояние", screen().contains("UPST=paused"));
        pc = box[1];
        command("os.run({}, '/rom/programs/hostd.lua', 'resume', 'test.mine')");
        command("os.run({}, '/rom/programs/hostd.lua', 'until', 'vasya', '01.01.2020')");
        for (int i = 0; i < 20; i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        pc = box[0];
        String expired = probe(box, "EXPIRED");
        expect("срок вышел: заглушка «срок работы закончился»", expired.startsWith("423") && expired.contains("ended"));
        pc = box[1];
        command("os.run({}, '/rom/programs/hostd.lua', 'until', 'vasya', '-')");
        for (int i = 0; i < 20; i++) { for (Computer c : box) c.tick(); Thread.sleep(2); }
        pc = box[0];
        String back = probe(box, "BACK");
        show("срок работы и приостановка");
        expect("без срока и без паузы сайт снова работает", back.startsWith("200") && back.contains("page"));
    }

    /** Открыть test.mine/more и коротко описать ответ: код и что в нём (заглушка, причина, «закончился», сама страница). */
    static String probe(Computer[] box, String tag) throws Exception {
        command("local b, c = require('http').get('test.mine/more', nil, nil, 10) b = tostring(b) print('" + tag + "' .. '=' .. tostring(c)"
                + " .. (b:find('временно не работает') and ' stub' or '') .. (b:find('ремонт') and ' reason' or '')"
                + " .. (b:find('закончился') and ' ended' or '') .. (b:find('Ещё страница') and ' page' or ''))");
        long end = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < end && !screen().contains(tag + "=")) { for (Computer c : box) c.tick(); Thread.sleep(5); }
        for (String l : screen().split("\n")) {
            int i = l.indexOf(tag + "=");
            if (i >= 0) { System.out.println("  " + l.substring(i)); return l.substring(i + tag.length() + 1).trim(); }
        }
        System.out.println("  " + tag + ": нет ответа");
        return "";
    }

    /** Запрос с клиента (box[0], в lua) к сайту; ждём ответ по реальному времени. Возвращает «код|тело» одной строкой. */
    static String fetch(Computer[] box, String tag, String url, int waitMs) throws Exception {
        command("local b, c = require('http').get('" + url + "', nil, nil, 10) print('" + tag + "=' .. tostring(c) .. '|'"
                + " .. (tostring(b):gsub('%s+', ' ')):sub(1, 90))");
        long end = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < end && !screen().contains(tag + "=")) { for (Computer c : box) c.tick(); Thread.sleep(5); }
        for (String l : screen().split("\n")) {
            int i = l.indexOf(tag + "=");
            if (i >= 0) { System.out.println("  " + l.substring(i)); return l.substring(i + tag.length() + 1); }
        }
        System.out.println("  " + tag + ": нет ответа");
        return "";
    }

    /** Песочница хостинга: страницы клиента в /sites/<имя> не выходят из своей папки и не вредят серверу. */
    static void testSandbox(Computer[] box, Path site) throws Exception {
        Files.writeString(site.resolve("steal.lua"), "return 'S' .. tostring(fs.exists('/etc')) .. tostring(fs.exists('../../www/hosting.lua')) .. tostring(fs.exists('/sites'))");
        Files.writeString(site.resolve("off.lua"), "os.shutdown() return 'alive'");
        Files.writeString(site.resolve("loop.lua"), "while true do end");
        Files.writeString(site.resolve("book.lua"), "local f = fs.open('/book.txt', 'a') f.write(request.params.msg .. ';') f.close()"
                + " f = fs.open('/book.txt', 'r') local t = f.readAll() f.close() return 'B' .. t");
        Files.writeString(site.resolve("port.lua"), "return 'P' .. tostring(pcall(net.open, 80)) .. tostring(pcall(net.close, 80))"
                + " .. tostring(pcall(net.send, 'x', 22, 'hi')) .. tostring(pcall(net.open, 41234))");
        Files.writeString(site.resolve("tbl.lua"), "return {1}");
        Files.writeString(site.resolve("cp.lua"), "return 'C' .. tostring(pcall(fs.copy, '/../../www/hosting.lua', '/x.lua')) .. tostring(fs.exists('/x.lua'))");
        Files.writeString(site.resolve("fetch.lua"), "local b = require('http').get('test.mine/more') return 'F' .. tostring(b)");
        // чужой запрос на порт 80 сервера (его же и шлёт страница) не должен дойти до страницы
        Files.writeString(site.resolve("spy.lua"), "net.open(41500) net.send(net.address(), 80, 'GET', 1, 41999, '/more', {}, 'test.mine')"
                + " local t = os.startTimer(1.5) local e"
                + " repeat e = {os.pullEvent()} until e[1] == 'timer' or e[1] == 'net_message' return 'Y' .. e[1]");

        String r = fetch(box, "SBX1", "test.mine/steal.lua", 6000);
        expect("песочница: страница не видит /etc, /www и /sites сервера", r.startsWith("200|Sfalsefalsefalse"));
        r = fetch(box, "SBX2", "test.mine/off.lua", 6000);
        expect("песочница: os.shutdown из страницы — ошибка 500 «Недоступно на хостинге»", r.startsWith("500|") && r.contains("Недоступно"));
        expect("песочница: сервер не выключился", box[1].state() == Computer.State.RUNNING);
        r = fetch(box, "SBX3", "test.mine/loop.lua", 9000);
        expect("песочница: бесконечный цикл — 504", r.startsWith("504|"));
        r = fetch(box, "SBX4", "test.mine/more", 6000);
        long end;
        expect("песочница: после зависшей страницы сайт отвечает", r.startsWith("200|") && r.contains("Ещё"));
        command("local b, c = require('http').get('test.mine/book.lua', {msg = 'раз'}, nil, 10) print('SBX5=' .. tostring(c))");
        end = System.currentTimeMillis() + 6000;
        while (System.currentTimeMillis() < end && !screen().contains("SBX5=")) { for (Computer c : box) c.tick(); Thread.sleep(5); }
        command("local b, c = require('http').get('test.mine/book.lua', {msg = 'два'}, nil, 10) print('SBX6=' .. tostring(c) .. '|' .. tostring(b))");
        end = System.currentTimeMillis() + 6000;
        while (System.currentTimeMillis() < end && !screen().contains("SBX6=")) { for (Computer c : box) c.tick(); Thread.sleep(5); }
        show("гостевая книга");
        expect("песочница: гостевая книга пишет в свой файл", screen().contains("два;") && Files.exists(site.resolve("book.txt")));
        r = fetch(box, "SBX7", "test.mine/port.lua", 6000);
        expect("песочница: порты сервера недоступны, свои 40000–49999 — да", r.startsWith("200|Pfalsefalsefalsetrue"));
        r = fetch(box, "SBX8", "test.mine/tbl.lua", 6000);
        expect("песочница: страница вернула таблицу — 200, сайт жив", r.startsWith("200|"));
        r = fetch(box, "SBXB", "test.mine/cp.lua", 6000);
        expect("песочница: чужой файл не скопировать в свою папку", r.startsWith("200|Cfalsefalse"));
        r = fetch(box, "SBX9", "test.mine/fetch.lua", 6000);
        expect("песочница: страница открывает другой сайт", r.startsWith("200|F") && r.contains("Ещё"));
        r = fetch(box, "SBXA", "test.mine/spy.lua", 6000);
        expect("песочница: страница не слышит чужие сетевые сообщения", r.startsWith("200|Ytimer"));
    }

    /** Wi-Fi: программа wifi со «своим» адаптером: поиск, подключение, неверный пароль. */
    static void testWifi() throws Exception {
        System.out.println("── Wi-Fi ──");
        String[] chosen = {"", ""};
        String[] switchState = {null, null};
        Computer c = new Computer(new Computer.Host() {
            @Override public long netBytesPerSecond() { return chosen[0].equals("Дом") && chosen[1].equals("12345678") ? 37_500 : 0; }
            @Override public Computer.WifiStatus wifiStatus() {
                boolean ok = chosen[0].equals("Дом") && chosen[1].equals("12345678");
                String err = chosen[0].isEmpty() || ok ? "" : chosen[0].equals("Дом") ? "неверный пароль" : "сеть не найдена";
                return new Computer.WifiStatus("802.11n", chosen[0], ok, ok ? 82 : 0, ok ? 300 : 0, err);
            }
            @Override public List<Computer.WifiNet> wifiScan() {
                return List.of(new Computer.WifiNet("Дом", 82, true, 300), new Computer.WifiNet("Кафе", 30, false, 75));
            }
            @Override public boolean wifiConnect(String ssid, String password) { chosen[0] = ssid; chosen[1] = password; return true; }
            @Override public void wifiDisconnect() { chosen[0] = ""; chosen[1] = ""; }
            @Override public List<java.util.Map<String, Object>> switchList() {
                return List.of(new java.util.LinkedHashMap<>(java.util.Map.of("id", "abcd1234", "kind", "desk", "ports", 2)));
            }
            @Override public List<java.util.Map<String, Object>> switchPorts(String id) {
                if (!id.equals("abcd1234")) return null;
                java.util.Map<String, Object> p1 = new java.util.LinkedHashMap<>(java.util.Map.of("port", 2, "side", "front", "n", 3,
                        "link", true, "device", "office-1", "enabled", switchState[0] == null, "vlan", switchState[1] == null ? "" : switchState[1]));
                return List.of(p1);
            }
            @Override public String switchSet(String id, int port, boolean enabled, int vlan) {
                if (!id.equals("abcd1234")) return "Нет такого коммутатора";
                switchState[0] = enabled ? null : "off";
                if (vlan >= 0) switchState[1] = vlan == 0 ? null : "v" + vlan;
                return null;
            }
        });
        c.setInfo(new Computer.Info("Тест", 2, 2800, "Lua", 2048, 2_000_000, "ГП", 1000));
        c.setOpsPerTick(100000);
        c.fileSystem().mount("rom", ComputerRom.mount());
        c.fileSystem().mount("", new DirMount(Files.createTempDirectory("minepc_wifi"), 64 * 1024, "SSD"));
        c.terminal().resize(50, 20);
        Computer saved = pc;
        pc = c;
        c.turnOn();
        for (int i = 0; i < 200 && !screen().contains("/>"); i++) { c.tick(); Thread.sleep(2); }
        command("wifi scan");
        command("wifi connect Дом 111");
        for (int i = 0; i < 100 && !screen().contains("Не удалось"); i++) { c.tick(); Thread.sleep(2); }
        command("wifi connect Дом 12345678");
        for (int i = 0; i < 100 && !screen().contains("Подключено"); i++) { c.tick(); Thread.sleep(2); }
        command("wifi");
        for (int i = 0; i < 20; i++) { c.tick(); Thread.sleep(2); }
        show("wifi");
        expect("wifi scan: обе сети, пароль помечен", screen().contains("Дом") && screen().contains("Кафе") && screen().contains("[пароль]"));
        expect("wifi connect: неверный пароль", screen().contains("неверный пароль"));
        expect("wifi connect: подключено", screen().contains("Подключено: сигнал 82%"));
        expect("wifi: состояние", screen().contains("Скорость: 300"));
        command("clear");
        command("switch");
        command("switch abcd1234");
        command("switch abcd1234 2 off");
        command("switch abcd1234 2 red");
        command("switch abcd1234");
        command("switch nope");
        for (int i = 0; i < 30; i++) { c.tick(); Thread.sleep(2); }
        show("switch");
        expect("switch: список и порты", screen().contains("abcd1234") && screen().contains("спереди 3") && screen().contains("office-1"));
        expect("switch: выключен и VLAN red (15)", "off".equals(switchState[0]) && "v15".equals(switchState[1]) && screen().contains("[v15]"));
        expect("switch: неизвестный — ошибка", screen().contains("Нет такого коммутатора"));
        pc = saved;
    }

    /** computer.stats(): нагрузка ядра от занятого процесса, запись на диск, температура. */
    static void testStats() throws Exception {
        System.out.println("── нагрузка ──");
        pc.setThermal(new Computer.Thermal(95, 60, 0, 0, false, 250, 400, true));
        pc.setLimits(new Computer.Limits(100000, 2, 64L << 20, 1_000_000, 200_000, 0, 16));
        pc.shutdown();
        for (String m : pc.fileSystem().mounts().keySet()) pc.fileSystem().unmount(m);
        pc.fileSystem().mount("rom", ComputerRom.mount());
        root = Files.createTempDirectory("minepc_stats");
        pc.fileSystem().mount("", new DirMount(root, 64 * 1024, "SSD"));
        pc.terminal().resize(50, 16);
        pc.turnOn();
        for (int i = 0; i < 300 && !screen().contains("/>"); i++) ticks(1);
        java.nio.file.Files.writeString(root.resolve("busy.lua"), "while true do end\n");
        java.nio.file.Files.writeString(root.resolve("st.lua"), String.join("\n",
                "local pid = computer.spawn('/busy.lua')",
                "local f = fs.open('/w.bin', 'wb') f.write(('x'):rep(10000)) f.close()",
                "sleep(3)",
                "local s = computer.stats()",
                "local busy",
                "for _, p in ipairs(s.processes) do if p.path:find('busy') then busy = p end end",
                "local maxCore = 0",
                "for _, v in ipairs(s.cores) do maxCore = math.max(maxCore, v) end",
                "print('ST', math.floor(maxCore), busy and math.floor(busy.cpu) or -1, s.disks[1].writeTotal >= 10000, s.cpuTemp > 30, s.fan > 0, s.power > 0)",
                "computer.kill(pid)",
                ""), java.nio.charset.StandardCharsets.UTF_8);
        command("st");
        for (int i = 0; i < 200 && !screen().contains("ST "); i++) ticks(1);
        show("computer.stats");
        java.util.regex.Matcher mm = java.util.regex.Pattern.compile("ST\\s+(\\d+)\\s+(-?\\d+)\\s+(\\w+)\\s+(\\w+)\\s+(\\w+)\\s+(\\w+)").matcher(screen());
        boolean found = mm.find();
        expect("занятое ядро загружено почти на 100%", found && Integer.parseInt(mm.group(1)) >= 80);
        expect("процесс виден со своей нагрузкой", found && Integer.parseInt(mm.group(2)) >= 10);
        expect("запись на диск посчитана", found && mm.group(3).equals("true"));
        expect("температура выросла, вентилятор крутится, питание есть", found && mm.group(4).equals("true") && mm.group(5).equals("true") && mm.group(6).equals("true"));

        // Живая частота: холодный процессор в бусте, под нагрузкой на слабом кулере — сбрасывает частоту
        pc.setThermal(new Computer.Thermal(200, 60, 0, 0, false, 250, 400, true, 0.2));
        for (int i = 0; i < 160; i++) ticks(1);
        double idle = pc.clockFactor();
        command("clear");
        command("lua");
        command("P = computer.spawn('/busy.lua')");
        for (int i = 0; i < 300; i++) ticks(1);
        double hot = pc.clockFactor();
        command("print('CLK', computer.stats().clock) computer.kill(P)");
        command("exit()");
        show("частота");
        System.out.println("  частота: в простое ×" + idle + ", под нагрузкой ×" + hot);
        expect("в простое буст, под нагрузкой на слабом кулере частота ниже базовой", idle > 1.05 && hot < 1 && hot > 0.3);
        pc.setThermal(new Computer.Thermal(95, 60, 0, 0, false, 250, 400, true));
        for (int i = 0; i < 100; i++) ticks(1);

        // top: рисует ядра и процессы, выходит по q
        command("clear");
        command("top");
        for (int i = 0; i < 60 && !screen().contains("PID"); i++) ticks(1);
        show("top");
        expect("top показывает ядра, память и процессы", screen().contains("Пам") && screen().contains("PID") && screen().contains("bios.lua"));
        pc.queueEvent("char", "q");
        for (int i = 0; i < 30 && !screen().contains("/>"); i++) ticks(1);
        expect("top закрывается по q", screen().contains("/>"));

        // event.pull(0) как в OpenComputers: отдаёт уже пришедшие события и не ждёт такта
        command("clear");
        command("lua");
        command("E = require('event') os.queueEvent('aa', 1) os.queueEvent('bb', 2) T = computer.uptime() X, Y, Z = E.pull(0), E.pull(0), E.pull(0) D = computer.uptime() - T");
        command("print('PULL0', X, Y, Z, D < 0.2, computer.pending())");
        for (int i = 0; i < 20; i++) ticks(1);
        command("exit()");
        show("event.pull(0)");
        expect("event.pull(0) забирает очередь без ожидания", screen().replaceAll("\\s+", " ").contains("PULL0 aa bb nil true 0"));

        // Настоящее время: os.sleep(0.01) спит 10 мс, а не тик (50 мс); при паузе игры часы стоят
        command("clear");
        command("lua");
        // Здесь тикаем как настоящий сервер (раз в 50 мс): быстрые тики теста подгоняют часы вперёд
        realLine("T0 = computer.uptime() for i = 1, 40 do os.sleep(0.01) end SL = computer.uptime() - T0", 40);
        realLine("print('SLEEP40', SL > 0.35 and SL < 0.9, SL)", 10);
        realLine("U0 = computer.uptime()", 10);
        Thread.sleep(1500);                                                   // «пауза»: сервер не тикает 1,5 с
        realLine("print('PAUSE', computer.uptime() - U0 < 0.9, computer.uptime() - U0)", 10);
        command("exit()");
        show("время");
        String flat = screen().replaceAll("\\s+", " ");
        expect("os.sleep(0.01) ×40 — примерно 0,4 с, а не 2 с", flat.contains("SLEEP40 true"));
        expect("пока игра на паузе, время компьютера стоит", flat.contains("PAUSE true"));

        // Кадровый буфер: столбец стены с текстурой, спрайт за стеной не виден, вывод полублоками
        command("clear");
        command("lua");
        command("CV = component.gpu.canvas(8, 8) TX = component.gpu.newTexture(2, 2, {0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF})");
        command("CV:clear(0x101010) CV:column(1, 0, 8, TX, 0, 0, 2, 1, 5) SP = component.gpu.newTexture(1, 1, {0xFFFF00})");
        command("CV:sprite(SP, 0, 0, 3, 3, 1, 9) CV:span(7, 4, 8, TX, 0, 0, 4, 0)");
        command("print('CANVAS', CV:get(1, 0), CV:get(1, 5), CV:get(0, 0), CV:get(1, 1) == 0xFF0000, CV:get(5, 7), CV:present(1, 1))");
        command("exit()");
        show("кадровый буфер");
        String cvs = screen().replaceAll("\\s+", " ");
        // (1,0) красный, (1,5) синий; спрайт в (0,0) жёлтый, а в столбце 1 закрыт стеной (глубина 5 < 9)
        expect("кадровый буфер: стена с текстурой, спрайт за стеной скрыт",
                cvs.contains("CANVAS 16711680 255 16776960 true"));
        expect("кадровый буфер: вывод полублоками", pc.terminal().line(0).startsWith("\u2580\u2580"));

        // Звук: пищалка и звуковой чип отправляют команды, синтезатор у игрока собирает звук
        command("clear");
        AUDIO.clear();
        command("lua");
        command("print('BEEP', computer.beep(440, 0.3), sound.channels(), sound.note('A4'))");
        command("exit()");
        show("пищалка");
        double[] beep = renderAudio(400);
        System.out.println("  пищалка: пакетов " + AUDIO.size() + ", громкость " + beep[0] + ", частота " + beep[1]);
        expect("computer.beep: пакет ушёл, звучит ~440 Гц", flat().contains("BEEP true 8 440") && AUDIO.size() == 1
                && beep[0] > 0.05 && Math.abs(beep[1] - 440) < 5);
        AUDIO.clear();
        command("lua");
        command("sound.setWave(1, sound.modes.sine) sound.setFrequency(1, 'E5') sound.setVolume(1, 0.8) sound.open(1) sound.delay(300) sound.close(1) print('SND', sound.process(), sound.pending() > 0.2, pcall(sound.open, 9))");
        command("exit()");
        show("звуковой чип");
        double[] chip = renderAudio(350);
        System.out.println("  чип: громкость " + chip[0] + ", частота " + chip[1]);
        expect("sound: очередь отправлена одним пакетом, синус ~659 Гц", flat().contains("SND true true false") && AUDIO.size() == 1
                && chip[0] > 0.05 && Math.abs(chip[1] - 659.3) < 5);
        expect("sound: понятная ошибка про несуществующий канал", flat().contains("Нет канала 9"));
        AUDIO.clear();
        command("lua");
        command("for i = 1, 4 do sound.open(1) sound.delay(1000) sound.close(1) end print('LONG', sound.delay(1500), sound.process(), sound.process())");
        command("exit()");
        show("очередь звука");
        expect("sound: пакет не длиннее 5 с, очередь вперёд не больше 6 с", flat().contains("LONG false true"));
        command("lua");
        command("for i = 1, 4 do sound.delay(1000) end local p1 = sound.process() for i = 1, 4 do sound.delay(1000) end print('FULL', p1, sound.process()) sound.clear() sound.stop()");
        command("exit()");
        expect("sound: переполненная очередь отказывает", flat().contains("FULL true false"));

        // Проигрыватель: мелодия уходит пакетами, Q останавливает; библиотека note считает частоты
        command("clear");
        AUDIO.clear();
        realLine("music korobeiniki", 50);
        key("q", false);
        ticks(10);
        show("music");
        double[] mus = renderAudio(2500);
        System.out.println("  music: пакетов " + AUDIO.size() + ", громкость " + mus[0]);
        expect("music: играет мелодию (пакеты звука) и останавливается по Q", flat().contains("Коробейники") && AUDIO.size() >= 2
                && mus[0] > 0.03 && flat().trim().endsWith("/>"));
        command("music nosuch");
        expect("music: понятное сообщение о неизвестной мелодии", flat().contains("Нет такой мелодии или файла: nosuch"));
        command("lua");
        command("local n = require('note') print('NOTE', n.freq('A4'), n.midi('C4'), n.name(69))");
        command("exit()");
        expect("note: частоты и названия нот", flat().contains("NOTE 440.0 60 A4") || flat().contains("NOTE 440 60 A4"));

        // 3D-игра на кадровом буфере: замер рисует кадры и печатает скорость
        command("clear");
        command("bunker bench 10");
        for (int i = 0; i < 200 && !screen().contains("BUNKER"); i++) ticks(1);
        show("bunker bench");
        expect("bunker: кадры рисуются (замер)", screen().contains("BUNKER") && screen().contains("кадров/с"));

        // «Разлом» с дискеты: файлы пишутся как на настоящую дискету, замер рисует кадры настоящим 3D (gpu.newMesh)
        Path razlom = Files.createTempDirectory("minepc_razlom");
        ProgramDisks.populate(razlom, "razlom");
        pc.fileSystem().mount("razlom", new DirMount(razlom, 1 << 20, "razlom"));
        command("clear");
        command("/razlom/app/game.lua bench 10");
        for (int i = 0; i < 600 && !flat().contains("RAZLOM") && !flat().contains("не запустился") && !flat().contains("Нужна"); i++) ticks(1);
        show("razlom bench");
        expect("razlom: 3D-кадры рисуются (замер)", flat().contains("RAZLOM") && flat().contains("клеток"));

        // Захват мыши: признак едет в кадре вторым битом (мигание курсора не путается), событие mouse_move
        // доходит до программы, а оболочка снимает захват, если программа упала, не отпустив мышь
        boolean blinkBefore = TerminalCodec.blink(pc.terminal().snapshot());
        pc.terminal().setMouseLock(true);
        byte[] lockedFrame = pc.terminal().snapshot();
        pc.terminal().setMouseLock(false);
        expect("мышь: захват в кадре, мигание курсора не тронуто",
                TerminalCodec.mouseLock(lockedFrame) && TerminalCodec.blink(lockedFrame) == blinkBefore
                        && !TerminalCodec.mouseLock(pc.terminal().snapshot()));
        Files.writeString(razlom.resolve("lock.lua"), "term.setMouseLock(true) os.queueEvent('mouse_move', 1.5, -2) "
                + "local e, dx, dy = os.pullEvent('mouse_move') print('LOCK', term.getMouseLock(), dx, dy) error('boom')");
        command("clear");
        command("/razlom/lock.lua");
        show("mouse lock");
        expect("мышь: term.setMouseLock и событие mouse_move", flat().contains("LOCK true 1.5 -2"));
        expect("мышь: оболочка отпускает захват после упавшей программы", !pc.terminal().mouseLock());
        // «Разлом» в бою просит захват, а при выходе отпускает
        command("clear");
        type("/razlom/app/game.lua play 2 god");
        key("enter", false);
        boolean lockedInGame = false;
        for (int i = 0; i < 300 && !lockedInGame; i++) { ticks(1); lockedInGame = pc.terminal().mouseLock(); }
        for (int i = 0; i < 600 && !flat().contains("Разлом: врагов"); i++) ticks(1);
        expect("razlom: мышь захвачена в бою и отпущена после", lockedInGame && !pc.terminal().mouseLock());

        // BIOS: настройки сохраняются, порядок загрузки нормализуется
        command("clear");
        command("lua");
        command("bios.set({lan = false, cores = 2, bootOrder = {'usb', 'hdd'}}); local b = bios.get(); print('BIOS', b.lan, b.cores, b.bootOrder[1], b.bootOrder[2], bios.info().theme)");
        for (int i = 0; i < 20; i++) ticks(1);
        command("exit()");
        show("bios");
        expect("bios.set/get: LAN выключен, 2 ядра, порядок с флешки", screen().contains("BIOS") && screen().contains("false") && screen().contains("usb") && screen().contains("classic"));
    }
}
