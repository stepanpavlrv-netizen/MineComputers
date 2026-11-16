package com.strikerr212.minepc.computer;

import com.strikerr212.minepc.computer.fs.FileSystem;
import com.strikerr212.minepc.computer.fs.FsException;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * «Клетка» процесса: страница-программа сайта клиента на хостинге. Видит как свой диск только папку сайта
 * (и /rom только для чтения), слушает только свои порты, живёт не дольше {@link #TIMEOUT_MS}.
 * Ограничения проверяет Java — из Lua их не обойти.
 */
final class Jail {
    /** Сколько страница может работать: посетитель ждёт ответа 4 с, страница должна успеть ответить или отключиться. */
    static final long TIMEOUT_MS = 3000;
    /** Память страницы, байт. */
    static final long MEMORY = 512 * 1024;
    /** Самый длинный ответ страницы, символов; длиннее — обрезается. */
    static final int MAX_RESULT = 64 * 1024;
    /** Порты, которые страница может открыть (для ответов на свои запросы). */
    static final int PORT_MIN = 40000, PORT_MAX = 49999;
    /** Куда страница может отправлять сообщения: сайты и почта. */
    static final Set<Integer> SEND_PORTS = Set.of(80, 25);

    /** Папка сайта в общей файловой системе, без ведущего «/» (например sites/shop.mine). */
    final String root;
    /** Файл страницы внутри клетки (например /index.lua) и запрос посетителя. */
    final String file;
    final Map<?, ?> request;
    /** Порты, которые открыла эта страница: только на них она получает сообщения. */
    final Set<Integer> ports = ConcurrentHashMap.newKeySet();
    final long startedMillis = System.currentTimeMillis();

    Jail(String root, String file, Map<?, ?> request) {
        this.root = FileSystem.normalize(root);
        this.file = file;
        this.request = request;
    }

    /** Путь страницы → путь в общей файловой системе. «..» за корень не выводит: normalize его съедает. */
    String map(String luaPath) {
        String norm = FileSystem.normalize("/" + luaPath.replace('\\', '/'));
        if (norm.equals("rom") || norm.startsWith("rom/")) return norm;
        return norm.isEmpty() ? root : root + "/" + norm;
    }

    /** Путь только для чтения (системные библиотеки). */
    static boolean readOnly(String mapped) {
        return mapped.equals("rom") || mapped.startsWith("rom/");
    }

    boolean expired() {
        return System.currentTimeMillis() - startedMillis > TIMEOUT_MS;
    }

    static void deny() throws org.squiddev.cobalt.LuaError {
        throw LuaApis.error("Недоступно на хостинге");
    }

    static FsException noFile() {
        return new FsException("Нет такого файла");
    }
}
