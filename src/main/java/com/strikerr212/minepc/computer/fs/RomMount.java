package com.strikerr212.minepc.computer.fs;

import java.util.*;

/** Постоянная память с системой: файлы лежат внутри мода, менять их нельзя. */
public class RomMount implements Mount {

    private final Map<String, byte[]> files;
    private final Set<String> dirs = new HashSet<>();

    public RomMount(Map<String, byte[]> files) {
        this.files = new HashMap<>(files);
        dirs.add("");
        for (String f : files.keySet()) {
            String p = f;
            while (p.contains("/")) {
                p = p.substring(0, p.lastIndexOf('/'));
                dirs.add(p);
            }
        }
    }

    @Override
    public boolean exists(String path) {
        return files.containsKey(path) || dirs.contains(path);
    }

    @Override
    public boolean isDirectory(String path) {
        return dirs.contains(path);
    }

    @Override
    public List<String> list(String path) throws FsException {
        if (!dirs.contains(path)) throw new FsException("Нет такой папки");
        String prefix = path.isEmpty() ? "" : path + "/";
        TreeSet<String> out = new TreeSet<>();
        for (String f : files.keySet()) collect(prefix, f, out);
        for (String d : dirs) collect(prefix, d, out);
        return new ArrayList<>(out);
    }

    private static void collect(String prefix, String p, Set<String> out) {
        if (p.isEmpty() || !p.startsWith(prefix) || p.length() == prefix.length()) return;
        String rest = p.substring(prefix.length());
        int slash = rest.indexOf('/');
        out.add(slash < 0 ? rest : rest.substring(0, slash));
    }

    @Override
    public long size(String path) throws FsException {
        byte[] b = files.get(path);
        if (b != null) return b.length;
        if (dirs.contains(path)) return 0;
        throw new FsException("Нет такого файла");
    }

    @Override
    public byte[] read(String path) throws FsException {
        byte[] b = files.get(path);
        if (b == null) throw new FsException(dirs.contains(path) ? "Это папка" : "Нет такого файла");
        return b;
    }

    @Override
    public String label() {
        return "ПЗУ";
    }
}
