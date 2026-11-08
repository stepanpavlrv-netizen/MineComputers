package com.strikerr212.minepc.computer.fs;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Накопитель, файлы которого лежат в папке мира: minepc/drives/&lt;номер накопителя&gt;/.
 * Следит за объёмом: каждый файл и папка занимают свой размер плюс {@link #ENTRY_COST} байт.
 */
public class DirMount implements Mount {

    public static final int ENTRY_COST = 64;

    private final Path root;
    private final long capacity;
    private final String label;
    private long used = -1;

    public DirMount(Path root, long capacity, String label) {
        this.root = root;
        this.capacity = capacity;
        this.label = label;
    }

    private Path resolve(String path) throws FsException {
        Path p = root.resolve(path).normalize();
        if (!p.startsWith(root)) throw new FsException("Недопустимый путь");
        return p;
    }

    private Path resolveQuiet(String path) {
        try {
            return resolve(path);
        } catch (FsException e) {
            return root;
        }
    }

    @Override
    public boolean exists(String path) {
        return path.isEmpty() || Files.exists(resolveQuiet(path));
    }

    @Override
    public boolean isDirectory(String path) {
        return path.isEmpty() || Files.isDirectory(resolveQuiet(path));
    }

    @Override
    public List<String> list(String path) throws FsException {
        Path p = resolve(path);
        if (path.isEmpty() && !Files.exists(p)) return new ArrayList<>();
        if (!Files.isDirectory(p)) throw new FsException("Нет такой папки");
        try (Stream<Path> s = Files.list(p)) {
            List<String> out = new ArrayList<>();
            s.forEach(f -> out.add(f.getFileName().toString()));
            Collections.sort(out);
            return out;
        } catch (IOException e) {
            throw new FsException("Ошибка чтения");
        }
    }

    @Override
    public long size(String path) throws FsException {
        Path p = resolve(path);
        if (Files.isDirectory(p) || path.isEmpty()) return 0;
        try {
            return Files.size(p);
        } catch (IOException e) {
            throw new FsException("Нет такого файла");
        }
    }

    @Override
    public byte[] read(String path) throws FsException {
        Path p = resolve(path);
        if (Files.isDirectory(p)) throw new FsException("Это папка");
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new FsException("Нет такого файла");
        }
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public long lastModified(String path) {
        try {
            return Files.getLastModifiedTime(resolve(path)).toMillis();
        } catch (IOException | FsException e) {
            return 0;
        }
    }

    @Override
    public synchronized long used() {
        if (used < 0) {
            used = 0;
            if (Files.exists(root)) {
                try (Stream<Path> s = Files.walk(root)) {
                    s.filter(f -> !f.equals(root)).forEach(f -> {
                        used += ENTRY_COST;
                        if (Files.isRegularFile(f)) {
                            try {
                                used += Files.size(f);
                            } catch (IOException ignored) {
                            }
                        }
                    });
                } catch (IOException ignored) {
                }
            }
        }
        return used;
    }

    @Override
    public long capacity() {
        return capacity;
    }

    @Override
    public String label() {
        return label;
    }

    private void ensureParent(Path p) throws FsException {
        Path parent = p.getParent();
        if (parent == null || Files.isDirectory(parent)) return;
        // Создаём недостающие папки (каждая тоже занимает место)
        List<Path> missing = new ArrayList<>();
        for (Path q = parent; q != null && q.startsWith(root) && !Files.exists(q); q = q.getParent()) missing.add(q);
        long need = (long) missing.size() * ENTRY_COST;
        if (used() + need > capacity) throw new FsException("Недостаточно места");
        try {
            Files.createDirectories(parent);
            used += need;
        } catch (IOException e) {
            throw new FsException("Не удалось создать папку");
        }
    }

    @Override
    public synchronized void write(String path, byte[] data, boolean append) throws FsException {
        if (path.isEmpty()) throw new FsException("Это папка");
        Path p = resolve(path);
        if (Files.isDirectory(p)) throw new FsException("Это папка");
        long old = 0;
        boolean existed = Files.exists(p);
        try {
            if (existed) old = Files.size(p);
        } catch (IOException ignored) {
        }
        long after = used() + (append ? data.length : data.length - old) + (existed ? 0 : ENTRY_COST);
        if (after > capacity) throw new FsException("Недостаточно места");
        ensureParent(p);
        try {
            if (append) Files.write(p, data, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            else Files.write(p, data);
        } catch (IOException e) {
            throw new FsException("Ошибка записи");
        }
        recount();
    }

    private void recount() {
        used = -1;
        used();
    }

    @Override
    public synchronized void makeDirectory(String path) throws FsException {
        Path p = resolve(path);
        if (Files.exists(p)) {
            if (Files.isDirectory(p)) return;
            throw new FsException("Файл с таким именем уже есть");
        }
        if (used() + ENTRY_COST > capacity) throw new FsException("Недостаточно места");
        ensureParent(p);
        try {
            Files.createDirectory(p);
        } catch (IOException e) {
            throw new FsException("Не удалось создать папку");
        }
        recount();
    }

    @Override
    public synchronized void delete(String path) throws FsException {
        if (path.isEmpty()) throw new FsException("Нельзя удалить корень накопителя");
        Path p = resolve(path);
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            List<Path> all = new ArrayList<>(s.toList());
            Collections.reverse(all);
            for (Path f : all) Files.delete(f);
        } catch (IOException e) {
            throw new FsException("Не удалось удалить");
        }
        recount();
    }
}
