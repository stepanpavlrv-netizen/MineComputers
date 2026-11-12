package com.strikerr212.minepc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.lan.BracketBlock;
import com.strikerr212.minepc.lan.CableBlockEntity;
import com.strikerr212.minepc.lan.CableItem;
import com.strikerr212.minepc.lan.CuttersItem;
import com.strikerr212.minepc.lan.LanDevice;
import com.strikerr212.minepc.lan.LanManager;
import com.strikerr212.minepc.lan.RoutePlanner;
import com.strikerr212.minepc.lan.TrayBlockEntity;
import com.strikerr212.minepc.lan.Wire;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private record PortInfo(boolean link, String device, boolean enabled, int vlan, int length) {
    }

    private record DeviceInfo(long time, int kbit, Map<Integer, PortInfo> ports) {
    }

    private static final Map<BlockPos, DeviceInfo> infos = new HashMap<>();
    private static BlockPos requested;
    private static long requestedAt;

    // ----- Призрак маршрута: пересчитываем не каждый тик -----
    private static BlockPos ghostFrom, ghostTo;
    private static long ghostAt;
    private static RoutePlanner.Route ghostRoute;

    // ----- Мигание лампочек -----
    private static final Map<BlockPos, Long> active = new HashMap<>();

    public static void onPortsInfo(PortsInfoPayload p) {
        Map<Integer, PortInfo> ports = new HashMap<>();
        for (String line : p.data().split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length < 6) continue;
            try {
                ports.put(Integer.parseInt(f[0]), new PortInfo(f[1].equals("1"), f[2], f[3].equals("1"),
                        Integer.parseInt(f[4]), Integer.parseInt(f[5])));
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

        // Патч-корд с первым концом: где он повиснет и не мешает ли блок
        if (held.getItem() instanceof com.strikerr212.minepc.lan.PatchCordItem) {
            var data = held.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (data != null && data.copyTag().contains("cord_pos") && target != null && state.getBlock() instanceof LanDevice) {
                BlockPos from = BlockPos.of(data.copyTag().getLong("cord_pos"));
                if (!from.equals(target)) {
                    BlockPos blocked = com.strikerr212.minepc.lan.CordShape.blocker(mc.level, from, target);
                    if (blocked != null) {
                        lines.add(Component.translatable("hud.minepc.aerial_blocked", mc.level.getBlockState(blocked).getBlock().getName())
                                .withStyle(s -> s.withColor(0xE05555)));
                        highlight.add(new AABB(blocked));
                        highlightColor = 0xE05555;
                    }
                    ghostBlocked = blocked != null;
                    ghost.addAll(com.strikerr212.minepc.lan.CordShape.points(mc.level, from, target));
                    return;
                }
            }
        }
        // Моток с началом маршрута
        if (held.getItem() instanceof CableItem cable && CableItem.routeFrom(held) != null) {
            BlockPos from = CableItem.routeFrom(held);
            route(mc, cable, held, from, target, state);
            return;
        }
        // Монтажник: провод под прицелом
        boolean tool = held.getItem() instanceof CableItem || held.getItem() instanceof CuttersItem;
        if (tool && target != null && mc.level.getBlockEntity(target) instanceof CableBlockEntity be && be.facade() == null
                && !be.wires().isEmpty()) {
            Vec3 at = hit.getLocation().subtract(target.getX(), target.getY(), target.getZ()).scale(16);
            Wire w = be.nearest(at.x, at.y, at.z);
            if (w != null) trace(mc.level, target, w);
            return;
        }
        // Гнездо устройства
        if (target != null && state.getBlock() instanceof LanDevice dev && hit instanceof BlockHitResult bhr) {
            port(mc, target, state, dev, bhr);
        }
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

    /** Отрезок провода: обход по проводам, загруженным у игрока. */
    private static void trace(Level level, BlockPos start, Wire first) {
        record Ref(BlockPos pos, Wire w) {
        }
        ArrayDeque<Ref> q = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        Set<BlockPos> cells = new HashSet<>();
        List<Component> ends = new ArrayList<>();
        q.add(new Ref(start, first));
        seen.add(start.asLong() + ":" + first.packed());
        while (!q.isEmpty() && cells.size() < 2100) {
            Ref r = q.poll();
            cells.add(r.pos);
            for (float[] b : CableBlockEntity.boxes(r.w)) {
                highlight.add(new AABB(b[0] / 16, b[1] / 16, b[2] / 16, b[3] / 16, b[4] / 16, b[5] / 16).move(r.pos.getX(), r.pos.getY(), r.pos.getZ()));
            }
            Direction[] ds = r.w.dirs();
            for (int i = 0; i < 4; i++) {
                if (r.w.port(i) >= 0) {
                    BlockPos dev = r.pos.relative(ds[i]);
                    if (ends.size() < 4) ends.add(Component.translatable("hud.minepc.wire_end", level.getBlockState(dev).getBlock().getName(), r.w.port(i) + 1));
                    continue;
                }
                Object[] t = LanManager.target(r.pos, r.w, i);
                if (t == null) continue;
                BlockPos n = (BlockPos) t[0];
                Direction face = (Direction) t[1];
                if (r.w.conn[i] == Wire.FLAT && level.getBlockEntity(n) instanceof TrayBlockEntity) {
                    if (ends.size() < 4) ends.add(Component.translatable("hud.minepc.wire_tray"));
                    continue;
                }
                if (!(level.getBlockEntity(n) instanceof CableBlockEntity other)) continue;
                int edge = Wire.edgeAxis(r.w.face, ds[i]).ordinal();
                for (Wire o : other.wires()) {
                    if (o.face == face && o.sameKind(r.w) && o.lane[edge] == r.w.lane[edge] && seen.add(n.asLong() + ":" + o.packed())) {
                        q.add(new Ref(n.immutable(), o));
                    }
                }
            }
        }
        int limit = first.fiber ? LanManager.MAX_FIBER : LanManager.MAX_SEGMENT;
        int len = cells.size();
        Component color = Component.translatable("color.minecraft." + DyeColor.byId(first.color).getName());
        lines.add(Component.translatable(first.fiber ? "hud.minepc.fiber_len" : "hud.minepc.wire_len", color, len, limit)
                .withStyle(s -> s.withColor(len > limit ? 0xE05555 : 0xFFFFFF)));
        if (ends.isEmpty()) lines.add(Component.translatable("hud.minepc.wire_noends").withStyle(s -> s.withColor(0x9097A1)));
        for (Component e : ends) lines.add(Component.literal("→ ").append(e).withStyle(s -> s.withColor(0xB4BAC4)));
        highlightColor = len > limit ? 0xE05555 : 0xFFE15A;
    }

    /** Маршрут от выбранного устройства до того, на которое смотрим. */
    private static void route(Minecraft mc, CableItem cable, ItemStack held, BlockPos from, BlockPos target, BlockState state) {
        Component fromName = mc.level.getBlockState(from).getBlock().getName();
        boolean aerial = mc.level.getBlockState(from).getBlock() instanceof BracketBlock;
        if (target == null || !(state.getBlock() instanceof LanDevice) || target.equals(from)) {
            lines.add(Component.translatable(aerial ? "hud.minepc.route_from_aerial" : "hud.minepc.route_from", fromName)
                    .withStyle(s -> s.withColor(0x8FD3F0)));
            lines.add(Component.translatable("hud.minepc.route_hint").withStyle(s -> s.withColor(0x9097A1)));
            ghostTo = null;
            return;
        }
        if (aerial && state.getBlock() instanceof BracketBlock) {
            double dist = Math.sqrt(from.distSqr(target));
            int len = Math.max(2, (int) Math.ceil(dist));
            boolean ok = dist <= BracketBlock.MAX_SPAN;
            lines.add(Component.translatable(ok ? "hud.minepc.aerial_ok" : "hud.minepc.aerial_far", len, BracketBlock.MAX_SPAN)
                    .withStyle(s -> s.withColor(ok ? 0x8FD3F0 : 0xE05555)));
            // Где провод пройдёт: если упрётся в блок — призрак красный и этот блок обведён
            BlockPos blocked = com.strikerr212.minepc.lan.CordShape.blocker(mc.level, from, target);
            if (blocked != null) {
                lines.add(Component.translatable("hud.minepc.aerial_blocked", mc.level.getBlockState(blocked).getBlock().getName())
                        .withStyle(s -> s.withColor(0xE05555)));
                highlight.add(new AABB(blocked));
                highlightColor = 0xE05555;
            }
            ghostBlocked = blocked != null || !ok;
            ghost.addAll(com.strikerr212.minepc.lan.CordShape.points(mc.level, from, target));
            return;
        }
        long now = mc.level.getGameTime();
        if (!target.equals(ghostTo) || !from.equals(ghostFrom) || now - ghostAt > 20) {
            ghostFrom = from.immutable();
            ghostTo = target.immutable();
            ghostAt = now;
            ghostRoute = RoutePlanner.plan(mc.level, from, target, cable.fiber());
        }
        if (ghostRoute == null) {
            lines.add(Component.translatable("hud.minepc.route_none").withStyle(s -> s.withColor(0xE05555)));
            return;
        }
        int need = ghostRoute.pieces().size();
        boolean enough = mc.player.getAbilities().instabuild || held.getCount() >= need;
        lines.add(Component.translatable("hud.minepc.route", ghostRoute.length(), need)
                .withStyle(s -> s.withColor(enough ? 0x8FD3F0 : 0xE05555)));
        lines.add(Component.translatable("hud.minepc.route_click").withStyle(s -> s.withColor(0x9097A1)));
        for (RoutePlanner.Piece p : ghostRoute.pieces()) ghost.add(center(p));
    }

    /** Середина провода маршрута (по дорожкам) в мировых координатах. */
    private static Vec3 center(RoutePlanner.Piece p) {
        Direction.Axis[] ax = Wire.planeAxes(p.face());
        double[] c = new double[3];
        c[ax[0].ordinal()] = Wire.LANE[p.a()] + 0.5;
        c[ax[1].ordinal()] = Wire.LANE[p.b()] + 0.5;
        int n = p.face().getAxis().ordinal();
        c[n] = p.face().getAxisDirection() == Direction.AxisDirection.POSITIVE ? 15.5 : 0.5;
        if (p.face().getAxis().isHorizontal()) c[1] = 8;      // стенка ступеньки — середина по высоте
        return new Vec3(p.cell().getX() + c[0] / 16, p.cell().getY() + c[1] / 16, p.cell().getZ() + c[2] / 16);
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
