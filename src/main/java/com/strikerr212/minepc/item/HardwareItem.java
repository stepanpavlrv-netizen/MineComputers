package com.strikerr212.minepc.item;

import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.Units;
import com.strikerr212.minepc.hardware.spec.PartDesign;
import com.strikerr212.minepc.hardware.spec.PartSpec;
import com.strikerr212.minepc.hardware.spec.Specs;
import com.strikerr212.minepc.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Общий родитель для всех комплектующих.
 *
 * Внутри предмета хранится только "чертёж" (PartDesign): какие варианты выбраны.
 * Все характеристики вычисляет спецификация вида детали (см. пакет hardware.spec).
 */
public abstract class HardwareItem extends Item {

    public HardwareItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    /** Вид детали — определяет слот в корпусе и спецификацию. */
    public abstract HardwareType getHardwareType();

    public PartSpec spec() {
        return Specs.of(getHardwareType());
    }

    /** Выбранные варианты (всегда правильной длины). */
    public static List<Integer> values(ItemStack stack, PartSpec spec) {
        PartDesign design = stack.get(ModDataComponents.DESIGN.get());
        return spec.sanitize(design == null ? null : design.values());
    }

    public List<Integer> values(ItemStack stack) {
        return values(stack, spec());
    }

    public static String label(ItemStack stack) {
        PartDesign design = stack.get(ModDataComponents.DESIGN.get());
        return design == null ? "" : design.label();
    }

    public int getTier(ItemStack stack) {
        return spec().tier(values(stack));
    }

    public int getPowerDraw(ItemStack stack) {
        return spec().powerDraw(values(stack));
    }

    public int getHeat(ItemStack stack) {
        return spec().heat(values(stack));
    }

    /** Название вида "Процессор (Ур. 3)". */
    @Override
    public Component getName(ItemStack stack) {
        List<Integer> v = values(stack);
        return Component.translatable("name.minepc.tiered",
                Component.translatable(spec().nameKey(v)), spec().tier(v));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String label = label(stack);
        if (!label.isEmpty()) {
            tooltip.add(Component.translatable(spec().labelKey(), Component.literal(label).withStyle(ChatFormatting.AQUA))
                    .withStyle(ChatFormatting.GRAY));
        }
        List<Integer> v = values(stack);
        spec().describe(v, tooltip);
        int power = spec().powerDraw(v);
        if (power > 0) {
            tooltip.add(Component.translatable("tooltip.minepc.power", Component.literal("").append(Units.watts(power))
                    .withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
        }
        if (flag.isAdvanced()) {
            tooltip.add(Component.literal("design: " + v).withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
