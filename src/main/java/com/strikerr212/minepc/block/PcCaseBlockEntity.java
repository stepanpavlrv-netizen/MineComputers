package com.strikerr212.minepc.block;

import com.strikerr212.minepc.hardware.CpuData;
import com.strikerr212.minepc.hardware.GpuData;
import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.HddData;
import com.strikerr212.minepc.hardware.MotherboardData;
import com.strikerr212.minepc.hardware.RamData;
import com.strikerr212.minepc.hardware.SsdData;
import com.strikerr212.minepc.hardware.SystemModel;
import com.strikerr212.minepc.hardware.Units;
import com.strikerr212.minepc.item.CoolerItem;
import com.strikerr212.minepc.item.CpuItem;
import com.strikerr212.minepc.item.GpuItem;
import com.strikerr212.minepc.item.HardwareItem;
import com.strikerr212.minepc.item.HddItem;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    /** Собрать паспорта установленных деталей для расчёта производительности. */
    private SystemModel.Input collectInput() {
        int power = 0;
        List<RamData> ram = new ArrayList<>();
        List<HddData> hdds = new ArrayList<>();
        List<SsdData> ssds = new ArrayList<>();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = getStack(slot);
            if (!(stack.getItem() instanceof HardwareItem hardware)) continue;
            power += hardware.getPowerDraw(stack);
            if (stack.getItem() instanceof RamItem) ram.add(RamItem.data(stack));
            if (stack.getItem() instanceof HddItem) hdds.add(HddItem.data(stack));
            if (stack.getItem() instanceof SsdItem) ssds.add(SsdItem.data(stack));
        }
        ItemStack cpu = getStack(SLOT_CPU), cooler = getStack(SLOT_COOLER), gpu = getStack(SLOT_GPU), psu = getStack(SLOT_PSU);
        return new SystemModel.Input(motherboard(),
                cpu.getItem() instanceof CpuItem ? CpuItem.data(cpu) : null,
                cooler.getItem() instanceof CoolerItem ? CoolerItem.data(cooler) : null,
                ram,
                gpu.getItem() instanceof GpuItem ? GpuItem.data(gpu) : null,
                psu.getItem() instanceof PsuItem ? PsuItem.data(psu) : null,
                hdds, ssds, power);
    }

    public void sendStatus(Player player) {
        player.sendSystemMessage(Component.translatable("message.minepc.status.header").withStyle(ChatFormatting.AQUA));

        boolean any = false;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = getStack(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof HardwareItem)) continue;
            any = true;
            player.sendSystemMessage(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(stack.getHoverName()));
        }
        if (!any) {
            player.sendSystemMessage(Component.translatable("message.minepc.empty").withStyle(ChatFormatting.GRAY));
            return;
        }

        SystemModel.Input in = collectInput();
        SystemModel.Report r = SystemModel.evaluate(in);

        // ----- Что реально выдаёт каждая часть -----
        player.sendSystemMessage(Component.translatable("message.minepc.status.perf").withStyle(ChatFormatting.AQUA));
        if (in.cpu() != null) {
            boolean reduced = r.cpuThrottle() < 1 || r.cpuMemory() < 0.85;
            player.sendSystemMessage(perf("message.minepc.status.cpu", reduced, Units.ops(r.cpuGips()), Units.level(r.cpuLevel())));
        }
        if (in.gpu() != null) {
            player.sendSystemMessage(perf("message.minepc.status.gpu", r.gpuThrottle() < 1,
                    Units.tflops(r.gpuTflops()), Units.level(r.gpuLevel())));
        }
        if (r.ramMb() > 0) {
            MutableComponent line = perf("message.minepc.status.ram", r.channels() < (in.board() != null ? in.board().channels() : 1),
                    Units.size(r.ramMb()), Units.frequency(r.ramMts()), r.channels(), Units.bandwidth(r.ramGbs()));
            if (hasNote(r, SystemModel.Issue.RAM_LIMITED)) {
                line.append(Component.translatable("message.minepc.status.ram_limited").withStyle(ChatFormatting.GOLD));
            }
            player.sendSystemMessage(line);
        }
        if (r.bootSeconds() > 0) {
            player.sendSystemMessage(perf("message.minepc.status.boot", r.bootSeconds() > 20, Units.seconds(r.bootSeconds()),
                    Component.translatable(r.bootFromSsd() ? "message.minepc.status.boot_ssd" : "message.minepc.status.boot_hdd")));
        }

        // ----- Питание и охлаждение -----
        player.sendSystemMessage(powerStatus(in.powerDraw()));
        if (r.wallWatts() > 0) {
            player.sendSystemMessage(Component.translatable("message.minepc.power.wall",
                    Units.watts(r.wallWatts()), Units.watts(r.wallWatts() - in.powerDraw())).withStyle(ChatFormatting.DARK_GRAY));
        }
        player.sendSystemMessage(coolingStatus(r));
        Component gpu = gpuCoolingStatus(r);
        if (gpu != null) player.sendSystemMessage(gpu);

        // ----- Итог -----
        if (r.bootable()) {
            player.sendSystemMessage(Component.translatable("message.minepc.status.score",
                    Component.literal(String.valueOf(r.score())).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                    .withStyle(ChatFormatting.AQUA));
        } else {
            player.sendSystemMessage(Component.translatable("message.minepc.status.no_boot").withStyle(ChatFormatting.RED));
        }
        boolean warned = false;
        for (SystemModel.Note n : r.notes()) {
            // Эти замечания уже видны в строках выше
            if (n.issue() == SystemModel.Issue.RAM_LIMITED || n.issue() == SystemModel.Issue.PSU_HIGH) continue;
            if (!n.issue().critical && !r.bootable()) continue;   // сначала пусть запустится
            player.sendSystemMessage(Component.literal(" ! ").append(noteText(n))
                    .withStyle(n.issue().critical ? ChatFormatting.RED : ChatFormatting.GOLD));
            warned = true;
        }
        if (!warned && r.bootable()) {
            player.sendSystemMessage(Component.translatable("message.minepc.status.balanced").withStyle(ChatFormatting.GREEN));
        }
    }

    private static boolean hasNote(SystemModel.Report r, SystemModel.Issue issue) {
        return r.notes().stream().anyMatch(n -> n.issue() == issue);
    }

    private static MutableComponent perf(String key, boolean reduced, Object... args) {
        return Component.translatable(key, args).withStyle(reduced ? ChatFormatting.GOLD : ChatFormatting.GRAY);
    }

    private static Component noteText(SystemModel.Note n) {
        String key = "message.minepc.note." + n.issue().name().toLowerCase(Locale.ROOT);
        return switch (n.issue()) {
            case MEMORY_BOUND -> Component.translatable(key, Math.round(n.a()));
            case CHANNELS -> Component.translatable(key, (int) n.a(), (int) n.b());
            case MIXED_RAM -> Component.translatable(key, Units.frequency((int) n.a()));
            case CPU_BOTTLENECK, GPU_BOTTLENECK -> Component.translatable(key,
                    String.format(Locale.ROOT, "%.1f", n.a()), String.format(Locale.ROOT, "%.1f", n.b()));
            case PSU_OVERLOAD, PSU_HIGH -> Component.translatable(key, Math.round(n.a()));
            case RAM_LIMITED -> Component.translatable(key, Units.frequency((int) n.a()));
            case SLOW_BOOT -> Component.translatable(key, Units.seconds(n.a()));
            default -> Component.translatable(key);
        };
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

    private static int pct(double fraction) {
        return (int) Math.round(fraction * 100);
    }

    /** Хватает ли кулера на тепло процессора и сколько частоты он держит. */
    private Component coolingStatus(SystemModel.Report r) {
        ItemStack cpu = getStack(SLOT_CPU);
        ItemStack cooler = getStack(SLOT_COOLER);
        if (!(cpu.getItem() instanceof HardwareItem cpuItem)) {
            return Component.translatable("message.minepc.cooling.no_cpu").withStyle(ChatFormatting.GRAY);
        }
        int heat = cpuItem.getHeat(cpu);
        if (!(cooler.getItem() instanceof CoolerItem)) {
            if (r.cpuThrottle() >= 1) {
                return Component.translatable("message.minepc.cooling.passive", Units.watts(heat)).withStyle(ChatFormatting.GREEN);
            }
            return Component.translatable("message.minepc.cooling.none", Units.watts(heat), pct(r.cpuThrottle()))
                    .withStyle(ChatFormatting.RED);
        }
        int capacity = CoolerItem.data(cooler).coolingW();
        if (heat > capacity) {
            return Component.translatable("message.minepc.cooling.weak", Units.watts(heat), Units.watts(capacity),
                    pct(r.cpuThrottle())).withStyle(ChatFormatting.GOLD);
        }
        return Component.translatable("message.minepc.cooling.ok", Units.watts(heat), Units.watts(capacity))
                .withStyle(ChatFormatting.GREEN);
    }

    /** Хватает ли видеокарте её собственного охлаждения. */
    private Component gpuCoolingStatus(SystemModel.Report r) {
        ItemStack gpu = getStack(SLOT_GPU);
        if (!(gpu.getItem() instanceof GpuItem)) {
            return null;
        }
        GpuData d = GpuItem.data(gpu);
        if (!d.hasCooler()) {
            if (r.gpuThrottle() >= 1) {
                return Component.translatable("message.minepc.gpu.passive", Units.watts(d.tdp())).withStyle(ChatFormatting.GREEN);
            }
            return Component.translatable("message.minepc.gpu.none", Units.watts(d.tdp()), pct(r.gpuThrottle()))
                    .withStyle(ChatFormatting.RED);
        }
        if (d.tdp() > d.coolingW()) {
            return Component.translatable("message.minepc.gpu.weak", Units.watts(d.tdp()), Units.watts(d.coolingW()),
                    pct(r.gpuThrottle())).withStyle(ChatFormatting.GOLD);
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
