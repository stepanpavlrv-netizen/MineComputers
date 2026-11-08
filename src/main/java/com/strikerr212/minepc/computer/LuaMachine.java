package com.strikerr212.minepc.computer;

import org.squiddev.cobalt.*;
import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.debug.DebugFrame;
import org.squiddev.cobalt.debug.DebugHook;
import org.squiddev.cobalt.debug.DebugState;
import org.squiddev.cobalt.function.LuaClosure;
import org.squiddev.cobalt.interrupt.InterruptAction;
import org.squiddev.cobalt.lib.CoreLibraries;

import java.io.ByteArrayInputStream;

/**
 * Виртуальная машина Lua одного компьютера (движок Cobalt — тот же, что в CC: Tweaked).
 *
 * Лимит команд: каждые {@link #HOOK_STEP} команд срабатывает счётчик и списывает их из порции.
 * Когда порция кончилась (или вышло время куска работы), машина «замирает» прямо посреди программы
 * и продолжает с того же места в следующий раз.
 */
final class LuaMachine {

    /** Ошибка, после которой компьютер падает (синий экран). */
    static final class Crash extends Exception {
        Crash(String message) {
            super(message, null, false, false);
        }
    }

    static final int HOOK_STEP = 500;

    private final Computer computer;
    private final LuaState state;
    private final LuaThread mainRoutine;
    private volatile boolean pauseRequested;
    private volatile boolean abortRequested;
    private boolean started;
    private boolean paused;
    private long deadline;
    private String filter;
    private boolean finished;

    LuaMachine(Computer computer) throws Crash {
        this.computer = computer;
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
            g.rawset("collectgarbage", Constants.NIL);
            g.rawset("dofile", Constants.NIL);
            g.rawset("loadfile", Constants.NIL);
            g.rawset("print", Constants.NIL);
            LuaApis.install(state, g, computer);
            byte[] bios = ComputerRom.bios();
            LuaClosure fn = LoadState.load(state, new ByteArrayInputStream(bios), "@bios.lua", g);
            mainRoutine = new LuaThread(state, fn);
        } catch (LuaError | CompileException e) {
            throw new Crash("Не удалось запустить BIOS: " + e.getMessage());
        }
        // Счётчик команд наследуют все сопрограммы, которые создаст программа
        mainRoutine.getDebugState().setHook(new DebugHook() {
            @Override
            public void onCount(LuaState s, DebugState ds, DebugFrame frame) {
                computer.spend(HOOK_STEP);
                if (computer.budget() <= 0 || System.nanoTime() > deadline) {
                    pauseRequested = true;
                    s.interrupt();
                }
            }
        }, false, false, false, HOOK_STEP);
    }

    boolean isPaused() {
        return paused;
    }

    boolean isFinished() {
        return finished;
    }

    /** Попросить машину замереть как можно скорее (из другого потока). */
    void interrupt() {
        pauseRequested = true;
        try {
            state.interrupt();
        } catch (IllegalStateException ignored) {
        }
    }

    /**
     * Продолжить работу. Возвращает false, если делать больше нечего (ждём событие) или машина замерла.
     */
    boolean resume(long deadline) throws Crash {
        this.deadline = deadline;
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

    /** Прервать программу, которая долго не отвечает на Ctrl+T: в ней возникнет ошибка «Прервано». */
    void abort() {
        abortRequested = true;
        try {
            state.interrupt();
        } catch (IllegalStateException ignored) {
        }
    }

    /** Следующее событие, которое ждёт программа (остальные пропускаем, кроме terminate). */
    private Object[] nextEvent() {
        Object[] ev;
        while ((ev = computer.pollEvent()) != null) {
            String name = (String) ev[0];
            if (name.equals("terminate")) computer.terminateDelivered();
            if (filter == null || filter.equals(name) || name.equals("terminate")) return ev;
        }
        return null;
    }

    void close() {
        finished = true;
    }
}
