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
        expect("end после блока сдвинут влево", Files.readString(dir.resolve("code/hello.lua"), Cp1251.CHARSET).endsWith("\nend"));

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

        // Передача экрана: изменения строк и сжатие
        Terminal big = new Terminal(160, 50);
        byte[] f1 = big.snapshot();
        byte[] p1 = TerminalCodec.encode(null, f1);
        byte[] client = TerminalCodec.apply(null, p1);
        big.setCursor(3, 7);
        big.setTextColor(4);
        big.write(Cp1251.encode("Привет, большой экран!"));
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

        System.out.println(failures == 0 ? "ВСЁ ХОРОШО" : "ОШИБОК: " + failures);
        System.exit(failures == 0 ? 0 : 1);
    }
}
