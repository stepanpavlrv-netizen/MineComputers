package com.strikerr212.minepc.block;

import com.strikerr212.minepc.hardware.CpuData;
import com.strikerr212.minepc.hardware.GpuData;
import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.MotherboardData;
import com.strikerr212.minepc.hardware.Units;
import com.strikerr212.minepc.item.CoolerItem;
import com.strikerr212.minepc.item.CpuItem;
import com.strikerr212.minepc.item.GpuItem;
import com.strikerr212.minepc.item.HardwareItem;
import com.strikerr212.minepc.item.MotherboardItem;
import com.strikerr212.minepc.item.PsuItem;
import com.strikerr212.minepc.item.RamItem;
import com.strikerr212.minepc.item.SsdItem;
import com.strikerr212.minepc.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * "Мозг" системного блока: хранит установленные детали и проверяет совместимость.
 */
public class PcCaseBlockEntity extends BlockEntity {

    // ----- Раскладка слотов -----
    public static final int SLOT_MOTHERBOARD = 0;
    public static final int SLOT_CPU = 1;
    public static final int SLOT_COOLER = 2;
    public static final int SLOT_RAM = 3;          // 3..6
    public static final int RAM_SLOTS = 4;
    public static final int SLOT_GPU = 7;
    public static final int SLOT_PSU = 8;
    public static final int SLOT_HDD = 9;          // 9..10 — корзина для дисков
    public static final int HDD_SLOTS = 2;
    public static final int SLOT_SATA_SSD = 11;    // 11..12 — крепления на поддоне
    public static final int SATA_SSD_SLOTS = 2;
    public static final int SLOT_M2 = 13;          // 13..14 — слоты M.2 на плате
    public static final int M2_SLOTS = 2;
    public static final int SLOT_COUNT = 15;

    /** Версия раскладки: если сохранение старое, детали раскладываются заново. */
    private static final int LAYOUT_VERSION = 2;

    /** Порядок снятия: как в жизни, плата и блок питания — последними. */
    private static final int[] REMOVE_ORDER = {
            SLOT_GPU, 14, 13, 12, 11, 10, 9, 6, 5, 4, 3, SLOT_COOLER, SLOT_CPU, SLOT_PSU, SLOT_MOTHERBOARD};

    /** Инвентарь корпуса: по одной детали в слоте. */
    private static class CaseInventory extends ItemStackHandler {
        private final PcCaseBlockEntity owner;

        CaseInventory(PcCaseBlockEntity owner) {
            super(SLOT_COUNT);
            this.owner = owner;
        }

        @Override
        protected void onContentsChanged(int slot) {
            owner.markUpdated();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        /** Положить предмет без оповещений (используется при загрузке). */
        void setDirect(int slot, ItemStack stack) {
            stacks.set(slot, stack);
        }
    }

    private final CaseInventory inventory = new CaseInventory(this);

    public PcCaseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PC_CASE.get(), pos, state);
    }

    public ItemStack getStack(int slot) {
        return inventory.getStackInSlot(slot);
    }

    /** Данные установленной платы или null, если её нет. */
    private MotherboardData motherboard() {
        ItemStack board = getStack(SLOT_MOTHERBOARD);
        return board.getItem() instanceof MotherboardItem ? MotherboardItem.data(board) : null;
    }

    // ======================= Установка и снятие =======================

    public Component tryInstall(ItemStack held, Player player) {
        if (!(held.getItem() instanceof HardwareItem hardware)) {
            return error("message.minepc.not_hardware");
        }
        HardwareType type = hardware.getHardwareType();
        MotherboardData board = motherboard();

        // Почти всё крепится к материнской плате
        if (board == null && type != HardwareType.MOTHERBOARD && type != HardwareType.PSU) {
            return error("message.minepc.need_motherboard");
        }
        if (type == HardwareType.COOLER && getStack(SLOT_CPU).isEmpty()) {
            return error("message.minepc.need_cpu");
        }
        if (type == HardwareType.CPU) {
            CpuData cpu = CpuItem.data(held);
            if (cpu.socket() != board.socket()) {
                return error("message.minepc.wrong_socket",
                        Component.translatable("param.minepc.cpu.socket." + cpu.socket()),
                        Component.translatable("param.minepc.cpu.socket." + board.socket()));
            }
            if (cpu.tier() > board.maxCpuTier()) {
                return error("message.minepc.cpu_unsupported", board.maxCpuTier());
            }
        }

        int slot = switch (type) {
            case MOTHERBOARD -> freeSlot(SLOT_MOTHERBOARD, 1);
            case CPU -> freeSlot(SLOT_CPU, 1);
            case COOLER -> freeSlot(SLOT_COOLER, 1);
            case RAM -> freeSlot(SLOT_RAM, Math.min(RAM_SLOTS, board.ramSlots()));
            case GPU -> freeSlot(SLOT_GPU, 1);
            case PSU -> freeSlot(SLOT_PSU, 1);
            case HDD -> sataPortFree(board) ? freeSlot(SLOT_HDD, HDD_SLOTS) : -2;
            case SSD -> SsdItem.data(held).isNvme()
                    ? (board.m2Slots() == 0 ? -3 : freeSlot(SLOT_M2, Math.min(M2_SLOTS, board.m2Slots())))
                    : (sataPortFree(board) ? freeSlot(SLOT_SATA_SSD, SATA_SSD_SLOTS) : -2);
        };
        if (slot == -2) {
            return error("message.minepc.no_sata", board.sataPorts());
        }
        if (slot == -3) {
            return error("message.minepc.no_m2");
        }
        if (slot < 0) {
            return error("message.minepc.no_slot");
        }

        inventory.setStackInSlot(slot, held.copyWithCount(1));
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        return Component.translatable("message.minepc.installed", getStack(slot).getHoverName())
                .withStyle(ChatFormatting.GREEN);
    }

    public Component removeLast(Player player) {
        for (int slot : REMOVE_ORDER) {
            ItemStack stack = getStack(slot);
            if (!stack.isEmpty()) {
                Component name = stack.getHoverName();
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                return Component.translatable("message.minepc.removed", name).withStyle(ChatFormatting.YELLOW);
            }
        }
        return Component.translatable("message.minepc.empty").withStyle(ChatFormatting.GRAY);
    }

    private int freeSlot(int first, int count) {
        for (int slot = first; slot < first + count; slot++) {
            if (getStack(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /** Жёсткие диски и SATA SSD занимают порты SATA на плате. */
    private int sataDevices() {
        int count = 0;
        for (int i = 0; i < HDD_SLOTS; i++) if (!getStack(SLOT_HDD + i).isEmpty()) count++;
        for (int i = 0; i < SATA_SSD_SLOTS; i++) if (!getStack(SLOT_SATA_SSD + i).isEmpty()) count++;
        return count;
    }

    private boolean sataPortFree(MotherboardData board) {
        return sataDevices() < board.sataPorts();
    }

    private static MutableComponent error(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.RED);
    }

    // ======================= Отчёт о сборке =======================

    public void sendStatus(Player player) {
        player.sendSystemMessage(Component.translatable("message.minepc.status.header").withStyle(ChatFormatting.AQUA));

        int power = 0;
        int ramMb = 0;
        int ramMhz = Integer.MAX_VALUE;
        int ramSticks = 0;
        boolean any = false;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = getStack(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof HardwareItem hardware)) {
                continue;
            }
            any = true;
            power += hardware.getPowerDraw(stack);
            if (stack.getItem() instanceof RamItem) {
                ramMb += RamItem.data(stack).capacityMb();
                ramMhz = Math.min(ramMhz, RamItem.data(stack).speedMhz());
                ramSticks++;
            }
            player.sendSystemMessage(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(stack.getHoverName()));
        }
        if (!any) {
            player.sendSystemMessage(Component.translatable("message.minepc.empty").withStyle(ChatFormatting.GRAY));
            return;
        }

        MotherboardData board = motherboard();
        if (ramMb > 0) {
            int effective = board != null ? Math.min(ramMhz, board.maxRamMhz()) : ramMhz;
            MutableComponent line = Component.translatable("message.minepc.status.ram",
                    Units.size(ramMb), Units.frequency(effective)).withStyle(ChatFormatting.GRAY);
            if (board != null && board.maxRamMhz() < ramMhz) {
                line.append(Component.translatable("message.minepc.status.ram_limited").withStyle(ChatFormatting.GOLD));
            }
            player.sendSystemMessage(line);
            if (board != null) {
                // Режим каналов: сколько каналов реально задействовано установленными планками
                int mode = Math.min(board.channels(), ramSticks);
                player.sendSystemMessage(Component.translatable("message.minepc.status.channels", mode)
                        .withStyle(mode >= board.channels() ? ChatFormatting.GRAY : ChatFormatting.GOLD));
            }
        }
        player.sendSystemMessage(powerStatus(power));
        player.sendSystemMessage(coolingStatus());
        Component gpu = gpuCoolingStatus();
        if (gpu != null) {
            player.sendSystemMessage(gpu);
        }
    }

    /** Хватает ли мощности блока питания. */
    private Component powerStatus(int draw) {
        ItemStack psu = getStack(SLOT_PSU);
        if (!(psu.getItem() instanceof PsuItem)) {
            return Component.translatable("message.minepc.power.none", Units.watts(draw)).withStyle(ChatFormatting.RED);
        }
        int max = PsuItem.data(psu).wattage();
        int percent = max > 0 ? draw * 100 / max : 999;
        if (draw > max) {
            return Component.translatable("message.minepc.power.overload", Units.watts(draw), Units.watts(max))
                    .withStyle(ChatFormatting.RED);
        }
        ChatFormatting color = percent > 85 ? ChatFormatting.GOLD : ChatFormatting.GREEN;
        String key = percent > 85 ? "message.minepc.power.high" : "message.minepc.power.ok";
        return Component.translatable(key, Units.watts(draw), Units.watts(max), percent).withStyle(color);
    }

    /** Хватает ли кулера на тепло процессора. */
    private Component coolingStatus() {
        ItemStack cpu = getStack(SLOT_CPU);
        ItemStack cooler = getStack(SLOT_COOLER);
        if (!(cpu.getItem() instanceof HardwareItem cpuItem)) {
            return Component.translatable("message.minepc.cooling.no_cpu").withStyle(ChatFormatting.GRAY);
        }
        int heat = cpuItem.getHeat(cpu);
        if (!(cooler.getItem() instanceof CoolerItem)) {
            return Component.translatable("message.minepc.cooling.none", Units.watts(heat)).withStyle(ChatFormatting.RED);
        }
        int capacity = CoolerItem.data(cooler).coolingW();
        if (heat > capacity) {
            return Component.translatable("message.minepc.cooling.weak", Units.watts(heat), Units.watts(capacity))
                    .withStyle(ChatFormatting.GOLD);
        }
        return Component.translatable("message.minepc.cooling.ok", Units.watts(heat), Units.watts(capacity))
                .withStyle(ChatFormatting.GREEN);
    }

    /** Хватает ли видеокарте её собственного охлаждения. */
    private Component gpuCoolingStatus() {
        ItemStack gpu = getStack(SLOT_GPU);
        if (!(gpu.getItem() instanceof GpuItem)) {
            return null;
        }
        GpuData d = GpuItem.data(gpu);
        if (!d.hasCooler()) {
            return Component.translatable("message.minepc.gpu.none", Units.watts(d.tdp())).withStyle(ChatFormatting.RED);
        }
        if (d.tdp() > d.coolingW()) {
            return Component.translatable("message.minepc.gpu.weak", Units.watts(d.tdp()), Units.watts(d.coolingW()))
                    .withStyle(ChatFormatting.GOLD);
        }
        return Component.translatable("message.minepc.gpu.ok", Units.watts(d.tdp()), Units.watts(d.coolingW()))
                .withStyle(ChatFormatting.GREEN);
    }

    // ======================= Служебное =======================

    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = getStack(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack.copy());
            }
        }
    }

    /** Сохранить изменения и отправить их игрокам, чтобы детали отрисовались. */
    private void markUpdated() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putInt("layout", LAYOUT_VERSION);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ItemStackHandler saved = new ItemStackHandler();
        saved.deserializeNBT(registries, tag.getCompound("inventory"));
        boolean sameLayout = tag.getInt("layout") == LAYOUT_VERSION && saved.getSlots() == SLOT_COUNT;

        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            inventory.setDirect(slot, sameLayout ? saved.getStackInSlot(slot) : ItemStack.EMPTY);
        }
        if (!sameLayout) {
            // Сохранение из старой версии мода: раскладываем детали по новым слотам
            for (int i = 0; i < saved.getSlots(); i++) {
                ItemStack stack = saved.getStackInSlot(i);
                if (stack.getItem() instanceof HardwareItem hardware) {
                    int slot = legacySlot(hardware.getHardwareType(), stack);
                    if (slot >= 0) {
                        inventory.setDirect(slot, stack);
                    }
                }
            }
        }
    }

    private int legacySlot(HardwareType type, ItemStack stack) {
        return switch (type) {
            case MOTHERBOARD -> freeSlot(SLOT_MOTHERBOARD, 1);
            case CPU -> freeSlot(SLOT_CPU, 1);
            case COOLER -> freeSlot(SLOT_COOLER, 1);
            case RAM -> freeSlot(SLOT_RAM, RAM_SLOTS);
            case GPU -> freeSlot(SLOT_GPU, 1);
            case PSU -> freeSlot(SLOT_PSU, 1);
            case HDD -> freeSlot(SLOT_HDD, HDD_SLOTS);
            case SSD -> SsdItem.data(stack).isNvme() ? freeSlot(SLOT_M2, M2_SLOTS) : freeSlot(SLOT_SATA_SSD, SATA_SSD_SLOTS);
        };
    }

    // Эти два метода отправляют содержимое на клиент, чтобы его можно было нарисовать
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
