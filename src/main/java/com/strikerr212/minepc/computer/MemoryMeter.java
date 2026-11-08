package com.strikerr212.minepc.computer;

import org.squiddev.cobalt.LuaState;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сколько памяти занимают данные программы: обходит всё, до чего программа может дотянуться
 * (глобальные переменные, стеки сопрограмм, замыкания, таблицы, строки), и складывает размеры.
 *
 * Движок Lua (Cobalt) не считает память сам, поэтому обход делается редко — когда программа
 * выделила столько памяти, сколько у компьютера было свободно (это видно по счётчику JVM).
 * Объекты Java «толще», чем в обычном Lua на C, поэтому размеры умножаются на {@link #SCALE}:
 * числа получаются близкими к настоящему Lua (и к OpenComputers).
 */
final class MemoryMeter {

    /** Итог обхода: байт и объектов (по числу объектов списываются команды — как сборка мусора). */
    record Result(long bytes, long objects, boolean complete) {
    }

    static final double SCALE = 0.6;

    private MemoryMeter() {
    }

    private record ClassInfo(long shallow, Field[] refs, boolean descend) {
    }

    private static final ConcurrentHashMap<Class<?>, ClassInfo> INFO = new ConcurrentHashMap<>();

    private static boolean isLuaClass(Class<?> c) {
        String n = c.getName();
        return n.startsWith("org.squiddev.cobalt.") || n.startsWith("cc.tweaked.cobalt.");
    }

    private static long align(long size) {
        return (size + 7) & ~7L;
    }

    private static int primitiveSize(Class<?> t) {
        if (t == long.class || t == double.class) return 8;
        if (t == int.class || t == float.class) return 4;
        if (t == short.class || t == char.class) return 2;
        if (t == byte.class || t == boolean.class) return 1;
        return 4;     // ссылка (сжатые указатели)
    }

    private static ClassInfo info(Class<?> c) {
        return INFO.computeIfAbsent(c, MemoryMeter::describe);
    }

    private static ClassInfo describe(Class<?> c) {
        boolean descend = isLuaClass(c) && !LuaState.class.isAssignableFrom(c);
        long size = 12;
        List<Field> refs = new ArrayList<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                size += primitiveSize(f.getType());
                if (descend && !f.getType().isPrimitive()) {
                    try {
                        f.setAccessible(true);
                        refs.add(f);
                    } catch (RuntimeException ignored) {
                        // поле недоступно — считаем только размер самого объекта
                    }
                }
            }
        }
        return new ClassInfo(align(size), refs.toArray(new Field[0]), descend);
    }

    /** Корни для обхода машины Lua: все значения из полей LuaState (глобальные, реестр, потоки, метатаблицы). */
    static List<Object> roots(LuaState state, Object... extra) {
        List<Object> out = new ArrayList<>();
        for (Field f : LuaState.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(state);
                if (v != null && (isLuaClass(v.getClass()) || v instanceof Object[])) out.add(v);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        for (Object o : extra) if (o != null) out.add(o);
        return out;
    }

    /**
     * Обойти всё, что достижимо из корней. Обход прекращается, если найдено больше {@code stopAt} байт
     * (память уже кончилась — дальше считать незачем).
     */
    static Result measure(List<Object> roots, long stopAt) {
        IdentitySet seen = new IdentitySet();
        ArrayDeque<Object> stack = new ArrayDeque<>();
        for (Object r : roots) if (seen.add(r)) stack.push(r);
        long bytes = 0, objects = 0;
        long limit = (long) (stopAt / SCALE);
        while (!stack.isEmpty()) {
            Object o = stack.pop();
            objects++;
            Class<?> c = o.getClass();
            if (c.isArray()) {
                Class<?> e = c.getComponentType();
                int len = java.lang.reflect.Array.getLength(o);
                bytes += align(16 + (long) len * primitiveSize(e));
                if (!e.isPrimitive()) {
                    for (Object v : (Object[]) o) {
                        if (v != null && (v instanceof Object[] || isLuaClass(v.getClass())) && seen.add(v)) stack.push(v);
                    }
                }
            } else {
                ClassInfo ci = info(c);
                bytes += ci.shallow;
                for (Field f : ci.refs) {
                    Object v;
                    try {
                        v = f.get(o);
                    } catch (IllegalAccessException | RuntimeException e) {
                        continue;
                    }
                    if (v == null) continue;
                    Class<?> vc = v.getClass();
                    if (vc.isArray()) {
                        if (seen.add(v)) stack.push(v);
                    } else if (isLuaClass(vc) && seen.add(v)) {
                        stack.push(v);
                    }
                }
            }
            if (bytes > limit) return new Result((long) (bytes * SCALE), objects, false);
        }
        return new Result((long) (bytes * SCALE), objects, true);
    }

    /** Множество объектов по ссылке (быстрее IdentityHashMap для миллионов объектов). */
    private static final class IdentitySet {
        private Object[] table = new Object[1 << 12];
        private int size;

        boolean add(Object o) {
            if (size * 2 >= table.length) grow();
            int mask = table.length - 1;
            int i = mix(System.identityHashCode(o)) & mask;
            while (true) {
                Object cur = table[i];
                if (cur == null) {
                    table[i] = o;
                    size++;
                    return true;
                }
                if (cur == o) return false;
                i = (i + 1) & mask;
            }
        }

        private static int mix(int h) {
            h ^= h >>> 16;
            h *= 0x85ebca6b;
            h ^= h >>> 13;
            return h;
        }

        private void grow() {
            Object[] old = table;
            table = new Object[old.length * 2];
            int mask = table.length - 1;
            for (Object o : old) {
                if (o == null) continue;
                int i = mix(System.identityHashCode(o)) & mask;
                while (table[i] != null) i = (i + 1) & mask;
                table[i] = o;
            }
        }
    }
}
