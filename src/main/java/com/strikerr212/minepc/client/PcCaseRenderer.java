package com.strikerr212.minepc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.strikerr212.minepc.block.PcCaseBlock;
import com.strikerr212.minepc.block.PcCaseBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Рисует установленные детали внутри корпуса.
 *
 * Каждая деталь — 3D-модель, в которой уже заложено её место в корпусе
 * (материнская плата у поддона, видеокарта в слоте PCIe, блок питания внизу сзади...).
 * Рендерер лишь ставит "центр" модели в опорную точку корпуса,
 * а для второго, третьего... слота сдвигает деталь на шаг слота.
 *
 * Все размеры ниже — в пикселях блока (1/16), для корпуса, смотрящего на север.
 */
public class PcCaseRenderer implements BlockEntityRenderer<PcCaseBlockEntity> {

    /** Точка корпуса, в которую попадает центр (8, 8, 8) каждой модели детали. */
    private static final float ANCHOR_X = 4f, ANCHOR_Y = 8f, ANCHOR_Z = 8f;

    // Шаги между соседними слотами одного вида
    private static final float RAM_STEP_Z = -0.35f;       // слоты памяти идут к передней панели
    private static final float HDD_STEP_Y = 1.5f;         // корзина дисков — снизу вверх
    private static final float SATA_SSD_STEP_Y = -3.0f;   // крепления SSD на поддоне — сверху вниз
    private static final float M2_STEP_Y = -2.8f;         // второй слот M.2 — под видеокартой

    private final ItemRenderer itemRenderer;

    public PcCaseRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(PcCaseBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        Direction facing = be.getBlockState().getValue(PcCaseBlock.FACING);

        pose.pushPose();
        // Поворачиваем всю "сцену" вокруг центра блока по направлению корпуса
        pose.translate(0.5f, 0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(-yRotation(facing)));
        pose.translate(-0.5f, 0f, -0.5f);

        part(be, PcCaseBlockEntity.SLOT_MOTHERBOARD, 0, 0, 0, pose, buffers, light, overlay);
        part(be, PcCaseBlockEntity.SLOT_CPU, 0, 0, 0, pose, buffers, light, overlay);
        part(be, PcCaseBlockEntity.SLOT_COOLER, 0, 0, 0, pose, buffers, light, overlay);
        for (int i = 0; i < PcCaseBlockEntity.RAM_SLOTS; i++) {
            part(be, PcCaseBlockEntity.SLOT_RAM + i, 0, 0, RAM_STEP_Z * i, pose, buffers, light, overlay);
        }
        part(be, PcCaseBlockEntity.SLOT_GPU, 0, 0, 0, pose, buffers, light, overlay);
        part(be, PcCaseBlockEntity.SLOT_PSU, 0, 0, 0, pose, buffers, light, overlay);
        for (int i = 0; i < PcCaseBlockEntity.HDD_SLOTS; i++) {
            part(be, PcCaseBlockEntity.SLOT_HDD + i, 0, HDD_STEP_Y * i, 0, pose, buffers, light, overlay);
        }
        for (int i = 0; i < PcCaseBlockEntity.SATA_SSD_SLOTS; i++) {
            part(be, PcCaseBlockEntity.SLOT_SATA_SSD + i, 0, SATA_SSD_STEP_Y * i, 0, pose, buffers, light, overlay);
        }
        for (int i = 0; i < PcCaseBlockEntity.M2_SLOTS; i++) {
            part(be, PcCaseBlockEntity.SLOT_M2 + i, 0, M2_STEP_Y * i, 0, pose, buffers, light, overlay);
        }

        pose.popPose();
    }

    /** Нарисовать деталь из слота со сдвигом (dx, dy, dz) в пикселях. */
    private void part(PcCaseBlockEntity be, int slot, float dx, float dy, float dz,
                      PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack stack = be.getStack(slot);
        if (stack.isEmpty()) {
            return;
        }
        pose.pushPose();
        pose.translate((ANCHOR_X + dx) / 16f, (ANCHOR_Y + dy) / 16f, (ANCHOR_Z + dz) / 16f);
        // NONE — модель рисуется "как есть", без поворотов и масштабов для руки или инвентаря
        itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, light, overlay, pose, buffers, be.getLevel(), 0);
        pose.popPose();
    }

    /** Поворот модели в blockstate: север 0°, восток 90°, юг 180°, запад 270°. */
    private static float yRotation(Direction facing) {
        return switch (facing) {
            case EAST -> 90f;
            case SOUTH -> 180f;
            case WEST -> 270f;
            default -> 0f;
        };
    }
}
