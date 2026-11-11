package com.strikerr212.minepc.block;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;

/**
 * Отправка экранов игрокам — отдельным потоком, а не раз в тик. Кадр уходит сразу, как программа его
 * нарисовала (до screenFps раз в секунду), поэтому окна ходят за мышью без задержки в тик, а игры
 * могут показывать больше 20 кадров. Заодно сжатие кадров не нагружает главный поток сервера.
 */
final class ScreenSender {

    private static final Set<CaseComputer> TRACKED = ConcurrentHashMap.newKeySet();
    private static Thread thread;

    private ScreenSender() {
    }

    static void track(CaseComputer c) {
        if (TRACKED.add(c)) start();
    }

    private static synchronized void start() {
        if (thread != null && thread.isAlive()) return;
        thread = new Thread(ScreenSender::loop, "MineComputers-Screens");
        thread.setDaemon(true);
        thread.start();
    }

    private static void loop() {
        while (true) {
            long now = System.nanoTime();
            for (CaseComputer c : TRACKED) {
                // Корпус больше не тикает (сломан, чанк выгружен) — забываем
                if (now - c.lastTickNanos() > 2_000_000_000L) {
                    TRACKED.remove(c);
                    continue;
                }
                try {
                    c.flushScreen(false);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            LockSupport.parkNanos(2_000_000L);
        }
    }
}
