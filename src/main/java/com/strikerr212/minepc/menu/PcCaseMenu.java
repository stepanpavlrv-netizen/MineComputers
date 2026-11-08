package com.strikerr212.minepc.menu;

import com.strikerr212.minepc.block.PcCaseBlockEntity;
import com.strikerr212.minepc.registry.ModBlocks;
import com.strikerr212.minepc.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Окно системного блока: слоты для деталей (как в OpenComputers), USB-порты, кнопка питания.
 * Правила (что куда можно поставить и что можно вынуть) проверяет сам системный блок.
 */
public class PcCaseMenu extends AbstractContainerMenu {

    public static final int BTN_POWER = 0;

    /** Положение слотов в окне (x, y) — в том же порядке, что слоты корпуса. */
    public static final int[][] SLOT_POS = {
            {10, 22},                                   // плата
            {32, 22},                                   // процессор
            {50, 22},                                   // кулер
            {10, 46}, {28, 46}, {46, 46}, {64, 46},     // память
            {72, 22},                                   // видеокарта
            {94, 22},                                   // блок питания
            {10, 70}, {28, 70},                         // жёсткие диски
            {52, 70}, {70, 70},                         // SATA SSD
            {94, 46}, {112, 46},                        // M.2
            {112, 70}, {130, 70},                       // USB
    };
    public static final int INV_X = 8, INV_Y = 100;

    private final PcCaseBlockEntity be;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    /** Клиент: позиция корпуса приходит с сервера. */
    public PcCaseMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, blockEntity(inventory, buf.readBlockPos()));
    }

    private static PcCaseBlockEntity blockEntity(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof PcCaseBlockEntity pc ? pc : null;
    }

    public PcCaseMenu(int id, Inventory inventory, PcCaseBlockEntity be) {
        super(ModMenus.PC_CASE.get(), id);
        this.be = be;
        this.access = be != null && be.getLevel() != null ? ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()) : ContainerLevelAccess.NULL;
        IItemHandler handler = be != null ? be.inventory() : new ItemStackHandler(PcCaseBlockEntity.SLOT_COUNT);
        for (int i = 0; i < PcCaseBlockEntity.SLOT_COUNT; i++) {
            addSlot(new CaseSlot(handler, i, SLOT_POS[i][0] + 1, SLOT_POS[i][1] + 1));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INV_X + col * 18, INV_Y + 58));
        }
        boolean server = be != null && be.getLevel() != null && !be.getLevel().isClientSide;
        data = server ? new ContainerData() {
            @Override
            public int get(int index) {
                return index == 0 ? be.computerState() : (be.hasKeyboard() ? 1 : 0);
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 2;
            }
        } : new SimpleContainerData(2);
        addDataSlots(data);
    }

    public PcCaseBlockEntity blockEntity() {
        return be;
    }

    /** 0 — выключен, 1 — работает, 2 — ошибка. */
    public int computerState() {
        return data.get(0);
    }

    public boolean hasKeyboard() {
        return data.get(1) == 1;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BTN_POWER && be != null) {
            Component msg = be.togglePower();
            if (msg != null) player.displayClientMessage(msg, true);
            return true;
        }
        return false;
    }

    /** Shift+клик: деталь из инвентаря — в подходящий слот, из корпуса — в инвентарь. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int caseSlots = PcCaseBlockEntity.SLOT_COUNT;
        if (index < caseSlots) {
            if (!slot.mayPickup(player)) return ItemStack.EMPTY;
            if (!moveItemStackTo(stack, caseSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(stack, 0, caseSlots, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return be != null && stillValid(access, player, ModBlocks.PC_CASE.get());
    }

    /** Слот корпуса: одна деталь, правила — у системного блока. */
    public static class CaseSlot extends SlotItemHandler {
        public CaseSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }
}
