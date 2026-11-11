package com.strikerr212.minepc.computer;

import java.util.ArrayList;
import java.util.List;

/**
 * Видеокарта компьютера: экран и буферы в видеопамяти (как gpu.allocateBuffer в OpenComputers 1.8).
 * В буфер можно рисовать заранее, а потом одной командой bitblt скопировать на экран.
 * Буфер с номером 0 — сам экран. Объём видеопамяти зависит от видеокарты.
 */
final class Gpu {

    private final Computer computer;
    /** Буферы с номерами 1, 2, ...; освобождённые остаются пустыми (null), чтобы номера не менялись. */
    private final List<Terminal> buffers = new ArrayList<>();
    private int active;

    Gpu(Computer computer) {
        this.computer = computer;
    }

    /** Кадровые буферы (точки) и текстуры — тоже в видеопамяти. */
    private final List<Canvas> canvases = new ArrayList<>();
    private final List<Canvas.Texture> textures = new ArrayList<>();

    synchronized void reset() {
        buffers.clear();
        active = 0;
        for (Canvas c : canvases) c.freed = true;
        canvases.clear();
        textures.clear();
    }

    /** Куда сейчас рисует видеокарта. */
    synchronized Terminal target() {
        return active == 0 ? computer.terminal() : buffers.get(active - 1);
    }

    synchronized boolean targetIsScreen() {
        return active == 0;
    }

    synchronized int active() {
        return active;
    }

    /** Буфер по номеру (0 — экран) или null. */
    synchronized Terminal get(int index) {
        if (index == 0) return computer.terminal();
        if (index < 1 || index > buffers.size()) return null;
        return buffers.get(index - 1);
    }

    /** Сделать буфер текущим. Возвращает прежний номер или -1, если такого буфера нет. */
    synchronized int setActive(int index) {
        Terminal next = get(index);
        if (next == null) return -1;
        Terminal prev = target();
        // Цвета — свойство видеокарты, а не буфера: переносим их
        if (prev != next) {
            if (prev.textIndex() >= 0) next.setTextIndex(prev.textIndex()); else next.setTextRgb(prev.textRgb());
            if (prev.backIndex() >= 0) next.setBackIndex(prev.backIndex()); else next.setBackRgb(prev.backRgb());
        }
        int old = active;
        active = index;
        return old;
    }

    synchronized long usedCells() {
        long n = 0;
        for (Terminal t : buffers) if (t != null) n += t.cells();
        for (Canvas c : canvases) n += c.cells();
        for (Canvas.Texture t : textures) if (t != null) n += t.cells();
        return n;
    }

    /** Кадровый буфер w×h точек (не больше экрана: ширина × двойная высота) или null, если не хватает видеопамяти. */
    synchronized Canvas newCanvas(int w, int h) {
        Terminal screen = computer.terminal();
        if (w < 1 || h < 1 || w > screen.maxWidth() || h > screen.maxHeight() * 2) return null;
        Canvas c = new Canvas(w, h);
        if (usedCells() + c.cells() > totalCells()) return null;
        canvases.add(c);
        return c;
    }

    synchronized void freeCanvas(Canvas c) {
        c.freed = true;
        canvases.remove(c);
    }

    /** Текстура (номер с 1) или -1, если не хватает видеопамяти. */
    synchronized int newTexture(int w, int h, int[] px) {
        Canvas.Texture t = new Canvas.Texture(w, h, px);
        if (usedCells() + t.cells() > totalCells()) return -1;
        for (int i = 0; i < textures.size(); i++) {
            if (textures.get(i) == null) {
                textures.set(i, t);
                return i + 1;
            }
        }
        textures.add(t);
        return textures.size();
    }

    synchronized Canvas.Texture texture(int id) {
        return id < 1 || id > textures.size() ? null : textures.get(id - 1);
    }

    synchronized boolean freeTexture(int id) {
        if (texture(id) == null) return false;
        textures.set(id - 1, null);
        return true;
    }

    long totalCells() {
        return computer.limits().vramCells();
    }

    /** Выделить буфер w×h. Возвращает номер или -1, если не хватает видеопамяти. */
    synchronized int allocate(int w, int h) {
        if (w < 1 || h < 1 || w > Terminal.LIMIT_WIDTH * 2 || h > Terminal.LIMIT_HEIGHT * 2) return -1;
        if (usedCells() + (long) w * h > totalCells()) return -1;
        Terminal t = new Terminal(1, 1);
        t.setLimitUnchecked(w, h);
        Terminal screen = computer.terminal();
        t.setMaxDepth(screen.maxDepth());
        t.setDepth(screen.depth());
        for (int i = 0; i < 16; i++) t.setPaletteColor(i, screen.paletteColor(i));
        t.setTextRgb(screen.textRgb());
        t.setBackRgb(screen.backRgb());
        t.clear();
        for (int i = 0; i < buffers.size(); i++) {
            if (buffers.get(i) == null) {
                buffers.set(i, t);
                return i + 1;
            }
        }
        buffers.add(t);
        return buffers.size();
    }

    synchronized boolean free(int index) {
        if (index < 1 || index > buffers.size() || buffers.get(index - 1) == null) return false;
        buffers.set(index - 1, null);
        if (active == index) active = 0;
        while (!buffers.isEmpty() && buffers.get(buffers.size() - 1) == null) buffers.remove(buffers.size() - 1);
        return true;
    }

    synchronized void freeAll() {
        buffers.clear();
        active = 0;
        for (Canvas c : canvases) c.freed = true;
        canvases.clear();
        textures.clear();
    }

    synchronized List<Integer> indexes() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < buffers.size(); i++) if (buffers.get(i) != null) out.add(i + 1);
        return out;
    }
}
