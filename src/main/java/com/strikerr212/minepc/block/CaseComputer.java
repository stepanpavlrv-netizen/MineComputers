package com.strikerr212.minepc.block;

import com.strikerr212.minepc.computer.Computer;
import com.strikerr212.minepc.computer.ComputerRom;
import com.strikerr212.minepc.computer.TerminalCodec;
import com.strikerr212.minepc.computer.fs.Mount;
import com.strikerr212.minepc.hardware.CpuData;
import com.strikerr212.minepc.hardware.GpuData;
import com.strikerr212.minepc.hardware.HddData;
import com.strikerr212.minepc.hardware.Perf;
import com.strikerr212.minepc.hardware.SsdData;
import com.strikerr212.minepc.hardware.SystemModel;
import com.strikerr212.minepc.item.CpuItem;
import com.strikerr212.minepc.item.DriveItem;
import com.strikerr212.minepc.item.GpuItem;
import com.strikerr212.minepc.item.HardwareItem;
import com.strikerr212.minepc.item.HddItem;
import com.strikerr212.minepc.item.SsdItem;
import com.strikerr212.minepc.network.TerminalInputPayload;
import com.strikerr212.minepc.network.TerminalPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Связь системного блока с компьютером: включение, накопители, периферия, отправка экрана игрокам.
 * Сам компьютер ({@link Computer}) ничего не знает о Minecraft — всё нужное ему передаётся отсюда.
 */
public final class CaseComputer implements Computer.Host {

    private static final int SCAN_INTERVAL = 10;       // как часто искать монитор, клавиатуру, дисководы (тики)
    private static final int SEND_INTERVAL = 2;        // экран отправляется не чаще раза в 2 тика
    private static final int KEEPALIVE = 100;          // и обязательно раз в 5 секунд (для новых зрителей)

    private final PcCaseBlockEntity be;
    private Computer computer;
    private boolean wantOn;
    private String label = "";
    private boolean hasKeyboard;
    private final List<BlockPos> monitors = new ArrayList<>();

    /** Съёмные накопители, подключённые сейчас: путь в компьютере → номер накопителя. */
    private final Map<String, String> removable = new LinkedHashMap<>();
    private int lastVersion = -1;
    /** Последний отправленный кадр: следующий пакет содержит только отличия от него. */
    private byte[] lastFrame;
    private boolean forceFull;
    private Computer.State lastState = Computer.State.OFF;
    private long lastSent = -1000;
    private long age;

    CaseComputer(PcCaseBlockEntity be) {
        this.be = be;
    }

    // ======================= Состояние для окна и сохранения =======================

    boolean wantOn() { return wantOn; }
    void setWantOn(boolean on) { wantOn = on; }
    String label() { return label; }
    void setLabel(String l) { label = l == null ? "" : l; }
    boolean hasKeyboard() { return hasKeyboard; }

    /** Загрузка всех ядер за последнюю секунду, 0..100 %. */
    int loadPercent() {
        if (computer == null || !computer.isOn()) return 0;
        Computer.Limits l = computer.limits();
        long max = (long) l.opsPerTick() * Computer.TICKS_PER_SECOND * Math.max(1, l.cores());
        return (int) Math.min(100, computer.opsLastSecond() * 100 / Math.max(1, max));
    }

    /** 0 — выключен, 1 — работает, 2 — ошибка. */
    int stateCode() {
        if (computer == null) return 0;
        return switch (computer.state()) {
            case OFF -> 0;
            case RUNNING -> 1;
            case CRASHED -> 2;
        };
    }

    Computer computer() {
        return computer;
    }

    // ======================= Computer.Host =======================

    @Override
    public double timeOfDay() {
        if (be.getLevel() == null) return 12;
        long t = be.getLevel().getDayTime() % 24000;
        return ((t / 1000.0) + 6) % 24;
    }

    @Override
    public int day() {
        return be.getLevel() == null ? 1 : (int) (be.getLevel().getDayTime() / 24000) + 1;
    }

    /** Постоянный адрес компьютера (сохраняется в блоке). */
    private String address = java.util.UUID.randomUUID().toString();

    @Override
    public String address() {
        return address;
    }

    String getAddress() { return address; }

    void setAddress(String a) {
        if (a != null && !a.isEmpty()) address = a;
    }

    @Override
    public void onLabelChanged(String l) {
        label = l;
        be.setChanged();
    }

    // ======================= Включение =======================

    /** Включить. Возвращает null, если получилось, или список причин, почему компьютер не запустится. */
    List<SystemModel.Note> powerOn(ServerLevel level) {
        SystemModel.Input in = be.collectInput();
        SystemModel.Report report = SystemModel.evaluate(in);
        if (!report.bootable()) {
            List<SystemModel.Note> reasons = new ArrayList<>();
            for (SystemModel.Note n : report.notes()) if (n.issue().critical) reasons.add(n);
            return reasons;
        }
        if (computer == null) computer = new Computer(this);
        else computer.shutdown();
        configure(level, in, report);
        computer.setLabel(label);
        computer.turnOn();
        wantOn = true;
        be.setChanged();
        return null;
    }

    void powerOff() {
        if (computer != null) computer.shutdown();
        wantOn = false;
        be.setChanged();
    }

    /** Детали поменяли на работающем компьютере — как в жизни, он выключается. */
    void partsChanged() {
        if (computer != null && computer.isOn()) {
            computer.shutdown();
            wantOn = false;
        }
    }

    void unload() {
        if (computer != null) computer.unload();
    }

    /** Сколько блоков в ширину и высоту у подключённого монитора (по нему считается размер экрана). */
    private int wallCols = 1, wallRows = 1;
    private int gpuTier;

    /** Настоящие лимиты компьютера по деталям и настройкам сервера. */
    public static Computer.Limits limitsFor(SystemModel.Input in, SystemModel.Report report, double speed) {
        int opsPerSecond = (int) Math.min(500_000_000L, Math.round(Perf.gameOpsPerSecond(report.cpuGips()) * speed));
        int cores = in.cpu() == null ? 1 : Math.max(1, Math.min(in.cpu().cores(), com.strikerr212.minepc.MineConfig.maxCores()));
        long memory = Math.min((long) com.strikerr212.minepc.MineConfig.maxMemoryMb() << 20,
                Math.round(Perf.gameMemoryKb(report.ramMb()) * 1024L * com.strikerr212.minepc.MineConfig.memory()));
        GpuData gpu = in.gpu();
        int tier = gpu == null ? 0 : gpu.tier();
        int cells = (int) Math.max(100, Perf.gpuCellsPerSecond(tier) * com.strikerr212.minepc.MineConfig.gpu() / Computer.TICKS_PER_SECOND);
        int boot = com.strikerr212.minepc.MineConfig.bootTime() ? Perf.gameBootTicks(report.bootSeconds()) : 0;
        return new Computer.Limits(Math.max(100, opsPerSecond / Computer.TICKS_PER_SECOND), cores, Math.max(256 * 1024, memory),
                cells, Perf.vramCells(gpu), boot, com.strikerr212.minepc.MineConfig.maxProcesses());
    }

    /** Самый большой экран: сколько тянет видеокарта и сколько помещается на мониторе. */
    private int[] maxScreen() {
        int[] gpu = Perf.screenSize(gpuTier);
        int[] wall = Perf.wallSize(wallCols, wallRows);
        return new int[]{Math.min(gpu[0], wall[0]), Math.min(gpu[1], wall[1])};
    }

    /** Настроить железо компьютера: скорость, экран, накопители. */
    private void configure(ServerLevel level, SystemModel.Input in, SystemModel.Report report) {
        com.strikerr212.minepc.computer.ComputerExecutor.configure(com.strikerr212.minepc.MineConfig.threads(),
                com.strikerr212.minepc.MineConfig.sliceMs());
        // Скорость, ядра, память, видеокарта — с учётом перегрева, нехватки памяти и настроек сервера
        Computer.Limits limits = limitsFor(in, report, com.strikerr212.minepc.MineConfig.speedFor(level));
        computer.setLimits(limits);
        int opsPerSecond = limits.opsPerTick() * Computer.TICKS_PER_SECOND;

        // Экран: без видеокарты — монохромный текст, дальше больше и в цвете (самый большой — на стене мониторов)
        GpuData gpu = in.gpu();
        gpuTier = gpu == null ? 0 : gpu.tier();
        int[] size = maxScreen();
        int w = size[0], h = size[1];
        computer.terminal().setMaxDepth(Perf.colorDepth(gpuTier));
        computer.terminal().setMaxSize(w, h);

        CpuData cpu = in.cpu();
        ItemStack cpuStack = be.getStack(PcCaseBlockEntity.SLOT_CPU);
        String cpuLabel = cpuStack.getItem() instanceof HardwareItem ? HardwareItem.label(cpuStack) : "";
        String cpuName = (cpuLabel.isEmpty() ? "Процессор ур. " + cpu.tier() : cpuLabel)
                + ", ядер: " + cpu.cores() + ", " + cpu.clockMhz() + " МГц";
        String gpuName = gpu == null ? "" : "Видеокарта ур. " + gpu.tier() + ", до " + Perf.screenSize(gpuTier)[0] + "×" + Perf.screenSize(gpuTier)[1];
        computer.setInfo(new Computer.Info(cpuName, cpu.cores(), cpu.clockMhz(), "Lua",
                (int) (limits.memoryBytes() / 1024), opsPerSecond, gpuName, report.score()));

        // Накопители: системный — самый быстрый (M.2, потом SATA SSD, потом жёсткий диск)
        var fs = computer.fileSystem();
        for (String m : fs.mounts().keySet()) fs.unmount(m);
        removable.clear();
        fs.mount("rom", ComputerRom.mount());
        int[] order = {PcCaseBlockEntity.SLOT_M2, PcCaseBlockEntity.SLOT_M2 + 1,
                PcCaseBlockEntity.SLOT_SATA_SSD, PcCaseBlockEntity.SLOT_SATA_SSD + 1,
                PcCaseBlockEntity.SLOT_HDD, PcCaseBlockEntity.SLOT_HDD + 1};
        int data = 0;
        boolean root = false;
        for (int slot : order) {
            ItemStack stack = be.getStack(slot);
            long capacity;
            String name;
            long speed;
            int opBytes;
            if (stack.getItem() instanceof SsdItem) {
                SsdData d = SsdItem.data(stack);
                capacity = Perf.gameStorageBytes(d.capacityMb());
                name = d.isNvme() ? "NVMe SSD" : "SATA SSD";
                speed = Perf.gameDiskBytesPerSecond(d.speedMbs());
                opBytes = 32;
            } else if (stack.getItem() instanceof HddItem) {
                HddData d = HddItem.data(stack);
                capacity = Perf.gameStorageBytes(d.capacityMb());
                name = "Жёсткий диск";
                speed = Perf.gameDiskBytesPerSecond(d.speedMbs());
                opBytes = 512;          // головке диска нужно время, чтобы найти файл
            } else {
                continue;
            }
            Mount mount = DriveStorage.mount(level.getServer(), stack, capacity, name)
                    .withSpeed(Math.round(speed * com.strikerr212.minepc.MineConfig.disk()), opBytes);
            if (!root) {
                fs.mount("", mount);
                root = true;
            } else {
                fs.mount(data == 0 ? "data" : "data" + (data + 1), mount);
                data++;
            }
        }
        be.setChanged();   // накопители могли получить номера
        updateRemovable(level, false);
    }

    // ======================= Каждый тик =======================

    void tick(ServerLevel level, BlockPos pos) {
        age++;
        if (age == 1 && wantOn && (computer == null || !computer.isOn())) {
            // Мир загрузился, а компьютер был включён — включаем снова
            if (powerOn(level) != null) wantOn = false;
        }
        if (age % SCAN_INTERVAL == 1) scanDevices(level, pos);

        if (computer != null) {
            computer.tick();
            Computer.State state = computer.state();
            if (lastState == Computer.State.RUNNING && state == Computer.State.OFF) {
                wantOn = false;           // выключился сам (команда shutdown)
                be.setChanged();
            }
            if (state != lastState) be.stateChanged();
            lastState = state;
        }
        syncScreen(level, pos);
    }

    private void scanDevices(ServerLevel level, BlockPos pos) {
        // Монитор рядом с корпусом — и все блоки большого монитора, в который он входит
        java.util.Set<BlockPos> found = new java.util.HashSet<>();
        int cols = 1, rows = 1;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (level.getBlockEntity(n) instanceof MonitorBlockEntity) {
                MonitorGroup group = MonitorGroup.of(level, n);
                found.addAll(group.blocks());
                if (group.cols() * group.rows() > cols * rows) {
                    cols = group.cols();
                    rows = group.rows();
                }
            }
        }
        if (cols != wallCols || rows != wallRows) {
            wallCols = cols;
            wallRows = rows;
            // Монитор достроили или разобрали на работающем компьютере — меняется самый большой экран
            if (computer != null && computer.isOn()) {
                int[] max = maxScreen();
                var t = computer.terminal();
                if (t.setLimit(max[0], max[1])) computer.queueEvent("screen_resized", t.width(), t.height());
            }
        }
        for (BlockPos p : found) {
            if (level.getBlockEntity(p) instanceof MonitorBlockEntity monitor) monitor.link(pos);
        }
        // Блоки, которые отсоединили от монитора, перестают показывать экран
        for (BlockPos old : monitors) {
            if (!found.contains(old) && level.isLoaded(old) && level.getBlockEntity(old) instanceof MonitorBlockEntity m
                    && pos.equals(m.casePos())) {
                m.link(null);
            }
        }
        monitors.clear();
        monitors.addAll(found);
        hasKeyboard = DeviceBlock.hasKeyboard(level, pos);
        if (computer != null && computer.isOn()) updateRemovable(level, true);
    }

    /** Дискеты в дисководах рядом и флешки в USB-портах: подключить новые, отключить вынутые. */
    void updateRemovable(ServerLevel level, boolean notify) {
        if (computer == null || !computer.isOn() && notify) return;
        Map<String, ItemStack> wanted = new LinkedHashMap<>();
        int disks = 0;
        for (Direction d : Direction.values()) {
            BlockPos n = be.getBlockPos().relative(d);
            if (level.getBlockEntity(n) instanceof DiskDriveBlockEntity drive && !drive.disk().isEmpty()) {
                wanted.put(disks == 0 ? "disk" : "disk" + (disks + 1), drive.disk());
                disks++;
            }
        }
        for (int i = 0; i < PcCaseBlockEntity.USB_SLOTS; i++) {
            ItemStack stack = be.getStack(PcCaseBlockEntity.SLOT_USB + i);
            if (stack.getItem() instanceof DriveItem) wanted.put(i == 0 ? "usb" : "usb" + (i + 1), stack);
        }
        var fs = computer.fileSystem();
        // Отключить вынутые и заменённые
        for (String path : new ArrayList<>(removable.keySet())) {
            ItemStack now = wanted.get(path);
            if (now == null || !Objects.equals(DriveItem.id(now), removable.get(path))) {
                fs.unmount(path);
                removable.remove(path);
                if (notify) computer.queueEvent("disk_eject", "/" + path);
            }
        }
        // Подключить новые
        for (Map.Entry<String, ItemStack> e : wanted.entrySet()) {
            if (removable.containsKey(e.getKey())) continue;
            ItemStack stack = e.getValue();
            if (!(stack.getItem() instanceof DriveItem drive)) continue;
            boolean floppy = drive.kind() == DriveItem.Kind.FLOPPY;
            String label = floppy ? "Дискета" : "Флешка";
            long speed = Math.round((floppy ? 128 * 1024 : 4 * 1024 * 1024) * com.strikerr212.minepc.MineConfig.disk());
            fs.mount(e.getKey(), DriveStorage.mount(level.getServer(), stack, drive.capacity(), label)
                    .withSpeed(speed, floppy ? 256 : 32));
            removable.put(e.getKey(), DriveItem.id(stack));
            if (notify) computer.queueEvent("disk", "/" + e.getKey());
        }
    }

    // ======================= Экран для игроков =======================

    /** Игрок открыл экран — пусть получит весь кадр, а не только изменения. */
    void requestFullFrame() {
        forceFull = true;
    }

    private void syncScreen(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        boolean on = computer != null && computer.state() != Computer.State.OFF;
        int version = on ? computer.terminal().version() : -1;
        boolean changed = version != lastVersion;
        boolean keepalive = now - lastSent >= KEEPALIVE;
        if ((changed && now - lastSent >= SEND_INTERVAL) || keepalive || forceFull) {
            byte[] data;
            if (on) {
                byte[] frame = computer.terminal().snapshot();
                // Раз в 5 секунд — весь кадр (для игроков, которые только подошли), иначе только изменения
                data = TerminalCodec.encode(keepalive || forceFull ? null : lastFrame, frame);
                lastFrame = frame;
            } else {
                data = new byte[0];
                lastFrame = null;
            }
            PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(pos),
                    new TerminalPayload(pos, data, hasKeyboard));
            lastSent = now;
            lastVersion = version;
            forceFull = false;
        }
    }

    void mouse(int kind, int button, int x, int y) {
        if (computer == null || !computer.isOn()) return;
        switch (kind) {
            case TerminalInputPayload.MOUSE_DOWN -> computer.queueEvent("mouse_click", button, x, y);
            case TerminalInputPayload.MOUSE_DRAG -> computer.queueEvent("mouse_drag", button, x, y);
            case TerminalInputPayload.MOUSE_UP -> computer.queueEvent("mouse_up", button, x, y);
            case TerminalInputPayload.MOUSE_SCROLL -> computer.queueEvent("mouse_scroll", button, x, y);
            default -> {
            }
        }
    }

    /** Загрузка файла с компьютера игрока на системный диск (в корень, под тем же именем). */
    net.minecraft.network.chat.Component upload(String rawName, int offset, int total, byte[] data) {
        if (computer == null || !computer.isOn()) {
            return offset == 0 ? net.minecraft.network.chat.Component.translatable("message.minepc.upload.off") : null;
        }
        String name = com.strikerr212.minepc.computer.fs.FileSystem.name(rawName).replaceAll("[\\\\:*?\"<>|]", "_");
        if (name.isEmpty() || total > com.strikerr212.minepc.network.FileUploadPayload.MAX_FILE) return null;
        var fs = computer.fileSystem();
        try {
            if (offset == 0) fs.write(name, new byte[0], false);
            fs.write(name, data, true);
        } catch (com.strikerr212.minepc.computer.fs.FsException e) {
            return net.minecraft.network.chat.Component.translatable("message.minepc.upload.failed", name, e.getMessage())
                    .withStyle(net.minecraft.ChatFormatting.RED);
        }
        if (offset + data.length >= total) {
            computer.queueEvent("file_upload", "/" + name, total);
            return net.minecraft.network.chat.Component.translatable("message.minepc.upload.done", "/" + name,
                    com.strikerr212.minepc.hardware.Units.bytes(total)).withStyle(net.minecraft.ChatFormatting.GREEN);
        }
        return null;
    }

    /** Ввод от игрока (уже проверенный): символы, клавиши, вставка текста, Ctrl+T. */
    void input(int kind, String text, boolean ctrl, boolean shift) {
        if (computer == null || !computer.isOn()) return;
        switch (kind) {
            case TerminalInputPayload.CHAR -> {
                for (int i = 0; i < text.length() && i < 32; i++) {
                    char c = text.charAt(i);
                    if (com.strikerr212.minepc.computer.Text.supports(c)) computer.queueEvent("char", String.valueOf(c));
                }
            }
            case TerminalInputPayload.KEY -> computer.queueEvent("key", text, ctrl, shift);
            case TerminalInputPayload.KEY_UP -> computer.queueEvent("key_up", text);
            case TerminalInputPayload.PASTE -> {
                StringBuilder clean = new StringBuilder();
                for (char c : text.toCharArray()) {
                    if (c == '\n' || c == '\t' || com.strikerr212.minepc.computer.Text.supports(c)) clean.append(c);
                }
                computer.queueEvent("paste", clean.toString());
            }
            case TerminalInputPayload.TERMINATE -> computer.terminate();
            default -> {
            }
        }
    }
}
