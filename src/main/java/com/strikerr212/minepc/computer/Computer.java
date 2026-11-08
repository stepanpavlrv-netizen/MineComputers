package com.strikerr212.minepc.computer;

import com.strikerr212.minepc.computer.fs.FileSystem;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Один работающий компьютер: экран, файлы, очередь событий и программа на Lua.
 *
 * Как устроено время. Каждый тик сервер вызывает {@link #tick()}: компьютер получает порцию команд
 * (лимит процессора) и, если ему есть что делать, встаёт в очередь {@link ComputerExecutor}.
 * Там программа выполняется, пока не начнёт ждать событие (нажатие клавиши, таймер) или пока не
 * кончится порция — тогда она замирает посреди работы и продолжит в следующем тике.
 *
 * Здесь нет ничего из Minecraft: блок системного блока передаёт сюда только числа и накопители.
 */
public final class Computer {

    public enum State { OFF, RUNNING, CRASHED }

    /** Что компьютеру нужно знать о мире. Реализует системный блок. */
    public interface Host {
        /** Время суток в часах 0..24. */
        default double timeOfDay() { return 12; }

        default int day() { return 1; }

        default void onLabelChanged(String label) { }
    }

    /** Сведения о железе для программ (computer.info()). */
    public record Info(String cpu, int cores, int clockMhz, String architecture, int ramKb, int opsPerSecond,
                       String gpu, int score) {
    }

    public static final int TICKS_PER_SECOND = 20;
    /** Больше событий в очереди не держим: программа, которая их не читает, не съест всю память. */
    private static final int MAX_EVENTS = 256;
    /** Сколько порций можно накопить, пока программа ждёт (чтобы после ожидания сразу был запас). */
    private static final int BUDGET_BANK_TICKS = 2;
    /** Максимальное время одного куска работы: даже очень быстрый процессор делится потоками честно. */
    static final long SLICE_NANOS = 20_000_000L;

    private final Host host;
    private final Terminal terminal = new Terminal(50, 16);
    private final FileSystem fs = new FileSystem();
    private final ConcurrentLinkedQueue<Object[]> events = new ConcurrentLinkedQueue<>();
    private final AtomicInteger eventCount = new AtomicInteger();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final Object lock = new Object();

    private volatile State state = State.OFF;
    private volatile int opsPerTick = 10_000;
    private volatile long budget;
    private volatile Info info = new Info("?", 1, 0, "Lua", 0, 0, "", 0);
    private volatile String label = "";
    private volatile String crashMessage = "";

    private LuaMachine machine;            // трогается только под lock
    private volatile boolean stopRequested, rebootRequested;

    /** Сколько тиков назад нажали Ctrl+T, а программа так и не прочитала событие (-1 — не нажимали). */
    private volatile int terminateWait = -1;
    private static final int TERMINATE_GRACE_TICKS = 20;

    private long ticks;
    private final Map<Integer, Long> timers = new HashMap<>();
    private int nextTimer = 1;

    /** Сколько команд выполнено за последнюю секунду (для программ и отладки). */
    private volatile long opsLastSecond;
    private long opsThisSecond;

    public Computer(Host host) {
        this.host = host;
    }

    // ======================= Состояние =======================

    public Terminal terminal() { return terminal; }
    public FileSystem fileSystem() { return fs; }
    public State state() { return state; }
    public boolean isOn() { return state == State.RUNNING; }
    public String crashMessage() { return crashMessage; }
    public Host host() { return host; }
    public Info info() { return info; }
    public long opsLastSecond() { return opsLastSecond; }
    public int opsPerTick() { return opsPerTick; }
    public String label() { return label; }

    public void setInfo(Info info) { this.info = info; }

    /** Лимит процессора: команд за тик. */
    public void setOpsPerTick(int ops) { opsPerTick = Math.max(100, ops); }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
    }

    double uptimeSeconds() {
        synchronized (timers) {
            return ticks / (double) TICKS_PER_SECOND;
        }
    }

    // ======================= Включение и выключение =======================

    public void turnOn() {
        synchronized (lock) {
            if (state == State.RUNNING) return;
            closeMachine();
            events.clear();
            eventCount.set(0);
            terminateWait = -1;
            synchronized (timers) {
                timers.clear();
                ticks = 0;
            }
            stopRequested = rebootRequested = false;
            crashMessage = "";
            terminal.setTextColor(Terminal.WHITE);
            terminal.setBackColor(Terminal.BLACK);
            terminal.clear();
            terminal.setCursor(0, 0);
            budget = opsPerTick;
            state = State.RUNNING;
        }
    }

    /** Выключить. Безопасно вызывать с любого потока: если программа сейчас работает, она остановится. */
    public void shutdown() {
        stopRequested = true;
        LuaMachine m = machine;
        if (m != null) m.interrupt();
        synchronized (lock) {
            closeMachine();
            state = State.OFF;
            stopRequested = false;
            terminal.clear();
            terminal.setCursor(0, 0);
        }
    }

    public void reboot() {
        shutdown();
        turnOn();
    }

    private void closeMachine() {
        if (machine != null) {
            machine.close();
            machine = null;
        }
    }

    void requestShutdown() { stopRequested = true; }

    void requestReboot() { rebootRequested = true; stopRequested = true; }

    // ======================= События =======================

    /** Добавить событие: имя и значения (строки, числа, логические, null). */
    public void queueEvent(String name, Object... args) {
        if (state != State.RUNNING) return;
        if (eventCount.get() >= MAX_EVENTS) return;
        Object[] ev = new Object[args.length + 1];
        ev[0] = name;
        System.arraycopy(args, 0, ev, 1, args.length);
        events.add(ev);
        eventCount.incrementAndGet();
    }

    /** Ctrl+T: попросить программу завершиться. Если она занята и не читает события — прервать через секунду. */
    public void terminate() {
        if (state != State.RUNNING) return;
        queueEvent("terminate");
        terminateWait = 0;
    }

    void terminateDelivered() {
        terminateWait = -1;
    }

    Object[] pollEvent() {
        Object[] ev = events.poll();
        if (ev != null) eventCount.decrementAndGet();
        return ev;
    }

    boolean hasEvents() {
        return !events.isEmpty();
    }

    int startTimer(double seconds) {
        synchronized (timers) {
            int id = nextTimer++;
            long dueTicks = ticks + Math.max(1, Math.round(seconds * TICKS_PER_SECOND));
            timers.put(id, dueTicks);
            return id;
        }
    }

    void cancelTimer(int id) {
        synchronized (timers) {
            timers.remove(id);
        }
    }

    // ======================= Работа =======================

    /** Вызывается сервером каждый тик (главный поток). */
    public void tick() {
        if (state != State.RUNNING) return;
        synchronized (timers) {
            ticks++;
            for (Iterator<Map.Entry<Integer, Long>> it = timers.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, Long> e = it.next();
                if (e.getValue() <= ticks) {
                    it.remove();
                    queueEvent("timer", e.getKey());
                }
            }
            if (ticks % TICKS_PER_SECOND == 0) {
                opsLastSecond = opsThisSecond;
                opsThisSecond = 0;
            }
        }
        budget = Math.min(budget + opsPerTick, (long) opsPerTick * BUDGET_BANK_TICKS);
        if (terminateWait >= 0 && ++terminateWait > TERMINATE_GRACE_TICKS) {
            terminateWait = -1;
            // Событие так и не прочитали — убираем его и прерываем программу ошибкой
            if (events.removeIf(ev -> "terminate".equals(ev[0]))) eventCount.decrementAndGet();
            LuaMachine m = machine;
            if (m != null) m.abort();
        }
        if (needsWork() && scheduled.compareAndSet(false, true)) {
            ComputerExecutor.submit(this);
        }
    }

    private boolean needsWork() {
        if (state != State.RUNNING || budget <= 0) return false;
        LuaMachine m = machine;
        return m == null || m.isPaused() || hasEvents() || stopRequested;
    }

    long budget() { return budget; }

    void spend(long ops) {
        budget -= ops;
        synchronized (timers) {
            opsThisSecond += ops;
        }
    }

    /** Выполнить кусок работы (поток исполнителя). */
    void work() {
        scheduled.set(false);
        boolean reboot = false;
        synchronized (lock) {
            if (state != State.RUNNING) return;
            try {
                if (machine == null) machine = new LuaMachine(this);
                long deadline = System.nanoTime() + SLICE_NANOS;
                while (!stopRequested && budget > 0 && System.nanoTime() < deadline) {
                    if (!machine.resume(deadline)) break;     // нечего делать или замерли
                }
                if (machine != null && machine.isFinished()) {
                    stopRequested = true;
                }
            } catch (LuaMachine.Crash crash) {
                crash(crash.getMessage());
                return;
            } catch (Throwable t) {
                crash("Внутренняя ошибка: " + t);
                return;
            }
            if (stopRequested) {
                reboot = rebootRequested;
                closeMachine();
                state = State.OFF;
                stopRequested = rebootRequested = false;
                terminal.clear();
                terminal.setCursor(0, 0);
            }
        }
        if (reboot) {
            turnOn();
        }
    }

    private void crash(String message) {
        closeMachine();
        crashMessage = message;
        state = State.CRASHED;
        terminal.setBackColor(Terminal.BLACK);
        terminal.setTextColor(14);
        int y = Math.min(terminal.height() - 1, Math.max(0, terminal.cursorY() + 1));
        if (terminal.cursorX() == 0) y = Math.max(0, terminal.cursorY());
        for (String part : wrap("Ошибка: " + message, terminal.width())) {
            if (y >= terminal.height()) {
                terminal.scroll(1);
                y = terminal.height() - 1;
            }
            terminal.setCursor(0, y++);
            terminal.write(Cp1251.encode(part));
        }
        terminal.setTextColor(Terminal.WHITE);
        terminal.setCursorBlink(false);
    }

    static java.util.List<String> wrap(String text, int width) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String line : text.split("\n")) {
            while (line.length() > width) {
                out.add(line.substring(0, width));
                line = line.substring(width);
            }
            out.add(line);
        }
        return out;
    }

    /** Выгрузить без следов (чанк выгружен, блок сломан). */
    public void unload() {
        shutdown();
    }
}
