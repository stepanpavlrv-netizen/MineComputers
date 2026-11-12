package com.strikerr212.minepc.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Модуль серверной стойки (1U): сервер или консоль. Ставится ПКМ по свободному месту в стойке. Вынутый сервер
 * уносит с собой всё, что в нём стоит (детали, имя, адрес).
 */
public class RackModuleItem extends Item {

    private final boolean server;

    public RackModuleItem(Properties properties, boolean server) {
        super(properties);
        this.server = server;
    }

    public boolean server() {
        return server;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean tall = stack.is(com.strikerr212.minepc.registry.ModItems.SERVER_MODULE_2U.get());
        tooltip.add(Component.translatable(tall ? "tooltip.minepc.server_module_2u" : server ? "tooltip.minepc.server_module" : "tooltip.minepc.console_module")
                .withStyle(ChatFormatting.GRAY));
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (server && data != null && data.copyTag().contains("machine")) {
            CompoundTag m = data.copyTag().getCompound("machine");
            int parts = 0;
            var items = m.getCompound("inventory").getList("Items", Tag.TAG_COMPOUND);
            parts = items.size();
            String label = m.getString("label");
            if (!label.isEmpty()) tooltip.add(Component.literal(label).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.minepc.server_module_parts", parts).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
