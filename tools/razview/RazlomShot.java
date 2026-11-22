import com.strikerr212.minepc.computer.*;
import com.strikerr212.minepc.computer.fs.DirMount;

import java.nio.file.*;

/**
 * Кадры «Разлома» без Minecraft: настоящая видеокарта мода (Raster3D), дискета из src/main/resources.
 * Запуск — через tools/razview/shot.py. Аргументы: ширина высота папка_вывода "аргументы game.lua" ...
 * Каждый набор аргументов — один кадр: game.lua shot <аргументы> out=<файл>.
 */
public class RazlomShot {
    public static void main(String[] args) throws Exception {
        int w = Integer.parseInt(args[0]), h = Integer.parseInt(args[1]);
        Path out = Paths.get(args[2]);
        Path drive = Files.createTempDirectory("razshot");
        Computer pc = new Computer(new Computer.Host() { });
        pc.setInfo(new Computer.Info("Тест", 4, 2800, "Lua", 2048, 2_000_000, "Видеокарта", 1000));
        pc.setLimits(new Computer.Limits(5_000_000, 4, 512L << 20, 50_000_000, Long.getLong("vram", 400_000L), 0, 16));
        pc.fileSystem().mount("rom", ComputerRom.mount());
        pc.fileSystem().mount("", new DirMount(drive, 64L << 20, "SSD"));
        Path disk = Files.createTempDirectory("razdisk");
        ProgramDisks.populate(disk, "razlom");
        pc.fileSystem().mount("razlom", new DirMount(disk, 4 << 20, "razlom"));
        pc.terminal().setMaxSize(w, h);
        pc.terminal().setMaxDepth(24);
        pc.turnOn();
        for (int i = 0; i < 400 && !screen(pc).contains("/>"); i++) tick(pc);
        for (int k = 3; k < args.length; k++) {
            String name = "f" + (k - 3) + ".txt";
            Path f = drive.resolve(name);
            Files.deleteIfExists(f);
            String line = "/razlom/app/game.lua " + (args[k].startsWith("check") ? "" : "shot ") + args[k] + " out=/" + name;
            for (char ch : line.toCharArray()) pc.queueEvent("char", String.valueOf(ch));
            pc.queueEvent("key", "enter", false, false);
            boolean done = false;
            for (int i = 0; i < 40000 && !done; i++) {
                tick(pc);
                done = Files.exists(drive.resolve(name + ".ok"));
                if (i % 50 == 0 && (screen(pc).contains("не запустился") || screen(pc).contains("rror"))) break;
            }
            if (!done) {
                System.out.println("НЕ ВЫШЛО: " + args[k]);
                System.out.println(screen(pc));
                try (var l = Files.list(drive)) { l.forEach(x -> System.out.println("  файл: " + x.getFileName())); }
                continue;
            }
            if (args[k].startsWith("check")) { System.out.print(Files.readString(f)); continue; }
            Files.copy(f, out.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("КАДР " + name + " " + args[k]);
        }
        System.exit(0);
    }

    static void tick(Computer pc) throws Exception { pc.tick(); Thread.sleep(1); }

    static String screen(Computer pc) {
        Terminal t = pc.terminal();
        StringBuilder b = new StringBuilder();
        for (int y = 0; y < t.height(); y++) b.append(t.line(y).replaceAll("\\s+$", "")).append('\n');
        return b.toString();
    }
}
