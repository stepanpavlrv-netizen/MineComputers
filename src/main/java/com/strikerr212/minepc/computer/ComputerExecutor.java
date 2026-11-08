package com.strikerr212.minepc.computer;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Потоки, в которых выполняются программы компьютеров. Сервер Minecraft (главный поток) только
 * раздаёт им работу раз в тик, поэтому даже тяжёлая программа не тормозит игру.
 */
public final class ComputerExecutor {

    private static final LinkedBlockingQueue<Computer> QUEUE = new LinkedBlockingQueue<>();
    private static volatile boolean started;

    private ComputerExecutor() {
    }

    /** Сколько потоков отдаём компьютерам: не больше двух и всегда оставляем ядро игре. */
    static int threadCount() {
        return Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 1));
    }

    private static synchronized void start() {
        if (started) return;
        started = true;
        for (int i = 0; i < threadCount(); i++) {
            Thread t = new Thread(ComputerExecutor::loop, "MineComputers-" + i);
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            t.start();
        }
    }

    static void submit(Computer computer) {
        start();
        QUEUE.offer(computer);
    }

    private static void loop() {
        while (true) {
            try {
                Computer c = QUEUE.take();
                c.work();
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                // Ошибка одного компьютера не должна останавливать остальные
                t.printStackTrace();
            }
        }
    }
}
