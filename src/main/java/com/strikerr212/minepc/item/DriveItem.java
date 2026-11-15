package com.strikerr212.minepc.item;

import com.strikerr212.minepc.hardware.Units;
import com.strikerr212.minepc.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.UUID;

/**
 * Съёмный накопитель: дискета (вставляется в дисковод) или флешка (в USB-порт системного блока).
 * Файлы хранятся в папке мира, а в предмете — только номер накопителя, поэтому дискету можно
 * вынуть, отнести к другому компьютеру, и файлы будут на ней.
 */
public class DriveItem extends Item {

    public enum Kind { FLOPPY, FLASH }

    private final Kind kind;
    private final long capacity;

    public DriveItem(Kind kind, long capacityBytes, Properties properties) {
        super(properties.stacksTo(1));
        this.kind = kind;
        this.capacity = capacityBytes;
    }

    public Kind kind() {
        return kind;
    }

    public long capacity() {
        return capacity;
    }

    /** Номер накопителя или "" (если на нём ещё ничего не записывали). */
    public static String id(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.DRIVE_ID.get(), "");
    }

    /** Номер накопителя; выдаётся при первом подключении к компьютеру. */
    public static String ensureId(ItemStack stack) {
        String id = id(stack);
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString();
            stack.set(ModDataComponents.DRIVE_ID.get(), id);
        }
        return id;
    }

    /** Программа на дискете из деревни или null. */
    public static com.strikerr212.minepc.computer.ProgramDisks.Program program(ItemStack stack) {
        return com.strikerr212.minepc.computer.ProgramDisks.get(stack.get(ModDataComponents.PROGRAM.get()));
    }

    /** Дискета с программой — с цветной наклейкой. */
    public static ItemStack withProgram(ItemStack stack, String program) {
        stack.set(ModDataComponents.PROGRAM.get(), program);
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        var p = program(stack);
        if (p != null && !stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            return Component.translatable("item.minepc.floppy_disk.program", p.name() + " " + p.version());
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var p = program(stack);
        if (p != null) {
            tooltip.add(Component.translatable("tooltip.minepc.program_disk").withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable(p.id().equals("wired") ? "tooltip.minepc.program_disk_wired" : "tooltip.minepc.program_disk_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.minepc.capacity", Units.bytes(capacity).copy().withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(kind == Kind.FLOPPY ? "tooltip.minepc.floppy_hint" : "tooltip.minepc.flash_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        String id = id(stack);
        if (!id.isEmpty() && flag.isAdvanced()) {
            tooltip.add(Component.literal("id: " + id.substring(0, 8)).withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
