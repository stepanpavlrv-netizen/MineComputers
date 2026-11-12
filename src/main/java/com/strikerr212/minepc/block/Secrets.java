package com.strikerr212.minepc.block;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Пароли (BIOS, BMC) хранятся не открытым текстом, а отпечатком SHA-256: детали с настройками ездят по сети
 * к игрокам (в окне корпуса, в руках), и пароль из них не должен читаться. Старые сохранения с паролем открытым
 * текстом тоже подходят и при следующей записи превращаются в отпечаток.
 */
public final class Secrets {

    private static final String PREFIX = "h:";

    private Secrets() {
    }

    public static String hash(String password) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(("minepc:" + password).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(PREFIX);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean hashed(String stored) {
        return stored.length() == PREFIX.length() + 64 && stored.startsWith(PREFIX);
    }

    /** Как хранить: "" — пароля нет, иначе отпечаток (уже готовый отпечаток не трогаем). */
    public static String store(String password) {
        if (password == null || password.isEmpty()) return "";
        return hashed(password) ? password : hash(password);
    }

    /** Подходит ли введённый пароль к сохранённому (отпечатку или старому открытому тексту). */
    public static boolean matches(String stored, String password) {
        if (password == null) password = "";
        return hashed(stored) ? stored.equals(hash(password)) : stored.equals(password);
    }
}
