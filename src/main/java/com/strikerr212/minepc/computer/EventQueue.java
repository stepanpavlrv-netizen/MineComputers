package com.strikerr212.minepc.computer;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Очередь событий одного процесса (нажатия клавиш, таймеры, сообщения от других процессов).
 * Пишут в неё разные потоки (сервер, другие процессы), читает только сам процесс.
 */
final class EventQueue {

    /** Больше событий не держим: программа, которая их не читает, не съест всю память. */
    static final int MAX_EVENTS = 256;

    private final ConcurrentLinkedQueue<Object[]> events = new ConcurrentLinkedQueue<>();
    private final AtomicInteger count = new AtomicInteger();

    /** Добавить событие [имя, значения...]. Возвращает false, если очередь переполнена. */
    boolean offer(Object[] ev) {
        if (count.get() >= MAX_EVENTS) return false;
        events.add(ev);
        count.incrementAndGet();
        return true;
    }

    Object[] poll() {
        Object[] ev = events.poll();
        if (ev != null) count.decrementAndGet();
        return ev;
    }

    boolean isEmpty() {
        return events.isEmpty();
    }

    /** Убрать все события с этим именем. Возвращает true, если что-то убрали. */
    boolean removeNamed(String name) {
        boolean removed = false;
        for (var it = events.iterator(); it.hasNext(); ) {
            if (name.equals(it.next()[0])) {
                it.remove();
                count.decrementAndGet();
                removed = true;
            }
        }
        return removed;
    }

    void clear() {
        events.clear();
        count.set(0);
    }
}
