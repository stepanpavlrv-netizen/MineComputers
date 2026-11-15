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
        Path folder = folder(server, id);
        // Дискета с программой из деревни: в первый раз на неё записываются файлы программы
        String program = stack.get(com.strikerr212.minepc.registry.ModDataComponents.PROGRAM.get());
        // Программная дискета: файлы пишутся при первом подключении и обновляются, если мод новее дискеты
        if (program != null && com.strikerr212.minepc.computer.ProgramDisks.outdated(folder, program)) {
            try {
                com.strikerr212.minepc.computer.ProgramDisks.populate(folder, program);
            } catch (java.io.IOException e) {
                com.mojang.logging.LogUtils.getLogger().warn("Дискета «{}» не записалась: {}", program, e.toString());
            }
        }
        return new DirMount(folder, capacity, label);
    }
}
