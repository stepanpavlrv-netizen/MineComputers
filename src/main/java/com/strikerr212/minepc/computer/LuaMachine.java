package com.strikerr212.minepc.computer;

import org.squiddev.cobalt.*;
import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.debug.DebugFrame;
import org.squiddev.cobalt.debug.DebugHook;
import org.squiddev.cobalt.debug.DebugState;
import org.squiddev.cobalt.function.Dispatch;
import org.squiddev.cobalt.function.LuaClosure;
import org.squiddev.cobalt.function.LuaFunction;
import org.squiddev.cobalt.function.RegisteredFunction;
import org.squiddev.cobalt.interrupt.InterruptAction;
import org.squiddev.cobalt.lib.CoreLibraries;

import java.io.ByteArrayInputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.squiddev.cobalt.ValueFactory.valueOf;

/**
 * Один процесс компьютера: своя машина Lua (движок Cobalt — тот же, что в CC: Tweaked), свои события и
 * своя порция команд. Главный процесс запускает BIOS и командную строку; остальные (computer.spawn)
 * выполняют одну программу и работают параллельно на других ядрах.
 *
 * Лимит команд: каждые {@link #HOOK_STEP} команд срабатывает счётчик и списывает их из порции.
 * Когда порция кончилась (или вышло время куска работы, или видеокарта и диск не успевают), машина
 * «замирает» прямо посреди программы и продолжает с того же места в следующий раз.
 *
 * Память: на том же счётчике смотрим, сколько байт выделил поток (счётчик JVM). Как только выделено
 * больше, чем было свободно, обходим данные программы ({@link MemoryMeter}) и узнаём, сколько из них
 * живо на самом деле. Не помещается — в программе возникает ошибка «Недостаточно памяти».
 */
final class LuaMachine {

    /** Ошибка, после которой процесс завершается (для главного — синий экран). */
    static final class Crash extends Exception {
        Crash(String message) {
            super(message, null, false, false);
        }
    }

    static final int HOOK_STEP = 500;
    /** Сколько байт обрабатывают встроенные функции (string.rep, table.concat...) за одну команду. */
    static final int BYTES_PER_OP = 16;
    /** Обход памяти стоит команд за каждый найденный объект (как сборка мусора в настоящем Lua). */
    static final int OPS_PER_OBJECT = 2;
    /** Если счётчик выделений JVM недоступен, считаем, что каждая команда выделяет столько байт. */
    private static final int FALLBACK_BYTES_PER_OP = 32;

    final Computer computer;
    final int pid, parentPid;
    final EventQueue events;
    final String programPath;
    final Object[] programArgs;
    final Object lock = new Object();
    final AtomicBoolean scheduled = new AtomicBoolean();

    private LuaState state;
    private LuaThread mainRoutine;
    private volatile boolean pauseRequested, abortRequested, killRequested;
    private boolean started, paused;
    private volatile boolean finished;
    private long deadline;
    private String filter;
    private volatile long budget;
    private volatile long opsTotal;
    /** Команд за последнюю секунду (для диспетчера задач). */
    volatile long opsLastSecond;
    private long opsMark;

    private volatile long liveBytes;
    private long allocSince, allocMark;

    LuaMachine(Computer computer, int pid, int parentPid, EventQueue events, String programPath, Object[] programArgs) {
        this.computer = computer;
        this.pid = pid;
        this.parentPid = parentPid;
        this.events = events;
        this.programPath = programPath;
        this.programArgs = programArgs;
    }

    boolean isMain() { return programPath == null; }
    boolean isFinished() { return finished; }
    long liveBytes() { return liveBytes; }
    long opsTotal() { return opsTotal; }

    /** Раз в секунду (поток сервера): сколько команд выполнено за секунду. */
    void sampleOps() {
        long t = opsTotal;
        opsLastSecond = t - opsMark;
        opsMark = t;
    }
    long budget() { return budget; }

    /** Состояние для списка процессов. */
    String status() {
        if (finished) return "finished";
        if (state == null) return "starting";
        return paused || !events.isEmpty() ? "running" : "waiting";
    }

    // ======================= Запуск машины =======================

    private void init() throws Crash {
        state = LuaState.builder()
                .interruptHandler(() -> {
                    if (abortRequested) {
                        abortRequested = false;
                        throw LuaApis.error("Прервано");   // Ctrl+T для программы, которая не ждёт событий
                    }
                    return pauseRequested ? InterruptAction.SUSPEND : InterruptAction.CONTINUE;
                })
                .build();
        try {
            CoreLibraries.debugGlobals(state);
            LuaTable g = state.globals();
            org.squiddev.cobalt.lib.Bit32Lib.add(state, g);   // bit32 есть в Lua 5.2 и OpenComputers
            // Из отладочной библиотеки оставляем только безопасное: трассировку ошибок и сведения о функции
            // (debug.sethook сломал бы счётчик команд)
            LuaTable fullDebug = (LuaTable) g.rawget("debug");
            LuaTable debug = new LuaTable();
            debug.rawset("traceback", fullDebug.rawget("traceback"));
            debug.rawset("getinfo", fullDebug.rawget("getinfo"));
            g.rawset("debug", debug);
            // Убираем то, что даёт доступ к файлам сервера или ломает песочницу
            g.rawset("dofile", Constants.NIL);
            g.rawset("loadfile", Constants.NIL);
            g.rawset("print", Constants.NIL);
            LuaApis.install(state, g, computer, this);
            chargeBuiltins(g);
            g.rawset("collectgarbage", RegisteredFunction.ofV("collectgarbage", (s, a) -> collectGarbage(a)).create());
            if (programPath != null) {
                LuaTable p = new LuaTable();
                p.rawset("path", LuaApis.str(programPath));
                LuaTable args = new LuaTable();
                for (int i = 0; i < programArgs.length; i++) args.rawset(i + 1, LuaApis.toLua(programArgs[i]));
                args.rawset("n", valueOf(programArgs.length));
                p.rawset("args", args);
                p.rawset("pid", valueOf(pid));
                g.rawset("_PROCESS", p);
            }
            byte[] bios = ComputerRom.bios();
            LuaClosure fn = LoadState.load(state, new ByteArrayInputStream(bios), "@bios.lua", g);
            mainRoutine = new LuaThread(state, fn);
        } catch (LuaError | CompileException e) {
            throw new Crash("Не удалось запустить BIOS: " + e.getMessage());
        }
        // Счётчик команд наследуют все сопрограммы, которые создаст программа
        mainRoutine.getDebugState().setHook(new DebugHook() {
            @Override
            public void onCount(LuaState s, DebugState ds, DebugFrame frame) throws LuaError {
                spend(HOOK_STEP);
                trackMemory();
                if (budget <= 0 || System.nanoTime() > deadline || computer.mustWait() || killRequested) {
                    pauseRequested = true;
                    s.interrupt();
                }
            }
        }, false, false, false, HOOK_STEP);
    }

    // ======================= Цена встроенных функций =======================

    private interface Cost {
        long ops(Varargs args, Varargs result) throws LuaError;
    }

    private static long len(Varargs a, int i) {
        LuaValue v = a.arg(i);
        return v.isString() ? ((LuaString) v.toLuaString()).length() : 0;
    }

    /**
     * Встроенные функции на Java выполняют за один вызов тысячи операций (string.rep на мегабайт —
     * это один вызов). Здесь каждая списывает команды по объёму работы: {@link #BYTES_PER_OP} байт
     * за команду — всё равно в разы быстрее, чем то же самое циклом на Lua.
     * Оборачиваются только функции, которые не вызывают Lua-код (format, gsub, sort, load обёрнуты в BIOS).
     */
    private void chargeBuiltins(LuaTable g) throws LuaError {
        LuaTable string = (LuaTable) g.rawget("string");
        wrap(string, "sub", (a, r) -> len(r, 1) / BYTES_PER_OP);
        wrap(string, "upper", (a, r) -> len(a, 1) / BYTES_PER_OP);
        wrap(string, "lower", (a, r) -> len(a, 1) / BYTES_PER_OP);
        wrap(string, "reverse", (a, r) -> len(a, 1) / BYTES_PER_OP);
        wrap(string, "find", (a, r) -> len(a, 1) / BYTES_PER_OP);
        wrap(string, "match", (a, r) -> len(a, 1) / BYTES_PER_OP);
        wrap(string, "gmatch", (a, r) -> len(a, 1) / BYTES_PER_OP);
        wrap(string, "byte", (a, r) -> r.count() / 4);
        wrap(string, "char", (a, r) -> a.count() / 4);
        LuaFunction rep = (LuaFunction) string.rawget("rep");
        string.rawset("rep", RegisteredFunction.ofV("rep", (s, a) -> {
            long n = Math.max(0, (long) a.arg(2).optNumber(valueOf(0)).toDouble());
            long total = len(a, 1) * n + (a.arg(3).isNil() ? 0 : len(a, 3) * Math.max(0, n - 1));
            if (a.arg(1).isNumber()) total = (long) a.arg(1).toString().length() * n;
            if (total > Integer.MAX_VALUE - 64 || !reserve(total)) throw LuaApis.error("Недостаточно памяти");
            spend(total / BYTES_PER_OP);
            return call(s, rep, a);
        }).create());

        LuaTable table = (LuaTable) g.rawget("table");
        wrap(table, "concat", (a, r) -> len(r, 1) / BYTES_PER_OP);
        wrap(table, "unpack", (a, r) -> r.count() / 4);
        if (g.rawget("unpack") instanceof LuaFunction) g.rawset("unpack", table.rawget("unpack"));

        LuaTable unicode = (LuaTable) g.rawget("unicode");
        for (String name : new String[]{"len", "wlen", "sub", "lower", "upper", "reverse", "wtrunc"}) {
            wrap(unicode, name, (a, r) -> len(a, 1) / BYTES_PER_OP);
        }
        wrap(unicode, "char", (a, r) -> a.count() / 4);
    }

    private void wrap(LuaTable t, String name, Cost cost) throws LuaError {
        LuaValue v = t.rawget(name);
        if (!(v instanceof LuaFunction fn)) return;
        t.rawset(name, RegisteredFunction.ofV(name, (s, a) -> {
            Varargs r = call(s, fn, a);
            long ops = cost.ops(a, r);
            if (ops > 0) spend(ops);
            return r;
        }).create());
    }

    private static Varargs call(LuaState s, LuaFunction fn, Varargs a) throws LuaError {
        try {
            return Dispatch.invoke(s, fn, a);
        } catch (UnwindThrowable e) {
            throw LuaApis.error("Эту функцию нельзя прервать");
        }
    }

    // ======================= Память =======================

    private static final MethodHandle ALLOCATED = allocatedHandle();

    private static MethodHandle allocatedHandle() {
        try {
            Class<?> mf = Class.forName("java.lang.management.ManagementFactory");
            Object bean = mf.getMethod("getThreadMXBean").invoke(null);
            Class<?> sun = Class.forName("com.sun.management.ThreadMXBean");
            if (!sun.isInstance(bean)) return null;
            if (!(boolean) sun.getMethod("isThreadAllocatedMemorySupported").invoke(bean)) return null;
            sun.getMethod("setThreadAllocatedMemoryEnabled", boolean.class).invoke(bean, true);
            MethodHandle h = MethodHandles.publicLookup()
                    .findVirtual(sun, "getCurrentThreadAllocatedBytes", MethodType.methodType(long.class))
                    .bindTo(bean);
            long probe = (long) h.invoke();
            return probe >= 0 ? h : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Сколько байт выделил текущий поток за всё время (или -1, если JVM этого не умеет). */
    static long allocated() {
        if (ALLOCATED == null) return -1;
        try {
            return (long) ALLOCATED.invokeExact();
        } catch (Throwable t) {
            return -1;
        }
    }

    static boolean preciseAllocation() {
        return ALLOCATED != null;
    }

    private void trackMemory() throws LuaError {
        long a = allocated();
        if (a >= 0) {
            allocSince += a - allocMark;
            allocMark = a;
        } else {
            allocSince += (long) HOOK_STEP * FALLBACK_BYTES_PER_OP;
        }
        long limit = computer.memoryLimit();
        long free = limit - computer.memoryUsedExcept(this) - liveBytes;
        if (allocSince > Math.max(free, limit / 32)) {
            if (!measure()) throw LuaApis.error("Недостаточно памяти");
        }
    }

    /** Посчитать живые данные процесса. Возвращает false, если все процессы вместе не помещаются в память. */
    private boolean measure() {
        long limit = computer.memoryLimit();
        long others = computer.memoryUsedExcept(this);
        MemoryMeter.Result r = MemoryMeter.measure(MemoryMeter.roots(state, mainRoutine), Math.max(0, limit - others) + (1 << 20));
        liveBytes = r.bytes();
        allocSince = 0;
        long a = allocated();
        if (a >= 0) allocMark = a;
        spend(r.objects() * OPS_PER_OBJECT);
        return others + liveBytes <= limit;
    }

    /**
     * Хватит ли памяти, чтобы выделить ещё {@code bytes} байт (string.rep, чтение файла). Если по последнему
     * подсчёту не хватает — сначала пересчитываем (вдруг большая часть уже мусор).
     */
    boolean reserve(long bytes) {
        long limit = computer.memoryLimit();
        long free = limit - computer.memoryUsedExcept(this) - liveBytes - allocSince;
        if (bytes <= free) return true;
        measure();
        return bytes <= limit - computer.memoryUsedExcept(this) - liveBytes;
    }

    /** collectgarbage("count") — занято КБ, "collect"/"step" — пересчитать память, как сборка мусора. */
    private Varargs collectGarbage(Varargs a) throws LuaError {
        String opt = a.arg(1).isNil() ? "collect" : LuaApis.text(a.arg(1));
        switch (opt) {
            case "count" -> {
                double kb = liveBytes / 1024.0;
                return ValueFactory.varargsOf(valueOf(kb), valueOf((int) (liveBytes % 1024)));
            }
            case "collect", "step" -> {
                measure();
                return opt.equals("step") ? Constants.TRUE : valueOf(0);
            }
            case "isrunning" -> {
                return Constants.TRUE;
            }
            default -> {
                return valueOf(0);
            }
        }
    }

    // ======================= Порции и планирование =======================

    void spend(long ops) {
        budget -= ops;
        opsTotal += ops;
        computer.addOps(ops);
    }

    void refill(long perTick) {
        budget = Math.min(budget + perTick, perTick * Computer.BUDGET_BANK_TICKS);
    }

    boolean needsWork() {
        if (finished || budget <= 0 || computer.mustWait()) return false;
        return state == null || paused || !events.isEmpty() || killRequested;
    }

    /** Попросить машину замереть как можно скорее (из другого потока). */
    void interrupt() {
        pauseRequested = true;
        LuaState s = state;
        if (s == null) return;
        try {
            s.interrupt();
        } catch (IllegalStateException ignored) {
        }
    }

    /** Остановить процесс (из любого потока): он закроется при следующей возможности. */
    void kill() {
        killRequested = true;
        interrupt();
    }

    /** Прервать программу, которая долго не отвечает на Ctrl+T: в ней возникнет ошибка «Прервано». */
    void abort() {
        abortRequested = true;
        LuaState s = state;
        if (s == null) return;
        try {
            s.interrupt();
        } catch (IllegalStateException ignored) {
        }
    }

    /** Закрыть, дождавшись, пока процесс отдаст управление. */
    void closeLocked() {
        synchronized (lock) {
            finished = true;
            state = null;
            mainRoutine = null;
            liveBytes = 0;
        }
    }

    // ======================= Работа =======================

    /** Выполнить кусок работы (поток исполнителя). */
    void work() {
        scheduled.set(false);
        synchronized (lock) {
            if (finished) return;
            if (computer.state() != Computer.State.RUNNING || killRequested) {
                boolean wasKilled = killRequested && computer.state() == Computer.State.RUNNING;
                finished = true;
                state = null;
                mainRoutine = null;
                liveBytes = 0;
                if (wasKilled) computer.machineDone(this, false, "Процесс остановлен");
                return;
            }
            try {
                if (state == null) init();
                long a = allocated();
                if (a >= 0) allocMark = a;
                deadline = System.nanoTime() + ComputerExecutor.sliceNanos();
                while (!killRequested && budget > 0 && !computer.mustWait() && System.nanoTime() < deadline) {
                    if (!resume()) break;     // нечего делать или замерли
                }
                if (finished) {
                    state = null;
                    mainRoutine = null;
                    liveBytes = 0;
                    computer.machineDone(this, true, null);
                }
            } catch (Crash crash) {
                finishWithError(crash.getMessage());
            } catch (Throwable t) {
                finishWithError("Внутренняя ошибка: " + t);
            }
        }
    }

    private void finishWithError(String message) {
        finished = true;
        state = null;
        mainRoutine = null;
        liveBytes = 0;
        computer.machineDone(this, false, message);
    }

    /**
     * Продолжить работу. Возвращает false, если делать больше нечего (ждём событие) или машина замерла.
     */
    private boolean resume() throws Crash {
        pauseRequested = false;
        Varargs args;
        if (paused || !started) {
            paused = false;
            started = true;
            args = Constants.NONE;
        } else {
            Object[] ev = nextEvent();
            if (ev == null) return false;
            args = LuaApis.toVarargs(ev);
        }
        LuaThread thread = state.getCurrentThread();
        if (thread == null || thread == state.getMainThread()) thread = mainRoutine;
        Varargs result;
        try {
            result = LuaThread.run(thread, args);
        } catch (LuaError e) {
            finished = true;
            throw new Crash(LuaApis.errorText(e));
        }
        if (result == null) {
            paused = true;
            return false;
        }
        if (!mainRoutine.isAlive()) {
            finished = true;
            return false;
        }
        LuaValue f = result.first();
        filter = f.isString() ? LuaApis.text(f) : null;
        return true;
    }

    /** Следующее событие, которое ждёт программа (остальные пропускаем, кроме terminate). */
    private Object[] nextEvent() {
        Object[] ev;
        while ((ev = events.poll()) != null) {
            String name = (String) ev[0];
            if (name.equals("terminate")) computer.terminateDelivered();
            if (filter == null || filter.equals(name) || name.equals("terminate")) return ev;
        }
        return null;
    }
}
