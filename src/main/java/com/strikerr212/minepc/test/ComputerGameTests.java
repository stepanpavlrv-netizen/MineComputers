package com.strikerr212.minepc.test;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.block.PcCaseBlockEntity;
import com.strikerr212.minepc.hardware.HardwarePresets;
import com.strikerr212.minepc.network.TerminalInputPayload;
import com.strikerr212.minepc.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Игровые тесты: запускаются на настоящем сервере Minecraft (./gradlew runGameTestServer, и на GitHub
 * после каждой сборки). Проверяют, что компьютер собирается, включается и выполняет Lua внутри игры.
 */
@GameTestHolder(MineComputers.MODID)
@PrefixGameTestTemplate(false)
public class ComputerGameTests {

    private static final BlockPos CASE = new BlockPos(2, 1, 2);

    private static void install(PcCaseBlockEntity be, int slot, ItemStack stack) {
        ((IItemHandlerModifiable) be.inventory()).setStackInSlot(slot, stack);
    }

    private static PcCaseBlockEntity buildComputer(GameTestHelper h, int tier) {
        h.setBlock(CASE, ModBlocks.PC_CASE.get());
        h.setBlock(CASE.east(), ModBlocks.MONITOR.get());
        h.setBlock(CASE.east().south(), ModBlocks.KEYBOARD.get());
        PcCaseBlockEntity be = h.getBlockEntity(CASE);
        install(be, PcCaseBlockEntity.SLOT_MOTHERBOARD, HardwarePresets.motherboard(tier));
        install(be, PcCaseBlockEntity.SLOT_CPU, HardwarePresets.cpu(tier));
        install(be, PcCaseBlockEntity.SLOT_COOLER, HardwarePresets.cooler(tier));
        install(be, PcCaseBlockEntity.SLOT_RAM, HardwarePresets.ram(tier));
        install(be, PcCaseBlockEntity.SLOT_RAM + 1, HardwarePresets.ram(tier));
        install(be, PcCaseBlockEntity.SLOT_GPU, HardwarePresets.gpu(tier));
        install(be, PcCaseBlockEntity.SLOT_PSU, HardwarePresets.psu(tier));
        install(be, PcCaseBlockEntity.SLOT_SATA_SSD, HardwarePresets.ssd(1));
        return be;
    }

    private static void type(PcCaseBlockEntity be, String line) {
        be.input(TerminalInputPayload.CHAR, line, false, false);
        be.input(TerminalInputPayload.KEY, "enter", false, false);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void computerBootsAndRunsLua(GameTestHelper h) {
        PcCaseBlockEntity be = buildComputer(h, 3);
        h.startSequence()
                .thenExecute(() -> {
                    be.togglePower();
                    if (be.computerState() != 1) h.fail("Компьютер не включился");
                })
                .thenWaitUntil(() -> h.assertTrue(be.screenText().contains("/>"), "Нет командной строки:\n" + be.screenText()))
                .thenExecute(() -> h.assertTrue(be.hasKeyboard(), "Клавиатура не найдена"))
                .thenExecute(() -> type(be, "lua"))
                .thenWaitUntil(() -> h.assertTrue(be.screenText().contains("lua>"), "Lua не запустился:\n" + be.screenText()))
                .thenExecute(() -> type(be, "6 * 7"))
                .thenWaitUntil(() -> h.assertTrue(be.screenText().contains("42"), "Lua не посчитал:\n" + be.screenText()))
                .thenExecute(() -> {
                    type(be, "exit()");
                    type(be, "mkdir test");
                })
                .thenIdle(20)
                .thenExecute(() -> type(be, "ls"))
                .thenWaitUntil(() -> h.assertTrue(be.screenText().contains("test"), "Папка не создалась:\n" + be.screenText()))
                .thenExecute(() -> {
                    // Сменили деталь — компьютер выключается
                    install(be, PcCaseBlockEntity.SLOT_GPU, ItemStack.EMPTY);
                    h.assertTrue(be.computerState() == 0, "Компьютер должен выключиться при смене деталей");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void computerWithoutStorageDoesNotBoot(GameTestHelper h) {
        PcCaseBlockEntity be = buildComputer(h, 1);
        install(be, PcCaseBlockEntity.SLOT_SATA_SSD, ItemStack.EMPTY);
        be.togglePower();
        h.assertTrue(be.computerState() == 0, "Без накопителя компьютер не должен включаться");
        h.succeed();
    }
}
