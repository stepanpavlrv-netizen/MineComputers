package com.strikerr212.minepc.computer;

import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaThread;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.lib.CoreLibraries;

import java.io.ByteArrayInputStream;

/** Проверка при запуске игры: движок Lua подключён и считает (результат пишется в журнал). */
public final class LuaSelfTest {

    private LuaSelfTest() {
    }

    /** Возвращает текст для журнала: «Lua ready: 42» или описание ошибки. */
    public static String run() {
        try {
            LuaState state = LuaState.builder().build();
            CoreLibraries.standardGlobals(state);
            var fn = LoadState.load(state, new ByteArrayInputStream("return 6 * 7".getBytes()), "selftest", state.globals());
            Varargs r = LuaThread.runMain(state, fn);
            return "Lua (Cobalt) ready: " + r.first().toInteger();
        } catch (Throwable t) {
            return "Lua (Cobalt) NOT AVAILABLE: " + t;
        }
    }
}
