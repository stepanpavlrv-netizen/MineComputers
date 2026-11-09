package com.strikerr212.minepc.lan;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * USB Wi-Fi адаптер: вставляется в USB-порт корпуса. Три поколения: дальность 16/32/48 блоков,
 * скорость 54/300/1200 Мбит/с. Каждая сплошная стена между компьютером и роутером отнимает 3 блока дальности.
 */
public class WifiAdapterItem extends Item {

    private final String standard;
    private final int range;
    private final int mbit;

    public WifiAdapterItem(String standard, int range, int mbit, Properties properties) {
        super(properties.stacksTo(16));
        this.standard = standard;
        this.range = range;
        this.mbit = mbit;
    }

    public String standard() {
        return standard;
    }

    public int range() {
        return range;
    }

    public int mbit() {
        return mbit;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.minepc.wifi.spec", standard,
                Component.literal(String.valueOf(range)).withStyle(ChatFormatting.WHITE),
                Component.literal(String.valueOf(mbit)).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.minepc.wifi.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
