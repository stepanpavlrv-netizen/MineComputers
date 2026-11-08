package com.strikerr212.minepc.computer;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Потоки, в которых выполняются программы компьютеров. Сервер Minecraft (главный поток) только
 * раздаёт им работу раз в тик, поэтому даже тяжёлая программа не тормозит игру.
 *
 * Каждый процесс компьютера — отдельная задача, поэтому процессы одного компьютера (ядра процессора)
 * работают параллельно на разных потоках. Число потоков ограничивает, сколько ядер сервера отдано
 * компьютерам: если программ больше, они просто получают меньше времени, а игра не тормозит.
 */
public final class ComputerExecutor {

    private static final LinkedBlockingQueue<LuaMachine> QUEUE = new LinkedBlockingQueue<>();
    private static int started;
    private static volatile int wantThreads = defaultThreads();
    /** Максимальное время одного куска работы: даже очень быстрый процессор делится потоками честно. */
    private static volatile long sliceNanos = 20_000_000L;

    private ComputerExecutor() {
    }

    /** По умолчанию: до 4 потоков, но всегда оставляем одно ядро игре. */
    static int defaultThreads() {
        return Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
    }

    /** Настройки сервера: потоков (0 — сами решаем) и длина куска работы в миллисекундах. */
    public static synchronized void configure(int threads, int sliceMs) {
        wantThreads = threads <= 0 ? defaultThreads() : Math.min(32, threads);
        sliceNanos = Math.max(2, Math.min(45, sliceMs)) * 1_000_000L;
        if (started > 0) start();
    }

    static long sliceNanos() {
        return sliceNanos;
    }

    public static int threads() {
        return wantThreads;
    }

    private static synchronized void start() {
        // Потоки только добавляются: лишние просто будут спать в очереди
        while (started < wantThreads) {
            Thread t = new Thread(ComputerExecutor::loop, "MineComputers-" + started);
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            t.start();
            started++;
        }
    }

    static void submit(LuaMachine machine) {
        if (started < wantThreads) start();
        QUEUE.offer(machine);
    }

    private static void loop() {
        while (true) {
            try {
                LuaMachine m = QUEUE.take();
                m.work();
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                // Ошибка одного компьютера не должна останавливать остальные
                t.printStackTrace();
            }
        }
    }
}
