package com.strikerr212.minepc.menu;

import com.strikerr212.minepc.block.PcCaseBlockEntity;
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

    /** Слот, которого в этой раскладке нет: стоит далеко за окном. */
    public static final int HIDDEN = -10000;

    public static final int INV_X = CaseLayout.INV_X, INV_Y = CaseLayout.INV_Y;

    private final PcCaseBlockEntity be;
    private final ContainerLevelAccess access;
    /** Окно серверного шасси (своя раскладка слотов). */
    private final boolean serverLayout;
    /** Сколько отсеков дисков у шасси (лишние не показываются). */
    private final int bays;
    private final ContainerData data;

    /** Клиент: позиция корпуса приходит с сервера. */
    public PcCaseMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, blockEntity(inventory, buf.readBlockPos(), buf.isReadable() ? buf.readVarInt() : 0));
    }

    /** Системный блок или модуль серверной стойки (slot — место в стойке). */
    private static PcCaseBlockEntity blockEntity(Inventory inventory, BlockPos pos, int slot) {
        return com.strikerr212.minepc.block.Hosts.get(inventory.player.level(), pos, slot);
    }

    /** Сервер в стойке (у него в окне есть кнопка «Терминал»). */
    public boolean isModule() {
        return be != null && be.isModule();
    }


    public PcCaseMenu(int id, Inventory inventory, PcCaseBlockEntity be) {
        super(ModMenus.PC_CASE.get(), id);
        this.be = be;
        this.access = be != null && be.getLevel() != null ? ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()) : ContainerLevelAccess.NULL;
        IItemHandler handler = be != null ? be.inventory() : new ItemStackHandler(PcCaseBlockEntity.SLOT_COUNT);
        this.serverLayout = be != null && be.serverChassis();
        this.bays = be == null ? 4 : be.bayCount();
        for (int i = 0; i < PcCaseBlockEntity.SLOT_COUNT; i++) {
            addSlot(new CaseSlot(handler, i, slotX(i), slotY(i), be));
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
                return switch (index) {
                    case 0 -> be.computerState();
                    case 1 -> be.hasKeyboard() ? 1 : 0;
                    case 2 -> be.loadPercent();
                    case 3 -> be.netLinkCode();
                    case 4 -> be.netPeersCount();
                    case 5 -> be.netKbSent();
                    case 6 -> be.netKbReceived();
                    default -> be.netWifiMbit();
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 8;
            }
        } : new SimpleContainerData(8);
        addDataSlots(data);
    }

    public boolean serverLayout() {
        return serverLayout;
    }

    /** Значок слота в окне (HIDDEN — места в этой раскладке нет вовсе). */
    public int slotX(int slot) {
        return hit(slot) == null ? HIDDEN : CaseLayout.iconX(slot);
    }

    public int slotY(int slot) {
        return hit(slot) == null ? HIDDEN : CaseLayout.iconY(slot);
    }

    /** Место детали в окне: [x0, y0, x1, y1) или null. */
    public int[] hit(int slot) {
        return CaseLayout.hit(serverLayout, bays, slot);
    }

    public int bays() {
        return bays;
    }

    /** Место есть в раскладке и в корпусе сейчас (зависит от платы). */
    public boolean slotShown(int slot) {
        return hit(slot) != null && (be == null || be.slotPresent(slot));
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

    /** Сеть: 1 — кабель подключён; сколько компьютеров в сети; КБ отправлено и принято. */
    /** Бит 1 — кабель, бит 2 — Wi-Fi; выше — сигнал Wi-Fi, %. */
    public int netLink() { return data.get(3); }
    public int netWifiMbit() { return data.get(7); }
    public int netPeers() { return data.get(4); }
    public int netKbSent() { return data.get(5); }
    public int netKbReceived() { return data.get(6); }

    /** Загрузка процессора за последнюю секунду, 0..100 %. */
    public int loadPercent() {
        return data.get(2);
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
        if (be == null || be.isRemoved()) return false;
        return access.evaluate((level, pos) -> (level.getBlockState(pos).getBlock() instanceof com.strikerr212.minepc.block.PcCaseBlock
                || be.isModule() && com.strikerr212.minepc.block.Hosts.get(level, pos, be.moduleSlot()) == be)
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0, true);
    }

    /** Слот корпуса: одна деталь, правила — у системного блока. Места, которых у платы нет, неактивны. */
    public static class CaseSlot extends SlotItemHandler {
        private final PcCaseBlockEntity owner;

        public CaseSlot(IItemHandler handler, int index, int x, int y, PcCaseBlockEntity owner) {
            super(handler, index, x, y);
            this.owner = owner;
        }

        @Override
        public boolean isActive() {
            return x != HIDDEN && (owner == null || owner.slotPresent(getSlotIndex()));
        }

        /** Подсветку под мышью окно рисует само — формой детали. */
        @Override
        public boolean isHighlightable() {
            return false;
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
