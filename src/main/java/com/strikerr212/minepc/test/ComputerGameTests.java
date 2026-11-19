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

    /** Вентиляторы стойки: встают только в модуль сервера (место кулера) и добавляют отвод тепла; в 2U — в полтора раза больше. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rackFansCooling(GameTestHelper h) {
        var state = ModBlocks.PC_CASE.get().defaultBlockState();
        PcCaseBlockEntity mod = new PcCaseBlockEntity(h.absolutePos(new BlockPos(1, 1, 1)), state, 0);
        mod.setLevel(h.getLevel());
        h.assertTrue(mod.fanWallW() == 260, "Встроенные вентиляторы 1U: " + mod.fanWallW());
        ItemStack fans = new ItemStack(com.strikerr212.minepc.registry.ModItems.RACK_FANS_3.get());
        h.assertTrue(mod.placeError(PcCaseBlockEntity.SLOT_COOLER, fans) == null, "Вентиляторы не встают в модуль");
        h.assertTrue(mod.placeError(PcCaseBlockEntity.SLOT_RAM, fans) != null, "Вентиляторы встали не на своё место");
        ((IItemHandlerModifiable) mod.inventory()).setStackInSlot(PcCaseBlockEntity.SLOT_COOLER, fans);
        h.assertTrue(mod.fansTier() == 3 && mod.fanWallW() == 550, "Ур. 3 в 1U: " + mod.fanWallW());
        mod.setTall(true);
        h.assertTrue(mod.fanWallW() == 825, "Ур. 3 в 2U: " + mod.fanWallW());
        PcCaseBlockEntity pc = new PcCaseBlockEntity(h.absolutePos(new BlockPos(2, 1, 1)), state);
        h.assertTrue(pc.placeError(PcCaseBlockEntity.SLOT_COOLER, fans) != null, "Вентиляторы стойки встали в обычный корпус");
        h.succeed();
    }

    /** Пароль камеры: вход по сети, журнал, пауза после неверного пароля, игрокам не уходит отпечаток. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cameraPassword(GameTestHelper h) {
        BlockPos p = new BlockPos(1, 1, 1);
        h.setBlock(p, ModBlocks.CAMERA.get());
        var cam = (com.strikerr212.minepc.lan.CameraBlockEntity) h.getBlockEntity(p);
        h.assertTrue(!cam.locked() && cam.allows("") && cam.login("a1", "ПК", "что угодно", 0) == null, "Без пароля камера должна пускать всех");
        h.assertTrue(cam.accessLog().isEmpty(), "Без пароля входы не пишутся");
        cam.setPassword("1234");
        h.assertTrue(cam.locked() && !cam.allows("") && cam.allows(com.strikerr212.minepc.lan.CameraBlockEntity.hash("1234")), "Пароль не встал");
        h.assertTrue("Неверный пароль".equals(cam.login("a1", "Сосед", "0000", 1000)), "Неверный пароль пустил");
        h.assertTrue(cam.login("a1", "Сосед", "1234", 1500) != null, "Сразу после ошибки — пауза");
        h.assertTrue(cam.login("a2", "Кабинет", "1234", 1500) == null, "Другой адрес не должен ждать");
        h.assertTrue(cam.login("a1", "Сосед", "1234", 2100) == null, "После паузы верный пароль должен пускать");
        var log = cam.accessLog();
        h.assertTrue(log.size() == 3 && !log.get(0).ok() && log.get(1).ok() && log.get(1).computer().equals("Кабинет"), "Журнал: " + log);
        for (int i = 0; i < 60; i++) cam.login("a3", "x", "1234", 10_000 + i);
        h.assertTrue(cam.accessLog().size() == com.strikerr212.minepc.lan.CameraBlockEntity.LOG_MAX, "Журнал не обрезается до 50");
        var upd = cam.getUpdateTag(h.getLevel().registryAccess());
        h.assertTrue(!upd.contains("PassHash") && !upd.contains("AccessLog") && upd.getBoolean("Locked"), "Игрокам ушёл отпечаток или журнал: " + upd);
        h.assertTrue(!upd.toString().contains("1234"), "Пароль в пакете игрокам");
        cam.setPassword("");
        h.assertTrue(!cam.locked() && cam.allows(""), "Пароль не убрался");
        h.assertTrue(com.strikerr212.minepc.lan.CameraBlockEntity.strength("1234") == 1
                && com.strikerr212.minepc.lan.CameraBlockEntity.strength("Ворота-склад-7") == 4, "Оценка надёжности пароля");
        h.succeed();
    }

    /** Дисковод меньше блока:не должен прятать грани соседей (раньше сквозь камень рядом с ним было видно насквозь). */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void diskDriveDoesNotHideNeighbours(GameTestHelper h) {
        BlockPos p = new BlockPos(1, 1, 1);
        h.setBlock(p, ModBlocks.DISK_DRIVE.get());
        h.setBlock(p.east(), net.minecraft.world.level.block.Blocks.STONE);
        var state = h.getBlockState(p);
        h.assertTrue(!state.canOcclude(), "Дисковод закрывает соседей, как полный блок");
        var box = state.getShape(h.getLevel(), h.absolutePos(p)).bounds();
        h.assertTrue(box.maxY <= 0.5 + 1e-6 && box.minX >= 1 / 16.0 - 1e-6, "Рамка дисковода не по модели: " + box);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 800)
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

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void integratedGraphicsDrivesScreen(GameTestHelper h) {
        // Без видеокарты, но процессор с сильным видеоядром: экран как у видеокарты ур. 2
        PcCaseBlockEntity be = buildComputer(h, 3);
        install(be, PcCaseBlockEntity.SLOT_GPU, ItemStack.EMPTY);
        ItemStack apu = HardwarePresets.customExamples().stream()
                .filter(s -> s.getItem() instanceof com.strikerr212.minepc.item.CpuItem
                        && com.strikerr212.minepc.item.CpuItem.data(s).igpu() > 0).findFirst().orElseThrow();
        install(be, PcCaseBlockEntity.SLOT_CPU, apu);
        h.startSequence()
                .thenExecute(() -> {
                    be.togglePower();
                    if (be.computerState() != 1) h.fail("Компьютер с видеоядром не включился");
                })
                .thenWaitUntil(() -> h.assertTrue(be.screenText().contains("/>"), "Нет командной строки:\n" + be.screenText()))
                .thenExecute(() -> type(be, "info"))
                .thenWaitUntil(() -> h.assertTrue(be.screenText().contains("Видеоядро процессора, до 120×40"),
                        "info не показал видеоядро:\n" + be.screenText()))
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
