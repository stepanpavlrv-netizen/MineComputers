package com.strikerr212.minepc.block;

import com.strikerr212.minepc.hardware.CoolerData;
import com.strikerr212.minepc.hardware.CpuData;
import com.strikerr212.minepc.hardware.PsuData;
import com.strikerr212.minepc.hardware.GpuData;
import com.strikerr212.minepc.hardware.HardwareType;
import com.strikerr212.minepc.hardware.HddData;
import com.strikerr212.minepc.hardware.MotherboardData;
import com.strikerr212.minepc.hardware.Perf;
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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import com.strikerr212.minepc.item.DriveItem;
import com.strikerr212.minepc.menu.PcCaseMenu;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Мозг" системного блока: хранит установленные детали, проверяет совместимость
 * и управляет компьютером (см. {@link CaseComputer}).
 */
public class PcCaseBlockEntity extends BlockEntity implements com.strikerr212.minepc.lan.CordList.Holder, MenuProvider {

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
    public static final int SLOT_USB = 15;         // 15..16 — USB-порты на передней панели
    public static final int USB_SLOTS = 2;
    // ----- Серверное шасси (модуль стойки, серверный корпус) — в конце, чтобы старые номера не сдвинулись -----
    public static final int SLOT_CPU2 = 17;        // второй сокет серверной платы
    public static final int SLOT_PSU2 = 18;        // второй блок питания (резерв)
    public static final int SLOT_RAM_EXTRA = 19;   // 19..30 — слоты памяти 5–16 серверной платы
    public static final int RAM_EXTRA_SLOTS = 12;
    /** Всего слотов памяти (4 обычных + 12 серверных). */
    public static final int MAX_RAM_SLOTS = RAM_SLOTS + RAM_EXTRA_SLOTS;
    public static final int SLOT_BAY_EXTRA = 31;   // 31..34 — отсеки дисков 5–8 (сервер 2U, серверный корпус)
    public static final int BAY_EXTRA_SLOTS = 4;
    /** Отсеков дисков у самого большого шасси. */
    public static final int MAX_BAYS = HDD_SLOTS + SATA_SSD_SLOTS + BAY_EXTRA_SLOTS;
    public static final int SLOT_COUNT = 35;
    /** Охлаждение серверного шасси без кулера: стена вентиляторов поперёк корпуса, Вт отвода на процессор. */
    public static final int FAN_WALL_W = 260;

    /** Номер слота i-й планки памяти (0..15). */
    public static int ramSlot(int i) {
        return i < RAM_SLOTS ? SLOT_RAM + i : SLOT_RAM_EXTRA + i - RAM_SLOTS;
    }

    /** Номер планки (0..15) для слота или -1. */
    public static int ramIndex(int slot) {
        if (slot >= SLOT_RAM && slot < SLOT_RAM + RAM_SLOTS) return slot - SLOT_RAM;
        if (slot >= SLOT_RAM_EXTRA && slot < SLOT_RAM_EXTRA + RAM_EXTRA_SLOTS) return slot - SLOT_RAM_EXTRA + RAM_SLOTS;
        return -1;
    }

    /** Слот отсека i (0..7): у обычного корпуса 0–1 — жёсткие диски, 2–3 — SATA SSD; у сервера любой диск в любой. */
    public static int baySlot(int i) {
        return i < HDD_SLOTS + SATA_SSD_SLOTS ? SLOT_HDD + i : SLOT_BAY_EXTRA + i - HDD_SLOTS - SATA_SSD_SLOTS;
    }

    /** Номер отсека (0..7) для слота или -1. */
    public static int bayIndex(int slot) {
        if (slot >= SLOT_HDD && slot < SLOT_SATA_SSD + SATA_SSD_SLOTS) return slot - SLOT_HDD;
        if (slot >= SLOT_BAY_EXTRA && slot < SLOT_BAY_EXTRA + BAY_EXTRA_SLOTS) return slot - SLOT_BAY_EXTRA + HDD_SLOTS + SATA_SSD_SLOTS;
        return -1;
    }

    public static boolean isDriveBay(int slot) {
        return bayIndex(slot) >= 0;
    }

    /** Версия раскладки: если сохранение старое, детали раскладываются заново. */
    private static final int LAYOUT_VERSION = 5;

    /** Порядок снятия: как в жизни, плата и блок питания — последними. */
    private static final int[] REMOVE_ORDER = {
            16, 15, SLOT_GPU, 14, 13, 34, 33, 32, 31, 12, 11, 10, 9, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 6, 5, 4, 3,
            SLOT_COOLER, SLOT_CPU2, SLOT_CPU, SLOT_PSU2, SLOT_PSU, SLOT_MOTHERBOARD};

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
            owner.slotChanged(slot);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return owner.canPlace(slot, stack);
        }

        /** Деталь нельзя вынуть, если на ней что-то держится (плата под процессором, процессор под кулером). */
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!owner.canTake(slot)) return ItemStack.EMPTY;
            return super.extractItem(slot, amount, simulate);
        }

        /** Положить предмет без оповещений (используется при загрузке). */
        void setDirect(int slot, ItemStack stack) {
            stacks.set(slot, stack);
        }
    }

    private final CaseInventory inventory = new CaseInventory(this);
    private final CaseComputer pc = new CaseComputer(this);
    private final Bmc bmc = new Bmc(this);
    /** Модуль серверной стойки: не стоит в мире сам, живёт внутри {@link RackBlockEntity}; slot — место в стойке. */
    private final boolean module;
    private final int rackSlot;

    public PcCaseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PC_CASE.get(), pos, state);
        this.module = false;
        this.rackSlot = 0;
    }

    /** Сервер в стойке на месте slot (0 — нижний модуль). */
    public PcCaseBlockEntity(BlockPos rackPos, BlockState state, int slot) {
        super(ModBlockEntities.PC_CASE.get(), rackPos, state);
        this.module = true;
        this.rackSlot = slot;
    }

    /** BMC серверной платы (у обычной платы он есть как объект, но не работает: {@link Bmc#present()}). */
    public Bmc bmc() {
        return bmc;
    }

    CaseComputer caseComputer() {
        return pc;
    }

    /** Износить диск в слоте (как от долгой работы): для проверок и команд администратора. Только у работающего. */
    public void wearDisk(int slot, double add) {
        ItemStack d = getStack(slot);
        if (!d.isEmpty() && computerState() == 1) pc.wearDisk(slot, d, add);
    }

    /** На клиенте: какой отсек восстанавливает RAID (приходит с сервера). */
    private int clientRebuild = -1;

    /** Какой отсек сейчас восстанавливается массивом RAID (-1 — никакой): лампа на модуле мигает. */
    public int rebuildingBay() {
        return level != null && level.isClientSide ? clientRebuild : pc.rebuildBay();
    }

    public boolean isModule() {
        return module;
    }

    public int moduleSlot() {
        return rackSlot;
    }

    /** Кто это в сети, на экране и в меню: блок и место в нём. */
    public com.strikerr212.minepc.block.HostRef hostRef() {
        return new com.strikerr212.minepc.block.HostRef(worldPosition, rackSlot);
    }

    /** Модуль стойки: вызывать вместо выгрузки чанка или удаления блока. */
    public void unloadModule() {
        pc.unload();
    }

    public ItemStack getStack(int slot) {
        return inventory.getStackInSlot(slot);
    }

    public IItemHandler inventory() {
        return inventory;
    }

    /** Данные установленной платы или null, если её нет. */
    public MotherboardData motherboard() {
        ItemStack board = getStack(SLOT_MOTHERBOARD);
        return board.getItem() instanceof MotherboardItem ? MotherboardItem.data(board) : null;
    }

    // ======================= Правила слотов (для окна корпуса) =======================

    private boolean anyIn(int first, int count, int except) {
        for (int i = first; i < first + count; i++) if (i != except && !getStack(i).isEmpty()) return true;
        return false;
    }

    /**
     * Серверное шасси: модуль стойки или серверный корпус. Только в него встаёт серверная плата и второй блок питания;
     * без кулера процессоры охлаждает стена вентиляторов; диски и блоки питания меняются на ходу.
     */
    public boolean serverChassis() {
        return module || style() == CaseStyle.SERVER;
    }

    /** Модуль 2U: выше, восемь отсеков дисков, мощнее стена вентиляторов. */
    private boolean tall;

    public void setTall(boolean tall) {
        this.tall = tall;
    }

    public boolean isTall() {
        return tall;
    }

    /** Сколько отсеков дисков: модуль 1U — 4, модуль 2U и серверный корпус — 8, обычный корпус — 4 (2 HDD + 2 SSD). */
    public int bayCount() {
        if (module) return tall ? 8 : 4;
        return serverChassis() ? 8 : 4;
    }

    private boolean running() {
        return displayState() == 1;
    }

    /**
     * Слот закрыт, пока сервер работает: плату, процессоры, память, видеокарту и M.2 меняют только у выключенного.
     * Диски в отсеках, блоки питания (если второй держит нагрузку) и флешки — на ходу.
     * У обычного компьютера слоты не закрываются: замена детали его просто выключает.
     */
    public boolean lockedWhileRunning(int slot) {
        if (!serverChassis() || !running()) return false;
        if (slot >= SLOT_USB && slot < SLOT_USB + USB_SLOTS) return false;
        if (isDriveBay(slot)) return false;
        if (slot == SLOT_PSU || slot == SLOT_PSU2) return false;
        return true;
    }

    /** Можно ли положить предмет в этот слот: подходит ли вид детали и совместимость. */
    public boolean canPlace(int slot, ItemStack stack) {
        return placeError(slot, stack) == null;
    }

    /** Почему предмет не встаёт в слот (ключ перевода, без аргументов) или null — встаёт. */
    public String placeError(int slot, ItemStack stack) {
        if (stack.isEmpty()) return "message.minepc.not_hardware";
        if (lockedWhileRunning(slot)) return "message.minepc.server_running";
        if (slot >= SLOT_USB && slot < SLOT_USB + USB_SLOTS) {
            // В сервере в стойке Wi-Fi не нужен (и не поймает сквозь железо) — только флешки
            return stack.getItem() instanceof DriveItem d && d.kind() == DriveItem.Kind.FLASH
                    || !module && stack.getItem() instanceof com.strikerr212.minepc.lan.WifiAdapterItem ? null : "message.minepc.no_slot";
        }
        if (!(stack.getItem() instanceof HardwareItem hw)) return "message.minepc.not_hardware";
        HardwareType type = hw.getHardwareType();
        if (slot == SLOT_PSU) return type == HardwareType.PSU ? null : "message.minepc.no_slot";
        if (slot == SLOT_PSU2) {
            if (type != HardwareType.PSU) return "message.minepc.no_slot";
            return serverChassis() ? null : "message.minepc.need_server_chassis";
        }
        if (slot == SLOT_MOTHERBOARD) {
            if (type != HardwareType.MOTHERBOARD) return "message.minepc.no_slot";
            return MotherboardItem.data(stack).server() && !serverChassis() ? "message.minepc.need_server_chassis" : null;
        }
        MotherboardData board = motherboard();
        if (board == null) return "message.minepc.need_motherboard";
        if (slot == SLOT_CPU || slot == SLOT_CPU2) {
            if (type != HardwareType.CPU) return "message.minepc.no_slot";
            CpuData cpu = CpuItem.data(stack);
            if (cpu.socket() != board.socket()) return "message.minepc.wrong_socket_short";
            if (cpu.tier() > board.maxCpuTier()) return "message.minepc.cpu_unsupported_short";
            if (slot == SLOT_CPU2) {
                if (board.cpuSockets() < 2) return "message.minepc.one_socket";
                ItemStack first = getStack(SLOT_CPU);
                if (first.isEmpty()) return "message.minepc.need_cpu1";
                if (!sameDesign(first, stack)) return "message.minepc.cpu_mismatch";
            }
            return null;
        }
        if (slot == SLOT_COOLER) {
            if (type != HardwareType.COOLER) return "message.minepc.no_slot";
            if (module) return "message.minepc.no_cooler_1u";   // в 1U башня не влезет — там стена вентиляторов
            return getStack(SLOT_CPU).isEmpty() ? "message.minepc.need_cpu" : null;
        }
        int ram = ramIndex(slot);
        if (ram >= 0) {
            if (type != HardwareType.RAM) return "message.minepc.no_slot";
            if (ram >= board.ramSlots()) return "message.minepc.no_slot";
            RamData r = RamItem.data(stack);
            if (board.server() && !r.ecc()) return "message.minepc.need_ecc";
            if (!board.server() && r.ecc()) return "message.minepc.ecc_server_only";
            return null;
        }
        if (slot == SLOT_GPU) return type == HardwareType.GPU ? null : "message.minepc.no_slot";
        int bay = bayIndex(slot);
        if (bay >= 0) {
            if (bay >= bayCount()) return "message.minepc.no_slot";
            boolean hdd = type == HardwareType.HDD, sata = type == HardwareType.SSD && !SsdItem.data(stack).isNvme();
            // У сервера отсеки универсальные (салазки под любой диск), у обычного корпуса — корзина HDD и крепления SSD
            boolean fits = serverChassis() ? hdd || sata : slot < SLOT_SATA_SSD ? hdd : sata;
            if (!fits) return "message.minepc.no_slot";
            return sataFreeFor(board, slot) ? null : "message.minepc.no_sata_short";
        }
        if (slot >= SLOT_M2 && slot < SLOT_M2 + M2_SLOTS) {
            if (type != HardwareType.SSD || !SsdItem.data(stack).isNvme()) return "message.minepc.no_slot";
            return slot - SLOT_M2 < board.m2Slots() ? null : "message.minepc.no_m2";
        }
        return "message.minepc.no_slot";
    }

    /** Одинаковые ли детали по устройству (надпись не важна): два процессора сервера должны совпадать. */
    private static boolean sameDesign(ItemStack a, ItemStack b) {
        var da = a.get(com.strikerr212.minepc.registry.ModDataComponents.DESIGN.get());
        var db = b.get(com.strikerr212.minepc.registry.ModDataComponents.DESIGN.get());
        if (da == null || db == null) return da == db && a.getItem() == b.getItem();
        return da.values().equals(db.values());
    }

    /** Свободен ли порт SATA для устройства в слоте slot (сам слот не считаем). */
    private boolean sataFreeFor(MotherboardData board, int slot) {
        int used = 0;
        for (int i = 0; i < MAX_BAYS; i++) if (baySlot(i) != slot && !getStack(baySlot(i)).isEmpty()) used++;
        return used < board.sataPorts();
    }

    /** Можно ли вынуть деталь: плату — только когда на ней ничего нет, процессор — без кулера. */
    public boolean canTake(int slot) {
        return takeError(slot) == null;
    }

    /** Почему деталь не вынуть (ключ перевода) или null. */
    public String takeError(int slot) {
        if (lockedWhileRunning(slot)) return "message.minepc.server_running";
        if (slot == SLOT_MOTHERBOARD) {
            boolean free = getStack(SLOT_CPU).isEmpty() && getStack(SLOT_CPU2).isEmpty() && getStack(SLOT_GPU).isEmpty()
                    && !anyIn(SLOT_RAM, RAM_SLOTS, -1) && !anyIn(SLOT_RAM_EXTRA, RAM_EXTRA_SLOTS, -1)
                    && !anyIn(SLOT_HDD, HDD_SLOTS + SATA_SSD_SLOTS + M2_SLOTS, -1) && !anyIn(SLOT_BAY_EXTRA, BAY_EXTRA_SLOTS, -1);
            return free ? null : "message.minepc.board_busy";
        }
        if (slot == SLOT_CPU) {
            if (!getStack(SLOT_COOLER).isEmpty()) return "message.minepc.cpu_under_cooler";
            if (!getStack(SLOT_CPU2).isEmpty()) return "message.minepc.cpu2_first";
        }
        // Работающий сервер: блок питания вынимается, только если второй тянет нагрузку
        if ((slot == SLOT_PSU || slot == SLOT_PSU2) && serverChassis() && running()) {
            int other = slot == SLOT_PSU ? SLOT_PSU2 : SLOT_PSU;
            ItemStack o = getStack(other);
            if (!(o.getItem() instanceof PsuItem) || PsuItem.data(o).wattage() < collectInput().powerDraw()) return "message.minepc.psu_last";
        }
        return null;
    }

    // ======================= Компьютер =======================

    public static void serverTick(Level level, BlockPos pos, BlockState state, PcCaseBlockEntity be) {
        if (level instanceof ServerLevel server) be.pc.tick(server, pos);
    }

    /** Кнопка питания. Возвращает сообщение для игрока или null. */
    public Component togglePower() {
        if (!(level instanceof ServerLevel server)) return null;
        boolean off = pc.stateCode() == 1;
        server.playSound(null, worldPosition, (off ? com.strikerr212.minepc.registry.ModSounds.POWER_OFF
                : com.strikerr212.minepc.registry.ModSounds.POWER_ON).get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1f);
        if (off) {
            pc.powerOff();
            bmc.log("питание выключено");
            return Component.translatable("message.minepc.power_off").withStyle(ChatFormatting.GRAY);
        }
        List<SystemModel.Note> reasons = pc.powerOn(server);
        if (reasons == null) {
            bmc.log("питание включено");
            return Component.translatable("message.minepc.power_on").withStyle(ChatFormatting.GREEN);
        }
        pc.errorBeeps(reasons);
        MutableComponent msg = Component.translatable("message.minepc.status.no_boot").withStyle(ChatFormatting.RED);
        for (SystemModel.Note n : reasons) msg.append(" ").append(noteText(n));
        bmc.log("не запустился:" + msg.getString().substring(msg.getString().indexOf(':') + 1));
        return msg;
    }

    /** 0 — выключен, 1 — работает, 2 — ошибка. */
    public int computerState() {
        return pc.stateCode();
    }

    public boolean hasKeyboard() {
        return pc.hasKeyboard();
    }

    /** Игрок нажал клавишу — стук для остальных. */
    public void keyClick(net.minecraft.world.entity.player.Player player) {
        pc.keyClick(player);
    }

    // ======================= Сеть =======================

    /** Постоянный адрес компьютера в сети (есть и на клиенте). */
    public String address() {
        return pc.getAddress();
    }

    /** Имя компьютера (hostname / label). */
    public String label() {
        return pc.label();
    }

    /** Пришло сообщение по сети (вызывает LanManager в тике сервера). */
    public void netReceive(String from, int port, int hops, Object[] payload, int bytes) {
        pc.netReceive(from, port, hops, payload, bytes);
    }

    /** Для окна корпуса: состояние порта и счётчики. */
    public int netLinkCode() { return pc.netLinkCode(); }
    public int netPeersCount() { return pc.netPeersCount(); }
    public int netKbSent() { return pc.netKbSent(); }
    public int netKbReceived() { return pc.netKbReceived(); }
    public int netWifiMbit() { return pc.wifiMbit(); }

    /** Загрузка процессора за последнюю секунду, 0..100 %. */
    public int loadPercent() {
        return pc.loadPercent();
    }

    /** Тематика корпуса (оформление окна). */
    public CaseStyle style() {
        if (module) return CaseStyle.SERVER;
        return getBlockState().getBlock() instanceof PcCaseBlock b ? b.style() : CaseStyle.CLASSIC;
    }

    /** Текст на экране компьютера (для тестов и отладки), "" если выключен. */
    public String screenText() {
        var c = pc.computer();
        if (c == null || c.state() == com.strikerr212.minepc.computer.Computer.State.OFF) return "";
        StringBuilder b = new StringBuilder();
        for (int y = 0; y < c.terminal().height(); y++) b.append(c.terminal().line(y).stripTrailing()).append('\n');
        return b.toString();
    }

    /** Ввод с клавиатуры от игрока (проверка расстояния и клавиатуры — в обработчике пакета). */
    public void input(int kind, String text, boolean ctrl, boolean shift) {
        pc.input(kind, text, ctrl, shift);
    }

    /** Мышь в окне экрана: нажатие, перетаскивание, отпускание, прокрутка. */
    public void mouse(int kind, int button, int x, int y) {
        pc.mouse(kind, button, x, y);
    }

    /** Кусок файла, перетащенного игроком в окно экрана. Возвращает сообщение, когда файл загружен целиком. */
    public Component upload(String name, int offset, int total, byte[] data) {
        return pc.upload(name, offset, total, data);
    }

    /** Игрок открыл экран компьютера: отправить ему кадр целиком. */
    public void requestFullFrame() {
        pc.requestFullFrame();
    }

    void stateChanged() {
        markUpdated();
    }

    /** Красный камень рядом изменился (включение по сигналу — если разрешено в BIOS). */
    public void redstone(boolean powered) {
        if (level instanceof ServerLevel server) pc.redstone(server, powered);
    }

    private long lastPartSound = -1;

    public static boolean isUsb(int slot) {
        return slot >= SLOT_USB && slot < SLOT_USB + USB_SLOTS;
    }

    private void slotChanged(int slot) {
        if (level == null || level.isClientSide) return;
        if (level.getGameTime() != lastPartSound) {
            // Деталь поставили или сняли — щелчок защёлки (флешка в USB — щелчок разъёма)
            lastPartSound = level.getGameTime();
            boolean empty = getStack(slot).isEmpty();
            var sound = isUsb(slot)
                    ? (empty ? com.strikerr212.minepc.registry.ModSounds.LAN_PLUG_OUT : com.strikerr212.minepc.registry.ModSounds.LAN_PLUG_IN)
                    : (empty ? com.strikerr212.minepc.registry.ModSounds.PART_REMOVE : com.strikerr212.minepc.registry.ModSounds.PART_INSTALL);
            level.playSound(null, worldPosition, sound.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.8f,
                    0.95f + level.random.nextFloat() * 0.1f);
        }
        if (slot == SLOT_MOTHERBOARD) com.strikerr212.minepc.lan.LanManager.updateDevice(level, worldPosition);
        if (isUsb(slot)) {
            if (level instanceof ServerLevel server) pc.updateRemovable(server, true);
        } else if (serverChassis() && (isDriveBay(slot) || slot == SLOT_PSU || slot == SLOT_PSU2)) {
            // Сервер: диски в отсеках и блоки питания меняются на ходу
            if (level instanceof ServerLevel server) pc.hotSwap(server, slot);
        } else {
            pc.partsChanged();
        }
    }

    private final com.strikerr212.minepc.lan.CordList cords = new com.strikerr212.minepc.lan.CordList();

    @Override
    public com.strikerr212.minepc.lan.CordList cords() {
        return cords;
    }

    /** Чанк загрузился (или корпус только что поставили): занести в схему сети. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) com.strikerr212.minepc.lan.LanManager.updateDevice(level, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        pc.unload();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        pc.unload();
    }

    // ----- Окно корпуса -----

    @Override
    public Component getDisplayName() {
        if (module) return Component.translatable("gui.minepc.server_module", rackSlot + 1);
        return Component.translatable("block.minepc.pc_case");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new PcCaseMenu(id, playerInventory, this);
    }

    // ======================= Установка и снятие =======================

    public Component tryInstall(ItemStack held, Player player) {
        if (held.getItem() instanceof DriveItem drive && drive.kind() == DriveItem.Kind.FLASH) {
            int usb = freeSlot(SLOT_USB, USB_SLOTS);
            if (usb < 0) return error("message.minepc.no_slot");
            inventory.setStackInSlot(usb, held.copyWithCount(1));
            if (!player.getAbilities().instabuild) held.shrink(1);
            return Component.translatable("message.minepc.installed", getStack(usb).getHoverName()).withStyle(ChatFormatting.GREEN);
        }
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
        if (type == HardwareType.MOTHERBOARD && MotherboardItem.data(held).server() && !serverChassis()) {
            return error("message.minepc.need_server_chassis");
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

        int[] candidates = switch (type) {
            case MOTHERBOARD -> new int[]{SLOT_MOTHERBOARD};
            case CPU -> new int[]{SLOT_CPU, SLOT_CPU2};
            case COOLER -> new int[]{SLOT_COOLER};
            case RAM -> java.util.stream.IntStream.range(0, MAX_RAM_SLOTS).map(PcCaseBlockEntity::ramSlot).toArray();
            case GPU -> new int[]{SLOT_GPU};
            case PSU -> new int[]{SLOT_PSU, SLOT_PSU2};
            case HDD -> serverChassis() ? allBays() : new int[]{SLOT_HDD, SLOT_HDD + 1};
            case SSD -> SsdItem.data(held).isNvme() ? new int[]{SLOT_M2, SLOT_M2 + 1}
                    : serverChassis() ? allBays() : new int[]{SLOT_SATA_SSD, SLOT_SATA_SSD + 1};
        };
        // Первое свободное место, куда деталь встаёт; иначе — причина с первого свободного места
        int slot = -1;
        String why = null;
        for (int c : candidates) {
            if (!getStack(c).isEmpty()) continue;
            String err = placeError(c, held);
            if (err == null) {
                slot = c;
                break;
            }
            if (why == null) why = err;
        }
        if (slot < 0 && why != null) {
            if (why.equals("message.minepc.no_sata_short")) return error("message.minepc.no_sata", board.sataPorts());
            return error(why);
        }
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
        for (int i = 0; i < MAX_BAYS; i++) if (!getStack(baySlot(i)).isEmpty()) count++;
        return count;
    }

    private int[] allBays() {
        int[] out = new int[bayCount()];
        for (int i = 0; i < out.length; i++) out[i] = baySlot(i);
        return out;
    }

    private boolean sataPortFree(MotherboardData board) {
        return sataDevices() < board.sataPorts();
    }

    private static MutableComponent error(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.RED);
    }

    // ======================= Отчёт о сборке =======================

    /** Собрать паспорта установленных деталей для расчёта производительности. */
    public SystemModel.Input collectInput() {
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
        ItemStack cpu = getStack(SLOT_CPU), cpu2 = getStack(SLOT_CPU2), cooler = getStack(SLOT_COOLER), gpu = getStack(SLOT_GPU);
        CpuData cpuData = cpu.getItem() instanceof CpuItem ? CpuItem.data(cpu) : null;
        int sockets = 1;
        if (cpuData != null && cpu2.getItem() instanceof CpuItem) {
            // Два одинаковых процессора работают как один с удвоенным числом ядер (и теплом)
            CpuData b = CpuItem.data(cpu2);
            cpuData = new CpuData(cpuData.tier(), cpuData.socket(), cpuData.cores() + b.cores(), cpuData.clockKhz(),
                    cpuData.boostPct(), cpuData.processNm(), cpuData.cacheKb() + b.cacheKb(), cpuData.tdp() + b.tdp(), cpuData.igpu());
            sockets = 2;
        }
        CoolerData coolerData = cooler.getItem() instanceof CoolerItem ? CoolerItem.data(cooler) : null;
        if (serverChassis()) {
            // Стена вентиляторов гонит воздух через радиаторы всех процессоров; кулер (в напольном корпусе) — в помощь
            int wall = FAN_WALL_W * sockets * (tall ? 3 : 2) / 2;   // в 2U вентиляторы крупнее
            if (coolerData == null || coolerData.coolingW() < wall) {
                coolerData = new CoolerData(coolerData == null ? 3 : coolerData.tier(), CoolerData.FAN_WALL, wall, 6 * sockets);
            }
        } else if (coolerData != null && sockets == 2) {
            coolerData = new CoolerData(coolerData.tier(), coolerData.kind(), coolerData.coolingW() * 2, coolerData.powerDraw());
        }
        return new SystemModel.Input(motherboard(), cpuData, coolerData, ram,
                gpu.getItem() instanceof GpuItem ? GpuItem.data(gpu) : null,
                activePsu(), hdds, ssds, power);
    }

    /**
     * Блок питания, который считается в сборке. С двумя блоками каждый должен один тянуть весь сервер,
     * поэтому считаем по более слабому.
     */
    public PsuData activePsu() {
        ItemStack a = getStack(SLOT_PSU), b = getStack(SLOT_PSU2);
        PsuData pa = a.getItem() instanceof PsuItem ? PsuItem.data(a) : null;
        PsuData pb = b.getItem() instanceof PsuItem ? PsuItem.data(b) : null;
        if (pa == null) return pb;
        if (pb == null) return pa;
        return pa.wattage() <= pb.wattage() ? pa : pb;
    }

    /** Сколько блоков питания стоит (у сервера с одним — нет резерва). */
    public int psuCount() {
        return (getStack(SLOT_PSU).isEmpty() ? 0 : 1) + (getStack(SLOT_PSU2).isEmpty() ? 0 : 1);
    }

    public void sendStatus(Player player) {
        for (Component line : statusLines(true)) player.sendSystemMessage(line);
    }

    /** Отчёт о сборке: производительность, питание, охлаждение, узкие места (и в чате, и в окне корпуса). */
    public List<Component> statusLines(boolean withParts) {
        List<Component> out = new ArrayList<>();
        if (withParts) out.add(Component.translatable("message.minepc.status.header").withStyle(ChatFormatting.AQUA));

        boolean any = false;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = getStack(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof HardwareItem)) continue;
            any = true;
            if (withParts) out.add(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(stack.getHoverName()));
        }
        if (!any) {
            out.add(Component.translatable("message.minepc.empty").withStyle(ChatFormatting.GRAY));
            return out;
        }

        SystemModel.Input in = collectInput();
        SystemModel.Report r = SystemModel.evaluate(in);

        // ----- Что реально выдаёт каждая часть -----
        if (withParts) out.add(Component.translatable("message.minepc.status.perf").withStyle(ChatFormatting.AQUA));
        if (in.cpu() != null) {
            boolean reduced = r.cpuThrottle() < 1 || r.cpuMemory() < 0.85;
            int coreKhz = (int) Math.round(in.cpu().clockKhz() * r.cpuThrottle() * r.cpuMemory());
            out.add(perf("message.minepc.status.cpu", reduced, Units.gameClock(coreKhz), Units.gameClock((int) Math.round(r.cpuMhz() * 1000)),
                    Units.level(r.cpuLevel())));
            com.strikerr212.minepc.computer.Computer.Limits lim = CaseComputer.limitsFor(in, r, com.strikerr212.minepc.MineConfig.speedFor(level));
            long hz = (long) lim.opsPerTick() * com.strikerr212.minepc.computer.Computer.TICKS_PER_SECOND;
            out.add(perf("message.minepc.status.game", reduced, Units.gameClock((int) (hz / 1000)),
                    Units.gameClock((int) Math.round(hz / 1000.0 * (1 + in.cpu().boostPct() / 100.0))),
                    lim.cores(), Units.bytes(lim.memoryBytes())));
        }
        if (in.gpu() != null) {
            out.add(perf("message.minepc.status.gpu", r.gpuThrottle() < 1,
                    Units.cells(Perf.gpuCellsPerSecond(r.gpuLevel())), Units.level(r.gpuLevel())));
        } else if (in.integrated() != null) {
            out.add(perf("message.minepc.status.igpu", r.gpuThrottle() < 1,
                    Units.cells(Perf.gpuCellsPerSecond(r.gpuLevel())), Units.level(r.gpuLevel())));
        }
        if (r.ramMb() > 0) {
            MutableComponent line = perf("message.minepc.status.ram", r.channels() < (in.board() != null ? in.board().channels() : 1),
                    Units.gameSize(r.ramMb()), Units.gameBandwidth(Perf.ramChannelMbs(r.ramMts())), r.channels(), Units.gameBandwidth(r.ramMbs()));
            if (hasNote(r, SystemModel.Issue.RAM_LIMITED)) {
                line.append(Component.translatable("message.minepc.status.ram_limited").withStyle(ChatFormatting.GOLD));
            }
            out.add(line);
        }
        if (r.bootSeconds() > 0) {
            out.add(perf("message.minepc.status.boot", r.bootSeconds() > 20, Units.seconds(Perf.gameBootTicks(r.bootSeconds()) / 20.0),
                    Component.translatable(r.bootFromSsd() ? "message.minepc.status.boot_ssd" : "message.minepc.status.boot_hdd")));
        }

        // ----- Питание и охлаждение -----
        out.add(powerStatus(in.powerDraw()));
        if (r.wallWatts() > 0) {
            out.add(Component.translatable("message.minepc.power.wall",
                    Units.watts(r.wallWatts()), Units.watts(r.wallWatts() - in.powerDraw())).withStyle(ChatFormatting.DARK_GRAY));
        }
        out.add(coolingStatus(r));
        Component gpu = gpuCoolingStatus(r);
        if (gpu != null) out.add(gpu);

        // ----- Итог -----
        if (r.bootable()) {
            out.add(Component.translatable("message.minepc.status.score",
                    Component.literal(String.valueOf(r.score())).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                    .withStyle(ChatFormatting.AQUA));
        } else {
            out.add(Component.translatable("message.minepc.status.no_boot").withStyle(ChatFormatting.RED));
        }
        boolean warned = false;
        for (SystemModel.Note n : r.notes()) {
            // Эти замечания уже видны в строках выше
            if (n.issue() == SystemModel.Issue.RAM_LIMITED || n.issue() == SystemModel.Issue.PSU_HIGH) continue;
            if (!n.issue().critical && !r.bootable()) continue;   // сначала пусть запустится
            out.add(Component.literal(" ! ").append(noteText(n))
                    .withStyle(n.issue().critical ? ChatFormatting.RED : ChatFormatting.GOLD));
            warned = true;
        }
        if (!warned && r.bootable()) {
            out.add(Component.translatable("message.minepc.status.balanced").withStyle(ChatFormatting.GREEN));
        }
        return out;
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
            case MIXED_RAM -> Component.translatable(key, Units.gameBandwidth(Perf.ramChannelMbs((int) n.a())));
            case CPU_BOTTLENECK, GPU_BOTTLENECK -> Component.translatable(key,
                    String.format(Locale.ROOT, "%.1f", n.a()), String.format(Locale.ROOT, "%.1f", n.b()));
            case PSU_OVERLOAD, PSU_HIGH -> Component.translatable(key, Math.round(n.a()));
            case RAM_LIMITED -> Component.translatable(key, Units.gameBandwidth(Perf.ramChannelMbs((int) n.a())));
            case SLOW_BOOT -> Component.translatable(key, Units.seconds(Perf.gameBootTicks(n.a()) / 20.0));
            case INTEGRATED_GPU -> Component.translatable(key, Units.gameSize((int) n.a()));
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
        return Component.translatable("message.minepc.cooling.ok", Units.watts(heat), Units.watts(capacity), pct(r.cpuThrottle()))
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
    void markUpdated() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState here = module ? level.getBlockState(worldPosition) : getBlockState();
            level.sendBlockUpdated(getBlockPos(), here, here, Block.UPDATE_CLIENTS);
        }
    }

    /** Модуль стойки: сохранить изменения в стойке (у самой стойки — свой блок-сущность). */
    @Override
    public void setChanged() {
        if (module) {
            if (level != null) level.blockEntityChanged(worldPosition);
            return;
        }
        super.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putInt("layout", LAYOUT_VERSION);
        tag.putBoolean("on", pc.wantOn());
        if (!pc.label().isEmpty()) tag.putString("label", pc.label());
        tag.putString("address", pc.getAddress());
        tag.putInt("state", pc.stateCode());
        cords.save(tag);
        bmc.save(tag);
        tag.putInt("rebuild_bay", pc.rebuildBay());
        if (!pc.wifiSsid().isEmpty()) {
            tag.putString("wifi_ssid", pc.wifiSsid());
            tag.putString("wifi_password", pc.wifiPassword());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ItemStackHandler saved = new ItemStackHandler();
        saved.deserializeNBT(registries, tag.getCompound("inventory"));
        int layout = tag.getInt("layout");
        boolean sameLayout = layout == LAYOUT_VERSION && saved.getSlots() == SLOT_COUNT;
        // Версия 2 отличалась только отсутствием USB-портов в конце, версия 3 — серверных слотов в конце
        boolean prefix = layout == 2 && saved.getSlots() == 15 || layout == 3 && saved.getSlots() == 17
                || layout == 4 && saved.getSlots() == 31;

        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            boolean keep = sameLayout || (prefix && slot < saved.getSlots());
            inventory.setDirect(slot, keep ? saved.getStackInSlot(slot) : ItemStack.EMPTY);
        }
        pc.setWantOn(tag.getBoolean("on"));
        pc.setLabel(tag.getString("label"));
        pc.setAddress(tag.getString("address"));
        pc.setWifi(tag.getString("wifi_ssid"), tag.getString("wifi_password"));
        clientState = tag.getInt("state");
        cords.load(tag);
        bmc.load(tag);
        clientRebuild = tag.contains("rebuild_bay") ? tag.getInt("rebuild_bay") : -1;
        if (!sameLayout && !prefix) {
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

    /** Состояние компьютера, как его видит игрок (на клиенте приходит с сервера). */
    private int clientState;

    public int displayState() {
        return level != null && level.isClientSide ? clientState : pc.stateCode();
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
        return clientView(saveWithoutMetadata(registries));
    }

    /** То, что уходит игрокам: без паролей (Wi-Fi, BMC) и журнала BMC (его видно только через сам BMC). */
    public static CompoundTag clientView(CompoundTag tag) {
        tag.remove("wifi_password");
        tag.remove("bmc_password");
        tag.remove("bmc_log");
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
