package com.strikerr212.minepc.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Вентиляторы стойки: сменная стена вентиляторов модуля сервера (ПКМ по выдвинутому выключенному модулю, место
 * кулера). Без них процессоры охлаждают встроенные вентиляторы модуля. Мощнее — громче и чуть больше расход.
 */
public class RackFansItem extends Item {

    /** Отвод тепла на один процессор в модуле 1U, Вт: встроенные (0) и уровни 1–3. В 2U — в полтора раза больше. */
    public static final int[] PER_CPU_1U = {260, 330, 430, 550};
    /** Сколько потребляют сами вентиляторы на процессор, Вт. */
    public static final int[] DRAW = {6, 8, 14, 21};
    /** Во сколько раз быстрее крутятся (и громче гудят) под той же нагрузкой. */
    public static final float[] SPEED = {1f, 1.1f, 1.25f, 1.45f};

    private final int tier;

    public RackFansItem(int tier, Properties properties) {
        super(properties.stacksTo(16));
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    /** Отвод на процессор: tier 0 — встроенные вентиляторы. */
    public static int perCpu(int tier, boolean tall) {
        int w = PER_CPU_1U[Math.max(0, Math.min(3, tier))];
        return tall ? w * 3 / 2 : w;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.minepc.rack_fans.spec",
                com.strikerr212.minepc.hardware.Units.watts(perCpu(tier, false)).copy().withStyle(ChatFormatting.WHITE),
                com.strikerr212.minepc.hardware.Units.watts(perCpu(tier, true)).copy().withStyle(ChatFormatting.WHITE),
                com.strikerr212.minepc.hardware.Units.watts(PER_CPU_1U[0])).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.minepc.rack_fans.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
