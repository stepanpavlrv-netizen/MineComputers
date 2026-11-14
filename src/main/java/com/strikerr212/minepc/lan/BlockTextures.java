package com.strikerr212.minepc.lan;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Текстуры блоков для камер: у сервера своих картинок нет, их собирает игра у игрока (с учётом пакетов ресурсов)
 * и отдаёт серверу — в одиночной игре напрямую, на сервере — пакетами при входе; сервер хранит их рядом с миром.
 * Пока таблицы нет, камеры рисуют цветами с карты.
 */
public final class BlockTextures {

    private BlockTextures() {
    }

    /** Блок с текстурами: имя, верх/бок/низ (номера в списке картинок, -1 — нет) и растение ли крестом. */
    public record Named(String name, int top, int side, int bottom, boolean cross) {
    }

    /** Таблица по номеру блока в реестре (null — у блока нет своей текстуры). */
    private static volatile CameraRender.Tex[] table = new CameraRender.Tex[0];
    /** Сжатая таблица — как пришла (для сохранения). */
    private static volatile byte[] packed;
    private static volatile boolean ready;

    public static CameraRender.Tex[] table() {
        return table;
    }

    public static boolean ready() {
        return ready;
    }

    public static CameraRender.Tex get(Block block) {
        CameraRender.Tex[] t = table;
        int id = BuiltInRegistries.BLOCK.getId(block);
        return id >= 0 && id < t.length ? t[id] : null;
    }

    /** Поставить таблицу из сжатых байтов (пришла от игрока или прочитана с диска). */
    public static boolean install(byte[] data) {
        try {
            table = decode(data);
            packed = data;
            ready = true;
            return true;
        } catch (IOException | RuntimeException e) {
            com.mojang.logging.LogUtils.getLogger().warn("MineComputers: таблица текстур для камер не прочиталась", e);
            return false;
        }
    }

    public static byte[] packed() {
        return packed;
    }

    // ======================= Упаковка =======================

    /** Сжать: картинки 16×16 без повторов, потом блоки по именам (номера в реестре на разных серверах разные). */
    public static byte[] encode(List<int[]> images, List<Named> blocks) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes))) {
            out.writeInt(1);
            out.writeInt(images.size());
            for (int[] img : images) for (int c : img) out.writeInt(c);
            out.writeInt(blocks.size());
            for (Named b : blocks) {
                out.writeUTF(b.name());
                out.writeShort(b.top());
                out.writeShort(b.side());
                out.writeShort(b.bottom());
                out.writeBoolean(b.cross());
            }
        }
        return bytes.toByteArray();
    }

    private static CameraRender.Tex[] decode(byte[] data) throws IOException {
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)))) {
            if (in.readInt() != 1) throw new IOException("неизвестная версия");
            int n = in.readInt();
            if (n < 0 || n > 65536) throw new IOException("картинок " + n);
            int[][] images = new int[n][256];
            for (int[] img : images) for (int i = 0; i < 256; i++) img[i] = in.readInt();
            int blocks = in.readInt();
            CameraRender.Tex[] out = new CameraRender.Tex[BuiltInRegistries.BLOCK.size()];
            Map<Long, CameraRender.Tex> same = new HashMap<>();
            for (int i = 0; i < blocks; i++) {
                String name = in.readUTF();
                int top = in.readShort(), side = in.readShort(), bottom = in.readShort();
                boolean cross = in.readBoolean();
                ResourceLocation rl = ResourceLocation.tryParse(name);
                if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) continue;
                int id = BuiltInRegistries.BLOCK.getId(BuiltInRegistries.BLOCK.get(rl));
                if (id < 0 || id >= out.length || top < 0 && side < 0 && bottom < 0) continue;
                long key = ((long) (top & 0xFFFF) << 33) | ((long) (side & 0xFFFF) << 17) | ((bottom & 0xFFFF) << 1) | (cross ? 1 : 0);
                out[id] = same.computeIfAbsent(key, k -> new CameraRender.Tex(new int[][]{
                        top >= 0 && top < n ? images[top] : null,
                        side >= 0 && side < n ? images[side] : null,
                        bottom >= 0 && bottom < n ? images[bottom] : null}, cross));
            }
            return out;
        }
    }

    /** Список картинок без повторов: номер картинки (новой или такой же, что уже была). */
    public static int intern(List<int[]> images, Map<Integer, List<Integer>> byHash, int[] img) {
        if (img == null) return -1;
        int h = Arrays.hashCode(img);
        List<Integer> list = byHash.computeIfAbsent(h, k -> new ArrayList<>());
        for (int i : list) if (Arrays.equals(images.get(i), img)) return i;
        images.add(img);
        list.add(images.size() - 1);
        return images.size() - 1;
    }

    // ======================= Диск (сервер) =======================

    public static void load(Path file) {
        try {
            if (Files.isRegularFile(file)) install(Files.readAllBytes(file));
        } catch (IOException e) {
            com.mojang.logging.LogUtils.getLogger().warn("MineComputers: не прочитать {}", file, e);
        }
    }

    public static void save(Path file) {
        byte[] p = packed;
        if (p == null) return;
        try {
            Files.write(file, p);
        } catch (IOException e) {
            com.mojang.logging.LogUtils.getLogger().warn("MineComputers: не записать {}", file, e);
        }
    }

    /** Сервер выключился — следующий мир начнёт с чистого листа (в одиночной игре — свой). */
    public static void clear() {
        table = new CameraRender.Tex[0];
        packed = null;
        ready = false;
    }
}
