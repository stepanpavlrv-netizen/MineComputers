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
            case 1541 -> server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                p.teleportTo(p.serverLevel(), trayBase.getX() + 2.5, trayBase.getY() + 2.2, trayBase.getZ() - 2.6, 0f, 32f);
            });
            case 1544 -> shot(mc, "world_tray");
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
            // Маскировка: провод уходит в кирпич и выходит из него; прицел видит провод внутри
            case 1626 -> server.execute(() -> {
                if (maskCell == null) return;
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                // сверху: между глазом и кирпичом ничего нет
                var eye = new net.minecraft.world.phys.Vec3(maskPoint.x + 0.02, maskCell.getY() + 2.4, maskPoint.z - 0.35);
                var d = maskPoint.subtract(eye);
                float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) Math.toDegrees(-Math.atan2(d.y, Math.hypot(d.x, d.z)));
                p.teleportTo(p.serverLevel(), eye.x, eye.y - 1.62, eye.z, yaw, pitch);
            });
            case 1629 -> {
                shot(mc, "world_mask");
                server.execute(() -> {
                    if (maskCell == null) return;
                    ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                    var hit = com.strikerr212.minepc.wire.WireService.aimed(p);
                    com.mojang.logging.LogUtils.getLogger().info("[CI-MASK] блок {}, провод внутри спрятан {}, прицел видит провод {}, E видит {}",
                            p.serverLevel().getBlockState(maskCell).getBlock().getName().getString(),
                            com.strikerr212.minepc.wire.WireMask.inside(p.serverLevel(), maskPoint), hit == null ? "нет" : hit.line().id,
                            com.strikerr212.minepc.lan.LanManager.get(p.serverLevel()).reach(p.serverLevel(), lanE).size());
                });
            }
            // BIOS: классический (плата ур. 3) и UEFI (плата ур. 5)
            case 1630 -> { mc.options.hideGui = false; typeAt(lanE, "reboot"); typeAt(lanJ2, "reboot"); }
            case 1636, 1642, 1648, 1654, 1660 -> { keyAt(lanE, "delete"); keyAt(lanJ2, "delete"); }
            // Висячий провод сквозь блок: камень поперёк воздушной линии — линия обрывается и падает
            case 1670 -> server.execute(() -> {
                ServerLevel lvl = server.overworld();
                var data = com.strikerr212.minepc.lan.LanData.get(lvl);
                BlockPos mid = lanK3.east(7);
                com.mojang.logging.LogUtils.getLogger().info("[CI-CORD] до камня: проводов у кронштейна {}", data.linesAt(lanK3.east(2)).size());
                lvl.setBlockAndUpdate(mid, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            });
            case 1695 -> server.execute(() -> {
                ServerLevel lvl = server.overworld();
                var data = com.strikerr212.minepc.lan.LanData.get(lvl);
                com.mojang.logging.LogUtils.getLogger().info("[CI-CORD] после камня: проводов у кронштейна {}, K3 видит {}", data.linesAt(lanK3.east(2)).size(),
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
                // Призрак провода 3.0: от гнезда компьютера B к точке на полу у коммутатора
                var a = wireSocket(mc.level, drawPcB);
                if (a != null) WireDrawClient.debugPlan(net.minecraft.world.InteractionHand.MAIN_HAND, a,
                        List.of(floorPixel(drawBase.offset(11, 0, 4), 8, 8)));
            }
            case 4016 -> {
                var r = WireDrawClient.debugPreview();
                com.mojang.logging.LogUtils.getLogger().info("[CI-WIRE] призрак: {}", r == null ? "нет" : "узлов " + r.xs().length
                        + ", м " + r.meters() + ", ok " + r.ok() + ", " + r.problem());
                shot(mc, "wire_draw");
            }
            case 4020 -> {
                WireDrawClient.debugCancel();
                server.execute(CiScreenshots::layWires);
            }
            case 4036 -> com.mojang.logging.LogUtils.getLogger().info("[CI-WIRE] у игрока проводов: {}",
                    com.strikerr212.minepc.wire.WireStore.all().size());
            case 4030 -> server.execute(() -> logPower(server.overworld()));
            case 4038 -> server.execute(() -> server.getPlayerList().getPlayers().get(0).teleportTo(server.overworld(),
                    drawBase.getX() + 5.5, drawBase.getY() + 2.5, drawBase.getZ() - 4.5, -20f, 35f));
            case 4040 -> shot(mc, "wire_done");
            // Провода 3.0, шаг 2: воздушная линия между кронштейнами; убрали стену — провод над ней провисает
            case 4041 -> server.execute(CiScreenshots::wiresStep2);
            case 4043 -> {
                server.execute(CiScreenshots::logSag);
                shot(mc, "wire_sag");
            }
            case 4044 -> server.execute(() -> {
                logSag();
                placeEmptyCase(server.overworld());
            });
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
            // Провода 3.0, шаг 3: бирка на проводе A, тестер по проводу (подсветка целиком), окно коммутатора
            case 4121 -> server.execute(CiScreenshots::testWire3);
            case 4124 -> shot(mc, "wire_tester");
            case 4125 -> server.execute(() -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    server.getPlayerList().getPlayers().get(0), com.strikerr212.minepc.lan.SwitchPorts.openPayload(server.overworld(), drawSwitch)));
            case 4130 -> shot(mc, "wire_switch");
            case 4131 -> mc.setScreen(null);
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
            case 4520 -> server.execute(() -> logScreens("[CI-CAMLIST]"));   // camera.list: таблица камер
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
            // Камера с настоящими текстурами: сад в полдень
            case 4872 -> server.execute(CiScreenshots::placeGarden);
            case 4880 -> mc.setScreen(null);
            case 4884 -> mc.setScreen(new CameraScreen(gardenCam));
            case 4930 -> {
                logGarden();
                shot(mc, "camera_textured");
            }
            case 4934 -> server.execute(() -> {
                if (server.overworld().getBlockEntity(gardenCam) instanceof com.strikerr212.minepc.lan.CameraBlockEntity cam) cam.setRes(0);
            });
            case 4960 -> shot(mc, "camera_textured_64");
            // Wired: установка с дискеты из деревни, вход, рабочий стол, Проводник, «Пуск», Терминал в окне
            case 4966 -> {
                mc.setScreen(null);
                server.execute(CiScreenshots::placeWired);
            }
            case 5000 -> typeAt(wiredPos, "/disk/install");
            case 5012 -> keyAt(wiredPos, "enter");
            case 5018 -> typeAt(wiredPos, "admin");
            case 5022 -> typeAt(wiredPos, "secret1");
            case 5026 -> typeAt(wiredPos, "secret1");
            case 5030 -> mc.setScreen(new TerminalScreen(wiredPos));
            case 5050 -> {
                server.execute(() -> logWired("установка"));
                shot(mc, "wired_install");
            }
            case 5300 -> {
                server.execute(() -> logWired("после перезагрузки"));
                shot(mc, "wired_login");
            }
            case 5302 -> typeAt(wiredPos, "admin");
            case 5306 -> typeAt(wiredPos, "secret1");
            case 5330 -> shot(mc, "wired_desktop");
            case 5332, 5333 -> server.execute(() -> clickText(wiredPos, "Проводник", 2, -2));
            case 5340 -> server.execute(() -> clickText(wiredPos, "◆ WIRED", 1, 0));
            case 5350 -> shot(mc, "wired_menu");
            case 5352 -> server.execute(() -> clickText(wiredPos, "> Терминал", 3, 0));
            case 5366 -> typeAt(wiredPos, "ls /");
            case 5380 -> {
                server.execute(() -> logWired("окна"));
                shot(mc, "wired_windows");
            }
            // Дискета «Офис» из деревни: уведомление → установка → Документы
            case 5386 -> server.execute(() -> wiredDisk(null));
            case 5390 -> server.execute(() -> wiredDisk("office"));
            case 5410 -> server.execute(() -> logWired("дискета «Офис»"));
            case 5412 -> server.execute(() -> clickText(wiredPos, " Установить ", 2, 0));
            case 5424 -> server.execute(() -> clickText(wiredPos, " Установить ", 2, 0));
            case 5470 -> server.execute(() -> clickText(wiredPos, " Открыть ", 2, 0));
            case 5490 -> typeAt(wiredPos, "Привет из MineComputers!");
            case 5500 -> {
                server.execute(() -> logWired("офис"));
                shot(mc, "wired_office");
            }
            // Встроенные программы: Игры (Сапёр) и Музыка из «Пуска»
            case 5504 -> server.execute(() -> clickText(wiredPos, "◆ WIRED", 1, 0));
            case 5512 -> server.execute(() -> clickText(wiredPos, "Игры", 1, 0));
            case 5524 -> server.execute(() -> clickText(wiredPos, " Играть ", 2, 0));
            case 5532 -> server.execute(() -> clickText(wiredPos, "Сапёр · Новичок", -18, 4));
            case 5540 -> {
                server.execute(() -> logWired("игры"));
                shot(mc, "wired_games");
            }
            case 5544 -> server.execute(() -> clickText(wiredPos, "◆ WIRED", 1, 0));
            case 5552 -> server.execute(() -> clickText(wiredPos, "Музыка", 1, 0));
            case 5564 -> server.execute(() -> clickText(wiredPos, " ▶ Играть ", 2, 0));
            case 5600 -> {
                server.execute(() -> logWired("музыка"));
                shot(mc, "wired_music");
            }
            // Настенные роутер и коммутатор, корд в розетку, клавиатуры у широкого и изогнутого мониторов
            case 5604 -> {
                mc.setScreen(null);
                server.execute(CiScreenshots::placeMounts);
            }
            case 5650 -> shot(mc, "wall_mounts");
            case 5652 -> server.execute(() -> server.getPlayerList().getPlayers().get(0).teleportTo(server.overworld(),
                    mountPos.getX() + 3.5, mountPos.getY(), mountPos.getZ() - 2.0, 0f, -14f));
            case 5680 -> shot(mc, "wall_mounts_below");
            // 3D-игра «Разлом» с дискеты: облёт уровня, потом бой (бессмертие, всё оружие, огонь по стражу)
            case 5686 -> {
                mc.setScreen(null);
                server.execute(() -> {
                    boolean ok = server.overworld().getBlockEntity(lanJ2) instanceof PcCaseBlockEntity pc
                            && pc.mountProgramForTest("razlom", "razlom");
                    com.mojang.logging.LogUtils.getLogger().info("[CI-RAZLOM] дискета подключена: {}", ok);
                });
            }
            case 5690 -> typeAt(lanJ2, "/razlom/app/game.lua demo 12");
            case 5694 -> mc.setScreen(new TerminalScreen(lanJ2));
            case 5770 -> shot(mc, "razlom_demo_1");
            case 5850 -> { perf(mc, true, lanJ2); shot(mc, "razlom_demo_2"); }
            case 5930 -> { perf(mc, false, lanJ2); shot(mc, "razlom_demo_3"); }
            case 5990 -> typeAt(lanJ2, "/razlom/app/game.lua play at=13.5,7.5,0 god give");
            case 6040 -> keyAt(lanJ2, "2");
            case 6042 -> keyUpAt(lanJ2, "2");
            case 6046 -> keyAt(lanJ2, "ctrl");
            case 6066 -> { shot(mc, "razlom_fight"); logMouseLock(); }   // сверху подсказка «Мышь в игре · Esc — отпустить»
            case 6090 -> { keyUpAt(lanJ2, "ctrl"); keyAt(lanJ2, "up"); }
            case 6098 -> { keyUpAt(lanJ2, "up"); keyAt(lanJ2, "right"); }
            case 6110 -> keyUpAt(lanJ2, "right");
            case 6112 -> {   // поворот захваченной мышью: 45° вправо — как будто игрок двинул мышь
                server.execute(() -> {
                    if (server.overworld().getBlockEntity(lanJ2) instanceof PcCaseBlockEntity pc) {
                        pc.mouse(com.strikerr212.minepc.network.TerminalInputPayload.MOUSE_MOVE, 0, 4500, 0);
                    }
                });
            }
            case 6120 -> shot(mc, "razlom_look_up");
            case 6126 -> { keyAt(lanJ2, "q"); logRazlom(); }
            case 6128 -> keyAt(lanJ2, "q");
            case 6150 -> { logRazlom(); logMouseLock(); shot(mc, "razlom_exit"); }
            // Браузер с дискеты: стартовая страница — список сайтов майннета
            case 6154 -> mc.setScreen(new TerminalScreen(wiredPos));
            case 6156 -> server.execute(() -> wiredDisk(null));
            case 6160 -> server.execute(() -> wiredDisk("browser"));
            case 6182 -> server.execute(() -> clickText(wiredPos, " Установить ", 2, 0));
            case 6194 -> server.execute(() -> clickText(wiredPos, " Установить ", 2, 0));
            case 6240 -> server.execute(() -> clickText(wiredPos, " Открыть ", 2, 0));
            case 6320 -> {
                server.execute(() -> logWired("браузер: сайты майннета"));
                shot(mc, "wired_browser_sites");
            }
            // Большая серверная: сотни проводов в лотках — нет ли подтормаживаний, сеть та же
            case 6322 -> logPerf(mc, "до серверной");
            case 6324 -> server.execute(CiScreenshots::placeBigRoom);
            case 6380 -> {
                logPerf(mc, "серверная");
                shot(mc, "world_big_room");
            }
            case 6384 -> server.execute(CiScreenshots::logBigRoom);
            case 6390 -> mc.stop();
            default -> {
            }
        }
    }

    private static BlockPos mountPos = BlockPos.ZERO;

    /** Стена: роутер и коммутатор на ней, розетка с кордом в коммутатор; перед стеной — мониторы в два блока с клавиатурами. */
    private static void placeMounts() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerLevel level = server.overworld();
        BlockPos b = wiredPos.south(14);
        mountPos = b;
        for (int dx = -3; dx <= 9; dx++) {
            for (int dz = -9; dz <= 4; dz++) {
                level.setBlockAndUpdate(b.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
                for (int dy = 0; dy <= 4; dy++) level.setBlockAndUpdate(b.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }
        for (int dx = -1; dx <= 7; dx++) {
            for (int dy = 0; dy <= 3; dy++) level.setBlockAndUpdate(b.offset(dx, dy, 2), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
        }
        BlockPos router = b.offset(1, 2, 1), sw = b.offset(4, 2, 1), outlet = b.offset(6, 1, 1);
        level.setBlockAndUpdate(router, ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(com.strikerr212.minepc.lan.RouterBlock.FACING, Direction.NORTH).setValue(com.strikerr212.minepc.lan.WallMount.WALL, true));
        level.setBlockAndUpdate(sw, ModBlocks.NETWORK_SWITCH.get().defaultBlockState()
                .setValue(com.strikerr212.minepc.lan.SwitchBlock.FACING, Direction.NORTH).setValue(com.strikerr212.minepc.lan.WallMount.WALL, true));
        level.setBlockAndUpdate(outlet, ModBlocks.OUTLET.get().defaultBlockState()
                .setValue(com.strikerr212.minepc.lan.OutletBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                .setValue(com.strikerr212.minepc.lan.OutletBlock.FACING, Direction.NORTH));
        for (BlockPos d : new BlockPos[]{router, sw, outlet}) com.strikerr212.minepc.lan.LanManager.updateDevice(level, d);
        cord(level, outlet, com.strikerr212.minepc.lan.LanData.CABLE_PORTS, sw, 0, net.minecraft.world.item.DyeColor.BLUE.getId());
        cord(level, router, 1, sw, 2, net.minecraft.world.item.DyeColor.YELLOW.getId());
        // Мониторы смотрят на север, к игроку; «вправо» для зрителя — на запад
        for (int k = 0; k < 2; k++) {
            BlockPos c = b.offset(2 + k * 4, 0, -3);
            var wide = (k == 0 ? ModBlocks.MONITOR_WIDE : ModBlocks.MONITOR_CURVED).get().defaultBlockState()
                    .setValue(DeviceBlock.FACING, Direction.NORTH).setValue(com.strikerr212.minepc.block.MonitorBlock.KEYBOARD, true);
            level.setBlockAndUpdate(c, wide.setValue(com.strikerr212.minepc.block.WideMonitorBlock.HALF,
                    com.strikerr212.minepc.block.WideMonitorBlock.Half.LEFT));
            level.setBlockAndUpdate(c.west(), wide.setValue(com.strikerr212.minepc.block.WideMonitorBlock.HALF,
                    com.strikerr212.minepc.block.WideMonitorBlock.Half.RIGHT));
        }
        var p = server.getPlayerList().getPlayers().get(0);
        p.teleportTo(level, b.getX() + 3.5, b.getY(), b.getZ() - 7.5, 0f, 8f);
        com.mojang.logging.LogUtils.getLogger().info("[CI-MOUNT] роутер {}, коммутатор {}, розетка {}", level.getBlockState(router),
                level.getBlockState(sw), level.getBlockState(outlet));
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
    private static long comfortLooseLine = -1;

    /** Проложить маршрут кабеля между устройствами с биркой; в журнал — длина или «нет пути». */
    private static void route(ServerLevel level, BlockPos from, BlockPos to, String label) {
        var l = link(level, from, to, net.minecraft.world.item.DyeColor.BLUE.getId(), false, label);
        com.mojang.logging.LogUtils.getLogger().info("[CI-NET29] маршрут {} -> {} «{}»: {}", from.toShortString(), to.toShortString(), label,
                l == null ? "нет пути" : l.meters() + " м");
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
        var loose = com.strikerr212.minepc.wire.WireService.layDirect(level, false, net.minecraft.world.item.DyeColor.ORANGE.getId(), "",
                com.strikerr212.minepc.wire.WirePlan.Anchor.pixel(floorPixel(comfortLoose, 2, 8)), List.of(),
                com.strikerr212.minepc.wire.WirePlan.Anchor.pixel(floorPixel(comfortLoose.east(), 13, 8)));
        comfortLooseLine = loose == null ? -1 : loose.id;
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

    /** Первое свободное медное гнездо устройства. */
    private static com.strikerr212.minepc.wire.WirePlan.Anchor wireSocket(net.minecraft.world.level.Level level, BlockPos dev) {
        return freeSocket(level, dev, false, null);
    }

    /** Пиксель на полу клетки cell (над её нижней гранью). */
    private static int[] floorPixel(BlockPos cell, int u, int v) {
        return new int[]{cell.getX() * 16 + u, cell.getY() * 16, cell.getZ() * 16 + v};
    }

    /** [CI-WIRE]: провода 3.0 — сами через стену, щелчками, свободный конец, продолжить, срастить, разрезать. */
    private static void layWires() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        var log = com.mojang.logging.LogUtils.getLogger();
        var data = com.strikerr212.minepc.lan.LanData.get(level);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(com.strikerr212.minepc.registry.ModItems.CABLE.get(), 64));
        java.util.function.Function<com.strikerr212.minepc.network.WireLayPayload, String> lay = req -> {
            int before = data.lines().size();
            long t0 = System.nanoTime();
            com.strikerr212.minepc.wire.WireService.lay(p, req);
            return "проводов " + before + "→" + data.lines().size() + ", " + (System.nanoTime() - t0) / 1000 + " мкс";
        };
        // 1) A → коммутатор: путь сам, через стену в 2 блока
        var a = wireSocket(level, drawPcA);
        var sw = wireSocket(level, drawSwitch);
        log.info("[CI-WIRE] A → коммутатор (сам, через стену): {}", a == null || sw == null ? "нет гнезда"
                : lay.apply(new com.strikerr212.minepc.network.WireLayPayload(false, a, List.of(), sw)));
        // 2) B → две точки на полу → коммутатор
        var b = wireSocket(level, drawPcB);
        var sw2 = wireSocket(level, drawSwitch);
        log.info("[CI-WIRE] B → коммутатор (щелчками): {}", b == null || sw2 == null ? "нет гнезда"
                : lay.apply(new com.strikerr212.minepc.network.WireLayPayload(false, b,
                List.of(floorPixel(drawBase.offset(11, 0, 7), 8, 8), floorPixel(drawBase.offset(11, 0, 4), 8, 8)), sw2)));
        // 3) Свободный конец: коммутатор → точка на полу (провод C); отдельный кусок D; C продолжить и срастить с D
        var sw3 = wireSocket(level, drawSwitch);
        if (sw3 != null) {
            log.info("[CI-WIRE] C: коммутатор → пол (свободный конец): {}", lay.apply(new com.strikerr212.minepc.network.WireLayPayload(false, sw3,
                    List.of(floorPixel(drawBase.offset(8, 0, -2), 8, 8)), null)));
            log.info("[CI-WIRE] D: кусок на полу: {}", lay.apply(new com.strikerr212.minepc.network.WireLayPayload(false,
                    com.strikerr212.minepc.wire.WirePlan.Anchor.pixel(floorPixel(drawBase.offset(2, 0, -3), 8, 8)),
                    List.of(floorPixel(drawBase.offset(4, 0, -3), 8, 8)), null)));
            com.strikerr212.minepc.wire.WireLine c = data.lineAt(drawSwitch, sw3.port()), d = null;
            for (var l : data.lines()) if (l.devA == null && l.devB == null) d = l;
            if (c != null && d != null) {
                log.info("[CI-WIRE] C продолжить и срастить с D: {}", lay.apply(new com.strikerr212.minepc.network.WireLayPayload(false,
                        com.strikerr212.minepc.wire.WirePlan.Anchor.end(c.id, true), List.of(floorPixel(drawBase.offset(6, 0, -3), 8, 8)),
                        com.strikerr212.minepc.wire.WirePlan.Anchor.end(d.id, false))));
                var merged = data.line(c.id);
                log.info("[CI-WIRE] после срастания: D есть {}, C узлов {}, м {}, концы {} / {}", data.line(d.id) != null,
                        merged == null ? 0 : merged.nodes(), merged == null ? 0 : merged.meters(),
                        merged == null ? "-" : merged.devA, merged == null ? "-" : merged.devB);
                if (merged != null) {
                    int seg = merged.nodes() / 2;
                    var mid = merged.node(seg).lerp(merged.node(seg + 1), 0.5);
                    com.strikerr212.minepc.wire.WireService.cutAt(level, merged.id, seg, mid);
                    int free = 0;
                    for (var l : data.lines()) free += (l.free(false) ? 1 : 0) + (l.free(true) ? 1 : 0);
                    log.info("[CI-WIRE] разрезали посередине: проводов {}, свободных концов {}", data.lines().size(), free);
                }
            }
        }
        for (var l : data.lines()) {
            log.info("[CI-WIRE] провод {}: узлов {}, м {}, {}:{} → {}:{}, висит отрезков {}", l.id, l.nodes(), l.meters(), l.devA, l.portA, l.devB, l.portB,
                    java.util.stream.IntStream.range(0, l.hang.length).filter(i -> l.hang[i]).count());
        }
        server.execute(() -> {
            var lan = com.strikerr212.minepc.lan.LanManager.get(level);
            StringBuilder sbr = new StringBuilder("[CI-WIRE] связь A ->");
            for (var r : lan.reach(level, drawPcA)) sbr.append(' ').append(r.pos().equals(drawPcB) ? "B" : r.pos().toShortString()).append(" hops=").append(r.hops());
            sbr.append(" | кабеля в руке ").append(p.getMainHandItem().getCount());
            log.info(sbr.toString());
        });
    }

    /** [CI-WIRE3]: провод A подписан, тестер по нему; что нашла прозвонка; провод со свободным концом в коммутаторе. */
    private static void testWire3() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        var log = com.mojang.logging.LogUtils.getLogger();
        var data = com.strikerr212.minepc.lan.LanData.get(level);
        com.strikerr212.minepc.wire.WireLine a = null;
        for (var l : data.lines()) if (drawPcA.equals(l.devA) || drawPcA.equals(l.devB)) a = l;
        if (a == null) {
            log.info("[CI-WIRE3] провода от A нет");
            return;
        }
        data.putLine(new com.strikerr212.minepc.wire.WireLine(a.id, a.fiber, a.color, "серверная-1", a.xs, a.ys, a.zs, a.hang,
                a.devA, a.portA, a.devB, a.portB));
        a = data.line(a.id);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(com.strikerr212.minepc.registry.ModItems.CABLE_TESTER.get()));
        p.teleportTo(level, drawBase.getX() + 5.5, drawBase.getY() + 2.5, drawBase.getZ() - 4.5, -20f, 35f);
        var t = com.strikerr212.minepc.lan.LanManager.trace(data, null, 0, a);
        com.strikerr212.minepc.lan.CableTesterItem.testLine(level, p, p.getMainHandItem(), a);
        log.info("[CI-WIRE3] прозвонка провода A: проводов {}, концов {}, длина {}, обрывов {}, бирка в порту: {}",
                t.lines().size(), t.ends().size(), t.length(), t.open().size(),
                com.strikerr212.minepc.lan.LanManager.portLabelName(data, a.devA != null ? a.devA : a.devB, a.devA != null ? a.portA : a.portB));
        StringBuilder sb = new StringBuilder("[CI-WIRE3] порты коммутатора:");
        for (var port : com.strikerr212.minepc.lan.SwitchPorts.ports(level, drawSwitch)) {
            sb.append(" | ").append(port.side()).append(port.number()).append(' ').append(port.device()).append(' ').append(port.length())
                    .append(port.label().isEmpty() ? "" : " «" + port.label() + "»");
        }
        log.info(sb.toString());
    }

    private static long sagLine = -1;
    private static int sagBefore;

    /** [CI-WIRE2]: воздушная линия кронштейн ~~ кронштейн (и слишком длинная — отказ); стену под проводом A убрали. */
    private static void wiresStep2() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        var log = com.mojang.logging.LogUtils.getLogger();
        var data = com.strikerr212.minepc.lan.LanData.get(level);
        BlockState bracket = ModBlocks.BRACKET.get().defaultBlockState()
                .setValue(com.strikerr212.minepc.lan.BracketBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR);
        BlockPos k1 = drawBase.offset(1, 3, 8), k2 = drawBase.offset(11, 3, 8);
        for (BlockPos k : new BlockPos[]{k1, k2}) {
            for (int dy = 1; dy <= 3; dy++) level.setBlockAndUpdate(k.below(dy), net.minecraft.world.level.block.Blocks.SPRUCE_LOG.defaultBlockState());
            level.setBlockAndUpdate(k, bracket);
            com.strikerr212.minepc.lan.LanManager.updateDevice(level, k);
        }
        var a = com.strikerr212.minepc.wire.WirePlan.Anchor.socket(k1, 0);
        var b = com.strikerr212.minepc.wire.WirePlan.Anchor.socket(k2, 0);
        var plan = com.strikerr212.minepc.wire.WirePlan.build(level, data::line, false, a, List.of(), b, 60000);
        log.info("[CI-WIRE2] пролёт: ok {}, узлов {}, висит {}, {}", plan.ok(), plan.xs().length, plan.hang().length > 0 && plan.hang()[0], plan.problem());
        int before = data.lines().size();
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(com.strikerr212.minepc.registry.ModItems.CABLE.get(), 64));
        com.strikerr212.minepc.wire.WireService.lay(p, new com.strikerr212.minepc.network.WireLayPayload(false, a, List.of(), b));
        log.info("[CI-WIRE2] воздушная линия: проводов {}→{}", before, data.lines().size());
        // слишком длинный пролёт
        BlockPos k3 = drawBase.offset(-3, 3, -30);
        level.setBlockAndUpdate(k3, bracket);
        var far = com.strikerr212.minepc.wire.WirePlan.build(level, data::line, false, com.strikerr212.minepc.wire.WirePlan.Anchor.socket(k3, 0),
                List.of(), b, 60000);
        log.info("[CI-WIRE2] пролёт 40 блоков: ok {}, {}", far.ok(), far.problem());
        level.removeBlock(k3, false);
        // провод A → коммутатор лежит через стену: убрать стену — он должен провиснуть
        for (var l : data.linesAt(drawPcA)) {
            sagLine = l.id;
            int h = 0;
            for (boolean x : l.hang) if (x) h++;
            sagBefore = h;
        }
        for (int dz = -5; dz <= 9; dz++) {
            for (int dy = 0; dy <= 1; dy++) level.setBlockAndUpdate(drawBase.offset(5, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
    }

    private static void logSag() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerLevel level = server.overworld();
        var log = com.mojang.logging.LogUtils.getLogger();
        var data = com.strikerr212.minepc.lan.LanData.get(level);
        var l = data.line(sagLine);
        log.info("[CI-WIRE2] стену убрали: провод A {}, висящих отрезков {}→{}, узлов {}", l == null ? "пропал" : "цел", sagBefore,
                l == null ? 0 : java.util.stream.IntStream.range(0, l.hang.length).filter(i -> l.hang[i]).count(), l == null ? 0 : l.nodes());
        if (l != null) {
            StringBuilder nodes = new StringBuilder();
            for (int i = 0; i < l.nodes(); i++) nodes.append(' ').append(l.xs[i]).append(',').append(l.ys[i]).append(',').append(l.zs[i])
                    .append(i < l.hang.length && l.hang[i] ? "~" : "");
            var fixed = com.strikerr212.minepc.wire.WireSupport.fix(new com.strikerr212.minepc.wire.LevelVoxels(level), l);
            log.info("[CI-WIRE2] узлы A:{} | поправить сейчас: {}", nodes, fixed == null ? "нечего" : fixed.nodes() + " узлов");
        }
        var lan = com.strikerr212.minepc.lan.LanManager.get(level);
        StringBuilder sb = new StringBuilder("[CI-WIRE2] связь A ->");
        for (var r : lan.reach(level, drawPcA)) sb.append(' ').append(r.pos().equals(drawPcB) ? "B" : r.pos().toShortString());
        log.info(sb.toString());
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
            var t = com.strikerr212.minepc.lan.LanManager.trace(data, comfortPcN, used.get(0), null);
            log.info("[CI-NET29] прозвонка pcN порт {}: длина {} концов {} обрывов {} проводов {} пассивных {}", used.get(0) + 1,
                    t.length(), t.ends().size(), t.open().size(), t.lines().size(), t.passives().size());
        }
        var looseLine = data.line(comfortLooseLine);
        if (looseLine == null) {
            log.info("[CI-NET29] прозвонка куска: куска нет");
        } else {
            var loose = com.strikerr212.minepc.lan.LanManager.trace(data, null, 0, looseLine);
            log.info("[CI-NET29] прозвонка куска: длина {} концов {} обрывов {}", loose.length(), loose.ends().size(), loose.open().size());
        }
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
        link(level, rk, sw, blue, false, "");
        link(level, rk, sw, orange, false, "");
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
    private static BlockPos gardenCam = BlockPos.ZERO;

    /** Сад для камеры: дерево, трава и цветы, кирпичная стенка с окном, дорожка из плит, корова и овца. */
    private static void placeGarden() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        BlockPos g = drawBase.offset(0, 0, -34);
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -16; dz <= 6; dz++) {
                level.setBlockAndUpdate(g.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
                for (int dy = 0; dy <= 7; dy++) level.setBlockAndUpdate(g.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }
        java.util.Random r = new java.util.Random(7);
        for (int k = 0; k < 140; k++) {
            BlockPos f = g.offset(r.nextInt(25) - 12, 0, r.nextInt(20) - 15);
            var flower = switch (r.nextInt(9)) {
                case 0 -> net.minecraft.world.level.block.Blocks.POPPY;
                case 1 -> net.minecraft.world.level.block.Blocks.DANDELION;
                case 2 -> net.minecraft.world.level.block.Blocks.CORNFLOWER;
                default -> net.minecraft.world.level.block.Blocks.SHORT_GRASS;
            };
            level.setBlockAndUpdate(f, flower.defaultBlockState());
        }
        // дерево
        BlockPos t = g.offset(4, 0, -8);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 3; dy <= 5; dy++) {
            if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && dy == 5) continue;
            level.setBlockAndUpdate(t.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        }
        for (int dy = 0; dy <= 4; dy++) level.setBlockAndUpdate(t.above(dy), net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState());
        // стенка с окном
        for (int dx = -6; dx <= -2; dx++) for (int dy = 0; dy <= 3; dy++) {
            boolean win = dy >= 1 && dy <= 2 && (dx == -5 || dx == -4 || dx == -3);
            var b = win ? net.minecraft.world.level.block.Blocks.GLASS : dy == 0 ? net.minecraft.world.level.block.Blocks.COBBLESTONE
                    : net.minecraft.world.level.block.Blocks.BRICKS;
            level.setBlockAndUpdate(g.offset(dx, dy, -9), b.defaultBlockState());
        }
        // дорожка из плит и фонарь
        for (int dz = -1; dz >= -7; dz--) level.setBlockAndUpdate(g.offset(0, 0, dz), net.minecraft.world.level.block.Blocks.OAK_SLAB.defaultBlockState());
        level.setBlockAndUpdate(g.offset(1, 0, -7), net.minecraft.world.level.block.Blocks.OAK_FENCE.defaultBlockState());
        level.setBlockAndUpdate(g.offset(1, 1, -7), net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState());
        // животные
        var cow = net.minecraft.world.entity.EntityType.COW.create(level);
        var sheep = net.minecraft.world.entity.EntityType.SHEEP.create(level);
        for (var e : new net.minecraft.world.entity.Mob[]{cow, sheep}) {
            if (e == null) continue;
            e.setNoAi(true);
            level.addFreshEntity(e);
        }
        if (cow != null) cow.moveTo(g.getX() + 2.5, g.getY(), g.getZ() - 4.5, 90f, 0f);
        if (sheep != null) sheep.moveTo(g.getX() - 2.5, g.getY(), g.getZ() - 5.5, -30f, 0f);
        // камера на каменном столбике, смотрит на север
        level.setBlockAndUpdate(g.offset(0, 0, 3), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        gardenCam = g.offset(0, 1, 3);
        level.setBlockAndUpdate(gardenCam, ModBlocks.CAMERA.get().defaultBlockState()
                .setValue(com.strikerr212.minepc.lan.CameraBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                .setValue(com.strikerr212.minepc.lan.CameraBlock.FACING, Direction.NORTH));
        if (level.getBlockEntity(gardenCam) instanceof com.strikerr212.minepc.lan.CameraBlockEntity cam) {
            cam.setLabel("сад");
            cam.look(0 - cam.baseYaw(), -8);
            cam.setRes(6);
            cam.setFps(2);
        }
        level.setDayTime(5000);
        p.teleportTo(level, g.getX() + 0.5, g.getY(), g.getZ() + 5.5, 180f, 10f);
    }

    private static void logGarden() {
        var level = Minecraft.getInstance().getSingleplayerServer().overworld();
        int n = 0;
        for (var t : com.strikerr212.minepc.lan.BlockTextures.table()) if (t != null) n++;
        com.mojang.logging.LogUtils.getLogger().info("[CI-TEX] таблица текстур: готова {}, блоков с текстурой {}, трава {}",
                com.strikerr212.minepc.lan.BlockTextures.ready(), n,
                com.strikerr212.minepc.lan.BlockTextures.get(net.minecraft.world.level.block.Blocks.SHORT_GRASS) != null
                        && com.strikerr212.minepc.lan.BlockTextures.get(net.minecraft.world.level.block.Blocks.SHORT_GRASS).cross);
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            if (level.getBlockEntity(gardenCam) instanceof com.strikerr212.minepc.lan.CameraBlockEntity cam) {
                cam.render(320, 180);
                long t0 = System.nanoTime();
                for (int k = 0; k < 5; k++) cam.render(320, 180);
                com.mojang.logging.LogUtils.getLogger().info("[CI-TEX] кадр 320×180: {} мс", (System.nanoTime() - t0) / 5_000_000);
            }
        });
    }

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
        var hit = new net.minecraft.world.phys.BlockHitResult(com.strikerr212.minepc.wire.CordShape.socketPoint(pos, so), so.face(), pos, false);
        ItemStack stack = p.getMainHandItem();
        var r = stack.getItem().onItemUseFirst(stack, new net.minecraft.world.item.context.UseOnContext(p, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        var data = com.strikerr212.minepc.lan.LanData.get(level);
        BlockPos why = pos == cordB ? com.strikerr212.minepc.wire.CordShape.blocker(level, cordA, 1, cordB, socket) : null;
        com.mojang.logging.LogUtils.getLogger().info("[CI-CORD] клик {} гнездо {} → {} (проводов у A {}, мешает {} {})", pos == cordA ? "A" : "B",
                socket + 1, r, data.linesAt(cordA).size(), why, why == null ? "" : level.getBlockState(why).getBlock().getName().getString());
    }

    private static void logCords() {
        var mc = Minecraft.getInstance();
        var log = com.mojang.logging.LogUtils.getLogger();
        for (var l : com.strikerr212.minepc.wire.WireStore.all()) {
            boolean near = (l.devA != null && (l.devA.equals(cordA) || l.devA.equals(cordB))) || (l.devB != null && (l.devB.equals(cordA) || l.devB.equals(cordB)));
            if (near) log.info("[CI-CORD] у игрока провод {}: {} порт {} → {} порт {}, узлов {}, м {}", l.id,
                    cordA.equals(l.devA) ? "A" : "B", l.portA + 1, cordA.equals(l.devB) ? "A" : "B", l.portB + 1, l.nodes(), l.meters());
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
        var a = freeSocket(level, camPos, false, null);
        var b = freeSocket(level, drawSwitch, false, null);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(com.strikerr212.minepc.registry.ModItems.CABLE.get(), 64));
        if (a == null || b == null) {
            com.mojang.logging.LogUtils.getLogger().info("[CI-CAM] нет гнезда: камера={} коммутатор={}", a, b);
            return;
        }
        com.strikerr212.minepc.wire.WireService.lay(p, new com.strikerr212.minepc.network.WireLayPayload(false, a, List.of(), b));
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
        var a = freeSocket(level, ioPos, false, null);
        var b = freeSocket(level, drawSwitch, false, null);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(com.strikerr212.minepc.registry.ModItems.CABLE.get(), 64));
        if (a == null || b == null) {
            com.mojang.logging.LogUtils.getLogger().info("[CI-IO] нет гнезда: контроллер={} коммутатор={}", a, b);
            return;
        }
        com.strikerr212.minepc.wire.WireService.lay(p, new com.strikerr212.minepc.network.WireLayPayload(false, a, List.of(), b));
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

    private static void keyUpAt(BlockPos pos, String key) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            if (server.overworld().getBlockEntity(pos) instanceof PcCaseBlockEntity be) be.input(TerminalInputPayload.KEY_UP, key, false, false);
        });
    }

    /** Захват мыши, как его видит клиент (кадр с сервера) и окно экрана. */
    private static void logMouseLock() {
        var s = ClientTerminals.get(lanJ2);
        com.mojang.logging.LogUtils.getLogger().info("[CI-RAZLOM] захват мыши: {}", s != null && s.mouseLock());
    }

    /** Нижние строки экрана компьютера с «Разломом» (счёт, сообщения об ошибках) — в журнал. */
    private static void logRazlom() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            if (server.overworld().getBlockEntity(lanJ2) instanceof PcCaseBlockEntity pc) {
                var lines = pc.screenLines();
                for (int i = Math.max(0, lines.size() - 4); i < lines.size(); i++) {
                    String l = lines.get(i).replace("▀", "").strip();
                    if (!l.isBlank()) com.mojang.logging.LogUtils.getLogger().info("[CI-RAZLOM] | {}", l);
                }
            }
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

    /** Первое свободное гнездо устройства для меди или оптики (face — только на этой стороне; null — на любой). */
    private static com.strikerr212.minepc.wire.WirePlan.Anchor freeSocket(net.minecraft.world.level.Level level, BlockPos dev, boolean fiber,
                                                                         Direction face) {
        var socks = com.strikerr212.minepc.lan.LanDevice.socketsAt(level, dev);
        for (int i = 0; i < socks.size(); i++) {
            if (face != null && socks.get(i).face() != face) continue;
            if (socks.get(i).accepts(fiber) && com.strikerr212.minepc.wire.WireSockets.socketFree(level, dev, i)) {
                return com.strikerr212.minepc.wire.WirePlan.Anchor.socket(dev, i);
            }
        }
        return null;
    }

    /** Провод между свободными гнёздами двух устройств (путь — как у игрока); null — не проложился. */
    private static com.strikerr212.minepc.wire.WireLine link(ServerLevel level, BlockPos a, BlockPos b, int color, boolean fiber, String label) {
        return link(level, a, null, b, null, color, fiber, label, List.of());
    }

    /** То же, но гнёзда — на сторонах faceA и faceB (null — на любой), и путь через точки clicks. */
    private static com.strikerr212.minepc.wire.WireLine link(ServerLevel level, BlockPos a, Direction faceA, BlockPos b, Direction faceB, int color,
                                                             boolean fiber, String label, List<int[]> clicks) {
        var fa = freeSocket(level, a, fiber, faceA);
        var fb = freeSocket(level, b, fiber, faceB);
        var l = fa == null || fb == null ? null : com.strikerr212.minepc.wire.WireService.layDirect(level, fiber, color, label, fa, clicks, fb);
        if (l == null) com.mojang.logging.LogUtils.getLogger().info("[CI-LINK] провод {} → {} не проложился (гнёзда {} / {})",
                a.toShortString(), b.toShortString(), fa, fb);
        return l;
    }

    /** Шнур (патч-корд) между портами двух устройств. */
    private static com.strikerr212.minepc.wire.WireLine cord(ServerLevel level, BlockPos a, int pa, BlockPos b, int pb, int color) {
        var data = com.strikerr212.minepc.lan.LanData.get(level);
        var l = com.strikerr212.minepc.wire.WireLines.cord(level, data.newLineId(), a, pa, b, pb, color, "");
        data.putLine(l);
        return l;
    }

    /**
     * Сеть: два компьютера и коммутатор, провода в гнёзда коммутатора; третий компьютер за кирпичной стенкой — провод
     * поднимается по ней, идёт поверху и спускается; роутер Wi-Fi и ноутбук без кабеля.
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
        // C: компьютер за кирпичной стенкой
        for (int dx = 2; dx <= 4; dx++) level.setBlockAndUpdate(n.east(dx).north(2), net.minecraft.world.level.block.Blocks.BRICKS.defaultBlockState());
        netC = n.east(3).north(4);
        fillCase(level, netC, 2);
        put(level, netC.above(), ModBlocks.MONITOR.get().defaultBlockState().setValue(com.strikerr212.minepc.block.MonitorBlock.KEYBOARD, true));
        // Роутер Wi-Fi рядом с проводом C
        BlockPos router = n.east(4).north();
        level.setBlockAndUpdate(router, ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(com.strikerr212.minepc.lan.RouterBlock.FACING, Direction.NORTH));
        if (level.getBlockEntity(router) instanceof com.strikerr212.minepc.lan.RouterBlockEntity r) r.apply("Office", "minecraft1", 32);
        // Провода: A и B → коммутатор; C — через верх стенки (точка на её верху); роутер → коммутатор
        link(level, netA, sw, blue, false, "");
        link(level, netB, sw, blue, false, "");
        link(level, sw, null, netC, null, blue, false, "", List.of(floorPixel(n.east(3).north(2).above(), 8, 8)));
        link(level, sw, router, blue, false, "");
        // Рядом — отдельная оранжевая сеть: два провода по стене, просто для вида
        for (int dz = 0; dz <= 2; dz++) level.setBlockAndUpdate(n.west(2).south(dz), net.minecraft.world.level.block.Blocks.BRICKS.defaultBlockState());
        for (int k = 0; k < 2; k++) {
            int x = n.west().getX() * 16, y = n.getY() * 16 + 5 + 5 * k;
            com.strikerr212.minepc.wire.WireService.layDirect(level, false, orange, "",
                    com.strikerr212.minepc.wire.WirePlan.Anchor.pixel(new int[]{x, y, n.getZ() * 16 + 1}), List.of(),
                    com.strikerr212.minepc.wire.WirePlan.Anchor.pixel(new int[]{x, y, n.south(2).getZ() * 16 + 14}));
        }
        netD = n.offset(-2, 0, 6);
        fillCase(level, netD, 3);
        put(level, netD.above(), ModBlocks.MONITOR.get().defaultBlockState().setValue(com.strikerr212.minepc.block.MonitorBlock.KEYBOARD, true));
        if (level.getBlockEntity(netD) instanceof PcCaseBlockEntity be) {
            ((IItemHandlerModifiable) be.inventory()).setStackInSlot(PcCaseBlockEntity.SLOT_USB,
                    new net.minecraft.world.item.ItemStack(com.strikerr212.minepc.registry.ModItems.WIFI_N.get()));
        }
        netRouter = router;
        p.teleportTo(level, n.getX() + 3.5, n.getY() + 2.5, n.getZ() - 7.5, 0f, 22f);
    }

    private static BlockPos lanK3 = BlockPos.ZERO, lanK4 = BlockPos.ZERO, lanK5 = BlockPos.ZERO, lanK6 = BlockPos.ZERO;
    private static BlockPos lanBase = BlockPos.ZERO, lanE = BlockPos.ZERO, lanF = BlockPos.ZERO, lanJ1 = BlockPos.ZERO,
            lanJ2 = BlockPos.ZERO, radio1 = BlockPos.ZERO;

    private static BlockPos bigPcA = BlockPos.ZERO, bigPcB = BlockPos.ZERO;

    /** [CI-BIG]: кадров в секунду у игрока и средний тик сервера. */
    private static void logPerf(Minecraft mc, String what) {
        var server = mc.getSingleplayerServer();
        com.mojang.logging.LogUtils.getLogger().info("[CI-BIG] {}: {} кадр/с, тик сервера {} мс, проводов у игрока {}", what, mc.getFps(),
                server == null ? -1 : String.format(java.util.Locale.ROOT, "%.2f", server.getAverageTickTimeNanos() / 1e6),
                com.strikerr212.minepc.wire.WireStore.all().size());
    }

    /**
     * Большая серверная: два ряда патч-панелей (12 колонн по 3 в высоту) друг напротив друга через проход в 9 блоков,
     * под каждой колонной — лоток на полу поперёк прохода; панели соединены проводами гнездо в гнездо (6 на пару,
     * 216 проводов). За крайними панелями — по компьютеру: связь через всю серверную.
     */
    private static void placeBigRoom() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        var log = com.mojang.logging.LogUtils.getLogger();
        BlockPos b = drawBase.offset(0, 0, 40);
        for (int dx = -3; dx <= 16; dx++) {
            for (int dz = -4; dz <= 14; dz++) {
                level.setBlockAndUpdate(b.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
                for (int dy = 0; dy <= 5; dy++) level.setBlockAndUpdate(b.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }
        BlockState south = ModBlocks.PATCH_PANEL.get().defaultBlockState().setValue(com.strikerr212.minepc.lan.PatchPanelBlock.FACING, Direction.SOUTH);
        BlockState north = ModBlocks.PATCH_PANEL.get().defaultBlockState().setValue(com.strikerr212.minepc.lan.PatchPanelBlock.FACING, Direction.NORTH);
        for (int x = 0; x < 12; x++) {
            for (int y = 0; y < 3; y++) {
                level.setBlockAndUpdate(b.offset(x, y, 0), south);
                level.setBlockAndUpdate(b.offset(x, y, 10), north);
                com.strikerr212.minepc.lan.LanManager.updateDevice(level, b.offset(x, y, 0));
                com.strikerr212.minepc.lan.LanManager.updateDevice(level, b.offset(x, y, 10));
            }
            for (int z = 1; z <= 9; z++) tray(level, b.offset(x, 0, z), Direction.DOWN, com.strikerr212.minepc.block.Paint.NONE);
        }
        connectTrays(level, b.offset(0, 0, 1), b.offset(11, 0, 9));
        int colors = 0, laid = 0, failed = 0;
        long total = 0, worst = 0;
        for (int x = 0; x < 12; x++) {
            for (int y = 0; y < 3; y++) {
                for (int k = 0; k < com.strikerr212.minepc.lan.PatchPanelBlock.CHANNELS; k++) {
                    long t0 = System.nanoTime();
                    var l = link(level, b.offset(x, y, 0), Direction.SOUTH, b.offset(x, y, 10), Direction.NORTH, (colors++) % 16, false, "",
                            List.of());
                    long dt = System.nanoTime() - t0;
                    total += dt;
                    worst = Math.max(worst, dt);
                    if (l != null) laid++;
                    else failed++;
                }
            }
        }
        // Компьютеры за крайними панелями — в их задние гнёзда
        bigPcA = b.offset(0, 0, -2);
        bigPcB = b.offset(0, 0, 12);
        pcWithMonitor(level, bigPcA, Direction.NORTH);
        pcWithMonitor(level, bigPcB, Direction.SOUTH);
        link(level, bigPcA, null, b, Direction.NORTH, net.minecraft.world.item.DyeColor.RED.getId(), false, "A", List.of());
        link(level, bigPcB, null, b.offset(0, 0, 10), Direction.SOUTH, net.minecraft.world.item.DyeColor.RED.getId(), false, "B", List.of());
        int inTray = 0;
        for (var l : com.strikerr212.minepc.lan.LanData.get(level).lines()) {
            for (int i = 0; i < l.nodes(); i++) {
                if (level.getBlockState(BlockPos.containing(l.node(i))).getBlock() instanceof com.strikerr212.minepc.lan.TrayBlock) {
                    inTray++;
                    break;
                }
            }
        }
        log.info("[CI-BIG] проложено {} проводов (не вышло {}), всего {} мс, самый долгий {} мс; через лотки идут {}", laid, failed,
                total / 1_000_000, worst / 1_000_000, inTray);
        p.teleportTo(level, b.getX() + 6, b.getY() + 4.5, b.getZ() - 3.5, -20f, 35f);
    }

    private static void logBigRoom() {
        ServerLevel level = Minecraft.getInstance().getSingleplayerServer().overworld();
        StringBuilder sb = new StringBuilder("[CI-BIG] связь A ->");
        for (var r : com.strikerr212.minepc.lan.LanManager.get(level).reach(level, bigPcA)) {
            sb.append(' ').append(r.pos().equals(bigPcB) ? "B" : r.pos().toShortString()).append(" hops=").append(r.hops());
        }
        com.mojang.logging.LogUtils.getLogger().info(sb.toString());
    }

    private static BlockPos wiredPos, trayBase = BlockPos.ZERO, maskCell;
    private static net.minecraft.world.phys.Vec3 maskPoint;

    /** Лоток на месте pos, прикреплённый к face (связи — потом, {@link #connectTrays}). */
    private static void tray(ServerLevel level, BlockPos pos, Direction face, com.strikerr212.minepc.block.Paint paint) {
        level.setBlockAndUpdate(pos, ModBlocks.TRAY.get().defaultBlockState().setValue(com.strikerr212.minepc.lan.TrayBlock.FACE, face)
                .setValue(com.strikerr212.minepc.block.Paint.PROPERTY, paint));
    }

    /** Пересчитать связи всех лотков в коробке (дважды — подъёмы зависят от соседей). */
    private static void connectTrays(ServerLevel level, BlockPos from, BlockPos to) {
        for (int pass = 0; pass < 2; pass++) {
            for (BlockPos q : BlockPos.betweenClosed(from, to)) {
                var st = level.getBlockState(q);
                if (st.getBlock() instanceof com.strikerr212.minepc.lan.TrayBlock t) level.setBlockAndUpdate(q, t.connect(st, level, q));
            }
        }
    }

    /** Компьютер ур. 5 с монитором и дисководом, в дисководе — дискета «Wired» (как из сундука деревни). */
    private static void placeWired() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ServerPlayer p = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = p.serverLevel();
        BlockPos b = drawBase.offset(4, 0, 14);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -4; dz <= 2; dz++) {
                level.setBlockAndUpdate(b.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
                for (int dy = 0; dy <= 3; dy++) level.setBlockAndUpdate(b.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }
        wiredPos = b;
        level.setBlockAndUpdate(b.east(), ModBlocks.DISK_DRIVE.get().defaultBlockState().setValue(DeviceBlock.FACING, Direction.NORTH));
        if (level.getBlockEntity(b.east()) instanceof com.strikerr212.minepc.block.DiskDriveBlockEntity drive) {
            drive.setDisk(com.strikerr212.minepc.item.DriveItem.withProgram(new ItemStack(com.strikerr212.minepc.registry.ModItems.FLOPPY_DISK.get()), "wired"));
        }
        level.setBlockAndUpdate(b, ModBlocks.PC_CASE.get().defaultBlockState().setValue(PcCaseBlock.FACING, Direction.NORTH));
        level.setBlockAndUpdate(b.above(), ModBlocks.MONITOR.get().defaultBlockState()
                .setValue(com.strikerr212.minepc.block.MonitorBlock.KEYBOARD, true).setValue(DeviceBlock.FACING, Direction.NORTH));
        if (level.getBlockEntity(b) instanceof PcCaseBlockEntity be) {
            var inv = (IItemHandlerModifiable) be.inventory();
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_MOTHERBOARD, HardwarePresets.motherboard(5));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_CPU, HardwarePresets.cpu(5));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_COOLER, HardwarePresets.cooler(5));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_RAM, HardwarePresets.ram(5));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_RAM + 1, HardwarePresets.ram(5));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_GPU, HardwarePresets.gpu(5));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_PSU, HardwarePresets.psu(5));
            inv.setStackInSlot(PcCaseBlockEntity.SLOT_M2, HardwarePresets.ssd(5));
            be.togglePower();
        }
        p.teleportTo(level, b.getX() + 0.5, b.getY(), b.getZ() - 3.5, 0f, 10f);
    }

    private static void wiredDisk(String program) {
        var level = Minecraft.getInstance().getSingleplayerServer().overworld();
        if (level.getBlockEntity(wiredPos.east()) instanceof com.strikerr212.minepc.block.DiskDriveBlockEntity drive) {
            drive.setDisk(program == null ? ItemStack.EMPTY : com.strikerr212.minepc.item.DriveItem.withProgram(
                    new ItemStack(com.strikerr212.minepc.registry.ModItems.FLOPPY_DISK.get()), program));
        }
    }

    private static void logWired(String tag) {
        var level = Minecraft.getInstance().getSingleplayerServer().overworld();
        var log = com.mojang.logging.LogUtils.getLogger();
        if (level.getBlockEntity(wiredPos) instanceof PcCaseBlockEntity pc) {
            log.info("[CI-WIRED] {}", tag);
            for (String line : pc.screenLines()) if (!line.isBlank()) log.info("[CI-WIRED] | {}", line.stripTrailing());
        }
    }

    /** Щелчок мышью по надписи на экране компьютера (со сдвигом dx, dy от её начала). */
    private static void clickText(BlockPos pos, String text, int dx, int dy) {
        var level = Minecraft.getInstance().getSingleplayerServer().overworld();
        if (!(level.getBlockEntity(pos) instanceof PcCaseBlockEntity pc)) return;
        var lines = pc.screenLines();
        for (int y = 0; y < lines.size(); y++) {
            int i = lines.get(y).indexOf(text);
            if (i >= 0) {
                int x = i + 1 + dx, yy = y + 1 + dy;
                pc.mouse(TerminalInputPayload.MOUSE_DOWN, 1, x, yy);
                pc.mouse(TerminalInputPayload.MOUSE_UP, 1, x, yy);
                com.mojang.logging.LogUtils.getLogger().info("[CI-WIRED] щелчок «{}» в {},{}", text, x, yy);
                return;
            }
        }
        com.mojang.logging.LogUtils.getLogger().info("[CI-WIRED] на экране нет «{}»", text);
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
     * Вторая сцена сети: компьютер → патч-панель → патч-корд → коммутатор → оптоволокно (SFP) → второй коммутатор →
     * компьютер, VLAN «lime» на всём пути; провод через стенку; воздушная линия; пара радиомодемов на 300 блоков.
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
        // Два настольных коммутатора, связанные оптикой через их гнёзда SFP (раньше — стоечные коммутаторы)
        BlockState rack = ModBlocks.NETWORK_SWITCH.get().defaultBlockState().setValue(com.strikerr212.minepc.lan.SwitchBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(b.west(), panel);
        level.setBlockAndUpdate(b, rack);
        level.setBlockAndUpdate(b.east(5), rack);
        int blue = net.minecraft.world.item.DyeColor.BLUE.getId(), yellow = net.minecraft.world.item.DyeColor.YELLOW.getId();
        int limeDye = net.minecraft.world.item.DyeColor.LIME.getId();
        com.strikerr212.minepc.lan.LanManager.updateDevice(level, b.west());
        com.strikerr212.minepc.lan.LanManager.updateDevice(level, b);
        com.strikerr212.minepc.lan.LanManager.updateDevice(level, b.east(5));
        // Оптика: из гнезда SFP первого коммутатора во второй
        var fiberLine = link(level, b, b.east(5), yellow, true, "");
        // Компьютер F — сбоку от второго коммутатора, медью из его гнезда
        lanF = b.east(7).north();
        pcWithMonitor(level, lanF, Direction.EAST);
        var copperF = link(level, b.east(5), lanF, blue, false, "");
        // Компьютер E — сзади патч-панели, в её заднее гнездо
        lanE = b.south(2);
        pcWithMonitor(level, lanE, Direction.EAST);
        var lineE = link(level, lanE, null, b.west(), Direction.SOUTH, blue, false, "", List.of());
        // Маскировка «провод в стене»: на середину провода от E — каменный кирпич, провод проходит сквозь него
        if (lineE != null) {
            List<net.minecraft.world.phys.Vec3> pts = new java.util.ArrayList<>();
            for (int i = 0; i + 1 < lineE.nodes(); i++) {
                if (lineE.hang[i]) continue;
                var a0 = lineE.node(i);
                var a1 = lineE.node(i + 1);
                int n = (int) Math.ceil(a0.distanceTo(a1) * 2);
                for (int k = 0; k <= n; k++) pts.add(a0.lerp(a1, k / (double) Math.max(1, n)));
            }
            for (int k = pts.size() / 2; k < pts.size(); k++) {
                BlockPos c = BlockPos.containing(pts.get(k));
                if (!level.getBlockState(c).isAir() || c.distManhattan(lanE) < 1 || c.distManhattan(b.west()) < 1) continue;
                maskCell = c.immutable();
                maskPoint = pts.get(k);
                break;
            }
            if (maskCell != null) level.setBlockAndUpdate(maskCell, net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
            com.mojang.logging.LogUtils.getLogger().info("[CI-MASK] кирпич на проводе E: {}", maskCell);
        }
        // J1 → J2 по лоткам: ряд на полу у кирпичной стены, подъём на стену и ряд по стене (покрашен)
        lanJ1 = b.offset(8, 0, 4);
        lanJ2 = b.offset(15, 0, 4);
        pcWithMonitor(level, lanJ1, Direction.WEST);
        fillCase(level, lanJ2, 5, Direction.EAST);
        level.setBlockAndUpdate(lanJ2.above(), ModBlocks.MONITOR.get().defaultBlockState()
                .setValue(com.strikerr212.minepc.block.MonitorBlock.KEYBOARD, true).setValue(DeviceBlock.FACING, Direction.EAST));
        for (int x = 9; x <= 14; x++) for (int y = 0; y <= 3; y++) level.setBlockAndUpdate(b.offset(x, y, 5), net.minecraft.world.level.block.Blocks.BRICKS.defaultBlockState());
        for (int x = 9; x <= 14; x++) tray(level, b.offset(x, 0, 4), Direction.DOWN, com.strikerr212.minepc.block.Paint.NONE);
        for (BlockPos t : new BlockPos[]{b.offset(10, 1, 4), b.offset(10, 2, 4), b.offset(11, 2, 4), b.offset(12, 2, 4), b.offset(13, 2, 4), b.offset(13, 1, 4)}) {
            tray(level, t, Direction.SOUTH, com.strikerr212.minepc.block.Paint.YELLOW);
        }
        connectTrays(level, b.offset(8, 0, 3), b.offset(15, 3, 5));
        link(level, lanJ1, lanJ2, blue, false, "");
        // ещё два провода — вверх по лотку на стене, поверху и вниз
        for (int k = 0; k < 2; k++) {
            BlockPos w1 = b.offset(10, 2, 4), w2 = b.offset(13, 2, 4);
            var l = com.strikerr212.minepc.wire.WireService.layDirect(level, false, k == 0 ? net.minecraft.world.item.DyeColor.ORANGE.getId()
                            : net.minecraft.world.item.DyeColor.LIME.getId(), "",
                    com.strikerr212.minepc.wire.WirePlan.Anchor.pixel(floorPixel(b.offset(9, 0, 3), 8, 6 + k)),
                    List.of(new int[]{w1.getX() * 16 + 6 + 3 * k, w1.getY() * 16 + 6 + 3 * k, w1.getZ() * 16 + 14},
                            new int[]{w2.getX() * 16 + 9 - 3 * k, w2.getY() * 16 + 6 + 3 * k, w2.getZ() * 16 + 14}),
                    com.strikerr212.minepc.wire.WirePlan.Anchor.pixel(floorPixel(b.offset(14, 0, 3), 8, 6 + k)));
            com.mojang.logging.LogUtils.getLogger().info("[CI-TRAY] провод по стене {}: {}", k + 1, l == null ? "не проложился" : l.meters() + " м, узлов " + l.nodes());
        }
        trayBase = b.offset(9, 0, 4);
        StringBuilder ts = new StringBuilder("[CI-TRAY] лотки:");
        for (BlockPos t : BlockPos.betweenClosed(b.offset(9, 0, 4), b.offset(14, 2, 4))) {
            var st = level.getBlockState(t);
            if (st.getBlock() instanceof com.strikerr212.minepc.lan.TrayBlock) ts.append(' ').append(t.getX() - b.getX()).append(',').append(t.getY() - b.getY())
                    .append(st.getValue(com.strikerr212.minepc.lan.TrayBlock.FACE).getName().charAt(0)).append('[')
                    .append(st.getValue(com.strikerr212.minepc.lan.TrayBlock.N).getSerializedName().charAt(0))
                    .append(st.getValue(com.strikerr212.minepc.lan.TrayBlock.E).getSerializedName().charAt(0))
                    .append(st.getValue(com.strikerr212.minepc.lan.TrayBlock.S).getSerializedName().charAt(0))
                    .append(st.getValue(com.strikerr212.minepc.lan.TrayBlock.W).getSerializedName().charAt(0)).append(']');
        }
        com.mojang.logging.LogUtils.getLogger().info(ts.toString());
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
        BlockPos br1 = b.offset(0, 0, -5), br2 = b.offset(10, 0, -5);
        level.setBlockAndUpdate(br1, bracket);
        level.setBlockAndUpdate(br2, bracket);
        com.strikerr212.minepc.lan.LanManager.updateDevice(level, br1);
        com.strikerr212.minepc.lan.LanManager.updateDevice(level, br2);
        link(level, lanK3, br1, blue, false, "");
        link(level, lanK4, br2, blue, false, "");
        link(level, br1, br2, blue, false, "");
        // «От гнезда до гнезда» через каменную стенку: K5 → K6
        lanK5 = b.offset(3, 0, -3);
        lanK6 = b.offset(9, 0, -3);
        fillCase(level, lanK5, 2);
        fillCase(level, lanK6, 2);
        for (int dz = -4; dz <= -2; dz++) level.setBlockAndUpdate(b.offset(6, 0, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var route = link(level, lanK5, lanK6, limeDye, false, "");
        com.mojang.logging.LogUtils.getLogger().info("[CI-ROUTE] маршрут {}", route == null ? "не найден" : route.meters() + " м, узлов " + route.nodes());
        // Корд: из того канала патч-панели, куда воткнут провод от E, в первый корд-порт коммутатора
        var data = com.strikerr212.minepc.lan.LanData.get(level);
        int panelPort = lineE == null ? 0 : b.west().equals(lineE.devB) ? lineE.portB : lineE.portA;
        int ch = panelPort % com.strikerr212.minepc.lan.PatchPanelBlock.CHANNELS;
        cord(level, b.west(), com.strikerr212.minepc.lan.LanData.CABLE_PORTS + ch, b, com.strikerr212.minepc.lan.LanData.CABLE_PORTS, limeDye);
        // VLAN lime на всём пути E → F
        int lime = limeDye + 1;
        data.setPort(b, com.strikerr212.minepc.lan.LanData.CABLE_PORTS, true, lime);
        if (fiberLine != null) {
            data.setPort(fiberLine.devA, fiberLine.portA, true, lime);
            data.setPort(fiberLine.devB, fiberLine.portB, true, lime);
        }
        if (copperF != null) data.setPort(b.east(5), b.east(5).equals(copperF.devA) ? copperF.portA : copperF.portB, true, lime);
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
