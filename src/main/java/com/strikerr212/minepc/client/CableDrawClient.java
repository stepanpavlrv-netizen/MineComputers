package com.strikerr212.minepc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.lan.CableBlockEntity;
import com.strikerr212.minepc.lan.CableItem;
import com.strikerr212.minepc.lan.CablePath;
import com.strikerr212.minepc.lan.LanDevice;
import com.strikerr212.minepc.lan.Wire;
import com.strikerr212.minepc.network.CableLayPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Прокладка провода щелчками (у игрока): точки пути, призрак до прицела, ошибки на месте, свободные гнёзда вокруг.
 * Когда путь закончен (щелчок по гнезду или повторный — по последней точке), весь путь уходит на сервер
 * ({@link CableLayPayload}); сервер кладёт ровно то же, что было видно призраком (та же {@link CablePath}).
 */
@EventBusSubscriber(modid = MineComputers.MODID, value = Dist.CLIENT)
public final class CableDrawClient {

    private CableDrawClient() {
    }

    private static final List<CablePath.Point> points = new ArrayList<>();
    private static InteractionHand hand = InteractionHand.MAIN_HAND;
    private static long lastClick = -100;

    // ----- Призрак -----
    private static String previewKey = "";
    private static long previewAt;
    private static CablePath.Result preview;
    private static CablePath.Point cursor;
    private static final List<AABB> ghost = new ArrayList<>();
    private static final List<AABB> bad = new ArrayList<>();
    private static final List<AABB> freeSockets = new ArrayList<>();
    private static long socketsAt = -100;
    private static AABB cursorSocket;
    private static final List<Component> lines = new ArrayList<>();
    private static boolean holding;

    /** Идёт прокладка (для подсказок монтажника: не мешать). */
    public static boolean active() {
        return !points.isEmpty();
    }

    private static CableItem cableIn(InteractionHand h) {
        var p = Minecraft.getInstance().player;
        return p != null && p.getItemInHand(h).getItem() instanceof CableItem c ? c : null;
    }

    /** Точка по щелчку: гнездо устройства (свободное, подходящее) или пиксель на поверхности; null — некуда. */
    private static CablePath.Point pointAt(Level level, BlockPos pos, Direction side, Vec3 hit, boolean fiber) {
        if (level.getBlockState(pos).getBlock() instanceof LanDevice) {
            int port = CablePath.socketAt(level, pos, hit, side, fiber);
            return port < 0 ? null : CablePath.socketPoint(level, pos, port);
        }
        return CablePath.surfacePoint(level, new BlockHitResult(hit, side, pos, false));
    }

    private static void say(String key, Object... args) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.displayClientMessage(Component.translatable(key, args), true);
    }

    /** ПКМ мотком по блоку. */
    public static void click(InteractionHand h, BlockPos pos, Direction side, Vec3 hit) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        CableItem cable = cableIn(h);
        if (cable == null) return;
        long now = mc.level.getGameTime();
        if (now - lastClick < 5) return;               // зажатая кнопка повторяет щелчок — это не новые точки
        lastClick = now;
        if (mc.player.isShiftKeyDown()) {
            undo();
            return;
        }
        if (!points.isEmpty() && h != hand) points.clear();
        CablePath.Point p = pointAt(mc.level, pos, side, hit, cable.fiber());
        if (p == null) {
            say(mc.level.getBlockState(pos).getBlock() instanceof LanDevice ? "message.minepc.draw.no_free_socket" : "message.minepc.draw.no_support");
            return;
        }
        if (points.isEmpty()) {
            hand = h;
            points.add(p);
            mc.player.playSound(SoundEvents.WOOL_PLACE, 0.5f, 1.5f);
            say(p.socket() ? "message.minepc.draw.start_socket" : "message.minepc.draw.start", p.socket() ? p.port() + 1 : 0);
            return;
        }
        CablePath.Point last = points.get(points.size() - 1);
        if (p.same(last) || p.socket()) {
            if (!p.same(last)) points.add(p);
            finish(cable);
            return;
        }
        points.add(p);
        mc.player.playSound(SoundEvents.WOOL_PLACE, 0.4f, 1.8f);
    }

    /** ПКМ в воздух: Shift — шаг назад. */
    public static void air(InteractionHand h) {
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isShiftKeyDown() && mc.level != null && mc.level.getGameTime() - lastClick >= 5) {
            lastClick = mc.level.getGameTime();
            undo();
        }
    }

    private static void undo() {
        if (points.isEmpty()) return;
        points.remove(points.size() - 1);
        say(points.isEmpty() ? "message.minepc.draw.cancel" : "message.minepc.draw.undo", points.size());
    }

    /** Путь закончен: проверить (тем же путём, что сервер) и отправить. */
    private static void finish(CableItem cable) {
        Minecraft mc = Minecraft.getInstance();
        if (points.size() < 2) return;
        CablePath.Point a = points.get(0), b = points.get(points.size() - 1);
        boolean aerial = points.size() == 2 && a.socket() && b.socket()
                && mc.level.getBlockState(a.dev()).getBlock() instanceof com.strikerr212.minepc.lan.BracketBlock
                && mc.level.getBlockState(b.dev()).getBlock() instanceof com.strikerr212.minepc.lan.BracketBlock;
        if (!aerial) {
            CablePath.Result r = CablePath.build(mc.level, points, cable.fiber(), 0);
            if (!r.ok()) {
                CablePath.Problem pr = r.problems().isEmpty() ? null : r.problems().get(0);
                say(pr == null ? "message.minepc.draw.no_path" : pr.reason());
                points.remove(points.size() - 1);       // последнюю точку не принимаем — можно поправить
                return;
            }
        }
        PacketDistributor.sendToServer(new CableLayPayload(hand == InteractionHand.OFF_HAND, List.copyOf(points)));
        points.clear();
        preview = null;
        previewKey = "";
    }

    // ======================= Каждый тик: призрак и подсказки =======================

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ghost.clear();
        bad.clear();
        lines.clear();
        cursorSocket = null;
        holding = false;
        if (mc.player == null || mc.level == null) {
            points.clear();
            return;
        }
        CableItem cable = cableIn(points.isEmpty() ? InteractionHand.MAIN_HAND : hand);
        if (cable == null && points.isEmpty()) cable = cableIn(InteractionHand.OFF_HAND);
        if (cable == null) {
            points.clear();                              // моток убрали из руки — путь сброшен
            freeSockets.clear();
            return;
        }
        holding = true;
        Level level = mc.level;
        long now = level.getGameTime();
        if (now - socketsAt >= 10) {
            socketsAt = now;
            scanSockets(level, mc.player.blockPosition(), cable.fiber());
        }
        // Курсор
        cursor = null;
        if (mc.hitResult instanceof BlockHitResult bhr && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            cursor = pointAt(level, bhr.getBlockPos(), bhr.getDirection(), bhr.getLocation(), cable.fiber());
            if (cursor != null && cursor.socket()) {
                List<LanDevice.Socket> socks = LanDevice.socketsAt(level, cursor.dev());
                if (cursor.port() < socks.size()) cursorSocket = socketBox(cursor.dev(), socks.get(cursor.port()));
            }
        }
        if (points.isEmpty()) {
            lines.add(Component.translatable("hud.minepc.draw.hint_start").withStyle(s -> s.withColor(0x8FD3F0)));
            lines.add(Component.translatable("hud.minepc.draw.hint_start2").withStyle(s -> s.withColor(0x9097A1)));
            if (cursor != null && cursor.socket()) lines.add(Component.translatable("hud.minepc.draw.port", cursor.port() + 1).withStyle(s -> s.withColor(0x5BD47A)));
            return;
        }
        // Путь: поставленные точки + курсор (если он не та же точка)
        List<CablePath.Point> path = new ArrayList<>(points);
        if (cursor != null && !cursor.same(points.get(points.size() - 1))) path.add(cursor);
        String key = key(path);
        if (!key.equals(previewKey) && now - previewAt >= 2) {
            previewKey = key;
            previewAt = now;
            preview = path.size() >= 2 ? CablePath.build(level, path, cable.fiber(), 0) : null;
        }
        // Призрак
        if (preview != null) {
            for (CablePath.Piece p : preview.pieces()) {
                Wire w = p.wire(0, cable.fiber());
                for (float[] b : CableBlockEntity.boxes(w)) {
                    ghost.add(new AABB(b[0] / 16, b[1] / 16, b[2] / 16, b[3] / 16, b[4] / 16, b[5] / 16)
                            .move(p.cell().getX(), p.cell().getY(), p.cell().getZ()));
                }
            }
            for (CablePath.Problem pr : preview.problems()) bad.add(new AABB(pr.cell()).deflate(0.05));
        }
        // Точки излома — маленькие кубики
        for (CablePath.Point p : points) ghost.add(pointBox(p));
        int have = mc.player.getAbilities().instabuild ? Integer.MAX_VALUE : mc.player.getItemInHand(hand).getCount();
        int len = preview == null ? 0 : preview.length();
        lines.add(Component.translatable("hud.minepc.draw.status", points.size(), len)
                .withStyle(s -> s.withColor(len > have ? 0xE05555 : 0x8FD3F0)));
        if (len > have) lines.add(Component.translatable("message.minepc.route.short", len).withStyle(s -> s.withColor(0xE05555)));
        if (preview != null && !preview.problems().isEmpty()) {
            lines.add(Component.translatable(preview.problems().get(0).reason()).withStyle(s -> s.withColor(0xE05555)));
        } else if (cursor != null && cursor.socket()) {
            lines.add(Component.translatable("hud.minepc.draw.port_end", cursor.port() + 1).withStyle(s -> s.withColor(0x5BD47A)));
        }
        lines.add(Component.translatable("hud.minepc.draw.hint").withStyle(s -> s.withColor(0x9097A1)));
        lines.add(Component.translatable("hud.minepc.draw.hint2").withStyle(s -> s.withColor(0x9097A1)));
    }

    private static String key(List<CablePath.Point> path) {
        StringBuilder b = new StringBuilder();
        for (CablePath.Point p : path) {
            b.append(p.cell().asLong()).append(p.face().ordinal()).append(':').append(p.u()).append(',').append(p.v())
                    .append(p.socket() ? "s" + p.port() : "").append(';');
        }
        return b.toString();
    }

    /** Кубик точки излома (в пикселе, куда щёлкнули). */
    private static AABB pointBox(CablePath.Point p) {
        Direction.Axis[] ax = Wire.planeAxes(p.face());
        double[] c = new double[3];
        c[ax[0].ordinal()] = p.u() + 0.5;
        c[ax[1].ordinal()] = p.v() + 0.5;
        int n = p.face().getAxis().ordinal();
        c[n] = p.face().getAxisDirection() == Direction.AxisDirection.POSITIVE ? 15.5 : 0.5;
        Vec3 v = new Vec3(p.cell().getX() + c[0] / 16, p.cell().getY() + c[1] / 16, p.cell().getZ() + c[2] / 16);
        return new AABB(v, v).inflate(0.07);
    }

    /** Свободные гнёзда подходящего вида у устройств рядом (до 8 блоков). */
    private static void scanSockets(Level level, BlockPos me, boolean fiber) {
        freeSockets.clear();
        int cx = me.getX() >> 4, cz = me.getZ() >> 4;
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                if (!level.hasChunk(x, z)) continue;
                for (BlockEntity be : level.getChunk(x, z).getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (pos.distSqr(me) > 64 || !(be.getBlockState().getBlock() instanceof LanDevice)) continue;
                    List<LanDevice.Socket> socks = LanDevice.socketsAt(level, pos);
                    for (int i = 0; i < socks.size() && freeSockets.size() < 200; i++) {
                        if (socks.get(i).accepts(fiber) && CablePath.socketFree(level, pos, i)) freeSockets.add(socketBox(pos, socks.get(i)));
                    }
                }
            }
        }
    }

    private static AABB socketBox(BlockPos pos, LanDevice.Socket s) {
        Vec3 c = CablePath.socketCenter(s).scale(1 / 16.0);   // центр гнезда — в пикселях блока
        return new AABB(c, c).inflate(1.1 / 16).move(pos.getX(), pos.getY(), pos.getZ());
    }

    // ======================= Отрисовка =======================

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !holding) return;
        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        boolean ok = preview == null || preview.problems().isEmpty();
        VertexConsumer q = buffers.getBuffer(XrayRenderTypes.FLAT);
        for (AABB b : ghost) fill(q, m, b.inflate(0.01), ok ? 0.55f : 0.95f, ok ? 0.85f : 0.45f, ok ? 1f : 0.4f, 0.75f);
        buffers.endBatch(XrayRenderTypes.FLAT);
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        for (AABB b : freeSockets) LevelRenderer.renderLineBox(pose, vc, b, 0.35f, 1f, 0.5f, 0.9f);
        if (cursorSocket != null) LevelRenderer.renderLineBox(pose, vc, cursorSocket.inflate(0.02), 1f, 1f, 1f, 1f);
        for (AABB b : bad) LevelRenderer.renderLineBox(pose, vc, b, 1f, 0.3f, 0.3f, 1f);
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void fill(VertexConsumer q, Matrix4f m, AABB b, float r, float g, float bl, float a) {
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ, x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        float[][] faces = {
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1},
                {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0},
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0},
                {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}};
        for (float[] f : faces) {
            for (int i = 0; i < 12; i += 3) q.addVertex(m, f[i], f[i + 1], f[i + 2]).setColor(r, g, bl, a);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (lines.isEmpty() || mc.screen != null || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        var font = mc.font;
        int w = 0;
        for (Component c : lines) w = Math.max(w, font.width(c));
        int x = g.guiWidth() / 2 - w / 2, y = g.guiHeight() / 2 + 14;
        int h = lines.size() * 10 + 6;
        g.fill(x - 4, y - 3, x + w + 4, y + h - 3, 0xB0101216);
        for (Component c : lines) {
            g.drawString(font, c, x, y, 0xFFFFFF, false);
            y += 10;
        }
    }

    // ======================= Для CI =======================

    /** Поставить точки пути как будто щелчками (сцена проверки). */
    public static void debugPoints(InteractionHand h, List<CablePath.Point> pts) {
        points.clear();
        points.addAll(pts);
        hand = h;
        previewKey = "";
    }

    public static CablePath.Result debugPreview() {
        return preview;
    }
}
