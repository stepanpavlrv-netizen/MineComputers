package com.strikerr212.minepc.item;

import com.strikerr212.minepc.block.RackBlockEntity;
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
 * Модуль серверной стойки: сервер 1U/2U, консоль, коммутатор, патч-панель, роутер, ИБП, заглушка. Ставится ПКМ
 * по свободному месту в стойке. Вынутый сервер уносит с собой всё, что в нём стоит (детали, имя, адрес).
 */
public class RackModuleItem extends Item {

    private final int kind;

    public RackModuleItem(Properties properties, int kind) {
        super(properties);
        this.kind = kind;
    }

    /** Вид модуля ({@link RackBlockEntity#SERVER} и т.д.). */
    public int kind() {
        return kind;
    }

    public boolean server() {
        return RackBlockEntity.isServer(kind);
    }

    /** Вид модуля в предмете или -1 (не модуль). */
    public static int kindOf(ItemStack stack) {
        return stack.getItem() instanceof RackModuleItem m ? m.kind : -1;
    }

    private String key() {
        return switch (kind) {
            case RackBlockEntity.SERVER -> "server_module";
            case RackBlockEntity.SERVER_2U -> "server_module_2u";
            case RackBlockEntity.CONSOLE -> "console_module";
            case RackBlockEntity.SWITCH -> "switch_module";
            case RackBlockEntity.PATCH -> "patch_module";
            case RackBlockEntity.ROUTER -> "router_module";
            case RackBlockEntity.UPS -> "ups_module";
            default -> "blank_module";
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.minepc." + key()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.minepc.module_size", RackBlockEntity.sizeOf(kind)).withStyle(ChatFormatting.DARK_GRAY));
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (server() && data != null && data.copyTag().contains("machine")) {
            CompoundTag m = data.copyTag().getCompound("machine");
            var items = m.getCompound("inventory").getList("Items", Tag.TAG_COMPOUND);
            String label = m.getString("label");
            if (!label.isEmpty()) tooltip.add(Component.literal(label).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.minepc.server_module_parts", items.size()).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
