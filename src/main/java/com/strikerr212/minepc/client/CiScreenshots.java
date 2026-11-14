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
import java.util.List;
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

    /** Идут снимки CI: окна без подсказок под мышью (мышь стоит посреди экрана). */
    public static boolean active() {
        return ENABLED;
    }
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
            // Серверные детали на сборочном столе: плата «серверная», процессор MS-S
            case 2330 -> {
                assemblyTab(mc, 0);
                assemblySet(mc, com.strikerr212.minepc.hardware.spec.MotherboardSpec.FORM, 1);
            }
            case 2338 -> shot(mc, "assembly_server_board");
            case 2340 -> {
                assemblyTab(mc, 1);
                assemblySet(mc, com.strikerr212.minepc.hardware.spec.CpuSpec.SOCKET, 3);
            }
            case 2348 -> shot(mc, "assembly_server_cpu");
            case 2352 -> {
                mc.options.guiScale().set(0);
                mc.resizeDisplay();
                mc.setScreen(null);
            }
            // Серверная стойка: две стойки друг на друге, три сервера и консоль; выдвинут сервер на серверных деталях
            case 2372 -> server.execute(CiScreenshots::placeRack);
            case 2530 -> {
                typeHost(new com.strikerr212.minepc.block.HostRef(rackPos, 0), "hostname srv-a");
                typeHost(new com.strikerr212.minepc.block.HostRef(rackPos, 1), "hostname srv-b");
                typeHost(new com.strikerr212.minepc.block.HostRef(rackPos.above(), 0), "hostname srv-c");
            }
            case 2542 -> typeHost(new com.strikerr212.minepc.block.HostRef(rackPos, 0), "ping srv-b 2");
            // Горячая замена на srv-b: вынуть второй блок питания и диск данных, проверить, что сервер работает
            case 2550 -> server.execute(() -> hotSwapTest(server.overworld(), false));
            case 2554 -> typeHost(new com.strikerr212.minepc.block.HostRef(rackPos, 1), "ls /");
            case 2560 -> server.execute(() -> hotSwapTest(server.overworld(), true));
            case 2564 -> typeHost(new com.strikerr212.minepc.block.HostRef(rackPos, 1), "ls /");
            case 2575 -> shot(mc, "world_server_rack");
            case 2578 -> server.execute(() -> {
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
            case 2586 -> shot(mc, "rack_server_gui");
            case 2590 -> mc.setScreen(new TerminalScreen(new com.strikerr212.minepc.block.HostRef(rackPos, 1)));
            case 2602 -> shot(mc, "rack_terminal");
            case 2605 -> mc.setScreen(new ConsoleScreen(rackPos));
            case 2612 -> shot(mc, "rack_console");
            case 2615 -> {
                mc.setScreen(null);
                // Сверху на выдвинутый сервер: видно серверную плату целиком
                server.execute(() -> server.getPlayerList().getPlayers().get(0).teleportTo(server.overworld(),
                        rackPos.getX() + 0.5, rackPos.getY() + 0.75, rackPos.getZ() - 1.05, 0f, 72f));
            }
            case 2626 -> shot(mc, "rack_board_top");
            case 2628 -> server.execute(() -> {
                ServerLevel level = server.overworld();
                if (level.getBlockEntity(rackPos) instanceof com.strikerr212.minepc.block.RackBlockEntity r) {
                    com.strikerr212.minepc.block.RackBlock.retract(level, rackPos, level.getBlockState(rackPos), r);
                }
                // Сначала отойти: перед стойкой должно быть свободно
                server.getPlayerList().getPlayers().get(0).teleportTo(level,
                        rackPos.getX() + 0.5, rackPos.getY() + 1.6, rackPos.getZ() - 2.6, 0f, 45f);
            });
            case 2631 -> server.execute(() -> {
                ServerLevel level = server.overworld();
                BlockPos up = rackPos.above();
                if (level.getBlockEntity(up) instanceof com.strikerr212.minepc.block.RackBlockEntity r) {
                    com.strikerr212.minepc.block.RackBlock.extend(level, up, level.getBlockState(up), r, 0, null);
                }
            });
            case 2633 -> server.execute(() -> server.getPlayerList().getPlayers().get(0).teleportTo(server.overworld(),
                    rackPos.getX() + 0.5, rackPos.getY() + 1.75, rackPos.getZ() - 1.3, 0f, 70f));
            case 2640 -> shot(mc, "rack_board_top_8");
            // BMC: с srv-a (обычный компьютер) управлять srv-b по сети
            case 2645 -> {
                mc.setScreen(null);
                typeHost(new com.strikerr212.minepc.block.HostRef(rackPos, 0), "clear");
                typeHost(new com.strikerr212.minepc.block.HostRef(rackPos, 0), "bmc -p admin srv-b status");
            }
            case 2652 -> typeHost(new com.strikerr212.minepc.block.HostRef(rackPos, 0), "bmc -p admin srv-b log 5");
            case 2656 -> mc.setScreen(new TerminalScreen(new com.strikerr212.minepc.block.HostRef(rackPos, 0)));
            case 2668 -> shot(mc, "bmc_status");
            case 2670 -> {
                typeHost(new com.strikerr212.minepc.block.HostRef(rackPos, 0), "clear");
                typeHost(new com.strikerr212.minepc.block.HostRef(rackPos, 0), "bmc -p admin srv-b power off");
            }
            case 2675 -> typeHost(new com.strikerr212.minepc.block.HostRef(rackPos, 0), "bmc -p wrong srv-b status");
            case 2680 -> typeHost(new com.strikerr212.minepc.block.HostRef(rackPos, 0), "bmc -p admin srv-b status");
            case 2692 -> shot(mc, "bmc_power");
            case 2694 -> typeHost(new com.strikerr212.minepc.block.HostRef(rackPos, 0), "bmc -p admin srv-b power on");
            case 2706 -> server.execute(() -> {
                PcCaseBlockEntity b = com.strikerr212.minepc.block.Hosts.get(server.overworld(), rackPos, 1);
                com.mojang.logging.LogUtils.getLogger().info("[CI-BMC] srv-b state={} log={}", b == null ? -1 : b.computerState(),
                        b == null ? "" : String.join(" | ", b.bmc().entries()));
            });
            // Пароли не уходят игрокам: в данных блока для клиента нет ни пароля BMC, ни пароля Wi-Fi
            case 2708 -> server.execute(() -> {
                ServerLevel level = server.overworld();
                PcCaseBlockEntity b = com.strikerr212.minepc.block.Hosts.get(level, rackPos, 1);
                if (b == null || !(level.getBlockEntity(rackPos) instanceof com.strikerr212.minepc.block.RackBlockEntity rack)) return;
                b.bmc().setPassword("secret-42");
                String sent = rack.getUpdateTag(level.registryAccess()).toString();
                String saved = rack.saveWithoutMetadata(level.registryAccess()).toString();
                com.mojang.logging.LogUtils.getLogger().info("[CI-SECRETS] в данных для игроков пароль={} ключ bmc_password={} | в сохранении открытым текстом={} | пароль подходит={} чужой={} | старый открытый={}",
                        sent.contains("secret-42"), sent.contains("bmc_password"), saved.contains("secret-42"),
                        b.bmc().checkPassword("secret-42"), b.bmc().checkPassword("admin"),
                        com.strikerr212.minepc.block.Secrets.matches("old-plain", "old-plain"));
                b.bmc().setPassword(com.strikerr212.minepc.block.Bmc.DEFAULT_PASSWORD);
            });
            // RAID 5 на сервере 2U (srv-c): собрать, износить и уронить диск, заменить — массив восстанавливается
            case 2712 -> typeHost(new com.strikerr212.minepc.block.HostRef(rackPos.above(), 0), "raid create raid5");
            case 2716 -> typeHost(new com.strikerr212.minepc.block.HostRef(rackPos.above(), 0), "да");
            case 2850 -> {
                typeHost(new com.strikerr212.minepc.block.HostRef(rackPos.above(), 0), "clear");
                typeHost(new com.strikerr212.minepc.block.HostRef(rackPos.above(), 0), "cp /rom/programs/ping.lua /data/ping.lua");
            }
            case 2856 -> server.execute(() -> {
                PcCaseBlockEntity c = com.strikerr212.minepc.block.Hosts.get(server.overworld(), rackPos.above(), 0);
                if (c == null) return;
                c.wearDisk(PcCaseBlockEntity.baySlot(2), 0.92);      // отсек 3 — просит замены
                c.wearDisk(PcCaseBlockEntity.baySlot(1), 1.0);       // отсек 2 — отказал
                com.mojang.logging.LogUtils.getLogger().info("[CI-RAID] после отказа: state={} log={}", c.computerState(),
                        String.join(" | ", c.bmc().entries().subList(Math.max(0, c.bmc().entries().size() - 5), c.bmc().entries().size())));
            });
            case 2860 -> typeHost(new com.strikerr212.minepc.block.HostRef(rackPos.above(), 0), "raid");
            case 2866 -> typeHost(new com.strikerr212.minepc.block.HostRef(rackPos.above(), 0), "ls /data");
            case 2870 -> mc.setScreen(new TerminalScreen(new com.strikerr212.minepc.block.HostRef(rackPos.above(), 0)));
            case 2882 -> shot(mc, "raid_degraded");
            case 2884 -> {
                mc.setScreen(null);
                server.execute(() -> server.getPlayerList().getPlayers().get(0).teleportTo(server.overworld(),
                        rackPos.getX() + 1.9, rackPos.getY() + 0.4, rackPos.getZ() - 2.4, 30f, 12f));
            }
            case 2896 -> shot(mc, "rack_2u_leds");
            case 2898 -> server.execute(() -> {
                PcCaseBlockEntity c = com.strikerr212.minepc.block.Hosts.get(server.overworld(), rackPos.above(), 0);
                if (c == null) return;
                var inv = c.inventory();
                ItemStack old = inv.extractItem(PcCaseBlockEntity.baySlot(1), 1, false);
                ItemStack left = inv.insertItem(PcCaseBlockEntity.baySlot(1),
                        HardwarePresets.server(com.strikerr212.minepc.hardware.HardwareType.HDD), false);
                com.mojang.logging.LogUtils.getLogger().info("[CI-RAID] замена: вынут={} вставлен={} восстанавливается отсек={}",
                        !old.isEmpty(), left.isEmpty(), c.rebuildingBay() + 1);
            });
            case 2902 -> {
                typeHost(new com.strikerr212.minepc.block.HostRef(rackPos.above(), 0), "clear");
                typeHost(new com.strikerr212.minepc.block.HostRef(rackPos.above(), 0), "raid");
            }
            case 3330 -> {
                typeHost(new com.strikerr212.minepc.block.HostRef(rackPos.above(), 0), "raid");
                typeHost(new com.strikerr212.minepc.block.HostRef(rackPos.above(), 0), "ls /data");
            }
            case 3336 -> mc.setScreen(new TerminalScreen(new com.strikerr212.minepc.block.HostRef(rackPos.above(), 0)));
            case 3348 -> shot(mc, "raid_rebuilt");
            case 3350 -> server.execute(() -> {
                PcCaseBlockEntity c = com.strikerr212.minepc.block.Hosts.get(server.overworld(), rackPos.above(), 0);
                if (c != null) com.mojang.logging.LogUtils.getLogger().info("[CI-RAID] итог: {}",
                        String.join(" | ", c.bmc().entries().subList(Math.max(0, c.bmc().entries().size() - 4), c.bmc().entries().size())));
            });
            // Консоль BMC: с srv-a — экран и клавиатура srv-b по сети
            case 3352 -> {
                mc.setScreen(null);
                typeHost(new com.strikerr212.minepc.block.HostRef(rackPos, 0), "clear");
                typeHost(new com.strikerr212.minepc.block.HostRef(rackPos, 0), "bmc -p admin srv-b console");
            }
            case 3362 -> typeHost(new com.strikerr212.minepc.block.HostRef(rackPos, 0), "ls /");
            case 3370 -> mc.setScreen(new TerminalScreen(new com.strikerr212.minepc.block.HostRef(rackPos, 0)));
            case 3382 -> shot(mc, "bmc_console");
            // Ряд стоек с сетевыми модулями: шина, VLAN и роутер, консоль с вкладками серверов
            case 3386 -> {
                mc.setScreen(null);
                server.execute(CiScreenshots::placeRow);
            }
            case 3550 -> {
                typeHost(new com.strikerr212.minepc.block.HostRef(rowPos, 1), "hostname srv-x");
                typeHost(new com.strikerr212.minepc.block.HostRef(rowPos, 2), "hostname srv-y");
                typeHost(new com.strikerr212.minepc.block.HostRef(rowPos.east(), 1), "hostname srv-z");
            }
            case 3560 -> server.execute(() -> logBus("шина"));
            case 3562 -> typeHost(new com.strikerr212.minepc.block.HostRef(rowPos, 1), "ping srv-z 1");
            case 3570 -> server.execute(() -> {
                // srv-z — в красный VLAN: с srv-x его видно только через роутер (+1 переход)
                var data = com.strikerr212.minepc.lan.LanData.get(server.overworld());
                data.setPort(rowPos.east(), com.strikerr212.minepc.lan.LanData.BUS_PORT0 + 1, true, net.minecraft.world.item.DyeColor.RED.getId() + 1);
                com.strikerr212.minepc.lan.LanManager.invalidate(server.overworld());
                logBus("VLAN red у srv-z, роутер есть");
            });
            case 3575 -> shot(mc, "rack_row");
            case 3578 -> server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                        com.strikerr212.minepc.lan.SwitchPorts.openPayload(server.overworld(), rowPos));
            });
            case 3588 -> shot(mc, "rack_bus_ports");
            case 3590 -> mc.setScreen(new TerminalScreen(new com.strikerr212.minepc.block.HostRef(rowPos, 1), rowPos.east()));
            case 3602 -> shot(mc, "console_tabs");
            // Удобства сети: розетки сквозь стену, бирки, тестер, потолочная точка доступа и карта покрытия
            case 3606 -> {
                mc.setScreen(null);
                server.execute(CiScreenshots::placeComfort);
            }
            case 3640 -> server.execute(() -> logComfort("сеть"));
            case 3646 -> shot(mc, "lan_outlet");
            case 3650 -> server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                ServerLevel level = server.overworld();
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(com.strikerr212.minepc.registry.ModItems.CABLE_TESTER.get()));
                p.teleportTo(level, comfort.getX() + 3.5, comfort.getY(), comfort.getZ() - 6.5, 0f, 18f);
                var data = com.strikerr212.minepc.lan.LanData.get(level);
                var used = com.strikerr212.minepc.lan.LanManager.usedPorts(data, comfortPcN, data.device(comfortPcN));
                if (!used.isEmpty()) com.strikerr212.minepc.lan.CableTesterItem.testPort(level, p, p.getMainHandItem(), comfortPcN, used.get(0));
            });
            case 3664 -> shot(mc, "lan_tester");
            case 3668 -> server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                        com.strikerr212.minepc.lan.SwitchPorts.openPayload(server.overworld(), comfortSwitch));
            });
            case 3678 -> shot(mc, "switch_labels");
            case 3682 -> {
                mc.setScreen(null);
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(com.strikerr212.minepc.registry.ModItems.WIFI_N.get()));
                    p.teleportTo(server.overworld(), comfort.getX() + 0.7, comfort.getY(), comfort.getZ() + 1.8, -40f, 28f);
                });
            }
            case 3700, 3760, 3800 -> typeAt(comfortLaptop, "wifi connect Office-AP");
            case 3730 -> shot(mc, "wifi_coverage");
            case 3840 -> server.execute(() -> logComfort("wifi"));
            // Дальняя связь: вышка 20 секций (тарелка, сектор, штырь, громоотвод), вторая вышка, модем за холмом, молния
            case 3846 -> server.execute(CiScreenshots::placeTowers);
            case 3880 -> server.execute(() -> logTowers("вышки"));
            case 3900 -> shot(mc, "radio_tower");
            case 3902 -> server.execute(() -> server.getPlayerList().getPlayers().get(0).teleportTo(server.overworld(),
                    tower1.getX() + 7.5, tower1.getY(), tower1.getZ() - 5.5, 45f, -48f));
            case 3914 -> shot(mc, "radio_tower_close");
            case 3916 -> server.execute(() -> server.overworld().setDayTime(18000));
            case 3946 -> shot(mc, "radio_tower_night");
            case 3966 -> shot(mc, "radio_tower_night2");
            case 3970 -> server.execute(() -> {
                server.overworld().setDayTime(6000);
                ServerLevel level = server.overworld();
                boolean t2 = com.strikerr212.minepc.lan.LanManager.strike(level, towerModem2);
                boolean t1 = com.strikerr212.minepc.lan.LanManager.strike(level, towerModem1);
                com.mojang.logging.LogUtils.getLogger().info("[CI-TOWER] молния: вышка 2 (без громоотвода) выбита={}, вышка 1 (с громоотводом) выбита={}", t2, t1);
                logTowers("после молнии");
            });
            // Провода щелчками: от гнезда до гнезда через стену, по точкам излома; призрак и результат
            case 3980 -> server.execute(CiScreenshots::placeDraw);
            case 3996 -> {
                // Призрак: начало у компьютера B, точка излома на полу, прицел — на коммутатор
                var lvl = mc.level;
                var start = drawSocket(lvl, drawPcB, false);
                var bend = new com.strikerr212.minepc.lan.CablePath.Point(drawBase.offset(11, 0, 7), Direction.DOWN, 8, 8, null, -1);
                if (start != null) CableDrawClient.debugPoints(net.minecraft.world.InteractionHand.MAIN_HAND, List.of(start, bend));
            }
            case 4016 -> {
                var r = CableDrawClient.debugPreview();
                com.mojang.logging.LogUtils.getLogger().info("[CI-DRAW] призрак: {}", r == null ? "нет" : "кусков " + r.pieces().size()
                        + ", длина " + r.length() + ", ошибки " + r.problems());
                shot(mc, "cable_draw");
            }
            case 4020 -> {
                CableDrawClient.debugPoints(net.minecraft.world.InteractionHand.MAIN_HAND, List.of());
                server.execute(CiScreenshots::layDraw);
            }
            case 4030 -> server.execute(() -> logPower(server.overworld()));
            case 4040 -> shot(mc, "cable_draw_done");
            case 4044 -> server.execute(() -> placeEmptyCase(server.overworld()));
            case 4048 -> server.execute(() -> openEmptyCase(server.overworld()));
            case 4052 -> {
                PcCaseScreen.ciTheme = com.strikerr212.minepc.block.CaseStyle.GAMING;
                if (mc.screen instanceof PcCaseScreen s) s.ciRefresh();
            }
            case 4058 -> shot(mc, "case_gui_empty");
            case 4060 -> server.execute(() -> emptyCaseStep(server.overworld(), 1));
            case 4068 -> shot(mc, "case_gui_board");
            case 4070 -> server.execute(() -> emptyCaseStep(server.overworld(), 2));
            case 4078 -> shot(mc, "case_gui_cpu");
            case 4080, 4088, 4096, 4104, 4112 -> {
                int style = (ticks - 4080) / 8;
                server.execute(() -> boardStyle(server.overworld(), style));
            }
            case 4086, 4094, 4102, 4110, 4118 -> shot(mc, "case_board_style" + (ticks - 4086) / 8);
            case 4120 -> {
                PcCaseScreen.ciTheme = null;
                mc.setScreen(null);
            }
            case 4126 -> server.execute(CiScreenshots::placeIo);
            case 4134 -> server.execute(CiScreenshots::cableIo);
            case 4150 -> typeAt(drawPcA, "ioc");
            case 4180 -> typeAt(drawPcA, "ioc ctl set north 15");
            case 4205 -> typeAt(drawPcA, "ioc ctl");
            case 4230 -> server.execute(CiScreenshots::logIo);
            case 4240 -> mc.setScreen(new IoScreen(ioPos));
            case 4250 -> shot(mc, "io_gui");
            case 4252 -> {
                mc.setScreen(null);
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                    p.teleportTo(server.overworld(), drawPcA.getX() + 0.5, drawPcA.getY() + 1, drawPcA.getZ() + 3.5, 180f, 20f);
                });
            }
            case 4256 -> typeAt(drawPcB, "dns register shop");
            case 4270 -> typeAt(drawPcB, "httpd");
            case 4300 -> typeAt(drawPcA, "get shop.mine/");
            case 4330 -> typeAt(drawPcA, "browse shop.mine");
            case 4350 -> server.execute(() -> logScreens("[CI-WEB]"));
            case 4352 -> mc.setScreen(new TerminalScreen(drawPcA));
            case 4365 -> shot(mc, "browse_site");
            case 4368, 4370 -> keyAt(drawPcA, "tab");
            case 4372 -> charsAt(drawPcA, "Стив");
            case 4374 -> keyAt(drawPcA, "enter");
            case 4400 -> shot(mc, "browse_form");
            case 4402 -> charsAt(drawPcA, "q");
            case 4410 -> typeAt(drawPcB, "maild");
            case 4425 -> typeAt(drawPcA, "mail send admin@shop.mine Привет");
            case 4430 -> typeAt(drawPcA, "Как дела?");
            case 4434 -> typeAt(drawPcA, ".");
            case 4460 -> typeAt(drawPcB, "mail");
            case 4480 -> server.execute(() -> logScreens("[CI-MAIL]"));
            case 4482 -> shot(mc, "mail_sent");
            case 4484 -> mc.setScreen(null);
            case 4488 -> server.execute(CiScreenshots::placeCam);
            case 4494 -> server.execute(CiScreenshots::cableCam);
            case 4504 -> typeAt(drawPcA, "cam");
            case 4524 -> typeAt(drawPcA, "cam двор");
            case 4528 -> mc.setScreen(new TerminalScreen(drawPcA));
            case 4570 -> shot(mc, "cam_view");
            case 4572, 4574, 4576, 4578 -> keyAt(drawPcA, "left");
            case 4600 -> shot(mc, "cam_view_turned");
            case 4602 -> charsAt(drawPcA, "q");
            case 4606 -> server.execute(() -> logCam("после просмотра"));
            case 4608 -> mc.setScreen(new CameraScreen(camPos));
            case 4630 -> shot(mc, "camera_gui");
            case 4632 -> {
                mc.setScreen(null);
                if (mc.player != null) mc.player.getInventory().selected = 8;   // пустая рука — без подсказок кабеля
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                    p.teleportTo(server.overworld(), camPos.getX() + 2.1, camPos.getY(), camPos.getZ() - 1.1, 45f, 28f);
                });
            }
            case 4645 -> shot(mc, "camera_block");
            // Передача большого файла: send не должен терять куски (раньше маленький file_end обгонял их)
            case 4648 -> typeAt(drawPcB, "clear");
            case 4650 -> typeAt(drawPcB, "recv");
            case 4652 -> typeAt(drawPcA, "clear");
            case 4654 -> typeAt(drawPcA, "lua");
            case 4658 -> typeAt(drawPcA, "local t={} for i=1,50000 do t[i]=string.char(i%256,(i*7)%256,(i*13)%256,(i*31)%256) end"
                    + " local f=fs.open(\"big.bin\",\"wb\") f.write(table.concat(t)) f.close() print(fs.getSize(\"big.bin\"))");
            case 4690 -> typeAt(drawPcA, "exit()");
            case 4694 -> typeAt(drawPcA, "send shop.mine big.bin");
            case 4790 -> server.execute(() -> logScreens("[CI-SEND]"));
            // Патч-корды в гнёзда коммутатора: втыкаются туда, куда целишься, и видны
            case 4792 -> server.execute(CiScreenshots::placeCordScene);
            case 4800 -> server.execute(() -> {
                cordClick(cordA, 1);
                cordClick(cordB, 4);
                cordClick(cordA, 5);
                cordClick(cordB, 0);
                cordClick(cordA, 1);       // занятое гнездо — должен отказать
            });
            case 4812 -> logCords();
            case 4860 -> logCords();      // после проверки CordGuard (раз в 2 с): корды на месте
            case 4822 -> shot(mc, "cord_sockets");
            case 4870 -> mc.stop();
            default -> {
            }
        }
    }

    private static BlockPos rackPos = BlockPos.ZERO, rowPos = BlockPos.ZERO;

    /** Ряд из двух колонн стоек: коммутатор, патч-панель, роутер, ИБП, заглушки, консоль и три сервера. */
    private static void placeRow() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        BlockPos r1 = rackPos.west(4);
        rowPos = r1;
        BlockState st = ModBlocks.SERVER_RACK.get().defaultBlockState().setValue(com.strikerr212.minepc.block.RackBlock.FACING, Direction.NORTH);
        for (BlockPos b : new BlockPos[]{r1, r1.above(), r1.east(), r1.east().above()}) level.setBlockAndUpdate(b, st);
        java.util.function.BiConsumer<BlockPos, net.minecraft.world.item.Item[]> fill = (b, items) -> {
            if (!(level.getBlockEntity(b) instanceof com.strikerr212.minepc.block.RackBlockEntity r)) return;
            for (int i = 0; i < items.length; i++) if (items[i] != null) r.insert(i, new ItemStack(items[i]));
        };
        fill.accept(r1, new net.minecraft.world.item.Item[]{com.strikerr212.minepc.registry.ModItems.SWITCH_MODULE.get(),
                com.strikerr212.minepc.registry.ModItems.SERVER_MODULE.get(), com.strikerr212.minepc.registry.ModItems.SERVER_MODULE.get(),
                com.strikerr212.minepc.registry.ModItems.BLANK_MODULE.get()});
        fill.accept(r1.above(), new net.minecraft.world.item.Item[]{com.strikerr212.minepc.registry.ModItems.UPS_MODULE.get(), null,
                com.strikerr212.minepc.registry.ModItems.ROUTER_MODULE.get(), null});
        fill.accept(r1.east(), new net.minecraft.world.item.Item[]{com.strikerr212.minepc.registry.ModItems.PATCH_MODULE.get(),
                com.strikerr212.minepc.registry.ModItems.SERVER_MODULE.get(), com.strikerr212.minepc.registry.ModItems.CONSOLE_MODULE.get(),
                com.strikerr212.minepc.registry.ModItems.BLANK_MODULE.get()});
        fill.accept(r1.east().above(), new net.minecraft.world.item.Item[]{com.strikerr212.minepc.registry.ModItems.BLANK_MODULE.get(),
                com.strikerr212.minepc.registry.ModItems.BLANK_MODULE.get(), com.strikerr212.minepc.registry.ModItems.SERVER_MODULE_2U.get(), null});
        if (level.getBlockEntity(r1) instanceof com.strikerr212.minepc.block.RackBlockEntity r) {
            fillModule(r.server(1), 3);
            fillModule(r.server(2), 2);
        }
        if (level.getBlockEntity(r1.east()) instanceof com.strikerr212.minepc.block.RackBlockEntity r) fillModule(r.server(1), 4);
        p.teleportTo(level, r1.getX() + 1.0, r1.getY(), r1.getZ() - 3.4, 0f, 6f);
    }

    private static BlockPos comfort = BlockPos.ZERO, comfortPcN = BlockPos.ZERO, comfortPcS = BlockPos.ZERO, comfortSwitch = BlockPos.ZERO,
            comfortOutN = BlockPos.ZERO, comfortOutS = BlockPos.ZERO, comfortAp = BlockPos.ZERO, comfortLaptop = BlockPos.ZERO,
            comfortLoose = BlockPos.ZERO;

    /** Проложить маршрут кабеля между устройствами с биркой; в журнал — длина или «нет пути». */
    private static void route(ServerLevel level, BlockPos from, BlockPos to, String label) {
        var r = com.strikerr212.minepc.lan.RoutePlanner.plan(level, from, to, false);
        com.mojang.logging.LogUtils.getLogger().info("[CI-NET29] маршрут {} -> {} «{}»: {}", from.toShortString(), to.toShortString(), label,
                r == null ? "нет пути" : r.length() + " клеток");
        if (r != null) com.strikerr212.minepc.lan.RoutePlanner.lay(level, r, net.minecraft.world.item.DyeColor.BLUE.getId(), false,
                com.strikerr212.minepc.lan.LanData.get(level).labelId(label));
    }

    /**
     * Удобства сети: кирпичная стена с розетками на обеих сторонах; с севера — компьютер в розетку, с юга — розетка
     * в коммутатор и сервер; провода с бирками; за стеной комната с потолком, точкой доступа и ноутбуком за перегородкой.
     */
    private static void placeComfort() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        BlockPos b = rackPos.west(16);
        comfort = b;
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (int dx = -3; dx <= 11; dx++) {
            for (int dz = -8; dz <= 11; dz++) {
                level.setBlockAndUpdate(b.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
                for (int dy = 0; dy <= 5; dy++) level.setBlockAndUpdate(b.offset(dx, dy, dz), air);
                if (dz == 0) for (int dy = 0; dy <= 3; dy++) level.setBlockAndUpdate(b.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.BRICKS.defaultBlockState());
                if (dz >= 1) level.setBlockAndUpdate(b.offset(dx, 4, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                if (dx == 7 && dz >= 5) for (int dy = 0; dy <= 3; dy++) level.setBlockAndUpdate(b.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.BRICKS.defaultBlockState());
            }
        }
        var outlet = ModBlocks.OUTLET.get().defaultBlockState()
                .setValue(com.strikerr212.minepc.lan.OutletBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL);
        comfortOutN = b.offset(3, 0, -1);
        comfortOutS = b.offset(3, 0, 1);
        level.setBlockAndUpdate(comfortOutN, outlet.setValue(com.strikerr212.minepc.lan.OutletBlock.FACING, Direction.NORTH));
        level.setBlockAndUpdate(comfortOutS, outlet.setValue(com.strikerr212.minepc.lan.OutletBlock.FACING, Direction.SOUTH));
        comfortPcN = b.offset(6, 0, -3);
        pcWithMonitor(level, comfortPcN);
        comfortSwitch = b.offset(1, 0, 4);
        level.setBlockAndUpdate(comfortSwitch, ModBlocks.NETWORK_SWITCH.get().defaultBlockState()
                .setValue(com.strikerr212.minepc.lan.SwitchBlock.FACING, Direction.NORTH));
        comfortPcS = b.offset(4, 0, 8);
        fillCase(level, comfortPcS, 3);
        comfortAp = b.offset(3, 3, 6);
        level.setBlockAndUpdate(comfortAp, ModBlocks.ACCESS_POINT.get().defaultBlockState());
        if (level.getBlockEntity(comfortAp) instanceof com.strikerr212.minepc.lan.RouterBlockEntity r) r.apply("Office-AP", "", 16);
        comfortLaptop = b.offset(9, 0, 8);
        fillCase(level, comfortLaptop, 3);
        if (level.getBlockEntity(comfortLaptop) instanceof PcCaseBlockEntity be) {
            ((IItemHandlerModifiable) be.inventory()).setStackInSlot(PcCaseBlockEntity.SLOT_USB,
                    new ItemStack(com.strikerr212.minepc.registry.ModItems.WIFI_N.get()));
        }
        route(level, comfortSwitch, comfortOutS, "Бухгалтерия");
        route(level, comfortOutN, comfortPcN, "ПК-1");
        route(level, comfortSwitch, comfortPcS, "Сервер");
        // Кусок провода ни к чему не подключён: прозвонка покажет обрыв
        comfortLoose = b.offset(0, 0, -6);
        wire(level, comfortLoose, net.minecraft.world.item.DyeColor.ORANGE.getId(), 2, 2);
        wire(level, comfortLoose.east(), net.minecraft.world.item.DyeColor.ORANGE.getId(), 2, 2);
        reconnectArea(level, b.offset(-3, -1, -8), b.offset(11, 4, 11));
        p.teleportTo(level, b.getX() + 3.5, b.getY(), b.getZ() - 2.3, 0f, 38f);
    }

    private static BlockPos tower1 = BlockPos.ZERO, towerModem1 = BlockPos.ZERO, towerModem2 = BlockPos.ZERO, towerModem3 = BlockPos.ZERO;

    /** Вышка из n секций на bottom, сверху — штырь (и громоотвод). */
    private static void buildTower(ServerLevel level, BlockPos bottom, int n, boolean pin, boolean rod) {
        var section = ModBlocks.TOWER_SECTION.get();
        for (int i = 0; i < n; i++) {
            BlockPos p = bottom.above(i);
            level.setBlockAndUpdate(p, section.shaped(section.defaultBlockState(), level, p));
        }
        BlockPos top = bottom.above(n);
        if (pin) {
            level.setBlockAndUpdate(top, ModBlocks.TOWER_PIN.get().defaultBlockState());
            top = top.above();
        }
        if (rod) level.setBlockAndUpdate(top, net.minecraft.world.level.block.Blocks.LIGHTNING_ROD.defaultBlockState());
    }

    private static void antenna(ServerLevel level, BlockPos section, Direction facing, boolean dish) {
        level.setBlockAndUpdate(section.relative(facing), (dish ? ModBlocks.TOWER_DISH : ModBlocks.TOWER_SECTOR).get().defaultBlockState()
                .setValue(com.strikerr212.minepc.lan.TowerAntennaBlock.FACING, facing));
    }

    /**
     * Вышка 1: 20 секций, штырь, громоотвод, тарелка и сектор на восток. Вышка 2 в 120 блоках к востоку: 8 секций,
     * штырь, тарелка на запад, без громоотвода. Модем 3 без вышки — за каменным столбом в 25 блоков (холм на пути).
     */
    private static void placeTowers() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockPos t1 = rackPos.west(34);
        tower1 = t1;
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (BlockPos q : BlockPos.betweenClosed(t1.offset(-3, -1, -3), t1.offset(3, -1, 3))) {
            level.setBlockAndUpdate(q, net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
        }
        for (BlockPos q : BlockPos.betweenClosed(t1.offset(-3, 0, -3), t1.offset(3, 24, 3))) level.setBlockAndUpdate(q, air);
        buildTower(level, t1, 20, true, true);
        antenna(level, t1.above(16), Direction.EAST, false);
        antenna(level, t1.above(10), Direction.EAST, true);
        towerModem1 = t1.west().north();
        level.setBlockAndUpdate(towerModem1, ModBlocks.RADIO.get().defaultBlockState());
        BlockPos t2 = t1.east(120);
        for (BlockPos q : BlockPos.betweenClosed(t2.offset(-2, 0, -2), t2.offset(2, 12, 2))) level.setBlockAndUpdate(q, air);
        level.setBlockAndUpdate(t2.below(), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
        buildTower(level, t2, 8, true, false);
        antenna(level, t2.above(6), Direction.WEST, true);
        towerModem2 = t2.east().south();
        level.setBlockAndUpdate(towerModem2, ModBlocks.RADIO.get().defaultBlockState());
        BlockPos m3 = t1.east(60).south(40);
        level.setBlockAndUpdate(m3, ModBlocks.RADIO.get().defaultBlockState());
        towerModem3 = m3;
        // Каменный столб посередине между вышкой 1 и модемом 3
        BlockPos mid = t1.east(30).south(20);
        for (BlockPos q : BlockPos.betweenClosed(mid.offset(-2, 0, -2), mid.offset(2, 24, 2))) {
            level.setBlockAndUpdate(q, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }
        for (BlockPos m : new BlockPos[]{towerModem1, towerModem2, towerModem3}) com.strikerr212.minepc.lan.LanManager.updateDevice(level, m);
        p.teleportTo(level, t1.getX() - 9.5, t1.getY(), t1.getZ() - 21.5, -22f, -22f);
    }

    /** [CI-TOWER]: что видит каждый модем: через что, скорость, прямая видимость. */
    private static void logTowers(String what) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerLevel level = server.overworld();
        var data = com.strikerr212.minepc.lan.LanData.get(level);
        var log = com.mojang.logging.LogUtils.getLogger();
        var t = com.strikerr212.minepc.lan.Tower.near(level, towerModem1);
        log.info("[CI-TOWER] {}: вышка 1 {}", what, t);
        String[] names = {"m1", "m2", "m3"};
        BlockPos[] ms = {towerModem1, towerModem2, towerModem3};
        for (int i = 0; i < 3; i++) {
            var d = data.device(ms[i]);
            StringBuilder sb = new StringBuilder("[CI-TOWER] " + what + ": " + names[i] + " дальность " + (d == null ? -1 : d.extra() & 0xFFFF) + " ->");
            if (d != null) {
                for (var l : com.strikerr212.minepc.lan.LanManager.radioLinks(data, ms[i], d)) {
                    int j = l.pos().equals(towerModem1) ? 0 : l.pos().equals(towerModem2) ? 1 : 2;
                    sb.append(' ').append(names[j]).append(" через ").append(l.via()).append(' ').append(l.bits() / 800).append(" Б/с ").append(l.los());
                }
            }
            log.info(sb.toString());
        }
    }

    private static BlockPos drawBase = BlockPos.ZERO, drawPcA = BlockPos.ZERO, drawPcB = BlockPos.ZERO, drawSwitch = BlockPos.ZERO;

    /** Точка у первого свободного гнезда устройства (медь). */
    private static com.strikerr212.minepc.lan.CablePath.Point drawSocket(net.minecraft.world.level.Level level, BlockPos dev, boolean fiber) {
        var socks = com.strikerr212.minepc.lan.LanDevice.socketsAt(level, dev);
        for (int i = 0; i < socks.size(); i++) {
            if (socks.get(i).accepts(fiber) && com.strikerr212.minepc.lan.CablePath.socketFree(level, dev, i)) {
                return com.strikerr212.minepc.lan.CablePath.socketPoint(level, dev, i);
            }
        }
        return null;
    }

    /** Компьютер A — коммутатор за каменной стеной в 2 блока; компьютер B — сбоку от коммутатора. */
    private static void placeDraw() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        BlockPos b = rackPos.west(16).south(16);
        drawBase = b;
        for (int dx = -3; dx <= 13; dx++) {
            for (int dz = -5; dz <= 9; dz++) {
                level.setBlockAndUpdate(b.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
                for (int dy = 0; dy <= 4; dy++) level.setBlockAndUpdate(b.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                if (dx == 5) for (int dy = 0; dy <= 1; dy++) level.setBlockAndUpdate(b.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        drawPcA = b.offset(0, 0, 2);
        pcWithMonitor(level, drawPcA, Direction.WEST);
        drawSwitch = b.offset(9, 0, 2);
        level.setBlockAndUpdate(drawSwitch, ModBlocks.NETWORK_SWITCH.get().defaultBlockState()
                .setValue(com.strikerr212.minepc.lan.SwitchBlock.FACING, Direction.WEST));
        drawPcB = b.offset(9, 0, 6);
        pcWithMonitor(level, drawPcB, Direction.NORTH);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(com.strikerr212.minepc.registry.ModItems.CABLE.get(), 64));
        p.teleportTo(level, b.getX() + 5.5, b.getY(), b.getZ() - 4.5, -32f, 22f);
    }

    /** [CI-DRAW]: проложить оба провода (как будто щелчками) и проверить связь A ↔ B через коммутатор. */
    private static void layDraw() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        var log = com.mojang.logging.LogUtils.getLogger();
        var CP = new Object() {
            com.strikerr212.minepc.lan.CablePath.Point sock(BlockPos dev) {
                return drawSocket(level, dev, false);
            }
        };
        // 1) От гнезда до гнезда: A → коммутатор, путь сам через стену
        var a = CP.sock(drawPcA);
        var sw = CP.sock(drawSwitch);
        if (a == null || sw == null) {
            log.info("[CI-DRAW] нет свободных гнёзд: A={} коммутатор={}", a, sw);
            return;
        }
        var r1 = com.strikerr212.minepc.lan.CablePath.build(level, List.of(a, sw), false, 11);
        log.info("[CI-DRAW] A -> коммутатор (сам): ok={} длина {} ошибки {} грани {}", r1.ok(), r1.length(), r1.problems(),
                r1.pieces().stream().map(x -> x.face().getName().substring(0, 1)).reduce("", String::concat));
        com.strikerr212.minepc.lan.CableLaying.lay(p, net.minecraft.world.InteractionHand.MAIN_HAND, List.of(a, sw));
        // 2) Щелчками: B → точка на полу → ещё точка → гнездо коммутатора
        var bsock = CP.sock(drawPcB);
        var sw2 = CP.sock(drawSwitch);
        if (bsock == null || sw2 == null) {
            log.info("[CI-DRAW] нет свободных гнёзд: B={} коммутатор={}", bsock, sw2);
            return;
        }
        var bends = List.of(bsock,
                new com.strikerr212.minepc.lan.CablePath.Point(drawBase.offset(11, 0, 7), Direction.DOWN, 8, 8, null, -1),
                new com.strikerr212.minepc.lan.CablePath.Point(drawBase.offset(11, 0, 3), Direction.DOWN, 8, 4, null, -1),
                sw2);
        var r2 = com.strikerr212.minepc.lan.CablePath.build(level, bends, false, 11);
        log.info("[CI-DRAW] B -> коммутатор (щелчками): ok={} длина {} ошибки {}", r2.ok(), r2.length(), r2.problems());
        com.strikerr212.minepc.lan.CableLaying.lay(p, net.minecraft.world.InteractionHand.MAIN_HAND, bends);
        server.execute(() -> {
            var lan = com.strikerr212.minepc.lan.LanManager.get(level);
            StringBuilder sb = new StringBuilder("[CI-DRAW] A ->");
            for (var r : lan.reach(level, drawPcA)) sb.append(' ').append(r.pos().equals(drawPcB) ? "B" : r.pos().toShortString()).append(" hops=").append(r.hops());
            sb.append(" | кабеля в руке ").append(p.getMainHandItem().getCount());
            log.info(sb.toString());
        });
    }

    /** [CI-NET29]: связь сквозь стену, бирки на портах, прозвонка, Wi-Fi от точки доступа. */
    private static void logComfort(String what) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerLevel level = server.overworld();
        var log = com.mojang.logging.LogUtils.getLogger();
        var lan = com.strikerr212.minepc.lan.LanManager.get(level);
        var data = com.strikerr212.minepc.lan.LanData.get(level);
        StringBuilder sb = new StringBuilder("[CI-NET29] " + what + ": pcN ->");
        for (var r : lan.reach(level, comfortPcN)) sb.append(' ').append(r.pos().equals(comfortPcS) ? "pcS" : r.pos().toShortString()).append(" hops=").append(r.hops());
        log.info(sb.toString());
        log.info("[CI-NET29] розетки: N {} S {} пара={}", data.device(comfortOutN), data.device(comfortOutS),
                comfortOutS.equals(com.strikerr212.minepc.lan.LanManager.outletPartner(data, comfortOutN)));
        for (var port : com.strikerr212.minepc.lan.SwitchPorts.ports(level, comfortSwitch)) {
            log.info("[CI-NET29] коммутатор порт {}: {} бирка «{}» длина {}", port.port(), port.device(), port.label(), port.length());
        }
        var used = com.strikerr212.minepc.lan.LanManager.usedPorts(data, comfortPcN, data.device(comfortPcN));
        if (!used.isEmpty()) {
            var t = com.strikerr212.minepc.lan.LanManager.trace(data, comfortPcN, used.get(0), null, 0);
            log.info("[CI-NET29] прозвонка pcN порт {}: длина {} концов {} обрывов {} проводов {} пассивных {}", used.get(0) + 1,
                    t.length(), t.ends().size(), t.open().size(), t.wires().size(), t.passives().size());
        }
        var loose = com.strikerr212.minepc.lan.LanManager.trace(data, null, 0, comfortLoose, 0);
        log.info("[CI-NET29] прозвонка куска: длина {} концов {} обрывов {}", loose.length(), loose.ends().size(), loose.open().size());
        var link = data.wifiOf(comfortLaptop);
        if (level.getBlockEntity(comfortLaptop) instanceof PcCaseBlockEntity lp) log.info("[CI-NET29] ноутбук: связь {}", lp.netLinkCode());
        int walls = com.strikerr212.minepc.lan.WifiSignal.walls(level, comfortLaptop, comfortAp);
        log.info("[CI-NET29] Wi-Fi ноутбука: {} (к точке доступа: {}), стен до точки {}, сигнал по формуле {}%", link,
                link != null && link.router().equals(comfortAp), walls,
                com.strikerr212.minepc.lan.WifiSignal.signal(Math.sqrt(comfortLaptop.distSqr(comfortAp)), 16, walls,
                        com.strikerr212.minepc.lan.WifiSignal.wallCost(level.getBlockState(comfortAp))));
    }

    /** [CI-BUS]: кого видит srv-x (место 1 нижней левой стойки ряда) и через сколько переходов. */
    private static void logBus(String what) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerLevel level = server.overworld();
        var lan = com.strikerr212.minepc.lan.LanManager.get(level);
        StringBuilder sb = new StringBuilder("[CI-BUS] " + what + ": srv-x ->");
        for (var r : lan.reach(level, new com.strikerr212.minepc.block.HostRef(rowPos, 1))) {
            PcCaseBlockEntity pc = com.strikerr212.minepc.block.Hosts.get(level, r.pos(), r.slot());
            sb.append(' ').append(pc == null ? r.pos().toShortString() + "#" + r.slot() : pc.label()).append(" hops=").append(r.hops());
        }
        PcCaseBlockEntity x = com.strikerr212.minepc.block.Hosts.get(level, rowPos, 1);
        sb.append(" | linked=").append(lan.linked(level, new com.strikerr212.minepc.block.HostRef(rowPos, 1))).append(" link=").append(x == null ? -1 : x.netLinkCode());
        com.mojang.logging.LogUtils.getLogger().info(sb.toString());
    }

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
            fillServer(r.server(1), 12, 2);
        }
        if (level.getBlockEntity(rk.above()) instanceof com.strikerr212.minepc.block.RackBlockEntity r) {
            // Сверху — сервер 2U: восемь отсеков, в четырёх диски (для RAID 5)
            r.insert(0, new ItemStack(com.strikerr212.minepc.registry.ModItems.SERVER_MODULE_2U.get()));
            var inv = (IItemHandlerModifiable) r.server(0).inventory();
            inv.setStackInSlot(PcCaseBlockEntity.baySlot(2), HardwarePresets.server(com.strikerr212.minepc.hardware.HardwareType.HDD));
            inv.setStackInSlot(PcCaseBlockEntity.baySlot(3), HardwarePresets.server(com.strikerr212.minepc.hardware.HardwareType.HDD));
            fillServer(r.server(0), 8, 1);
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

    /** Обычные детали без кулера (в 1U он не влезет), один блок питания. */
    private static void fillModule(PcCaseBlockEntity be, int tier) {
        if (be == null) return;
        var inv = (IItemHandlerModifiable) be.inventory();
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_MOTHERBOARD, HardwarePresets.motherboard(tier));
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_CPU, HardwarePresets.cpu(tier));
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_RAM, HardwarePresets.ram(tier));
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_RAM + 1, HardwarePresets.ram(tier));
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_PSU, HardwarePresets.psu(tier));
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_HDD, HardwarePresets.hdd(2));
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_HDD + 1, HardwarePresets.hdd(2));
        be.togglePower();
    }

    /** Серверные детали: плата на два MS-S, два одинаковых процессора, ram планок ECC, блоков питания psus. */
    private static void fillServer(PcCaseBlockEntity be, int ram, int psus) {
        if (be == null) return;
        var inv = (IItemHandlerModifiable) be.inventory();
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_MOTHERBOARD, HardwarePresets.server(com.strikerr212.minepc.hardware.HardwareType.MOTHERBOARD));
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_CPU, HardwarePresets.server(com.strikerr212.minepc.hardware.HardwareType.CPU));
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_CPU2, HardwarePresets.server(com.strikerr212.minepc.hardware.HardwareType.CPU));
        for (int i = 0; i < ram; i++) {
            inv.setStackInSlot(PcCaseBlockEntity.ramSlot(i), HardwarePresets.server(com.strikerr212.minepc.hardware.HardwareType.RAM));
        }
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_PSU, HardwarePresets.psu(4));
        if (psus > 1) inv.setStackInSlot(PcCaseBlockEntity.SLOT_PSU2, HardwarePresets.psu(4));
        ItemStack ssd = HardwarePresets.ssd(3);
        inv.setStackInSlot(com.strikerr212.minepc.item.SsdItem.data(ssd).isNvme() ? PcCaseBlockEntity.SLOT_M2 : PcCaseBlockEntity.SLOT_SATA_SSD, ssd);
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_HDD, HardwarePresets.server(com.strikerr212.minepc.hardware.HardwareType.HDD));
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_HDD + 1, HardwarePresets.server(com.strikerr212.minepc.hardware.HardwareType.HDD));
        be.togglePower();
    }

    /** 0.31.1: топовый серверный процессор и два блока питания — мощность складывается, резерв считается отдельно. */
    private static void logPower(ServerLevel level) {
        var log = com.mojang.logging.LogUtils.getLogger();
        var spec = (com.strikerr212.minepc.hardware.spec.CpuSpec) com.strikerr212.minepc.hardware.spec.Specs.of(com.strikerr212.minepc.hardware.HardwareType.CPU);
        java.util.List<Integer> cpu = HardwarePresets.server(com.strikerr212.minepc.hardware.HardwareType.CPU)
                .get(com.strikerr212.minepc.registry.ModDataComponents.DESIGN.get()).values();
        java.util.List<Integer> v = new java.util.ArrayList<>(cpu);
        for (int p : new int[]{com.strikerr212.minepc.hardware.spec.CpuSpec.SRV_CORES, com.strikerr212.minepc.hardware.spec.CpuSpec.CLOCK,
                com.strikerr212.minepc.hardware.spec.CpuSpec.PROCESS}) v.set(p, spec.params().get(p).count() - 1);
        var top = spec.derive(v);
        log.info("[CI-POWER] топовый MS-S: {} ядер, {} МГц, TDP {} Вт; стоковый MS-S: {} Вт", top.cores(), top.clockKhz(), top.tdp(), spec.derive(cpu).tdp());
        PcCaseBlockEntity be = com.strikerr212.minepc.block.Hosts.get(level, rackPos, 1);
        if (be == null) return;
        int draw = be.collectInput().powerDraw();
        log.info("[CI-POWER] srv-b: блоков {}, вместе {} Вт, нагрузка {} Вт, резерв {}", be.psuCount(),
                be.activePsu() == null ? 0 : be.activePsu().wattage(), draw, be.reserve(draw));
        log.info("[CI-POWER] 2 × топ на 2 × 1600: нагрузка ~{} Вт, тянет {}, резерв {}", 2 * top.tdp() + 150,
                2 * top.tdp() + 150 <= 3200, 2 * top.tdp() + 150 <= 1600);
    }

    private static BlockPos emptyCasePos, ioPos, camPos;

    /** Камера на полу у компьютера A смотрит на коммутатор и контроллер. */
    private static BlockPos cordA = BlockPos.ZERO, cordB = BlockPos.ZERO;

    /** Как у игрока: настольный коммутатор на блоке земли и второй — на траве рядом. */
    private static void placeCordScene() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        BlockPos c = drawBase.offset(0, 0, -16);
        for (int dx = -3; dx <= 6; dx++) {
            for (int dz = -4; dz <= 5; dz++) {
                level.setBlockAndUpdate(c.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
                for (int dy = 0; dy <= 4; dy++) level.setBlockAndUpdate(c.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(c, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
        cordA = c.above();
        cordB = c.offset(2, 0, 0);
        var sw = ModBlocks.NETWORK_SWITCH.get().defaultBlockState().setValue(com.strikerr212.minepc.lan.SwitchBlock.FACING, Direction.SOUTH);
        level.setBlockAndUpdate(cordA, sw);
        level.setBlockAndUpdate(cordB, sw);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(com.strikerr212.minepc.registry.ModItems.PATCH_CORD.get(), 8));
        p.teleportTo(level, c.getX() + 1.5, c.getY(), c.getZ() + 3.2, 180f, 22f);
    }

    /** ПКМ патч-кордом в гнездо socket коммутатора pos — как игрок. */
    private static void cordClick(BlockPos pos, int socket) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        var state = level.getBlockState(pos);
        var so = ((com.strikerr212.minepc.lan.LanDevice) state.getBlock()).sockets(level, pos, state).get(socket);
        var hit = new net.minecraft.world.phys.BlockHitResult(com.strikerr212.minepc.lan.CordShape.socketPoint(pos, so), so.face(), pos, false);
        ItemStack stack = p.getMainHandItem();
        var r = stack.getItem().onItemUseFirst(stack, new net.minecraft.world.item.context.UseOnContext(p, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        var data = com.strikerr212.minepc.lan.LanData.get(level);
        BlockPos why = pos == cordB ? com.strikerr212.minepc.lan.CordShape.blocker(level, cordA, 1, cordB, socket) : null;
        com.mojang.logging.LogUtils.getLogger().info("[CI-CORD] клик {} гнездо {} → {} (кордов {}, мешает {} {})", pos == cordA ? "A" : "B",
                socket + 1, r, data.cords(cordA).size(), why, why == null ? "" : level.getBlockState(why).getBlock().getName().getString());
    }

    private static void logCords() {
        var mc = Minecraft.getInstance();
        var log = com.mojang.logging.LogUtils.getLogger();
        for (BlockPos pos : new BlockPos[]{cordA, cordB}) {
            if (mc.level.getBlockEntity(pos) instanceof com.strikerr212.minepc.lan.CordList.Holder h) {
                for (var e : h.cords().ends()) log.info("[CI-CORD] у игрока {}: порт {} → {} порт {}", pos == cordA ? "A" : "B",
                        e.port() + 1, e.other().equals(cordA) ? "A" : "B", e.otherPort() + 1);
            } else {
                log.info("[CI-CORD] у игрока нет блок-сущности {}", pos);
            }
        }
    }

    private static void placeCam() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerLevel level = server.overworld();
        camPos = drawPcA.offset(-2, 0, 2);
        level.setBlockAndUpdate(camPos, ModBlocks.CAMERA.get().defaultBlockState()
                .setValue(com.strikerr212.minepc.lan.CameraBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                .setValue(com.strikerr212.minepc.lan.CameraBlock.FACING, Direction.NORTH));
        if (level.getBlockEntity(camPos) instanceof com.strikerr212.minepc.lan.CameraBlockEntity cam) {
            cam.setLabel("двор");
            double dx = ioPos.getX() - camPos.getX(), dz = ioPos.getZ() - camPos.getZ();
            float worldYaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
            cam.look(worldYaw - cam.baseYaw(), -12);
            cam.setRes(4);
            cam.setFps(2);
        }
    }

    private static void cableCam() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        var a = drawSocket(level, camPos, false);
        var b = drawSocket(level, drawSwitch, false);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(com.strikerr212.minepc.registry.ModItems.CABLE.get(), 64));
        if (a == null || b == null) {
            com.mojang.logging.LogUtils.getLogger().info("[CI-CAM] нет гнезда: камера={} коммутатор={}", a, b);
            return;
        }
        com.strikerr212.minepc.lan.CableLaying.lay(p, net.minecraft.world.InteractionHand.MAIN_HAND, List.of(a, b));
    }

    private static void logCam(String when) {
        var level = Minecraft.getInstance().getSingleplayerServer().overworld();
        var log = com.mojang.logging.LogUtils.getLogger();
        if (level.getBlockEntity(camPos) instanceof com.strikerr212.minepc.lan.CameraBlockEntity cam) {
            log.info("[CI-CAM] {}: угол {} наклон {} снимок {} кадр {}×{} поток {} Б/с", when, cam.yaw(), cam.pitch(),
                    cam.snapshot() != null, cam.width(), cam.height(), cam.bytesPerSecond());
        }
        logScreens("[CI-CAM]");
    }

    /** Контроллер ввода-вывода у коммутатора сцены проводов: лампа на севере, редстоун-блок на юге, сундук сверху. */
    private static void placeIo() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        ioPos = drawBase.offset(12, 0, 2);
        level.setBlockAndUpdate(ioPos, ModBlocks.IO_CONTROLLER.get().defaultBlockState()
                .setValue(com.strikerr212.minepc.lan.IoControllerBlock.FACING, Direction.EAST));
        level.setBlockAndUpdate(ioPos.north(), net.minecraft.world.level.block.Blocks.REDSTONE_LAMP.defaultBlockState());
        level.setBlockAndUpdate(ioPos.south(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(ioPos.above(), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(ioPos.above()) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            for (int i = 0; i < 9; i++) chest.setItem(i, new ItemStack(net.minecraft.world.item.Items.WHEAT, 64));
        }
        if (level.getBlockEntity(ioPos) instanceof com.strikerr212.minepc.lan.IoControllerBlockEntity io) io.setLabel("ctl");
        p.teleportTo(level, ioPos.getX() + 3.5, ioPos.getY() + 1, ioPos.getZ() + 0.5, 90f, 25f);
    }

    private static void cableIo() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        var a = drawSocket(level, ioPos, false);
        var b = drawSocket(level, drawSwitch, false);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(com.strikerr212.minepc.registry.ModItems.CABLE.get(), 64));
        if (a == null || b == null) {
            com.mojang.logging.LogUtils.getLogger().info("[CI-IO] нет гнезда: контроллер={} коммутатор={}", a, b);
            return;
        }
        com.strikerr212.minepc.lan.CableLaying.lay(p, net.minecraft.world.InteractionHand.MAIN_HAND, List.of(a, b));
    }

    private static void logIo() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerLevel level = server.overworld();
        var log = com.mojang.logging.LogUtils.getLogger();
        var lamp = level.getBlockState(ioPos.north());
        if (level.getBlockEntity(ioPos) instanceof com.strikerr212.minepc.lan.IoControllerBlockEntity io) {
            log.info("[CI-IO] лампа горит: {} | вход юг {} | сундук сверху {} | адрес {} | выход север {}",
                    lamp.getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT), io.input(3), io.fill(1), io.address(), io.outLevel(2));
        }
        StringBuilder sb = new StringBuilder("[CI-IO] A ->");
        for (var r : com.strikerr212.minepc.lan.LanManager.get(level).reach(level, ioPos)) sb.append(' ').append(r.pos().toShortString());
        log.info(sb.toString());
        if (level.getBlockEntity(drawPcA) instanceof PcCaseBlockEntity pc) {
            for (String line : pc.screenLines()) if (!line.isBlank()) log.info("[CI-IO] | {}", line.stripTrailing());
        }
    }

    /** Окно корпуса 2.0: пустой корпус — только место под плату; плата — её места; процессор — место под кулер. */
    private static void placeEmptyCase(ServerLevel level) {
        ServerPlayer p = level.getServer().getPlayerList().getPlayers().get(0);
        emptyCasePos = p.blockPosition().above(2).north(2);
        level.setBlockAndUpdate(emptyCasePos, ModBlocks.PC_CASE.get().defaultBlockState().setValue(PcCaseBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(emptyCasePos) instanceof PcCaseBlockEntity be) {
            logSlots("пустой", be);
        }
    }

    private static void openEmptyCase(ServerLevel level) {
        ServerPlayer p = level.getServer().getPlayerList().getPlayers().get(0);
        if (level.getBlockEntity(emptyCasePos) instanceof PcCaseBlockEntity be) p.openMenu(be, buf -> buf.writeBlockPos(emptyCasePos));
    }

    private static void emptyCaseStep(ServerLevel level, int step) {
        if (!(level.getBlockEntity(emptyCasePos) instanceof PcCaseBlockEntity be)) return;
        var inv = (IItemHandlerModifiable) be.inventory();
        if (step == 1) {
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_MOTHERBOARD, HardwarePresets.motherboard(1));
            logSlots("плата ур. 1", be);
        } else {
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_CPU, HardwarePresets.cpu(1));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_RAM, HardwarePresets.ram(1));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_PSU, HardwarePresets.psu(2));
            logSlots("процессор", be);
            com.mojang.logging.LogUtils.getLogger().info("[CI-CASE2] вынуть плату: {} | кулер в пустой корпус: {}",
                    be.takeError(PcCaseBlockEntity.SLOT_MOTHERBOARD), be.placeError(PcCaseBlockEntity.SLOT_COOLER, HardwarePresets.cooler(1)));
        }
    }

    /** Плата 2.0: пять оформлений (бюджетная … премиум) на одной сборке. */
    private static void boardStyle(ServerLevel level, int style) {
        if (!(level.getBlockEntity(emptyCasePos) instanceof PcCaseBlockEntity be)) return;
        var inv = (IItemHandlerModifiable) be.inventory();
        ItemStack base = HardwarePresets.motherboard(3);
        java.util.List<Integer> v = new java.util.ArrayList<>(base.get(com.strikerr212.minepc.registry.ModDataComponents.DESIGN.get()).values());
        int[] pcb = {0, 3, 2, 2, 5};
        v.set(com.strikerr212.minepc.hardware.spec.MotherboardSpec.STYLE, style);
        v.set(com.strikerr212.minepc.hardware.spec.MotherboardSpec.PCB, pcb[style]);
        v.set(com.strikerr212.minepc.hardware.spec.MotherboardSpec.SATA, style / 2);
        v.set(com.strikerr212.minepc.hardware.spec.MotherboardSpec.M2, Math.min(2, style));
        if (style >= 3) v.set(com.strikerr212.minepc.hardware.spec.MotherboardSpec.LED_ON, 1);
        inv.setStackInSlot(PcCaseBlockEntity.SLOT_MOTHERBOARD, HardwarePresets.build(com.strikerr212.minepc.hardware.HardwareType.MOTHERBOARD, "", v));
        if (style == 2) inv.setStackInSlot(PcCaseBlockEntity.SLOT_GPU, HardwarePresets.gpu(3));
        if (style == 3) inv.setStackInSlot(PcCaseBlockEntity.SLOT_COOLER, HardwarePresets.cooler(3));
        if (style == 4) be.togglePower();
    }

    private static void logSlots(String what, PcCaseBlockEntity be) {
        StringBuilder b = new StringBuilder("[CI-CASE2] ").append(what).append(": видно");
        for (int i = 0; i < PcCaseBlockEntity.SLOT_COUNT; i++) if (be.slotPresent(i)) b.append(' ').append(i);
        var board = be.motherboard();
        if (board != null) b.append(" | плата: память ").append(board.ramSlots()).append(", M.2 ").append(board.m2Slots());
        com.mojang.logging.LogUtils.getLogger().info(b.toString());
    }

    private static ItemStack psuOut = ItemStack.EMPTY, diskOut = ItemStack.EMPTY;

    /** srv-b: вынуть (back = false) или вернуть второй блок питания и второй диск, как игрок через окно. */
    private static void hotSwapTest(ServerLevel level, boolean back) {
        PcCaseBlockEntity be = com.strikerr212.minepc.block.Hosts.get(level, rackPos, 1);
        if (be == null) return;
        var inv = be.inventory();
        int disk = PcCaseBlockEntity.SLOT_HDD + 1;
        StringBuilder sb = new StringBuilder("[CI-HOTSWAP] ");
        if (!back) {
            sb.append("до: state=").append(be.displayState()).append(" psu=").append(be.psuCount());
            psuOut = inv.extractItem(PcCaseBlockEntity.SLOT_PSU2, 1, false);
            diskOut = inv.extractItem(disk, 1, false);
            ItemStack cpu = inv.extractItem(PcCaseBlockEntity.SLOT_CPU2, 1, false);
            ItemStack ram = inv.extractItem(PcCaseBlockEntity.ramSlot(5), 1, false);
            ItemStack lastPsu = inv.extractItem(PcCaseBlockEntity.SLOT_PSU, 1, false);
            sb.append(" | вынут БП2=").append(!psuOut.isEmpty()).append(" диск=").append(!diskOut.isEmpty())
                    .append(" ЦП2=").append(!cpu.isEmpty()).append(" ОЗУ=").append(!ram.isEmpty()).append(" последний БП=").append(!lastPsu.isEmpty())
                    .append(" | после: state=").append(be.displayState()).append(" psu=").append(be.psuCount());
        } else {
            ItemStack r1 = inv.insertItem(PcCaseBlockEntity.SLOT_PSU2, psuOut, false);
            ItemStack r2 = inv.insertItem(disk, diskOut, false);
            sb.append("вернули БП2=").append(r1.isEmpty()).append(" диск=").append(r2.isEmpty())
                    .append(" | state=").append(be.displayState()).append(" psu=").append(be.psuCount());
        }
        com.mojang.logging.LogUtils.getLogger().info(sb.toString());
    }

    /** Выбрать вариант параметра на открытом сборочном столе, как щелчком. */
    private static void assemblySet(Minecraft mc, int param, int option) {
        if (mc.player != null && mc.gameMode != null && mc.player.containerMenu instanceof com.strikerr212.minepc.menu.AssemblyMenu m) {
            mc.gameMode.handleInventoryButtonClick(m.containerId, com.strikerr212.minepc.menu.AssemblyMenu.BTN_SET
                    + param * com.strikerr212.minepc.menu.AssemblyMenu.SET_STRIDE + option);
        }
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
        fillCase(level, pos, tier, Direction.NORTH);
    }

    private static void fillCase(ServerLevel level, BlockPos pos, int tier, Direction facing) {
        level.setBlockAndUpdate(pos, ModBlocks.PC_CASE.get().defaultBlockState().setValue(PcCaseBlock.FACING, facing));
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

    private static void charsAt(BlockPos pos, String text) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            if (server.overworld().getBlockEntity(pos) instanceof PcCaseBlockEntity be) {
                for (char c : text.toCharArray()) be.input(TerminalInputPayload.CHAR, String.valueOf(c), false, false);
            }
        });
    }

    private static void logScreens(String tag) {
        var level = Minecraft.getInstance().getSingleplayerServer().overworld();
        var log = com.mojang.logging.LogUtils.getLogger();
        for (BlockPos pos : new BlockPos[]{drawPcA, drawPcB}) {
            if (level.getBlockEntity(pos) instanceof PcCaseBlockEntity pc) {
                log.info("{} компьютер {}", tag, pos == drawPcA ? "A" : "B");
                for (String line : pc.screenLines()) if (!line.isBlank()) log.info("{} | {}", tag, line.stripTrailing());
            }
        }
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
        a = com.strikerr212.minepc.lan.Wire.LEGACY_PX[a];
        b = com.strikerr212.minepc.lan.Wire.LEGACY_PX[b];
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
        pcWithMonitor(level, pos, Direction.NORTH);
    }

    /** Компьютер лицом в сторону facing (сетевой разъём — сзади) и монитор на нём. */
    private static void pcWithMonitor(ServerLevel level, BlockPos pos, Direction facing) {
        fillCase(level, pos, 3, facing);
        level.setBlockAndUpdate(pos.above(), ModBlocks.MONITOR.get().defaultBlockState()
                .setValue(com.strikerr212.minepc.block.MonitorBlock.KEYBOARD, true).setValue(DeviceBlock.FACING, facing));
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
        pcWithMonitor(level, lanF, Direction.EAST);
        wire(level, b.east(5).north(), blue, 1, 2);
        wire(level, b.east(6).north(), blue, 1, 2);
        // Компьютер E — сзади патч-панели; кабель под маскировкой
        lanE = b.south(2);
        pcWithMonitor(level, lanE, Direction.EAST);
        wire(level, b.west().south(), blue, 1, 1);
        wire(level, b.west().south(2), blue, 1, 1);
        if (level.getBlockEntity(b.west().south()) instanceof com.strikerr212.minepc.lan.CableBlockEntity be) {
            be.setFacade(net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
        }
        // Лотки: от компьютера J1 вверх, поверху и вниз к J2
        lanJ1 = b.offset(8, 0, 4);
        lanJ2 = b.offset(15, 0, 4);
        pcWithMonitor(level, lanJ1, Direction.WEST);
        fillCase(level, lanJ2, 5, Direction.EAST);
        level.setBlockAndUpdate(lanJ2.above(), ModBlocks.MONITOR.get().defaultBlockState()
                .setValue(com.strikerr212.minepc.block.MonitorBlock.KEYBOARD, true).setValue(DeviceBlock.FACING, Direction.EAST));
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
        fillCase(level, lanK3, 2, Direction.WEST);
        fillCase(level, lanK4, 2, Direction.EAST);
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
