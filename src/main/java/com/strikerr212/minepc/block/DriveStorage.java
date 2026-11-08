package com.strikerr212.minepc.block;

import com.strikerr212.minepc.computer.fs.DirMount;
import com.strikerr212.minepc.item.DriveItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/** Где лежат файлы накопителей: папка мира / minepc / drives / номер накопителя. */
public final class DriveStorage {

    private DriveStorage() {
    }

    public static Path folder(MinecraftServer server, String id) {
        return server.getWorldPath(LevelResource.ROOT).resolve("minepc").resolve("drives").resolve(id).normalize();
    }

    /** Подключить накопитель из предмета: выдаёт номер, если его ещё нет. */
    public static DirMount mount(MinecraftServer server, ItemStack stack, long capacity, String label) {
        String id = DriveItem.ensureId(stack);
        return new DirMount(folder(server, id), capacity, label);
    }
}
