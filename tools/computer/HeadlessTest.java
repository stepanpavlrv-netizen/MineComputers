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

    static void show(String title) {
        System.out.println("┌── " + title + " ──");
        for (String l : screen().split("\n")) System.out.println("│" + l);
        System.out.println("└──");
    }

    static void expect(String what, boolean ok) {
        System.out.println((ok ? "  ✔ " : "  ✘ ") + what);
        if (!ok) failures++;
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("minepc_drive");
        pc = new Computer(new Computer.Host() { });
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
        type("for i = 1, 3 do");
        key("enter", false);
        type("  print(\"Привет, \" .. i)");
        key("enter", false);
        type("end");
        ticks(5);
        show("редактор");
        expect("подсветка: ключевое слово на экране", screen().contains("for i = 1, 3 do"));
        key("s", true);
        ticks(5);
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
        expect("бесконечный цикл ест не больше лимита", used <= (long) pc.opsPerTick() * 20 * 102 / 100);
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
        for (int i = 0; i < 100 && !screen().contains("COST"); i++) ticks(1);
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
        for (int i = 0; i < 200 && !screen().contains("IO "); i++) ticks(1);
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

        testNet();

        System.out.println(failures == 0 ? "ВСЁ ХОРОШО" : "ОШИБОК: " + failures);
        System.exit(failures == 0 ? 0 : 1);
    }

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
                if (target == null || target.equalsIgnoreCase(otherName) || box[other].address().startsWith(target)) {
                    box[other].netReceive(address, port, 1, payload, bytes);
                }
                return true;
            }
        });
        c.setInfo(new Computer.Info("Тест", 2, 2800, "Lua", 2048, 2_000_000, "ГП", 1000));
        c.setOpsPerTick(100000);
        c.fileSystem().mount("rom", ComputerRom.mount());
        c.fileSystem().mount("", new DirMount(dir, 64 * 1024, "SSD"));
        c.terminal().resize(50, 16);
        return c;
    }

    static void testNet() throws Exception {
        System.out.println("── сеть ──");
        Computer[] box = new Computer[2];
        box[0] = netComputer("aaaaaaaa-1111-4000-8000-000000000000", box, 1, "beta");
        box[1] = netComputer("bbbbbbbb-2222-4000-8000-000000000000", box, 0, "alpha");
        Path dirB = Files.createTempDirectory("minepc_netb");
        box[1].fileSystem().mount("", new DirMount(dirB, 64 * 1024, "SSD"));
        Files.writeString(dirB.resolve("recv.lua"), String.join("\n",
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
        command("recv");
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
        pc = saved;
    }
}
