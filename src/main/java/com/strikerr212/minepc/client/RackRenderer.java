package com.strikerr212.minepc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.strikerr212.minepc.block.PcCaseBlockEntity;
import com.strikerr212.minepc.block.RackBlock;
import com.strikerr212.minepc.block.RackBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

/**
 * Модули серверной стойки: задвинутые — лицевые панели с отсеками дисков и лампочками; выдвинутый модуль выезжает
 * на салазках, у сервера снята крышка — видно начинку (плата, процессор с радиатором, память, вентиляторы, блок
 * питания, диски); у консоли — клавиатура и поднятый экран.
 */
public class RackRenderer implements BlockEntityRenderer<RackBlockEntity> {

    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    private static final int BODY = 0x34373E, FRONT = 0x40444C, DARK = 0x1C1D21, EAR = 0x9A9EA6, BAY = 0x2A2C32;
    private static final int PCB = 0x22603F, STEEL = 0xC4C8CE, STEEL_D = 0x8C9096, RAM = 0x18191E, GOLD = 0xC8A03C;
    private static final int LED_G = 0x50FF78, LED_R = 0xFF4030, LED_B = 0x5AA0FF, LED_A = 0xFFAA28, OFF = 0x2A2C30;

    /** Плавное выдвижение: насколько выдвинут модуль сейчас (0..1) — у игрока, по каждому блоку стойки. */
    private static final Map<Long, float[]> PROGRESS = new HashMap<>();
    private static long lastNanos = System.nanoTime();

    public RackRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(RackBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.2);
    }

    @Override
    public void render(RackBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        Direction facing = be.getBlockState().hasProperty(RackBlock.FACING) ? be.getBlockState().getValue(RackBlock.FACING) : Direction.NORTH;
        // Свет перед стойкой — выдвинутый модуль освещён как то, что перед ней
        int frontLight = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().relative(facing));
        float[] prog = PROGRESS.computeIfAbsent(be.getBlockPos().asLong(), k -> new float[RackBlockEntity.SLOTS]);
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        if (PROGRESS.size() > 512) PROGRESS.clear();

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        long time = be.getLevel().getGameTime();
        for (int slot = 0; slot < RackBlockEntity.SLOTS; slot++) {
            int kind = be.kind(slot);
            float target = be.extended() == slot ? 1 : 0;
            // Салазки: ~0.35 с на полный ход
            prog[slot] += Math.signum(target - prog[slot]) * Math.min(Math.abs(target - prog[slot]), dt * 3f);
            float e = prog[slot], ease = e * e * (3 - 2 * e);
            float dz = -RackBlock.TRAVEL * ease;
            int y = slot * 4;
            int l = e > 0.05f ? frontLight : light;
            if (kind == RackBlockEntity.EMPTY) {
                box(vc, pose, 1, y + 0.2f, 1.5f, 15, y + 3.8f, 2, DARK, l);           // заглушка
                continue;
            }
            if (kind == RackBlockEntity.SERVER) server(vc, pose, be.server(slot), y, dz, ease > 0.6f, l, time, slot);
            else console(vc, pose, y, dz, ease, l);
        }
        pose.popPose();
    }

    private static void server(VertexConsumer vc, PoseStack pose, PcCaseBlockEntity s, float y, float dz, boolean open, int light,
                               long time, int slot) {
        int state = s == null ? 0 : s.displayState();
        // Корпус: дно и стенки, крышка — пока модуль не выдвинут
        box(vc, pose, 1, y + 0.2f, dz + 1, 15, y + 0.6f, dz + 16, BODY, light);
        box(vc, pose, 1, y + 0.2f, dz + 1, 1.5f, y + 3.8f, dz + 16, BODY, light);
        box(vc, pose, 14.5f, y + 0.2f, dz + 1, 15, y + 3.8f, dz + 16, BODY, light);
        if (!open) box(vc, pose, 1, y + 3.4f, dz + 1, 15, y + 3.8f, dz + 16, BODY, light);
        // Лицо: ушки, четыре отсека дисков, лампочки
        box(vc, pose, 0, y + 0.2f, dz, 16, y + 3.8f, dz + 1, FRONT, light);
        box(vc, pose, 0, y + 0.8f, dz - 0.6f, 0.8f, y + 3.2f, dz, EAR, light);
        box(vc, pose, 15.2f, y + 0.8f, dz - 0.6f, 16, y + 3.2f, dz, EAR, light);
        // Отсеки дисков: отсек i — слот диска i (два под жёсткие диски, два под SATA SSD)
        for (int i = 0; i < 4; i++) {
            float x = 2 + i * 3;
            boolean has = s != null && !s.getStack(PcCaseBlockEntity.SLOT_HDD + i).isEmpty();
            box(vc, pose, x, y + 0.9f, dz - 0.1f, x + 2.4f, y + 3.1f, dz + 0.2f, has ? 0x5A5E66 : BAY, light);
            boolean busy = state == 1 && has && ((time / 3 + i * 7 + slot) % 9 < 2);
            int led = !has ? OFF : state == 1 ? (busy ? LED_A : LED_G) : OFF;
            glow(vc, pose, x + 1.9f, y + 2.5f, dz - 0.2f, 0.35f, led);
        }
        glow(vc, pose, 14.3f, y + 2.6f, dz - 0.2f, 0.4f, state == 1 ? LED_B : state == 2 ? LED_R : OFF);
        // Лампа питания: жёлтая, если у работающего сервера нет второго блока (резерва)
        boolean noSpare = s != null && s.psuCount() < 2;
        glow(vc, pose, 14.3f, y + 1.4f, dz - 0.2f, 0.3f, state == 1 ? (noSpare ? LED_A : LED_G) : OFF);
        if (!open || s == null) return;
        // ----- Начинка (вид сверху) -----
        float b = y + 0.6f;
        if (!s.getStack(PcCaseBlockEntity.SLOT_MOTHERBOARD).isEmpty()) box(vc, pose, 1.6f, b, dz + 6, 14.4f, b + 0.25f, dz + 15.5f, PCB, light);
        // Диски в отсеках
        for (int i = 0; i < 4; i++) {
            if (!s.getStack(PcCaseBlockEntity.SLOT_HDD + i).isEmpty()) box(vc, pose, 2 + i * 3, b, dz + 1, 4.4f + i * 3, b + 2, dz + 4.5f, 0x7E828A, light);
        }
        // Стена вентиляторов
        for (int i = 0; i < 6; i++) {
            float x = 2 + i * 2;
            box(vc, pose, x, b, dz + 5, x + 1.8f, b + 2.6f, dz + 5.8f, i % 2 == 0 ? 0x26272B : 0x3A3C42, light);
            box(vc, pose, x + 0.6f, b + 0.9f, dz + 4.9f, x + 1.2f, b + 1.5f, dz + 5, 0x6A6E74, light);
        }
        // Процессоры: пассивные радиаторы (воздух гонит стена вентиляторов); у обычной платы — один
        float[] cpuX = {3.5f, 10.1f};
        int[] cpuSlot = {PcCaseBlockEntity.SLOT_CPU, PcCaseBlockEntity.SLOT_CPU2};
        for (int c = 0; c < 2; c++) {
            if (s.getStack(cpuSlot[c]).isEmpty()) continue;
            for (int i = 0; i < 5; i++) {
                float x = cpuX[c] + i * 0.5f;
                box(vc, pose, x, b + 0.25f, dz + 7, x + 0.3f, b + 2.2f, dz + 10.5f, i % 2 == 0 ? STEEL : STEEL_D, light);
            }
        }
        // Память: четыре ряда по четыре планки — по бокам от каждого процессора
        float[] bankX = {1.7f, 6.0f, 8.3f, 12.6f};
        for (int i = 0; i < PcCaseBlockEntity.MAX_RAM_SLOTS; i++) {
            if (s.getStack(PcCaseBlockEntity.ramSlot(i)).isEmpty()) continue;
            float x = bankX[i / 4] + (i % 4) * 0.45f;
            box(vc, pose, x, b + 0.25f, dz + 6.5f, x + 0.32f, b + 2.3f, dz + 11.5f, RAM, light);
            box(vc, pose, x, b + 2.3f, dz + 6.5f, x + 0.32f, b + 2.45f, dz + 11.5f, GOLD, light);
        }
        // Видеокарта (низкопрофильная) — если стоит
        if (!s.getStack(PcCaseBlockEntity.SLOT_GPU).isEmpty()) box(vc, pose, 2, b + 0.25f, dz + 12, 8, b + 1.2f, dz + 15.3f, 0x2E3138, light);
        // Два блока питания сзади справа: вынули один — на оставшемся жёлтая лампа
        int[] psuSlot = {PcCaseBlockEntity.SLOT_PSU, PcCaseBlockEntity.SLOT_PSU2};
        for (int k = 0; k < 2; k++) {
            if (s.getStack(psuSlot[k]).isEmpty()) continue;
            float x = 8.8f + k * 3;
            box(vc, pose, x, b, dz + 12, x + 2.8f, b + 2.6f, dz + 15.8f, 0x55585F, light);
            glow(vc, pose, x + 2.3f, b + 2.0f, dz + 11.9f, 0.3f, state == 1 ? (noSpare ? LED_A : LED_G) : OFF);
        }
    }

    private static void console(VertexConsumer vc, PoseStack pose, float y, float dz, float ease, int light) {
        // Лоток с клавиатурой
        box(vc, pose, 1, y + 0.2f, dz + 1, 15, y + 0.7f, dz + 16, BODY, light);
        box(vc, pose, 0, y + 0.2f, dz, 16, y + (ease > 0.5f ? 1.2f : 3.8f), dz + 1, FRONT, light);
        box(vc, pose, 6, y + 1.6f, dz - 0.5f, 10, y + 2.2f, dz, EAR, light);              // ручка
        if (ease > 0.5f) {
            box(vc, pose, 2, y + 0.7f, dz + 1.5f, 14, y + 1.1f, dz + 6, 0x24262B, light);  // клавиатура
            for (int r = 0; r < 3; r++) {
                for (int k = 0; k < 10; k++) {
                    box(vc, pose, 2.4f + k * 1.15f, y + 1.1f, dz + 1.9f + r * 1.3f, 3.3f + k * 1.15f, y + 1.3f, dz + 2.9f + r * 1.3f, 0x50545C, light);
                }
            }
            // Экран на петле — поднят
            float h = Math.min(11, 11 * (ease - 0.5f) * 2);
            box(vc, pose, 2, y + 0.7f, dz + 8, 14, y + 0.7f + h, dz + 8.6f, 0x1A1B1F, light);
            if (h > 6) {
                glow(vc, pose, 3, y + 1.5f, dz + 7.95f, 0, 0, 10, h - 1.6f, 0x0E2A1C);
                for (int i = 0; i < 4; i++) glow(vc, pose, 3.6f, y + 1.9f + h - 3 - i * 1.6f, dz + 7.9f, 0, 0, 3 + (i * 3) % 6, 0.5f, 0x3CFF8C);
            }
        }
    }

    // ======================= Коробки =======================

    private static void box(VertexConsumer vc, PoseStack pose, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, int light) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        PoseStack.Pose m = pose.last();
        quad(vc, m, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, r, g, b, light);           // верх
        quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, r * 6 / 10, g * 6 / 10, b * 6 / 10, light);
        quad(vc, m, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, r * 85 / 100, g * 85 / 100, b * 85 / 100, light);
        quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, r * 85 / 100, g * 85 / 100, b * 85 / 100, light);
        quad(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, r * 75 / 100, g * 75 / 100, b * 75 / 100, light);
        quad(vc, m, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, r * 75 / 100, g * 75 / 100, b * 75 / 100, light);
    }

    /** Лампочка: квадратик на лицевой стороне (смотрит наружу, к -z), светится в темноте. */
    private static void glow(VertexConsumer vc, PoseStack pose, float x, float y, float z, float half, int rgb) {
        glow(vc, pose, x - half, y - half, z, 0, 0, half * 2, half * 2, rgb);
    }

    private static void glow(VertexConsumer vc, PoseStack pose, float x, float y, float z, float u, float v, float w, float h, int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        quad(vc, pose.last(), x + w, y, z, x, y, z, x, y + h, z, x + w, y + h, z, 0, 0, -1, r, g, b, 0xF000F0);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose m, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, float nx, float ny, float nz,
                             int r, int g, int b, int light) {
        vertex(vc, m, x0, y0, z0, 0, 1, nx, ny, nz, r, g, b, light);
        vertex(vc, m, x1, y1, z1, 1, 1, nx, ny, nz, r, g, b, light);
        vertex(vc, m, x2, y2, z2, 1, 0, nx, ny, nz, r, g, b, light);
        vertex(vc, m, x3, y3, z3, 0, 0, nx, ny, nz, r, g, b, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose m, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, int r, int g, int b, int light) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(m, nx, ny, nz);
    }

    @SuppressWarnings("unused")
    private static boolean present(ItemStack s) {
        return !s.isEmpty();
    }
}
