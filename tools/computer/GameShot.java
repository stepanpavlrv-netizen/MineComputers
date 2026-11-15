import com.strikerr212.minepc.computer.*;
import com.strikerr212.minepc.computer.fs.DirMount;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/**
 * Снимки экрана компьютера без Minecraft: включить, запустить команду, сохранить кадры в PNG.
 * Удобно смотреть, как рисует программа с кадровым буфером (например, 3D-игра).
 *
 * Запуск (из корня репозитория, после javac как в run_test.py):
 *   java -cp out;cobalt.jar;src/main/resources GameShot ширина высота папка_дискеты папка_снимков "команда" [снимков] [тиков_между] [клавиши]
 * Клавиши — через запятую «тик:клавиша» (нажать) и «тик:-клавиша» (отпустить), например «20:w,60:-w».
 * Время идёт по-настоящему: 20 тиков в секунду, как на сервере.
 */
public class GameShot {

    static Computer pc;

    static String screen() {
        Terminal t = pc.terminal();
        StringBuilder b = new StringBuilder();
        for (int y = 0; y < t.height(); y++) b.append(t.line(y).replaceAll("\\s+$", "")).append('\n');
        return b.toString();
    }

    /** Экран в картинку: клетка — 2 точки (полублоки), текст — светлым квадратиком цвета букв. */
    static void png(Path file) throws Exception {
        Terminal t = pc.terminal();
        int s = 3;
        // клетка экрана 2:3 (точка 4:3, как у настоящего шрифта 6×9): 2s × 3s пикселей
        BufferedImage img = new BufferedImage(t.width() * 2 * s, t.height() * 3 * s, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < t.height(); y++) {
            for (int x = 0; x < t.width(); x++) {
                int[] c = t.get(x, y);
                int ch = c[0], fg = c[1], bg = c[2];
                int top, bot;
                if (ch == '▀') { top = fg; bot = bg; }
                else if (ch == ' ' || ch == 0) { top = bg; bot = bg; }
                else if (ch == '▄') { top = bg; bot = fg; }
                else if (ch == '█') { top = fg; bot = fg; }
                else { top = mix(fg, bg); bot = mix(fg, bg); }
                for (int dy = 0; dy < 3 * s; dy++) {
                    int col = dy < 3 * s / 2 ? top : bot;
                    for (int dx = 0; dx < 2 * s; dx++) img.setRGB(x * 2 * s + dx, y * 3 * s + dy, col);
                }
            }
        }
        ImageIO.write(img, "png", file.toFile());
    }

    static int mix(int a, int b) {
        int r = (((a >> 16) & 255) * 2 + ((b >> 16) & 255)) / 3, g = (((a >> 8) & 255) * 2 + ((b >> 8) & 255)) / 3,
                bl = ((a & 255) * 2 + (b & 255)) / 3;
        return r << 16 | g << 8 | bl;
    }

    public static void main(String[] args) throws Exception {
        int w = Integer.parseInt(args[0]), h = Integer.parseInt(args[1]);
        Path disk = Paths.get(args[2]);
        Path out = Paths.get(args[3]);
        String command = args[4];
        int shots = args.length > 5 ? Integer.parseInt(args[5]) : 4;
        int between = args.length > 6 ? Integer.parseInt(args[6]) : 20;
        String keyScript = args.length > 7 ? args[7] : "";
        Files.createDirectories(out);

        Path root = Files.createTempDirectory("minepc_shot");
        pc = new Computer(new Computer.Host() {
        });
        pc.setInfo(new Computer.Info("Тестовый процессор", 4, 2800, "Lua", 2048, 2_000_000, "Видеокарта", 1000));
        int ops = Integer.parseInt(System.getProperty("ops", "175000"));
        int gpuCells = Integer.parseInt(System.getProperty("gpu", "32000"));
        long vram = Long.parseLong(System.getProperty("vram", "400000"));
        pc.setLimits(new Computer.Limits(ops, 4, 64L << 20, gpuCells, vram, 0, 16));
        pc.fileSystem().mount("rom", ComputerRom.mount());
        pc.fileSystem().mount("", new DirMount(root, 4 * 1024 * 1024, "SSD"));
        pc.fileSystem().mount("disk", new DirMount(disk, 4 * 1024 * 1024, "Дискета"));
        pc.terminal().setMaxSize(w, h);
        pc.terminal().setMaxDepth(24);
        pc.terminal().resize(w, h);

        pc.turnOn();
        for (int i = 0; i < 400 && !screen().contains("/>"); i++) { pc.tick(); Thread.sleep(5); }
        for (char ch : command.toCharArray()) pc.queueEvent("char", String.valueOf(ch));
        pc.queueEvent("key", "enter", false, false);

        java.util.Map<Integer, java.util.List<String>> keys = new java.util.HashMap<>();
        for (String k : keyScript.split(",")) {
            if (k.isBlank()) continue;
            String[] p = k.split(":");
            keys.computeIfAbsent(Integer.parseInt(p[0].trim()), x -> new java.util.ArrayList<>()).add(p[1].trim());
        }
        int tick = 0;
        long t0 = System.nanoTime();
        for (int s = 1; s <= shots; s++) {
            for (int i = 0; i < between; i++) {
                for (String k : keys.getOrDefault(tick, java.util.List.of())) {
                    if (k.startsWith("-")) pc.queueEvent("key_up", k.substring(1));
                    else pc.queueEvent("key", k, false, false);
                }
                long due = t0 + (tick + 1) * 50_000_000L;
                pc.tick();
                tick++;
                long left = due - System.nanoTime();
                if (left > 0) Thread.sleep(left / 1_000_000, (int) (left % 1_000_000));
            }
            png(out.resolve(String.format("shot%02d.png", s)));
        }
        System.out.println("┌── экран в конце ──");
        for (String l : screen().split("\n")) System.out.println("│" + l);
        System.out.println("└──");
        System.exit(0);
    }
}
