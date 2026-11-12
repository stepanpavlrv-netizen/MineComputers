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
import net.minecraft.world.level.block.state.BlockState;
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
            case 830 -> server.execute(CiScreenshots::placeNetwork);
            case 990 -> { typeAt(netA, "hostname office-1"); typeAt(netB, "hostname server"); typeAt(netC, "hostname storage");
                typeAt(netD, "hostname laptop"); }
            case 1000 -> typeAt(netD, "wifi connect Office minecraft1");
            case 1005 -> { typeAt(netB, "chat"); typeAt(netA, "chat"); }
            case 1030 -> typeAt(netA, "привет по кабелю!");
            case 1045 -> typeAt(netB, "и тебе привет, A");
            case 1050 -> typeAt(netC, "ping office-1 3");
            case 1065 -> shot(mc, "world_network");
            // Вблизи: вилки в гнёздах коммутатора и роутера
            case 1066 -> server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                BlockPos sw = netA.east(3).south(2);
                p.teleportTo(p.serverLevel(), sw.getX() + 1.6, sw.getY() + 1.1, sw.getZ() - 1.4, 45f, 45f);
            });
            case 1069 -> shot(mc, "world_ports");
            case 1070 -> {
                mc.options.hideGui = false;
                mc.setScreen(new TerminalScreen(netB));
            }
            case 1085 -> shot(mc, "terminal_chat");
            case 1090 -> {
                mc.setScreen(null);
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                    if (p.serverLevel().getBlockEntity(netA) instanceof PcCaseBlockEntity be) {
                        p.openMenu(be, buf -> buf.writeBlockPos(netA));
                    }
                });
            }
            case 1100 -> ciCase(mc, null, 4);
            case 1106 -> shot(mc, "case_gui_net");
            case 1110 -> {
                mc.setScreen(null);
                typeAt(netC, "ping server 2");
                server.execute(CiScreenshots::logNetwork);
                // Подъём по кирпичной стенке: внутренний угол, стенка, поверху, вниз
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                    BlockPos w = netA.east(3).north(2);
                    p.teleportTo(p.serverLevel(), w.getX() + 3.2, w.getY() + 1.9, w.getZ() + 0.5, 90f, 30f);
                });
            }
            case 1118 -> shot(mc, "world_climb");
            case 1160 -> mc.setScreen(new TerminalScreen(netC));
            case 1175 -> shot(mc, "terminal_ping");
            case 1180 -> {
                mc.setScreen(null);
                typeAt(netD, "wifi scan");
            }
            case 1190 -> typeAt(netD, "wifi");
            case 1200 -> typeAt(netD, "ping storage 2");
            case 1240 -> mc.setScreen(new TerminalScreen(netD));
            case 1255 -> shot(mc, "terminal_wifi");
            case 1260 -> {
                mc.setScreen(null);
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                    if (p.serverLevel().getBlockEntity(netRouter) instanceof com.strikerr212.minepc.lan.RouterBlockEntity r) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                                com.strikerr212.minepc.lan.RouterBlock.openPayload(p.serverLevel(), netRouter, r));
                    }
                    logNetwork();
                });
            }
            case 1278 -> com.mojang.logging.LogUtils.getLogger().info("[CI] экран: {}", mc.screen);
            case 1280 -> shot(mc, "router_gui");
            case 1290 -> {
                mc.setScreen(null);
                server.execute(CiScreenshots::placeLan2);
            }
            case 1450 -> { typeAt(lanE, "hostname rack-e"); typeAt(lanF, "hostname rack-f");
                typeAt(lanJ1, "hostname tray-1"); typeAt(lanJ2, "hostname tray-2"); }
            case 1465 -> { typeAt(lanE, "ping rack-f 2"); typeAt(lanJ1, "ping tray-2 2"); }
            case 1515 -> { typeAt(lanE, "switch"); typeAt(lanJ1, "peers"); }
            case 1520 -> {
                mc.options.hideGui = true;
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                    p.teleportTo(p.serverLevel(), lanBase.getX() + 7.5, lanBase.getY() + 1.5, lanBase.getZ() - 8.5, 0f, 22f);
                });
            }
            case 1540 -> shot(mc, "world_lan2");
            case 1545 -> {
                mc.options.hideGui = false;
                mc.setScreen(new TerminalScreen(lanE));
            }
            case 1560 -> shot(mc, "terminal_rack");
            case 1565 -> mc.setScreen(new TerminalScreen(lanJ1));
            case 1580 -> shot(mc, "terminal_tray");
            case 1585 -> {
                mc.setScreen(null);
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                            com.strikerr212.minepc.lan.SwitchPorts.openPayload(p.serverLevel(), lanBase));
                    logLan2();
                });
            }
            case 1598 -> com.mojang.logging.LogUtils.getLogger().info("[CI] экран: {}", mc.screen);
            case 1600 -> shot(mc, "switch_gui");
            case 1602 -> {
                mc.setScreen(null);
                mc.options.hideGui = true;
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                    p.teleportTo(p.serverLevel(), lanBase.getX() + 0.5, lanBase.getY(), lanBase.getZ() - 3.2, 0f, 30f);
                });
            }
            case 1625 -> shot(mc, "world_rack");
            // BIOS: классический (плата ур. 3) и UEFI (плата ур. 5)
            case 1630 -> { mc.options.hideGui = false; typeAt(lanE, "reboot"); typeAt(lanJ2, "reboot"); }
            case 1636, 1642, 1648, 1654, 1660 -> { keyAt(lanE, "delete"); keyAt(lanJ2, "delete"); }
            // Висячий провод сквозь блок: камень поперёк воздушной линии — линия обрывается и падает
            case 1670 -> server.execute(() -> {
                ServerLevel lvl = server.overworld();
                var data = com.strikerr212.minepc.lan.LanData.get(lvl);
                BlockPos mid = lanK3.east(7);
                com.mojang.logging.LogUtils.getLogger().info("[CI-CORD] до камня: линий {}, блок на пути {}", data.allCords().size(),
                        com.strikerr212.minepc.lan.CordShape.blocker(lvl, lanK3.east(2), lanK4.west(2)));
                lvl.setBlockAndUpdate(mid, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            });
            case 1695 -> server.execute(() -> {
                ServerLevel lvl = server.overworld();
                var data = com.strikerr212.minepc.lan.LanData.get(lvl);
                com.mojang.logging.LogUtils.getLogger().info("[CI-CORD] после камня: линий {}, K3 видит {}", data.allCords().size(),
                        com.strikerr212.minepc.lan.LanManager.get(lvl).reach(lvl, lanK3).size());
            });
            case 1700 -> mc.setScreen(new TerminalScreen(lanE));
            case 1715 -> shot(mc, "bios_classic");
            case 1716, 1718, 1720, 1722, 1724 -> keyAt(lanE, "right");
            case 1740 -> shot(mc, "bios_classic_monitor");
            case 1745 -> mc.setScreen(new TerminalScreen(lanJ2));
            case 1760 -> shot(mc, "bios_uefi");
            case 1761, 1763 -> keyAt(lanJ2, "right");
            case 1780 -> shot(mc, "bios_uefi_boot");
            case 1781, 1783, 1785 -> keyAt(lanJ2, "right");
            case 1800 -> shot(mc, "bios_uefi_monitor");
            // 3D-игра bunker на кадровом буфере: выходим из BIOS (F10), запускаем демо и записываем видео
            case 1806 -> keyAt(lanJ2, "f10");
            case 2000 -> shot(mc, "bunker_boot");
            case 2002 -> typeAt(lanJ2, "bunker demo 20");
            case 2040 -> { startRecording(mc); perf(mc, true, lanJ2); }
            case 2140 -> { perf(mc, false, lanJ2); shot(mc, "bunker_1"); }
            case 2220 -> shot(mc, "bunker_2");
            // Сборочный стол 2.0: на обычном масштабе (окно 854×480 → 427×240) и на масштабе 1, в режиме выживания
            case 2270 -> {
                mc.setScreen(null);
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                    p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                    p.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.QUARTZ, 20));
                    p.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COPPER_INGOT, 4));
                    p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) ->
                            new com.strikerr212.minepc.menu.AssemblyMenu(id, inv, net.minecraft.world.inventory.ContainerLevelAccess.NULL),
                            net.minecraft.network.chat.Component.translatable("container.minepc.assembly_table")));
                });
            }
            case 2280 -> assemblyTab(mc, 1);
            case 2290 -> shot(mc, "assembly_cpu");
            case 2292 -> assemblyTab(mc, 4);
            case 2297 -> AssemblyScreen.ciTemplates = true;
            case 2302 -> shot(mc, "assembly_gpu_templates");
            case 2305 -> {
                AssemblyScreen.ciTemplates = false;
                assemblyTab(mc, 1);
                mc.options.guiScale().set(1);
                mc.resizeDisplay();
            }
            case 2315 -> shot(mc, "assembly_cpu_big");
            case 2318 -> assemblyTab(mc, 2);
            case 2326 -> shot(mc, "assembly_cooler_big");
            case 2330 -> {
                mc.options.guiScale().set(0);
                mc.resizeDisplay();
                mc.setScreen(null);
            }
            // Серверная стойка: две стойки друг на друге, три сервера и консоль; один сервер выдвинут
            case 2342 -> server.execute(CiScreenshots::placeRack);
            case 2395 -> {
                typeHost(new com.strikerr212.minepc.block.HostRef(rackPos, 0), "hostname srv-a");
                typeHost(new com.strikerr212.minepc.block.HostRef(rackPos, 1), "hostname srv-b");
                typeHost(new com.strikerr212.minepc.block.HostRef(rackPos.above(), 0), "hostname srv-c");
            }
            case 2402 -> typeHost(new com.strikerr212.minepc.block.HostRef(rackPos, 0), "ping srv-b 2");
            case 2425 -> shot(mc, "world_server_rack");
            case 2428 -> server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                if (p.serverLevel().getBlockEntity(rackPos) instanceof com.strikerr212.minepc.block.RackBlockEntity r && r.server(1) != null) {
                    p.openMenu(r.server(1), buf -> {
                        buf.writeBlockPos(rackPos);
                        buf.writeVarInt(1);
                    });
                }
                var lan = com.strikerr212.minepc.lan.LanManager.get(p.serverLevel());
                StringBuilder sb = new StringBuilder("[CI-RACK] srv-a ->");
                for (var r : lan.reach(p.serverLevel(), new com.strikerr212.minepc.block.HostRef(rackPos, 0))) {
                    sb.append(' ').append(r.pos().toShortString()).append('#').append(r.slot()).append(" hops=").append(r.hops());
                }
                com.mojang.logging.LogUtils.getLogger().info(sb.toString());
            });
            case 2436 -> shot(mc, "rack_server_gui");
            case 2440 -> mc.setScreen(new TerminalScreen(new com.strikerr212.minepc.block.HostRef(rackPos, 0)));
            case 2452 -> shot(mc, "rack_terminal");
            case 2455 -> mc.setScreen(new ConsoleScreen(rackPos));
            case 2462 -> shot(mc, "rack_console");
            case 2465 -> mc.setScreen(null);
            case 2475 -> mc.stop();
            default -> {
            }
        }
    }

    private static BlockPos rackPos = BlockPos.ZERO;

    /** Ввод в компьютер (и в модуль стойки) из CI. */
    private static void typeHost(com.strikerr212.minepc.block.HostRef host, String line) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            PcCaseBlockEntity be = com.strikerr212.minepc.block.Hosts.get(server.overworld(), host);
            if (be != null) {
                for (char c : line.toCharArray()) be.input(TerminalInputPayload.CHAR, String.valueOf(c), false, false);
                be.input(TerminalInputPayload.KEY, "enter", false, false);
            }
        });
    }

    /** Две стойки друг на друге: внизу два сервера и консоль, вверху сервер; сзади кабели в коммутатор. */
    private static void placeRack() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        BlockPos rk = casePos.east().south(26).west(14);
        rackPos = rk;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                level.setBlockAndUpdate(rk.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
                for (int dy = 0; dy <= 3; dy++) level.setBlockAndUpdate(rk.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }
        BlockState rackState = ModBlocks.SERVER_RACK.get().defaultBlockState().setValue(com.strikerr212.minepc.block.RackBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(rk, rackState);
        level.setBlockAndUpdate(rk.above(), rackState);
        if (level.getBlockEntity(rk) instanceof com.strikerr212.minepc.block.RackBlockEntity r) {
            r.insert(0, new ItemStack(com.strikerr212.minepc.registry.ModItems.SERVER_MODULE.get()));
            r.insert(1, new ItemStack(com.strikerr212.minepc.registry.ModItems.SERVER_MODULE.get()));
            r.insert(2, new ItemStack(com.strikerr212.minepc.registry.ModItems.CONSOLE_MODULE.get()));
            fillModule(r.server(0), 3);
            fillModule(r.server(1), 4);
        }
        if (level.getBlockEntity(rk.above()) instanceof com.strikerr212.minepc.block.RackBlockEntity r) {
            r.insert(0, new ItemStack(com.strikerr212.minepc.registry.ModItems.SERVER_MODULE.get()));
            fillModule(r.server(0), 2);
        }
        // Сзади: два провода из гнёзд модулей 1 и 2 в коммутатор
        BlockPos sw = rk.south(3);
        level.setBlockAndUpdate(sw, ModBlocks.NETWORK_SWITCH.get().defaultBlockState().setValue(com.strikerr212.minepc.lan.SwitchBlock.FACING, Direction.NORTH));
        int blue = net.minecraft.world.item.DyeColor.BLUE.getId(), orange = net.minecraft.world.item.DyeColor.ORANGE.getId();
        for (int dz = 1; dz <= 2; dz++) {
            wire(level, rk.south(dz), blue, 2, 0);
            wire(level, rk.south(dz), orange, 4, 2);
        }
        reconnectArea(level, rk.offset(-2, 0, -1), rk.offset(2, 1, 4));
        if (level.getBlockEntity(rk) instanceof com.strikerr212.minepc.block.RackBlockEntity r) {
            com.strikerr212.minepc.block.RackBlock.extend(level, rk, rackState, r, 1, null);
        }
        p.teleportTo(level, rk.getX() + 2.7, rk.getY() + 1.8, rk.getZ() - 2.3, 45f, 28f);
    }

    private static void fillModule(PcCaseBlockEntity be, int tier) {
        if (be == null) return;
        var inv = (IItemHandlerModifiable) be.inventory();
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_MOTHERBOARD, HardwarePresets.motherboard(tier));
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_CPU, HardwarePresets.cpu(tier));
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_COOLER, HardwarePresets.cooler(tier));
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_RAM, HardwarePresets.ram(tier));
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_RAM + 1, HardwarePresets.ram(tier));
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_RAM + 2, HardwarePresets.ram(tier));
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_PSU, HardwarePresets.psu(tier));
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_HDD, HardwarePresets.hdd(2));
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_HDD + 1, HardwarePresets.hdd(2));
        be.togglePower();
    }

    /** Переключить вкладку открытого сборочного стола, как будто игрок щёлкнул по ней. */
    private static void assemblyTab(Minecraft mc, int tab) {
        if (mc.player != null && mc.gameMode != null && mc.player.containerMenu instanceof com.strikerr212.minepc.menu.AssemblyMenu m) {
            mc.gameMode.handleInventoryButtonClick(m.containerId, com.strikerr212.minepc.menu.AssemblyMenu.BTN_TYPE + tab);
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

    private static BlockPos netA = BlockPos.ZERO, netB = BlockPos.ZERO, netC = BlockPos.ZERO, netD = BlockPos.ZERO,
            netRouter = BlockPos.ZERO;

    private static long perfSent, perfGot, perfAt, perfOne;

    /** Скорость экрана: сколько кадров отправил сервер, сколько получил клиент и сколько кадров в секунду рисует игра. */
    private static void perf(Minecraft mc, boolean start, net.minecraft.core.BlockPos at) {
        long sent = com.strikerr212.minepc.block.CaseComputer.FRAMES_SENT.get(), got = ClientTerminals.received;
        long now = System.nanoTime();
        ClientTerminals.countAt = at;
        if (!start) {
            double sec = (now - perfAt) / 1e9;
            com.strikerr212.minepc.MineComputers.LOGGER.info("CI экран: за {} с отправлено {} кадров ({}/с), получено {} ({}/с), игра рисует {} кадров/с",
                    String.format("%.1f", sec), sent - perfSent, String.format("%.1f", (sent - perfSent) / sec),
                    got - perfGot, String.format("%.1f", (got - perfGot) / sec), mc.getFps());
            com.strikerr212.minepc.MineComputers.LOGGER.info("CI звук: пакетов {}, кусочков звука {}, OpenAL {}, задержка «пакет → звук» в среднем {} мс",
                    ComputerAudio.packets, ComputerAudio.chunks, org.lwjgl.openal.ALC10.alcGetCurrentContext() != 0 ? "работает" : "нет",
                    ComputerAudio.latencyCount == 0 ? "?" : String.format("%.1f", ComputerAudio.latencySum / ComputerAudio.latencyCount));
            com.strikerr212.minepc.MineComputers.LOGGER.info("CI экран: из них от компьютера с игрой {} ({}/с)",
                    ClientTerminals.receivedAt - perfOne, String.format("%.1f", (ClientTerminals.receivedAt - perfOne) / sec));
            ClientTerminals.Snapshot sn = at == null ? null : ClientTerminals.get(at);
            if (sn != null) {
                String last = new String(sn.chars(), (sn.height() - 2) * sn.width(), sn.width()).trim().replaceAll(" +", " ");
                com.strikerr212.minepc.MineComputers.LOGGER.info("CI экран {}x{}, нижняя строка: {}", sn.width(), sn.height(), last);
            }
        }
        perfSent = sent;
        perfGot = got;
        perfAt = now;
        perfOne = ClientTerminals.receivedAt;
    }

    /** Сообщить скрипту CI, где окно игры: он запишет видео этой области (ffmpeg, 11 секунд). */
    private static void startRecording(Minecraft mc) {
        var w = mc.getWindow();
        try {
            java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("record.txt"),
                    w.getX() + " " + w.getY() + " " + (w.getWidth() & ~1) + " " + (w.getHeight() & ~1) + "\n");
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    private static void keyAt(BlockPos pos, String key) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            if (server.overworld().getBlockEntity(pos) instanceof PcCaseBlockEntity be) be.input(TerminalInputPayload.KEY, key, false, false);
        });
    }

    private static void typeAt(BlockPos pos, String line) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            if (server.overworld().getBlockEntity(pos) instanceof PcCaseBlockEntity be) {
                for (char c : line.toCharArray()) be.input(TerminalInputPayload.CHAR, String.valueOf(c), false, false);
                be.input(TerminalInputPayload.KEY, "enter", false, false);
            }
        });
    }

    /** Провод на грани face клетки pos с дорожками a, b (по осям плоскости грани в порядке X, Y, Z). */
    private static void wire(ServerLevel level, BlockPos pos, Direction face, int color, boolean fiber, int a, int b) {
        if (!(level.getBlockState(pos).getBlock() instanceof com.strikerr212.minepc.lan.CableBlock)) {
            level.setBlockAndUpdate(pos, ModBlocks.CABLE.get().defaultBlockState());
        }
        if (level.getBlockEntity(pos) instanceof com.strikerr212.minepc.lan.CableBlockEntity be && be.free(face, a, b)) {
            be.add(com.strikerr212.minepc.lan.CableBlockEntity.make(color, fiber, face, a, b));
        }
    }

    /** Провод на полу: дорожка по X (для хода север–юг) и по Z (для хода запад–восток). */
    private static void wire(ServerLevel level, BlockPos pos, int color, int lx, int lz) {
        wire(level, pos, Direction.DOWN, color, false, lx, lz);
    }

    /** Пересчитать все провода и лотки в коробке (дважды — соединения зависят друг от друга). */
    private static void reconnectArea(ServerLevel level, BlockPos from, BlockPos to) {
        for (int pass = 0; pass < 3; pass++) {
            for (BlockPos q : BlockPos.betweenClosed(from, to)) {
                if (level.getBlockEntity(q) instanceof com.strikerr212.minepc.lan.CableBlockEntity be) be.reconnect();
                if (level.getBlockEntity(q) instanceof com.strikerr212.minepc.lan.TrayBlockEntity t) t.reconnect();
            }
        }
    }

    /** Порт, в который воткнут провод клетки cell в сторону toward (первый такой провод), или -1. */
    private static int plugPort(ServerLevel level, BlockPos cell, Direction toward, boolean fiber) {
        if (level.getBlockEntity(cell) instanceof com.strikerr212.minepc.lan.CableBlockEntity be) {
            for (var w : be.wires()) {
                int k = w.index(toward);
                if (w.fiber == fiber && k >= 0 && w.port(k) >= 0) return w.port(k);
            }
        }
        return -1;
    }

    /**
     * Сеть: два компьютера и коммутатор, кабели 1×1 вдоль задних стенок в гнёзда коммутатора; третий компьютер за
     * кирпичной стенкой — провод поднимается по ней, идёт поверху и спускается; роутер Wi-Fi и ноутбук без кабеля.
     */
    private static void placeNetwork() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        BlockPos n = casePos.east().south(26).west(3);
        for (int dx = -3; dx <= 9; dx++) {
            for (int dz = -9; dz <= 7; dz++) {
                level.setBlockAndUpdate(n.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
                for (int dy = 0; dy <= 3; dy++) level.setBlockAndUpdate(n.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }
        netA = n;
        netB = n.east(6);
        fillCase(level, netA, 3);
        fillCase(level, netB, 4);
        for (BlockPos c : new BlockPos[]{netA, netB}) {
            put(level, c.above(), ModBlocks.MONITOR.get().defaultBlockState().setValue(com.strikerr212.minepc.block.MonitorBlock.KEYBOARD, true));
        }
        // Коммутатор позади, лицом к ряду кабелей вдоль задних стенок
        BlockPos sw = n.east(3).south(2), front = sw.north();
        level.setBlockAndUpdate(sw, ModBlocks.NETWORK_SWITCH.get().defaultBlockState()
                .setValue(com.strikerr212.minepc.lan.SwitchBlock.FACING, Direction.NORTH));
        int blue = net.minecraft.world.item.DyeColor.BLUE.getId(), orange = net.minecraft.world.item.DyeColor.ORANGE.getId();
        // A → коммутатор
        for (int dx = 0; dx <= 3; dx++) wire(level, n.east(dx).south(), blue, 0, 1);
        // B → коммутатор
        for (int dx = 3; dx <= 6; dx++) wire(level, n.east(dx).south(), blue, 1, 2);
        // C: из коммутатора на север, вверх по кирпичной стенке, поверху и вниз к компьютеру за ней
        wire(level, front, blue, 2, 3);
        wire(level, n.east(3), blue, 2, 0);
        wire(level, n.east(3).north(), blue, 2, 0);
        for (int dx = 2; dx <= 4; dx++) level.setBlockAndUpdate(n.east(dx).north(2), net.minecraft.world.level.block.Blocks.BRICKS.defaultBlockState());
        wire(level, n.east(3).north(), Direction.NORTH, blue, false, 2, 0);
        wire(level, n.east(3).north(2).above(), blue, 2, 0);
        wire(level, n.east(3).north(3), Direction.SOUTH, blue, false, 2, 0);
        wire(level, n.east(3).north(3), blue, 2, 0);
        netC = n.east(3).north(4);
        fillCase(level, netC, 2);
        put(level, netC.above(), ModBlocks.MONITOR.get().defaultBlockState().setValue(com.strikerr212.minepc.block.MonitorBlock.KEYBOARD, true));
        // Роутер Wi-Fi: от коммутатора на восток и на север, в гнездо сзади роутера
        wire(level, front, blue, 3, 4);
        wire(level, n.east(4).south(), blue, 3, 4);
        wire(level, n.east(4), blue, 3, 1);      // своя дорожка: не слиться с проводом C в соседней клетке
        BlockPos router = n.east(4).north();
        level.setBlockAndUpdate(router, ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(com.strikerr212.minepc.lan.RouterBlock.FACING, Direction.NORTH));
        if (level.getBlockEntity(router) instanceof com.strikerr212.minepc.lan.RouterBlockEntity r) r.apply("Office", "minecraft1", 32);
        // Рядом — отдельная оранжевая сеть: два провода по стене, просто для вида
        for (int dz = 0; dz <= 2; dz++) level.setBlockAndUpdate(n.west(2).south(dz), net.minecraft.world.level.block.Blocks.BRICKS.defaultBlockState());
        for (int dz = 0; dz <= 2; dz++) wire(level, n.west().south(dz), Direction.WEST, orange, false, 0, 5);
        netD = n.offset(-2, 0, 6);
        fillCase(level, netD, 3);
        put(level, netD.above(), ModBlocks.MONITOR.get().defaultBlockState().setValue(com.strikerr212.minepc.block.MonitorBlock.KEYBOARD, true));
        if (level.getBlockEntity(netD) instanceof PcCaseBlockEntity be) {
            ((IItemHandlerModifiable) be.inventory()).setStackInSlot(PcCaseBlockEntity.SLOT_USB,
                    new net.minecraft.world.item.ItemStack(com.strikerr212.minepc.registry.ModItems.WIFI_N.get()));
        }
        netRouter = router;
        reconnectArea(level, n.offset(-3, -1, -5), n.offset(8, 3, 3));
        p.teleportTo(level, n.getX() + 3.5, n.getY() + 2.5, n.getZ() - 7.5, 0f, 22f);
    }

    private static BlockPos lanK3 = BlockPos.ZERO, lanK4 = BlockPos.ZERO, lanK5 = BlockPos.ZERO, lanK6 = BlockPos.ZERO;
    private static BlockPos lanBase = BlockPos.ZERO, lanE = BlockPos.ZERO, lanF = BlockPos.ZERO, lanJ1 = BlockPos.ZERO,
            lanJ2 = BlockPos.ZERO, radio1 = BlockPos.ZERO;

    private static void tray(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, ModBlocks.TRAY.get().defaultBlockState());
        if (level.getBlockEntity(pos) instanceof com.strikerr212.minepc.lan.TrayBlockEntity t) {
            t.add(com.strikerr212.minepc.lan.TrayBlockEntity.make(net.minecraft.world.item.DyeColor.BLUE.getId(), false));
            t.add(com.strikerr212.minepc.lan.TrayBlockEntity.make(net.minecraft.world.item.DyeColor.ORANGE.getId(), false));
            t.add(com.strikerr212.minepc.lan.TrayBlockEntity.make(net.minecraft.world.item.DyeColor.YELLOW.getId(), true));
        }
    }

    private static void pcWithMonitor(ServerLevel level, BlockPos pos) {
        fillCase(level, pos, 3);
        put(level, pos.above(), ModBlocks.MONITOR.get().defaultBlockState().setValue(com.strikerr212.minepc.block.MonitorBlock.KEYBOARD, true));
    }

    /**
     * Вторая сцена сети: стойка (патч-панель → патч-корд → стоечный коммутатор → оптоволокно → второй стоечный → компьютер),
     * VLAN «lime» на всём пути, кабель под маскировкой, лотки вверх по стене и обратно, пара радиомодемов на 300 блоков.
     */
    private static void placeLan2() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        BlockPos b = casePos.east().south(26).east(14);
        lanBase = b;
        for (int dx = -3; dx <= 17; dx++) {
            for (int dz = -5; dz <= 6; dz++) {
                level.setBlockAndUpdate(b.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
                for (int dy = 0; dy <= 4; dy++) level.setBlockAndUpdate(b.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }
        BlockState panel = ModBlocks.PATCH_PANEL.get().defaultBlockState().setValue(com.strikerr212.minepc.lan.PatchPanelBlock.FACING, Direction.NORTH);
        BlockState rack = ModBlocks.RACK_SWITCH.get().defaultBlockState().setValue(com.strikerr212.minepc.lan.SwitchBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(b.west(), panel);
        level.setBlockAndUpdate(b, rack);
        level.setBlockAndUpdate(b.east(5), rack);
        int blue = net.minecraft.world.item.DyeColor.BLUE.getId(), yellow = net.minecraft.world.item.DyeColor.YELLOW.getId();
        // Оптика: из гнезда первого стоечного коммутатора вдоль фасада во второй
        for (int dx = 0; dx <= 5; dx++) wire(level, b.east(dx).north(), Direction.DOWN, yellow, true, 0, 1);
        // Компьютер F — сбоку от второго коммутатора, медью из его гнезда
        lanF = b.east(7).north();
        pcWithMonitor(level, lanF);
        wire(level, b.east(5).north(), blue, 1, 2);
        wire(level, b.east(6).north(), blue, 1, 2);
        // Компьютер E — сзади патч-панели; кабель под маскировкой
        lanE = b.south(2);
        pcWithMonitor(level, lanE);
        wire(level, b.west().south(), blue, 1, 1);
        wire(level, b.west().south(2), blue, 1, 1);
        if (level.getBlockEntity(b.west().south()) instanceof com.strikerr212.minepc.lan.CableBlockEntity be) {
            be.setFacade(net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
        }
        // Лотки: от компьютера J1 вверх, поверху и вниз к J2
        lanJ1 = b.offset(8, 0, 4);
        lanJ2 = b.offset(15, 0, 4);
        pcWithMonitor(level, lanJ1);
        fillCase(level, lanJ2, 5);
        put(level, lanJ2.above(), ModBlocks.MONITOR.get().defaultBlockState().setValue(com.strikerr212.minepc.block.MonitorBlock.KEYBOARD, true));
        wire(level, b.offset(9, 0, 4), blue, 1, 1);
        wire(level, b.offset(14, 0, 4), blue, 1, 1);
        for (int[] t : new int[][]{{10, 0}, {10, 1}, {10, 2}, {11, 2}, {12, 2}, {13, 2}, {13, 1}, {13, 0}}) tray(level, b.offset(t[0], t[1], 4));
        // Радиомодемы: один здесь, другой в 300 блоках (чанк потом выгрузится — связь в схеме останется)
        radio1 = b.offset(3, 0, 4);
        BlockState radio = ModBlocks.RADIO.get().defaultBlockState().setValue(com.strikerr212.minepc.block.Paint.PROPERTY,
                com.strikerr212.minepc.block.Paint.values()[net.minecraft.world.item.DyeColor.RED.getId() + 1]);
        level.setBlockAndUpdate(radio1, radio);
        level.setBlockAndUpdate(radio1.above(), ModBlocks.MAST.get().defaultBlockState());
        level.setBlockAndUpdate(radio1.above(2), ModBlocks.MAST.get().defaultBlockState());
        BlockPos far = radio1.east(300);
        level.setBlockAndUpdate(far, radio);
        level.setBlockAndUpdate(far.above(), ModBlocks.MAST.get().defaultBlockState());
        // Воздушная линия: компьютер — кронштейн ~~ 10 блоков ~~ кронштейн — компьютер
        lanK3 = b.offset(-2, 0, -5);
        lanK4 = b.offset(12, 0, -5);
        fillCase(level, lanK3, 2);
        fillCase(level, lanK4, 2);
        BlockState bracket = ModBlocks.BRACKET.get().defaultBlockState()
                .setValue(com.strikerr212.minepc.lan.BracketBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR);
        level.setBlockAndUpdate(b.offset(0, 0, -5), bracket);
        level.setBlockAndUpdate(b.offset(10, 0, -5), bracket);
        wire(level, b.offset(-1, 0, -5), blue, 2, 2);
        wire(level, b.offset(11, 0, -5), blue, 2, 2);
        // Прокладка «от гнезда до гнезда» через каменную стенку: K5 → K6
        lanK5 = b.offset(3, 0, -3);
        lanK6 = b.offset(9, 0, -3);
        fillCase(level, lanK5, 2);
        fillCase(level, lanK6, 2);
        for (int dz = -4; dz <= -2; dz++) level.setBlockAndUpdate(b.offset(6, 0, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        reconnectArea(level, b.offset(-2, 0, -5), b.offset(16, 2, 5));
        var route = com.strikerr212.minepc.lan.RoutePlanner.plan(level, lanK5, lanK6, false);
        com.mojang.logging.LogUtils.getLogger().info("[CI-ROUTE] маршрут {}", route == null ? "не найден"
                : route.length() + " клеток, проводов " + route.pieces().size() + ", дорожка " + route.lane());
        if (route != null) com.strikerr212.minepc.lan.RoutePlanner.lay(level, route, net.minecraft.world.item.DyeColor.LIME.getId(), false);
        var data = com.strikerr212.minepc.lan.LanData.get(level);
        com.strikerr212.minepc.lan.LanManager.updateDevice(level, b.offset(0, 0, -5));
        com.strikerr212.minepc.lan.LanManager.updateDevice(level, b.offset(10, 0, -5));
        data.addCord(new com.strikerr212.minepc.lan.LanData.Cord(b.offset(0, 0, -5), com.strikerr212.minepc.lan.LanData.CABLE_PORTS,
                b.offset(10, 0, -5), com.strikerr212.minepc.lan.LanData.CABLE_PORTS, blue));
        com.strikerr212.minepc.lan.CordList.refresh(level, b.offset(0, 0, -5));
        com.strikerr212.minepc.lan.CordList.refresh(level, b.offset(10, 0, -5));
        com.strikerr212.minepc.lan.LanManager.updateDevice(level, b.west());
        com.strikerr212.minepc.lan.LanManager.updateDevice(level, b);
        // Корд: из того канала патч-панели, куда воткнут кабель от E, в первый корд-порт коммутатора
        int panelPort = Math.max(0, plugPort(level, b.west().south(), Direction.NORTH, false));
        int ch = panelPort % com.strikerr212.minepc.lan.PatchPanelBlock.CHANNELS;
        data.addCord(new com.strikerr212.minepc.lan.LanData.Cord(b.west(), com.strikerr212.minepc.lan.LanData.CABLE_PORTS + ch, b,
                com.strikerr212.minepc.lan.LanData.CABLE_PORTS, net.minecraft.world.item.DyeColor.LIME.getId()));
        com.strikerr212.minepc.lan.CordList.refresh(level, b.west());
        com.strikerr212.minepc.lan.CordList.refresh(level, b);
        int lime = net.minecraft.world.item.DyeColor.LIME.getId() + 1;
        data.setPort(b, com.strikerr212.minepc.lan.LanData.CABLE_PORTS, true, lime);
        data.setPort(b, plugPort(level, b.north(), Direction.SOUTH, true), true, lime);
        data.setPort(b.east(5), plugPort(level, b.east(5).north(), Direction.SOUTH, true), true, lime);
        data.setPort(b.east(5), plugPort(level, b.east(5).north(), Direction.SOUTH, false), true, lime);
        p.teleportTo(level, b.getX() + 7.5, b.getY() + 1.5, b.getZ() - 8.5, 0f, 22f);
    }

    private static void logLan2() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerLevel level = server.overworld();
        var lan = com.strikerr212.minepc.lan.LanManager.get(level);
        var log = com.mojang.logging.LogUtils.getLogger();
        for (BlockPos c : new BlockPos[]{lanE, lanF, lanJ1, lanJ2, lanK3, lanK4, lanK5, lanK6}) {
            StringBuilder sb = new StringBuilder("[CI-LAN2] ").append(c.toShortString()).append(" ->");
            for (var r : lan.reach(level, c)) sb.append(' ').append(r.pos().toShortString()).append(" hops=").append(r.hops()).append(" bits=").append(r.bits());
            log.info(sb.toString());
        }
        var data = com.strikerr212.minepc.lan.LanData.get(level);
        var d = data.device(radio1);
        log.info("[CI-LAN2] радио {} слышит {}", d, d == null ? "-" : com.strikerr212.minepc.lan.LanManager.radioPeers(data, radio1, d));
        log.info("[CI-LAN2] порты R1: {}", com.strikerr212.minepc.lan.SwitchPorts.ports(level, lanBase));
    }

    /** В журнал CI: кого видит каждый компьютер по схеме сети (проверка без загрузки чанков). */
    private static void logNetwork() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerLevel level = server.overworld();
        var lan = com.strikerr212.minepc.lan.LanManager.get(level);
        for (BlockPos c : new BlockPos[]{netA, netB, netC, netD}) {
            StringBuilder b = new StringBuilder("[CI-LAN] ").append(c.toShortString()).append(" ->");
            for (var r : lan.reach(level, c)) b.append(' ').append(r.pos().toShortString()).append(" hops=").append(r.hops());
            com.mojang.logging.LogUtils.getLogger().info(b.toString());
        }
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
        // Снимки долгие: без мобов, вечный день, игрок неуязвим
        server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, server);
        level.setDayTime(6000);
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
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
