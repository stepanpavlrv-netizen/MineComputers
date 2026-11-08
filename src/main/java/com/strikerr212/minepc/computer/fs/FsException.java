package com.strikerr212.minepc.computer.fs;

/** Ошибка файловой системы. Текст увидит программа в компьютере, поэтому он по-русски. */
public class FsException extends Exception {
    public FsException(String message) {
        super(message, null, false, false);
    }
}
