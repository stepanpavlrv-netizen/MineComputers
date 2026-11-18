package com.strikerr212.minepc.client;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.block.HostRef;
import com.strikerr212.minepc.block.PcCaseBlockEntity;
import com.strikerr212.minepc.block.RackBlockEntity;
import com.strikerr212.minepc.network.TerminalInputPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Управление игрой по ходу — только для проверки модов (включается -Dminepc.live=true, в Gradle: -PliveCtl).
 * Раз в полсекунды игра смотрит файл run/live/cmd.txt: каждая строка — команда; выполнив все, пишет ответы в
 * run/live/out.txt и номер пачки в run/live/done.txt. Снимки — run/screenshots/live_&lt;имя&gt;.png.
 * Команды (координаты — целые, блока; slot — место модуля в стойке, с 0):
 * <pre>
 *   pos                         где игрок и куда смотрит
 *   tp x y z [yaw pitch]        переместить игрока (дробные можно)
 *   look yaw pitch              повернуть взгляд
 *   face x y z                  посмотреть на точку
 *   shot имя                    снимок экрана
 *   hud on|off                  показать/спрятать интерфейс
 *   close                       закрыть окно
 *   term x y z [slot]           открыть экран компьютера (или сервера в стойке)
 *   extend x y z slot           выдвинуть модуль стойки (-1 — задвинуть)
 *   setblock x y z id           поставить блок (minecraft:stone, minepc:...)
 *   item x y z slot номер id    положить предмет в слот компьютера/сервера (номер слота PcCaseBlockEntity)
 *   type x y z slot текст       набрать текст и Enter (slot -1 — обычный компьютер)
 *   key x y z slot клавиша      нажать клавишу (enter, up, delete…)
 *   click x y z slot текст      щелчок по надписи на экране компьютера
 *   screen x y z [slot]         текст экрана компьютера
 *   info x y z                  что за блок; у компьютера и стойки — состояние, детали, охлаждение
 *   wait тиков                  подождать
 *   time день|ночь|число        время суток
 *   quit                        закрыть игру
 * </pre>
 */
@EventBusSubscriber(modid = MineComputers.MODID, value = Dist.CLIENT)
public final class LiveControl {

    public static final boolean ENABLED = Boolean.getBoolean("minepc.live");
    private static final ArrayDeque<String> queue = new ArrayDeque<>();
    private static final List<String> out = new ArrayList<>();
    private static int ticks, waitUntil, batch;
    private static boolean ready;

    private LiveControl() {
    }

    private static Path dir(Minecraft mc) {
        return mc.gameDirectory.toPath().resolve("live");
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        ticks++;
        Path d = dir(mc);
        if (!ready) {
            ready = true;
            mc.options.pauseOnLostFocus = false;
            try {
                Files.createDirectories(d);
                Files.writeString(d.resolve("ready.txt"), "ok\n", StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
        }
        if (queue.isEmpty() && ticks % 10 == 0) {
            Path cmd = d.resolve("cmd.txt");
            if (Files.exists(cmd)) {
                try {
                    for (String l : Files.readAllLines(cmd, StandardCharsets.UTF_8)) {
                        if (!l.isBlank() && !l.startsWith("#")) queue.add(l.strip());
                    }
                    Files.delete(cmd);
                } catch (Exception e) {
                    say("! не прочитал cmd.txt: " + e);
                }
                if (queue.isEmpty()) finish(d);
            }
        }
        while (!queue.isEmpty() && !"__done".equals(queue.peek()) && ticks >= waitUntil) {
            String line = queue.poll();
            say("> " + line);
            try {
                run(mc, line);
            } catch (Exception e) {
                say("! " + e);
            }
            if (queue.isEmpty()) {
                // ответы сервера (server.execute) приходят на следующих тиках — закончить пачку чуть позже
                waitUntil = Math.max(waitUntil, ticks + 3);
                queue.add("__done");
            }
        }
        if ("__done".equals(queue.peek()) && ticks >= waitUntil) {
            queue.poll();
            finish(d);
        }
    }

    private static void finish(Path d) {
        batch++;
        try {
            Files.createDirectories(d);
            synchronized (out) {
                Files.write(d.resolve("out.txt"), out, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                out.clear();
            }
            Files.writeString(d.resolve("done.txt"), batch + "\n", StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static void say(String s) {
        synchronized (out) {
            out.add(s);
        }
    }

    private static int i(String s) {
        return Integer.parseInt(s);
    }

    private static double f(String s) {
        return Double.parseDouble(s);
    }

    private static PcCaseBlockEntity host(ServerLevel level, BlockPos pos, int slot) {
        return slot < 0 ? (level.getBlockEntity(pos) instanceof PcCaseBlockEntity be ? be : null)
                : com.strikerr212.minepc.block.Hosts.get(level, new HostRef(pos, slot));
    }

    private static String rest(String[] a, int from) {
        return String.join(" ", Arrays.copyOfRange(a, from, a.length));
    }

    private static void run(Minecraft mc, String line) {
        String[] a = line.split("\\s+");
        MinecraftServer server = mc.getSingleplayerServer();
        String cmd = a[0].toLowerCase(Locale.ROOT);
        switch (cmd) {
            case "pos" -> {
                Vec3 p = mc.player.position();
                say(String.format(Locale.ROOT, "  игрок %.2f %.2f %.2f, взгляд yaw %.1f pitch %.1f, блок под прицелом %s", p.x, p.y, p.z,
                        mc.player.getYRot(), mc.player.getXRot(),
                        mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bh ? bh.getBlockPos().toShortString() : "нет"));
            }
            case "tp" -> {
                double x = f(a[1]), y = f(a[2]), z = f(a[3]);
                float yaw = a.length > 4 ? (float) f(a[4]) : mc.player.getYRot(), pitch = a.length > 5 ? (float) f(a[5]) : mc.player.getXRot();
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                    p.teleportTo(p.serverLevel(), x, y, z, yaw, pitch);
                });
                waitUntil = ticks + 4;
            }
            case "look" -> {
                float yaw = (float) f(a[1]), pitch = (float) f(a[2]);
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                    p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), yaw, pitch);
                });
                waitUntil = ticks + 3;
            }
            case "face" -> {
                Vec3 t = new Vec3(f(a[1]), f(a[2]), f(a[3]));
                Vec3 d = t.subtract(mc.player.getEyePosition());
                float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) Math.toDegrees(-Math.atan2(d.y, Math.hypot(d.x, d.z)));
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                    p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), yaw, pitch);
                });
                waitUntil = ticks + 3;
            }
            case "shot" -> {
                String name = "live_" + (a.length > 1 ? a[1] : "shot") + ".png";
                Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> { });
                say("  снимок screenshots/" + name);
                waitUntil = ticks + 2;
            }
            case "hud" -> mc.options.hideGui = a.length > 1 && a[1].equals("off");
            case "close" -> mc.setScreen(null);
            case "term" -> {
                BlockPos p = new BlockPos(i(a[1]), i(a[2]), i(a[3]));
                mc.setScreen(a.length > 4 ? new TerminalScreen(new HostRef(p, i(a[4]))) : new TerminalScreen(p));
                waitUntil = ticks + 10;
            }
            case "extend" -> {
                BlockPos p = new BlockPos(i(a[1]), i(a[2]), i(a[3]));
                int slot = i(a[4]);
                server.execute(() -> {
                    ServerLevel level = server.overworld();
                    if (level.getBlockEntity(p) instanceof RackBlockEntity r) {
                        if (slot < 0) r.setExtended(-1);
                        else com.strikerr212.minepc.block.RackBlock.extend(level, p, level.getBlockState(p), r, slot, null);
                        say("  выдвинут " + r.extended());
                    } else say("  там не стойка");
                });
                waitUntil = ticks + 6;
            }
            case "setblock" -> {
                BlockPos p = new BlockPos(i(a[1]), i(a[2]), i(a[3]));
                var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(a[4]));
                server.execute(() -> server.overworld().setBlockAndUpdate(p, block.defaultBlockState()));
                waitUntil = ticks + 2;
            }
            case "item" -> {
                BlockPos p = new BlockPos(i(a[1]), i(a[2]), i(a[3]));
                int slot = i(a[4]), invSlot = i(a[5]);
                ItemStack stack = a[6].equals("empty") ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(a[6])));
                server.execute(() -> {
                    PcCaseBlockEntity be = host(server.overworld(), p, slot);
                    if (be == null) { say("  нет компьютера"); return; }
                    ((IItemHandlerModifiable) be.inventory()).setStackInSlot(invSlot, stack);
                    say("  в слоте " + invSlot + ": " + be.getStack(invSlot).getHoverName().getString());
                });
                waitUntil = ticks + 3;
            }
            case "type", "key" -> {
                BlockPos p = new BlockPos(i(a[1]), i(a[2]), i(a[3]));
                int slot = i(a[4]);
                String text = rest(a, 5);
                server.execute(() -> {
                    PcCaseBlockEntity be = host(server.overworld(), p, slot);
                    if (be == null) { say("  нет компьютера"); return; }
                    if (cmd.equals("type")) {
                        for (char ch : text.toCharArray()) be.input(TerminalInputPayload.CHAR, String.valueOf(ch), false, false);
                        be.input(TerminalInputPayload.KEY, "enter", false, false);
                    } else {
                        be.input(TerminalInputPayload.KEY, text, false, false);
                    }
                });
                waitUntil = ticks + 4;
            }
            case "click" -> {
                BlockPos p = new BlockPos(i(a[1]), i(a[2]), i(a[3]));
                int slot = i(a[4]);
                String text = rest(a, 5);
                server.execute(() -> {
                    PcCaseBlockEntity be = host(server.overworld(), p, slot);
                    if (be == null) { say("  нет компьютера"); return; }
                    var lines = be.screenLines();
                    for (int y = 0; y < lines.size(); y++) {
                        int x = lines.get(y).indexOf(text);
                        if (x >= 0) {
                            be.mouse(TerminalInputPayload.MOUSE_DOWN, 1, x + 2, y + 1);
                            be.mouse(TerminalInputPayload.MOUSE_UP, 1, x + 2, y + 1);
                            say("  щелчок в " + (x + 2) + "," + (y + 1));
                            return;
                        }
                    }
                    say("  на экране нет «" + text + "»");
                });
                waitUntil = ticks + 4;
            }
            case "screen" -> {
                BlockPos p = new BlockPos(i(a[1]), i(a[2]), i(a[3]));
                int slot = a.length > 4 ? i(a[4]) : -1;
                server.execute(() -> {
                    PcCaseBlockEntity be = host(server.overworld(), p, slot);
                    if (be == null) { say("  нет компьютера"); return; }
                    for (String l : be.screenLines()) if (!l.isBlank()) say("  | " + l.stripTrailing());
                });
                waitUntil = ticks + 2;
            }
            case "info" -> {
                BlockPos p = new BlockPos(i(a[1]), i(a[2]), i(a[3]));
                server.execute(() -> {
                    ServerLevel level = server.overworld();
                    say("  блок " + level.getBlockState(p));
                    if (level.getBlockEntity(p) instanceof PcCaseBlockEntity be) describe(be, "  ");
                    if (level.getBlockEntity(p) instanceof RackBlockEntity r) {
                        say("  выдвинут модуль " + r.extended());
                        for (int k = 0; k < RackBlockEntity.SLOTS; k++) {
                            if (r.server(k) != null) { say("  — место " + k + ":"); describe(r.server(k), "    "); }
                        }
                    }
                });
                // и то же глазами клиента: что дошло до экрана игрока
                if (mc.level.getBlockEntity(p) instanceof RackBlockEntity cr) {
                    for (int k = 0; k < RackBlockEntity.SLOTS; k++) {
                        if (cr.server(k) != null) say("  (клиент) место " + k + ": вентиляторы ур. " + cr.server(k).fansTier()
                                + ", кулер-слот: " + cr.server(k).getStack(PcCaseBlockEntity.SLOT_COOLER).getHoverName().getString());
                    }
                }
                waitUntil = ticks + 2;
            }
            case "wait" -> waitUntil = ticks + i(a[1]);
            case "time" -> {
                long t = a[1].equals("день") || a[1].equals("day") ? 6000 : a[1].equals("ночь") || a[1].equals("night") ? 18000 : Long.parseLong(a[1]);
                server.execute(() -> server.overworld().setDayTime(t));
            }
            case "quit" -> {
                say("  выхожу из игры");
                finish(dir(mc));
                mc.stop();
            }
            default -> say("  ? неизвестная команда");
        }
    }

    private static void describe(PcCaseBlockEntity be, String ind) {
        say(ind + "состояние " + be.computerState() + (be.serverChassis() ? ", серверное шасси" : "") + ", вентиляторы ур. "
                + be.fansTier() + ", отвод " + be.fanWallW() + " Вт");
        for (int s = 0; s < PcCaseBlockEntity.SLOT_COUNT; s++) {
            if (!be.getStack(s).isEmpty()) say(ind + "  слот " + s + ": " + be.getStack(s).getHoverName().getString());
        }
        for (var c : be.statusLines(false)) say(ind + "  " + c.getString());
    }
}
