package com.strikerr212.minepc.computer.fs;

import java.util.List;

/**
 * Накопитель, подключённый к файловой системе компьютера: диск, дискета, флешка или ПЗУ с системой.
 * Пути внутри накопителя — без ведущего слеша, "" — корень.
 */
public interface Mount {

    boolean exists(String path);

    boolean isDirectory(String path);

    List<String> list(String path) throws FsException;

    long size(String path) throws FsException;

    byte[] read(String path) throws FsException;

    default boolean isReadOnly() {
        return true;
    }

    default void write(String path, byte[] data, boolean append) throws FsException {
        throw new FsException("Только для чтения");
    }

    default void makeDirectory(String path) throws FsException {
        throw new FsException("Только для чтения");
    }

    default void delete(String path) throws FsException {
        throw new FsException("Только для чтения");
    }

    /** Время последнего изменения (мс с 1970 года), 0 — неизвестно. */
    default long lastModified(String path) {
        return 0;
    }

    /** Объём в байтах (0 — неизвестно / только чтение). */
    default long capacity() {
        return 0;
    }

    default long used() {
        return 0;
    }

    /** Подпись, которую видно в программах (например, "Дискета"). */
    default String label() {
        return "";
    }

    /** Скорость чтения и записи, байт/с (0 — без ограничения, как у ПЗУ). */
    default long bytesPerSecond() {
        return 0;
    }

    /** Цена одной операции с файлом (открыть, проверить, удалить) в байтах передачи — время поиска на диске. */
    default int opBytes() {
        return 0;
    }
}
