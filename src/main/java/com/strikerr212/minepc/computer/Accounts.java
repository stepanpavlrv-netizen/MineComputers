package com.strikerr212.minepc.computer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Пользователи компьютера, пароли (отпечатками с солью — из программ их не прочитать), журнал входов и защита от
 * перебора: после неверного пароля следующая попытка — не раньше чем через 2 с, после 5 ошибок подряд адрес
 * закрыт на 10 минут. Пока пользователей нет, компьютер открыт: все — администраторы.
 */
public final class Accounts {

    public static final int MAX_FAILS = 5;
    public static final long LOCK_MS = 10 * 60_000L;
    public static final long RETRY_MS = 2_000L;
    public static final int MAX_USERS = 16;
    private static final int JOURNAL = 200;

    public record User(String name, String hash, boolean admin) {
    }

    /** Ответ проверки пароля: ok, причина (wrong, locked, wait, nouser) и сколько секунд ждать, номер ошибки. */
    public record Check(boolean ok, String reason, int waitSeconds, int fails) {
    }

    private static final class Fails {
        int count;
        long lockedUntil, nextTry;
    }

    private final LinkedHashMap<String, User> users = new LinkedHashMap<>();
    private final ArrayDeque<String> journal = new ArrayDeque<>();
    private final Map<String, Fails> fails = new HashMap<>();
    private String notify = "";
    private volatile boolean dirty;
    private static final SecureRandom RANDOM = new SecureRandom();

    // ======================= Пользователи =======================

    public synchronized boolean open() {
        return users.isEmpty();
    }

    public synchronized List<User> list() {
        return new ArrayList<>(users.values());
    }

    public synchronized boolean exists(String name) {
        return users.containsKey(name);
    }

    public synchronized boolean isAdmin(String name) {
        User u = name == null ? null : users.get(name);
        return u != null && u.admin();
    }

    /** Добавить пользователя; null — получилось, иначе текст ошибки. Первый пользователь — всегда администратор. */
    public synchronized String add(String name, String password, boolean admin) {
        if (name == null || !name.matches("[a-zA-Z0-9_\\-а-яА-ЯёЁ]{1,16}")) return "Имя: буквы, цифры, - и _, до 16 знаков";
        if (users.containsKey(name)) return "Такой пользователь уже есть";
        if (users.size() >= MAX_USERS) return "Не больше " + MAX_USERS + " пользователей";
        String err = checkPassword(password);
        if (err != null) return err;
        users.put(name, new User(name, hash(password), admin || users.isEmpty()));
        changed();
        return null;
    }

    public synchronized String remove(String name) {
        User u = users.get(name);
        if (u == null) return "Нет такого пользователя";
        if (u.admin() && users.values().stream().filter(User::admin).count() == 1 && users.size() > 1) {
            return "Это последний администратор — сначала сделайте администратором другого";
        }
        users.remove(name);
        changed();
        return null;
    }

    public synchronized String setPassword(String name, String password) {
        User u = users.get(name);
        if (u == null) return "Нет такого пользователя";
        String err = checkPassword(password);
        if (err != null) return err;
        users.put(name, new User(name, hash(password), u.admin()));
        changed();
        return null;
    }

    public synchronized String setAdmin(String name, boolean admin) {
        User u = users.get(name);
        if (u == null) return "Нет такого пользователя";
        if (!admin && u.admin() && users.values().stream().filter(User::admin).count() == 1) return "Это последний администратор";
        users.put(name, new User(name, u.hash(), admin));
        changed();
        return null;
    }

    private static String checkPassword(String pw) {
        if (pw == null || pw.length() < 4) return "Пароль — не короче 4 знаков";
        if (pw.length() > 64) return "Пароль — не длиннее 64 знаков";
        return null;
    }

    /** Подходит ли пароль — без учёта попыток (для sudo своим же паролем после входа). */
    public synchronized boolean matches(String name, String password) {
        User u = users.get(name);
        return u != null && verify(u.hash(), password);
    }

    /**
     * Попытка входа с адреса from ("local" — с клавиатуры): учитывает паузу между попытками и блокировку,
     * пишет журнал. alert — вызывается, когда адрес только что закрыли (для письма владельцу).
     */
    public synchronized Check login(String name, String password, String from, String when, long now, java.util.function.Consumer<String> alert) {
        Fails f = fails.computeIfAbsent(from, k -> new Fails());
        if (f.lockedUntil > now) return new Check(false, "locked", (int) ((f.lockedUntil - now + 999) / 1000), f.count);
        if (f.nextTry > now) return new Check(false, "wait", (int) ((f.nextTry - now + 999) / 1000), f.count);
        User u = users.get(name);
        if (u != null && verify(u.hash(), password)) {
            fails.remove(from);
            log(when, "вход " + name + " с " + from + " — ok");
            return new Check(true, "ok", 0, 0);
        }
        f.count++;
        f.nextTry = now + RETRY_MS * Math.min(f.count, 3);
        log(when, (u == null ? "нет пользователя " : "неверный пароль ") + name + " с " + from);
        if (f.count >= MAX_FAILS) {
            f.lockedUntil = now + LOCK_MS;
            int n = f.count;
            f.count = 0;
            log(when, n + " ошибок — " + from + " закрыт на " + LOCK_MS / 60_000 + " мин");
            if (alert != null) alert.accept(from);
            return new Check(false, "locked", (int) (LOCK_MS / 1000), n);
        }
        return new Check(false, "wrong", (int) ((f.nextTry - now + 999) / 1000), f.count);
    }

    // ======================= Журнал =======================

    public synchronized void log(String when, String text) {
        journal.addLast(when + "  " + text);
        while (journal.size() > JOURNAL) journal.removeFirst();
        changed();
    }

    public synchronized List<String> journal(int last) {
        List<String> all = new ArrayList<>(journal);
        return all.subList(Math.max(0, all.size() - last), all.size());
    }

    public synchronized String notifyAddress() {
        return notify;
    }

    public synchronized void setNotify(String address) {
        notify = address == null ? "" : address.trim();
        changed();
    }

    // ======================= Хранение =======================

    private void changed() {
        dirty = true;
    }

    /** Было ли что сохранить (и сбросить признак). */
    public boolean takeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    /** В строку для сохранения: пользователи, адрес для писем, журнал. */
    public synchronized String save() {
        StringBuilder sb = new StringBuilder("v1\n");
        for (User u : users.values()) sb.append("u\t").append(u.name()).append('\t').append(u.hash()).append('\t').append(u.admin() ? 1 : 0).append('\n');
        if (!notify.isEmpty()) sb.append("n\t").append(notify.replace('\n', ' ')).append('\n');
        for (String j : journal) sb.append("j\t").append(j.replace('\n', ' ')).append('\n');
        return sb.toString();
    }

    public synchronized void load(String data) {
        users.clear();
        journal.clear();
        notify = "";
        if (data == null || !data.startsWith("v1\n")) return;
        for (String line : data.split("\n")) {
            String[] p = line.split("\t", -1);
            if (p[0].equals("u") && p.length >= 4) users.put(p[1], new User(p[1], p[2], p[3].equals("1")));
            else if (p[0].equals("n") && p.length >= 2) notify = p[1];
            else if (p[0].equals("j") && p.length >= 2) journal.addLast(p[1]);
        }
    }

    // ======================= Отпечатки =======================

    private static String hash(String password) {
        byte[] salt = new byte[8];
        RANDOM.nextBytes(salt);
        StringBuilder s = new StringBuilder();
        for (byte b : salt) s.append(String.format("%02x", b));
        return "s:" + s + ":" + digest(s + ":" + password);
    }

    private static boolean verify(String stored, String password) {
        if (password == null) return false;
        String[] p = stored.split(":", 3);
        if (p.length != 3) return false;
        return MessageDigest.isEqual(p[2].getBytes(StandardCharsets.UTF_8), digest(p[1] + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static String digest(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(("minepc-user:" + s).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
