package com.strikerr212.minepc.computer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;

/**
 * Часы всех включённых компьютеров: раз в миллисекунду пополняют процессор и видеокарту, срабатывают таймеры
 * и будят программы, которые ждут. Поэтому os.sleep(0.01) спит 10 мс, а не до следующего тика (50 мс),
 * а нажатие клавиши обрабатывается сразу.
 */
final class ComputerClock {

    private static final Set<Computer> RUNNING = ConcurrentHashMap.newKeySet();
    private static Thread thread;

    private ComputerClock() {
    }

    static void register(Computer c) {
        RUNNING.add(c);
        start();
    }

    static void unregister(Computer c) {
        RUNNING.remove(c);
    }

    private static synchronized void start() {
        if (thread != null && thread.isAlive()) return;
        thread = new Thread(ComputerClock::loop, "MineComputers-Clock");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY);
        thread.start();
    }

    private static void loop() {
        while (true) {
            for (Computer c : RUNNING) {
                try {
                    c.advance();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            LockSupport.parkNanos(1_000_000L);
        }
    }
}
