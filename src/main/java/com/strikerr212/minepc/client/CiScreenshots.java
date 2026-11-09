package com.strikerr212.minepc.client;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.block.DeviceBlock;
import com.strikerr212.minepc.block.PcCaseBlock;
import com.strikerr212.minepc.block.PcCaseBlockEntity;
import com.strikerr212.minepc.hardware.HardwarePresets;
import com.strikerr212.minepc.network.TerminalInputPayload;
import com.strikerr212.minepc.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Только для проверки на GitHub (включается свойством -Dminepc.ciShots=true): игра сама ставит компьютер,
 * включает его, набирает команды и делает скриншоты мира, экрана терминала и окна системного блока.
 * В обычной игре ничего не делает.
 */
@EventBusSubscriber(modid = MineComputers.MODID, value = Dist.CLIENT)
public class CiScreenshots {

    private static final boolean ENABLED = Boolean.getBoolean("minepc.ciShots");
    private static int ticks;
    private static BlockPos casePos;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        ticks++;
        var server = mc.getSingleplayerServer();
        switch (ticks) {
            case 60 -> server.execute(CiScreenshots::buildScene);
            case 140 -> type("help");
            case 200 -> {
                mc.options.hideGui = true;
                shot(mc, "world_help");
            }
            case 210 -> {
                type("clear");
                type("edit demo.lua");
            }
            case 240 -> {
                typeRaw("-- Демо: цвета и счёт");
                key("enter");
                typeRaw("local colorsList = { colors.red, colors.lime, colors.yellow }");
                key("enter");
                typeRaw("for i = 1, 10 do");
                key("enter");
                typeRaw("  term.setTextColor(colorsList[i % 3 + 1])");
                key("enter");
                typeRaw("  print(\"Строка \" .. i)");
                key("enter");
                typeRaw("end");
            }
            case 270 -> {
                mc.options.hideGui = false;
                mc.setScreen(new TerminalScreen(casePos));
            }
            case 290 -> shot(mc, "terminal_editor");
            case 300 -> {
                ctrlKey("s");
                ctrlKey("q");
            }
            case 320 -> type("demo");
            case 360 -> shot(mc, "terminal_demo");
            case 370 -> {
                mc.setScreen(null);
                mc.options.hideGui = true;
            }
            case 390 -> shot(mc, "world_demo");
            case 400 -> {
                mc.options.hideGui = false;
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                    if (p.level().getBlockEntity(casePos) instanceof PcCaseBlockEntity be) {
                        p.openMenu(be, buf -> buf.writeBlockPos(casePos));
                    }
                });
            }
            case 430 -> shot(mc, "case_gui");
            case 433 -> ciCase(mc, null, 1);
            case 437 -> shot(mc, "case_gui_power");
            case 440 -> ciCase(mc, null, 3);
            case 444 -> shot(mc, "case_gui_drives");
            case 447 -> ciCase(mc, com.strikerr212.minepc.block.CaseStyle.RETRO, 0);
            case 451 -> shot(mc, "case_gui_retro");
            case 454 -> ciCase(mc, com.strikerr212.minepc.block.CaseStyle.OFFICE, 0);
            case 458 -> shot(mc, "case_gui_office");
            case 461 -> ciCase(mc, com.strikerr212.minepc.block.CaseStyle.GAMING, 0);
            case 465 -> shot(mc, "case_gui_gaming");
            case 468 -> ciCase(mc, com.strikerr212.minepc.block.CaseStyle.INDUSTRIAL, 2);
            case 472 -> shot(mc, "case_gui_industrial");
            case 475 -> ciCase(mc, com.strikerr212.minepc.block.CaseStyle.SERVER, 0);
            case 479 -> shot(mc, "case_gui_server");
            case 482 -> {
                PcCaseScreen.ciTheme = null;
                PcCaseScreen.ciTab = -1;
                mc.setScreen(null);
                server.execute(() -> {
                    PcCaseBlockEntity be = be();
                    if (be != null) {
                        byte[] src = COLORS_DEMO.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        be.upload("colors.lua", 0, src.length, src);
                    }
                });
                type("colors");
            }
            case 560 -> {
                mc.options.hideGui = true;
                shot(mc, "world_colors");
            }
            case 570 -> {
                mc.options.hideGui = false;
                mc.setScreen(new TerminalScreen(casePos));
            }
            case 600 -> shot(mc, "terminal_colors");
            case 605 -> {
                mc.setScreen(null);
                server.execute(CiScreenshots::placeCases);
            }
            case 607 -> server.execute(CiScreenshots::placeMonitors);
            case 610 -> mc.options.hideGui = true;
            case 800 -> shot(mc, "world_cases");
            case 805 -> server.execute(CiScreenshots::lookAtMonitors);
            case 825 -> shot(mc, "world_monitors");
            case 840 -> mc.stop();
            default -> {
            }
        }
    }

    /** Все корпуса-варианты в ряд перед игроком. */
    private static void placeCases() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        BlockPos row = casePos.east().south(2);
        var variants = com.strikerr212.minepc.block.CaseVariant.values();
        for (int i = 0; i < variants.length; i++) {
            BlockPos pos = row.east(i - variants.length / 2);
            level.setBlockAndUpdate(pos.below(), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
            level.setBlockAndUpdate(pos, ModBlocks.CASES.get(variants[i]).get().defaultBlockState().setValue(PcCaseBlock.FACING, Direction.SOUTH));
            if (variants[i].screen != null) fillParts(level, pos, 3);   // со встроенным экраном — включить
            // Второй ряд — те же корпуса, покрашенные красителями
            BlockPos dyed = pos.north().above();
            level.setBlockAndUpdate(dyed.below(), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
            var paints = com.strikerr212.minepc.block.Paint.values();
            level.setBlockAndUpdate(dyed, ModBlocks.CASES.get(variants[i]).get().defaultBlockState().setValue(PcCaseBlock.FACING, Direction.SOUTH)
                    .setValue(com.strikerr212.minepc.block.Paint.PROPERTY, paints[1 + (i * 5) % (paints.length - 1)]));
        }
        p.teleportTo(level, row.getX() + 0.5, row.getY() + 1.2, row.getZ() + 7.5, 180f, 12f);
    }

    private static void fillCase(ServerLevel level, BlockPos pos, int tier) {
        level.setBlockAndUpdate(pos, ModBlocks.PC_CASE.get().defaultBlockState().setValue(PcCaseBlock.FACING, Direction.NORTH));
        fillParts(level, pos, tier);
    }

    private static void fillParts(ServerLevel level, BlockPos pos, int tier) {
        if (level.getBlockEntity(pos) instanceof PcCaseBlockEntity be) {
            var inv = (IItemHandlerModifiable) be.inventory();
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_MOTHERBOARD, HardwarePresets.motherboard(tier));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_CPU, HardwarePresets.cpu(tier));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_COOLER, HardwarePresets.cooler(tier));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_RAM, HardwarePresets.ram(tier));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_RAM + 1, HardwarePresets.ram(tier));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_GPU, HardwarePresets.gpu(tier));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_PSU, HardwarePresets.psu(tier));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_HDD, HardwarePresets.hdd(2));
            be.togglePower();
        }
    }

    private static void paint(ServerLevel level, BlockPos pos, net.minecraft.world.item.DyeColor dye) {
        var s = level.getBlockState(pos);
        if (s.hasProperty(com.strikerr212.minepc.block.Paint.PROPERTY)) {
            level.setBlockAndUpdate(pos, s.setValue(com.strikerr212.minepc.block.Paint.PROPERTY, com.strikerr212.minepc.block.Paint.of(dye)));
        }
    }

    private static void put(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        level.setBlockAndUpdate(pos, state.setValue(DeviceBlock.FACING, Direction.NORTH));
    }

    /** Мониторы всех видов, каждый на своём системном блоке, экранами к игроку. */
    private static void placeMonitors() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        BlockPos row = casePos.east().south(12).west(7);
        for (int dx = -1; dx <= 22; dx++) {
            for (int dz = -9; dz <= 1; dz++) {
                for (int dy = 0; dy <= 4; dy++) level.setBlockAndUpdate(row.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }
        int[] xs = {0, 2, 4, 6, 8};
        net.minecraft.world.level.block.Block[] singles = {ModBlocks.MONITOR_CRT.get(), ModBlocks.MONITOR_TV.get(),
                ModBlocks.MONITOR_TERMINAL.get(), ModBlocks.MONITOR_PANEL.get(), ModBlocks.MONITOR.get()};
        for (int i = 0; i < xs.length; i++) {
            BlockPos c = row.east(xs[i]);
            fillCase(level, c, 3);
            var state = singles[i].defaultBlockState();
            if (singles[i] == ModBlocks.MONITOR.get()) state = state.setValue(com.strikerr212.minepc.block.MonitorBlock.KEYBOARD, true);
            put(level, c.above(), state);
        }
        // Блочная стена 2×2 без швов, навесная клавиатура на ней
        BlockPos w = row.east(10);
        fillCase(level, w, 4);
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 1; dy <= 2; dy++) put(level, w.east(dx).above(dy), ModBlocks.MONITOR_BLOCK.get().defaultBlockState());
        }
        put(level, w.east().above().north(), ModBlocks.KEYBOARD.get().defaultBlockState()
                .setValue(com.strikerr212.minepc.block.KeyboardBlock.MOUNT, com.strikerr212.minepc.block.KeyboardBlock.Mount.WALL));
        // Белый и ретро-блоки поодиночке, клавиатурная стойка
        fillCase(level, row.east(13), 3);
        put(level, row.east(13).above(), ModBlocks.MONITOR_BLOCK_WHITE.get().defaultBlockState());
        fillCase(level, row.east(15), 2);
        put(level, row.east(15).above(), ModBlocks.MONITOR_BLOCK_RETRO.get().defaultBlockState());
        put(level, row.east(14).north(), ModBlocks.KEYBOARD_STAND.get().defaultBlockState());
        // Мониторы в два блока: широкий и изогнутый (половины ставятся вручную, как это делает предмет)
        for (int k = 0; k < 2; k++) {
            BlockPos c = row.east(17 + k * 3);
            fillCase(level, c, 4);
            var wide = (k == 0 ? ModBlocks.MONITOR_WIDE : ModBlocks.MONITOR_CURVED).get().defaultBlockState()
                    .setValue(DeviceBlock.FACING, Direction.NORTH);
            // Смотрит на север: «вправо» для зрителя — на запад
            level.setBlockAndUpdate(c.above(), wide.setValue(com.strikerr212.minepc.block.WideMonitorBlock.HALF,
                    com.strikerr212.minepc.block.WideMonitorBlock.Half.LEFT));
            level.setBlockAndUpdate(c.above().west(), wide.setValue(com.strikerr212.minepc.block.WideMonitorBlock.HALF,
                    com.strikerr212.minepc.block.WideMonitorBlock.Half.RIGHT));
        }
        // Покрашенные мониторы
        paint(level, row.east(0).above(), net.minecraft.world.item.DyeColor.RED);
        paint(level, row.east(4).above(), net.minecraft.world.item.DyeColor.GREEN);
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 1; dy <= 2; dy++) paint(level, w.east(dx).above(dy), net.minecraft.world.item.DyeColor.BLUE);
        }
        // Соседи сами пересчитают стыки; на всякий случай — обновить формы блочных мониторов
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 1; dy <= 2; dy++) {
                BlockPos b = w.east(dx).above(dy);
                level.setBlockAndUpdate(b, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(level.getBlockState(b), level, b));
            }
        }
    }

    private static void lookAtMonitors() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        BlockPos row = casePos.east().south(12).west(7);
        p.teleportTo(p.serverLevel(), row.getX() + 10, row.getY() + 1, row.getZ() - 8.5, 0f, 8f);
    }

    /** Окно корпуса для скриншота: другая тема и вкладка. */
    private static void ciCase(Minecraft mc, com.strikerr212.minepc.block.CaseStyle style, int tab) {
        PcCaseScreen.ciTheme = style;
        PcCaseScreen.ciTab = tab;
        if (mc.screen instanceof PcCaseScreen s) s.ciRefresh();
    }

    /** Поставить системный блок с деталями ур. 5, монитор и клавиатуру перед игроком и включить. */
    private static void buildScene() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        BlockPos base = p.blockPosition().north(3);
        casePos = base.west();
        level.setBlockAndUpdate(casePos, ModBlocks.PC_CASE.get().defaultBlockState().setValue(PcCaseBlock.FACING, Direction.SOUTH));
        // Монитор из 3×2 блоков: картинка растягивается на всю стену
        for (int dx = 0; dx < 3; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                level.setBlockAndUpdate(base.east(dx).above(dy), ModBlocks.MONITOR.get().defaultBlockState()
                        .setValue(DeviceBlock.FACING, Direction.SOUTH).setValue(com.strikerr212.minepc.block.MonitorBlock.STAND, dy == 0));
            }
        }
        level.setBlockAndUpdate(casePos.above(), ModBlocks.DISK_DRIVE.get().defaultBlockState().setValue(DeviceBlock.FACING, Direction.SOUTH));
        level.setBlockAndUpdate(base.east().south(), ModBlocks.KEYBOARD.get().defaultBlockState().setValue(DeviceBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(casePos) instanceof PcCaseBlockEntity be) {
            var inv = (IItemHandlerModifiable) be.inventory();
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_MOTHERBOARD, HardwarePresets.motherboard(5));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_CPU, HardwarePresets.cpu(5));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_COOLER, HardwarePresets.cooler(5));
            for (int i = 0; i < 4; i++) inv.setStackInSlot(PcCaseBlockEntity.SLOT_RAM + i, HardwarePresets.ram(5));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_GPU, HardwarePresets.gpu(5));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_PSU, HardwarePresets.psu(5));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_M2, HardwarePresets.ssd(5));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_HDD, HardwarePresets.hdd(3));
            be.togglePower();
        }
        // Встать перед монитором и смотреть на него
        p.teleportTo(level, base.getX() + 1.5, base.getY(), base.getZ() + 4.2, 180f, -4f);
    }

    /** Демо: плавный RGB-градиент, псевдографика и Unicode. */
    private static final String COLORS_DEMO = String.join("\n",
            "local w, h = term.maxResolution()",
            "term.setResolution(w, h)",
            "for y = 1, h do",
            "  for x = 1, w do",
            "    local r, g = math.floor((x - 1) / (w - 1) * 255), math.floor((y - 1) / (h - 1) * 255)",
            "    term.setBackgroundColorRGB(r * 65536 + g * 256 + 160)",
            "    term.setCursorPos(x, y)",
            "    term.write(\" \")",
            "  end",
            "end",
            "term.setBackgroundColorRGB(0x202020)",
            "term.setTextColorRGB(0xFFD700)",
            "local function row(t) return \"║  \" .. t .. string.rep(\" \", 30 - unicode.len(t)) .. \"║\" end",
            "local box = {",
            "  \"╔\" .. string.rep(\"═\", 32) .. \"╗\",",
            "  row(\"Привет! Unicode: ✓ ★ ♥ ☺ ♪ ∞\"),",
            "  row(\"Ελληνικά · ▁▂▃▄▅▆▇█ · ░▒▓\"),",
            "  row(\"Экран: \" .. w .. \"×\" .. h .. \", цвет: \" .. term.getDepth() .. \" бит\"),",
            "  \"╚\" .. string.rep(\"═\", 32) .. \"╝\",",
            "}",
            "for i, line in ipairs(box) do term.setCursorPos(4, 2 + i) term.write(line) end",
            "term.setBackgroundColorRGB(0x000000)",
            "term.setTextColorRGB(0xFFFFFF)",
            "term.setCursorPos(1, h)",
            "os.pullEvent(\"key\")",
            "");

    private static PcCaseBlockEntity be() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        return server != null && casePos != null && server.overworld().getBlockEntity(casePos) instanceof PcCaseBlockEntity be ? be : null;
    }

    private static void typeRaw(String text) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            PcCaseBlockEntity be = be();
            if (be != null) for (char c : text.toCharArray()) be.input(TerminalInputPayload.CHAR, String.valueOf(c), false, false);
        });
    }

    private static void key(String name) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            PcCaseBlockEntity be = be();
            if (be != null) be.input(TerminalInputPayload.KEY, name, false, false);
        });
    }

    private static void ctrlKey(String name) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            PcCaseBlockEntity be = be();
            if (be != null) be.input(TerminalInputPayload.KEY, name, true, false);
        });
    }

    private static void type(String line) {
        typeRaw(line);
        key("enter");
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "minepc_" + name + ".png", mc.getMainRenderTarget(), msg -> { });
    }
}
