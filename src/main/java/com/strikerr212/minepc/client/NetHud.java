package com.strikerr212.minepc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.lan.CableItem;
import com.strikerr212.minepc.lan.CuttersItem;
import com.strikerr212.minepc.lan.LanDevice;
import com.strikerr212.minepc.network.PortsInfoPayload;
import com.strikerr212.minepc.network.PortsRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Подсказки монтажника (только у игрока):
 * <ul>
 *     <li>взгляд на гнездо устройства — номер порта, есть ли связь, куда ведёт кабель и его длина, VLAN;</li>
 *     <li>с мотком или кусачками в руке взгляд на провод — весь его отрезок подсвечен, длина «47 / 100 блоков», концы;</li>
 *     <li>моток с выбранным началом маршрута и взгляд на второе устройство — призрак маршрута и сколько уйдёт кабеля.</li>
 * </ul>
 * Плюс лампочки над гнёздами: мигают, когда через устройство идут сообщения ({@link #activity}).
 */
@EventBusSubscriber(modid = MineComputers.MODID, value = Dist.CLIENT)
public final class NetHud {

    private NetHud() {
    }

    // ----- Что показываем сейчас -----
    private static final List<Component> lines = new ArrayList<>();
    private static final List<AABB> highlight = new ArrayList<>();
    private static int highlightColor = 0xFFE15A;
    private static final List<Vec3> ghost = new ArrayList<>();
    private static boolean ghostBlocked;

    // ----- Порты устройств с сервера -----
    private record PortInfo(boolean link, String device, boolean enabled, int vlan, int length, String label) {
    }

    private record DeviceInfo(long time, int kbit, Map<Integer, PortInfo> ports) {
    }

    private static final Map<BlockPos, DeviceInfo> infos = new HashMap<>();
    private static BlockPos requested;
    private static long requestedAt;

    // ----- Призрак маршрута: пересчитываем не каждый тик -----

    // ----- Мигание лампочек -----
    private static final Map<BlockPos, Long> active = new HashMap<>();

    public static void onPortsInfo(PortsInfoPayload p) {
        Map<Integer, PortInfo> ports = new HashMap<>();
        for (String line : p.data().split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length < 6) continue;
            try {
                ports.put(Integer.parseInt(f[0]), new PortInfo(f[1].equals("1"), f[2], f[3].equals("1"),
                        Integer.parseInt(f[4]), Integer.parseInt(f[5]), f.length > 6 ? f[6] : ""));
            } catch (NumberFormatException ignored) {
                // строка не по формату — пропускаем
            }
        }
        Level level = Minecraft.getInstance().level;
        infos.put(p.pos().immutable(), new DeviceInfo(level == null ? 0 : level.getGameTime(), p.kbit(), ports));
    }

    /** Через устройство прошло сообщение: лампочки мигают ещё 8 тиков. */
    public static void activity(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            if (active.size() > 256) active.clear();
            active.put(pos.immutable(), level.getGameTime() + 8);
        }
    }

    /** Мигает ли сейчас лампочка порта (для отрисовщика устройств). */
    public static boolean blinking(BlockPos pos, int port, long time) {
        Long until = active.get(pos);
        return until != null && time < until && ((time / 2 + port) & 1) == 0;
    }

    // ======================= Каждый тик: что под прицелом =======================

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        lines.clear();
        highlight.clear();
        ghost.clear();
        ghostBlocked = false;
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        ItemStack held = mc.player.getMainHandItem();
        HitResult hit = mc.hitResult;
        BlockPos target = hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK ? bhr.getBlockPos() : null;
        BlockState state = target == null ? null : mc.level.getBlockState(target);

        // Патч-корд: в какое гнездо воткнётся (у коммутатора — то, в которое смотришь), где повиснет, не мешает ли блок
        if (held.getItem() instanceof com.strikerr212.minepc.lan.PatchCordItem && target != null && state.getBlock() instanceof LanDevice) {
            var data = held.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            int toPort = -1;
            if (com.strikerr212.minepc.lan.PatchCordItem.intoSockets(state) && hit instanceof BlockHitResult bh) {
                toPort = com.strikerr212.minepc.lan.PatchCordItem.socketAt(mc.level, target, state, bh.getLocation(), bh.getDirection());
            }
            if (data != null && data.copyTag().contains("cord_pos")) {
                BlockPos from = BlockPos.of(data.copyTag().getLong("cord_pos"));
                int fromPort = data.copyTag().getInt("cord_port");
                if (!from.equals(target)) {
                    BlockPos blocked = com.strikerr212.minepc.wire.CordShape.blocker(mc.level, from, fromPort, target, toPort);
                    if (blocked != null) {
                        lines.add(Component.translatable("hud.minepc.aerial_blocked", mc.level.getBlockState(blocked).getBlock().getName())
                                .withStyle(s -> s.withColor(0xE05555)));
                        highlight.add(new AABB(blocked));
                        highlightColor = 0xE05555;
                    }
                    ghostBlocked = blocked != null;
                    ghost.addAll(com.strikerr212.minepc.wire.CordShape.points(mc.level, from, fromPort, target, toPort));
                    if (blocked != null) return;
                }
            }
            // дальше — обычная подсказка у гнезда: номер, что в нём, подсветка
        }
        // Идёт прокладка щелчками — подсказки у неё свои
        if (WireDrawClient.active()) return;
        // Монтажник: провод под прицелом (с мотком, кусачками, тестером или биркой)
        boolean tool = held.getItem() instanceof CableItem || held.getItem() instanceof CuttersItem;
        boolean tag = held.is(net.minecraft.world.item.Items.NAME_TAG);
        if (tool || tag || held.getItem() instanceof com.strikerr212.minepc.lan.CableTesterItem) {
            var aimed = com.strikerr212.minepc.wire.WireService.aimedClient(mc.player);
            if (aimed != null) {
                wireInfo(mc, aimed.line(), tag);
                return;
            }
        }
        // Гнездо устройства
        if (target != null && state.getBlock() instanceof LanDevice dev && hit instanceof BlockHitResult bhr) {
            port(mc, target, state, dev, bhr);
        }
    }

    /** Подсказка у провода: бирка, длина и вид, куда воткнуты концы; сам провод подсвечен. */
    private static void wireInfo(Minecraft mc, com.strikerr212.minepc.wire.WireLine l, boolean tag) {
        if (!l.label.isEmpty()) lines.add(Component.translatable("hud.minepc.port_label", l.label).withStyle(s -> s.withColor(0xF2F3F5)));
        lines.add(Component.translatable("hud.minepc.wire.info", l.meters(),
                Component.translatable(l.fiber ? "tooltip.minepc.tester.fiber" : "tooltip.minepc.tester.copper")).withStyle(s -> s.withColor(0x8FD3F0)));
        Component[] ends = new Component[2];
        for (int e = 0; e < 2; e++) {
            boolean b = e == 1;
            BlockPos d = l.dev(b);
            ends[e] = d == null ? Component.translatable("hud.minepc.wire.free_end")
                    : Component.translatable("hud.minepc.wire.end", mc.level.getBlockState(d).getBlock().getName(), l.port(b) + 1);
        }
        lines.add(Component.translatable("hud.minepc.wire.ends", ends[0], ends[1]).withStyle(s -> s.withColor(0xB4BAC4)));
        if (tag) lines.add(Component.translatable("hud.minepc.wire.tag_hint").withStyle(s -> s.withColor(0x9097A1)));
        for (int i = 0; i + 1 < l.nodes() && highlight.size() < 400; i++) {
            if (!l.hang[i]) highlight.add(new AABB(l.node(i), l.node(i + 1)).inflate(0.04));
        }
        highlightColor = l.fiber ? 0xFFD24A : 0x8FD3F0;
    }

    /** Подсказка у гнезда. */
    private static void port(Minecraft mc, BlockPos pos, BlockState state, LanDevice dev, BlockHitResult hit) {
        List<LanDevice.Socket> sockets = dev.sockets(mc.level, pos, state);
        Vec3 at = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ()).scale(16);
        int found = -1;
        double best = 2.6;
        for (int i = 0; i < sockets.size(); i++) {
            LanDevice.Socket s = sockets.get(i);
            if (s.face() != hit.getDirection() || !s.copper() && !s.fiber()) continue;
            Direction.Axis[] ax = LanDevice.faceAxes(s.face());
            double da = Math.abs(get(at, ax[0]) - (s.along() + 0.5)), dh = Math.abs(get(at, ax[1]) - (s.height() + 0.5));
            double d = Math.max(da, dh);
            if (d < best) {
                best = d;
                found = i;
            }
        }
        if (found < 0) return;
        long now = mc.level.getGameTime();
        if (!pos.equals(requested) || now - requestedAt > 20) {
            requested = pos.immutable();
            requestedAt = now;
            PacketDistributor.sendToServer(new PortsRequestPayload(pos));
        }
        lines.add(Component.translatable("hud.minepc.port", state.getBlock().getName(), found + 1).withStyle(s -> s.withColor(0xFFFFFF)));
        DeviceInfo info = infos.get(pos);
        PortInfo p = info == null ? null : info.ports().get(found);
        if (info == null) {
            lines.add(Component.literal("…").withStyle(s -> s.withColor(0x9097A1)));
        } else if (p == null) {
            lines.add(Component.translatable("hud.minepc.port_free").withStyle(s -> s.withColor(0x9097A1)));
        } else {
            if (p.device().equals("!")) {
                lines.add(Component.translatable("hud.minepc.port_long", p.length()).withStyle(s -> s.withColor(0xE05555)));
            } else if (p.device().equals("~")) {
                lines.add(Component.translatable("gui.minepc.switch.free_end").withStyle(s -> s.withColor(0xF2C14E)));
            } else if (p.link()) {
                lines.add(Component.translatable("hud.minepc.port_link", speed(info.kbit())).withStyle(s -> s.withColor(0x5BD47A)));
                lines.add(Component.translatable("hud.minepc.port_to", p.device(), p.length()).withStyle(s -> s.withColor(0xB4BAC4)));
            } else {
                lines.add(Component.translatable("hud.minepc.port_nolink").withStyle(s -> s.withColor(0x9097A1)));
            }
            if (p.vlan() > 0) {
                lines.add(Component.translatable("hud.minepc.port_vlan",
                        Component.translatable("color.minecraft." + DyeColor.byId(p.vlan() - 1).getName()))
                        .withStyle(s -> s.withColor(DyeColor.byId(p.vlan() - 1).getTextColor())));
            }
            if (!p.enabled()) lines.add(Component.translatable("hud.minepc.port_off").withStyle(s -> s.withColor(0xE05555)));
            if (!p.label().isEmpty()) lines.add(Component.translatable("hud.minepc.port_label", p.label()).withStyle(s -> s.withColor(0xF2F3F5)));
        }
        // Подсветка гнезда
        LanDevice.Socket s = sockets.get(found);
        highlight.add(socketBox(pos, s));
        highlightColor = 0x8FD3F0;
    }

    private static double get(Vec3 v, Direction.Axis a) {
        return a == Direction.Axis.X ? v.x : a == Direction.Axis.Y ? v.y : v.z;
    }

    /** Коробка вокруг гнезда (в мировых координатах). */
    private static AABB socketBox(BlockPos pos, LanDevice.Socket s) {
        Direction.Axis[] ax = LanDevice.faceAxes(s.face());
        double[] lo = new double[3], hi = new double[3];
        lo[ax[0].ordinal()] = s.along() - 0.5;
        hi[ax[0].ordinal()] = s.along() + 1.5;
        lo[ax[1].ordinal()] = s.height() - 0.5;
        hi[ax[1].ordinal()] = s.height() + 1.5;
        int n = s.face().getAxis().ordinal();
        double plane = s.face().getAxisDirection() == Direction.AxisDirection.POSITIVE ? 16 - s.depth() : s.depth();
        lo[n] = plane - 0.5;
        hi[n] = plane + 0.5;
        return new AABB(lo[0] / 16, lo[1] / 16, lo[2] / 16, hi[0] / 16, hi[1] / 16, hi[2] / 16).move(pos.getX(), pos.getY(), pos.getZ());
    }

    private static Component speed(int kbit) {
        if (kbit >= 1_000_000) {
            double g = kbit / 1_000_000.0;
            return Component.translatable("unit.minepc.gbit", g == Math.floor(g) ? String.valueOf((long) g) : String.format(java.util.Locale.ROOT, "%.1f", g));
        }
        return Component.translatable("unit.minepc.mbit", kbit / 1000);
    }

    // ======================= Отрисовка =======================

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (highlight.isEmpty() && ghost.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        float r = ((highlightColor >> 16) & 255) / 255f, g = ((highlightColor >> 8) & 255) / 255f, b = (highlightColor & 255) / 255f;
        for (AABB box : highlight) LevelRenderer.renderLineBox(pose, vc, box.inflate(0.004), r, g, b, 0.9f);
        // Призрак маршрута: голубая линия по будущим проводам
        PoseStack.Pose last = pose.last();
        for (int i = 1; i < ghost.size(); i++) {
            Vec3 p0 = ghost.get(i - 1), p1 = ghost.get(i);
            Vec3 d = p1.subtract(p0);
            if (d.lengthSqr() < 1e-6) continue;
            Vec3 n = d.normalize();
            float gr = ghostBlocked ? 1f : 0.45f, gg = ghostBlocked ? 0.35f : 0.85f, gb = ghostBlocked ? 0.33f : 1f;
            vc.addVertex(last, (float) p0.x, (float) p0.y, (float) p0.z).setColor(gr, gg, gb, 1f).setNormal(last, (float) n.x, (float) n.y, (float) n.z);
            vc.addVertex(last, (float) p1.x, (float) p1.y, (float) p1.z).setColor(gr, gg, gb, 1f).setNormal(last, (float) n.x, (float) n.y, (float) n.z);
        }
        if (!ghostBlocked) {
            for (Vec3 p : ghost) LevelRenderer.renderLineBox(pose, vc, new AABB(p, p).inflate(0.04), 0.45f, 0.85f, 1f, 1f);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (lines.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        GuiGraphics g = event.getGuiGraphics();
        var font = mc.font;
        int w = 0;
        for (Component c : lines) w = Math.max(w, font.width(c));
        int x = g.guiWidth() / 2 + 12, y = g.guiHeight() / 2 + 8;
        int h = lines.size() * 10 + 6;
        if (x + w + 8 > g.guiWidth()) x = Math.max(2, g.guiWidth() - w - 10);
        g.fill(x - 4, y - 3, x + w + 4, y + h - 3, 0xC0101216);
        g.fill(x - 4, y - 3, x - 3, y + h - 3, 0xFF3FA9F5);
        for (Component c : lines) {
            g.drawString(font, c, x, y, 0xFFFFFF, false);
            y += 10;
        }
    }
}
