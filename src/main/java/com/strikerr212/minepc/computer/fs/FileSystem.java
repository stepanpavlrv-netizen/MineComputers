package com.strikerr212.minepc.computer.fs;

import java.util.*;

/**
 * Файловая система компьютера: собирает накопители в одно дерево папок.
 * "/" — системный диск, "/rom" — ПЗУ с программами, "/disk" — дискета, "/usb" — флешка и т. д.
 */
public final class FileSystem {

    private final Map<String, Mount> mounts = new TreeMap<>();

    /** Привести путь к виду "a/b/c": без лишних слешей, точек и выходов выше корня. */
    public static String normalize(String path) {
        Deque<String> parts = new ArrayDeque<>();
        for (String part : path.replace('\\', '/').split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                if (!parts.isEmpty()) parts.removeLast();
                continue;
            }
            parts.addLast(part);
        }
        return String.join("/", parts);
    }

    public static String name(String path) {
        String p = normalize(path);
        int i = p.lastIndexOf('/');
        return i < 0 ? p : p.substring(i + 1);
    }

    public static String parent(String path) {
        String p = normalize(path);
        int i = p.lastIndexOf('/');
        return i < 0 ? "" : p.substring(0, i);
    }

    public synchronized void mount(String at, Mount mount) {
        mounts.put(normalize(at), mount);
    }

    public synchronized void unmount(String at) {
        mounts.remove(normalize(at));
    }

    public synchronized Map<String, Mount> mounts() {
        return new TreeMap<>(mounts);
    }

    private record Resolved(String mountPath, Mount mount, String local) {
    }

    private synchronized Resolved resolve(String path) throws FsException {
        String p = normalize(path);
        String best = null;
        for (String m : mounts.keySet()) {
            if (m.isEmpty() || p.equals(m) || p.startsWith(m + "/")) {
                if (best == null || m.length() > best.length()) best = m;
            }
        }
        if (best == null) throw new FsException("Нет такого файла");
        String local = best.isEmpty() ? p : (p.length() == best.length() ? "" : p.substring(best.length() + 1));
        return new Resolved(best, mounts.get(best), local);
    }

    private synchronized boolean isMountPoint(String p) {
        return mounts.containsKey(normalize(p));
    }

    public boolean exists(String path) {
        try {
            if (isMountPoint(path)) return true;
            Resolved r = resolve(path);
            return r.mount.exists(r.local);
        } catch (FsException e) {
            return false;
        }
    }

    public boolean isDirectory(String path) {
        try {
            if (isMountPoint(path)) return true;
            Resolved r = resolve(path);
            return r.mount.isDirectory(r.local);
        } catch (FsException e) {
            return false;
        }
    }

    public boolean isReadOnly(String path) {
        try {
            return resolve(path).mount.isReadOnly();
        } catch (FsException e) {
            return true;
        }
    }

    public List<String> list(String path) throws FsException {
        String p = normalize(path);
        Resolved r = resolve(p);
        TreeSet<String> out = new TreeSet<>(r.mount.list(r.local));
        // Точки подключения других накопителей видны как папки
        for (String m : mounts().keySet()) {
            if (!m.isEmpty() && parent(m).equals(p)) out.add(name(m));
        }
        return new ArrayList<>(out);
    }

    public long size(String path) throws FsException {
        Resolved r = resolve(path);
        return r.mount.size(r.local);
    }

    public byte[] read(String path) throws FsException {
        Resolved r = resolve(path);
        return r.mount.read(r.local);
    }

    public void write(String path, byte[] data, boolean append) throws FsException {
        if (isMountPoint(path)) throw new FsException("Это папка");
        Resolved r = resolve(path);
        r.mount.write(r.local, data, append);
    }

    public void makeDirectory(String path) throws FsException {
        if (isMountPoint(path)) return;
        Resolved r = resolve(path);
        r.mount.makeDirectory(r.local);
    }

    public void delete(String path) throws FsException {
        if (isMountPoint(path)) throw new FsException("Нельзя удалить накопитель");
        Resolved r = resolve(path);
        r.mount.delete(r.local);
    }

    public void copy(String from, String to) throws FsException {
        String f = normalize(from), t = normalize(to);
        if (!exists(f)) throw new FsException("Нет такого файла");
        if (exists(t)) throw new FsException("Такой файл уже есть");
        if (t.equals(f) || t.startsWith(f + "/")) throw new FsException("Нельзя копировать папку в саму себя");
        if (isDirectory(f)) {
            makeDirectory(t);
            for (String child : list(f)) copy(f + "/" + child, t + "/" + child);
        } else {
            write(t, read(f), false);
        }
    }

    public void move(String from, String to) throws FsException {
        if (isMountPoint(from)) throw new FsException("Нельзя переместить накопитель");
        copy(from, to);
        delete(from);
    }

    public long freeSpace(String path) {
        try {
            Mount m = resolve(path).mount;
            return m.isReadOnly() ? 0 : Math.max(0, m.capacity() - m.used());
        } catch (FsException e) {
            return 0;
        }
    }

    public long capacity(String path) {
        try {
            return resolve(path).mount.capacity();
        } catch (FsException e) {
            return 0;
        }
    }

    /** На каком накопителе лежит путь (путь точки подключения). */
    public String drive(String path) {
        try {
            return resolve(path).mountPath;
        } catch (FsException e) {
            return null;
        }
    }

    public String driveLabel(String path) {
        try {
            return resolve(path).mount.label();
        } catch (FsException e) {
            return "";
        }
    }
}
