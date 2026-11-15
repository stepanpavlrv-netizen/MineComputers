package com.strikerr212.minepc.item;

import com.strikerr212.minepc.computer.ProgramDisks;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.registry.ModDataComponents;
import com.strikerr212.minepc.registry.ModItems;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/** Дискеты с программами — в сундуках деревень (в каждом третьем сундуке, программа — по весу). */
@EventBusSubscriber(modid = MineComputers.MODID)
public final class ProgramLoot {

    /** Вероятность найти дискету в сундуке деревни. */
    public static final float CHANCE = 0.3f;

    private ProgramLoot() {
    }

    @SubscribeEvent
    public static void onLootLoad(LootTableLoadEvent event) {
        if (!event.getName().getNamespace().equals("minecraft") || !event.getName().getPath().startsWith("chests/village/")) return;
        LootPool.Builder pool = LootPool.lootPool()
                .name("minepc_program_disks")
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(CHANCE));
        for (ProgramDisks.Program p : ProgramDisks.ALL) {
            pool.add(LootItem.lootTableItem(ModItems.FLOPPY_DISK.get())
                    .setWeight(p.weight())
                    .apply(SetComponentsFunction.setComponent(ModDataComponents.PROGRAM.get(), p.id())));
        }
        event.getTable().addPool(pool.build());
    }
}
