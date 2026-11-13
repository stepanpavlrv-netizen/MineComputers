package com.strikerr212.minepc.block;

import com.strikerr212.minepc.computer.Computer;
import com.strikerr212.minepc.computer.ComputerRom;
import com.strikerr212.minepc.computer.TerminalCodec;
import com.strikerr212.minepc.computer.fs.Mount;
import com.strikerr212.minepc.hardware.CpuData;
import com.strikerr212.minepc.hardware.GpuData;
import com.strikerr212.minepc.hardware.HddData;
import com.strikerr212.minepc.hardware.MotherboardData;
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
import java.util.HashMap;
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
    private static final long BUSY_GAP_NANOS = 33_000_000L;

    /** Сколько кадров экрана отправлено всего (для проверки скорости в CI). */
    public static final java.util.concurrent.atomic.AtomicLong FRAMES_SENT = new java.util.concurrent.atomic.AtomicLong();

    /** Весь кадр (не только изменения) — раз в 5 секунд, для игроков, которые только подошли. */
    private static final long KEEPALIVE_NANOS = 5_000_000_000L;

    private final PcCaseBlockEntity be;
    private volatile Computer computer;
    private boolean wantOn;
    private volatile String label = "";
    private volatile boolean hasKeyboard;
    private final List<BlockPos> monitors = new ArrayList<>();

    /** Съёмные накопители, подключённые сейчас: путь в компьютере → номер накопителя. */
    private final Map<String, String> removable = new LinkedHashMap<>();
    private int lastVersion = -1;
    /** Последний отправленный кадр: следующий пакет содержит только отличия от него. */
    private byte[] lastFrame;
    private volatile boolean forceFull;
    private Computer.State lastState = Computer.State.OFF;
    private long lastSentNanos, lastFullNanos;
    /** Кто видит экран (игроки рядом с корпусом); обновляется каждый тик в главном потоке. */
    private volatile List<net.minecraft.server.level.ServerPlayer> viewers = List.of();
    private volatile boolean unlimited = true;
    private volatile long lastTickNanos = System.nanoTime();
    /** Запас трафика на зрителя (байт) для сервера; в одиночной игре не ограничиваем. */
    private double screenTokens;
    private final Object screenLock = new Object();
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
        labelDirty = true;  // вызывается из потока программы: сохраним и разошлём в тике сервера
    }

    private volatile boolean labelDirty;

    // ======================= Сеть =======================

    /** Порт: 0 — кабеля нет, 1 — подключён; скорость порта (бит/с) и кто в сети — обновляются в тике. */
    private volatile int linkCode;
    private volatile long linkBits;
    private volatile List<Computer.Peer> peers = List.of();

    @Override
    public boolean netSend(String target, int port, Object[] payload, int bytes) {
        if (linkCode == 0 || !(be.getLevel() instanceof ServerLevel level)) return false;
        com.strikerr212.minepc.lan.LanManager.get(level).post(be.hostRef(), target, port, payload, bytes);
        return true;
    }

    @Override
    public long netBytesPerSecond() {
        return linkCode != 0 ? linkBits / 800 : 0;
    }

    @Override
    public List<Computer.Peer> netPeers() {
        return peers;
    }

    void netReceive(String from, int port, int hops, Object[] payload, int bytes) {
        if (computer != null) computer.netReceive(from, port, hops, payload, bytes);
    }

    int netLinkCode() { return linkCode; }
    int netPeersCount() { return peers.size(); }
    int netKbSent() { return computer == null ? 0 : (int) Math.min(Integer.MAX_VALUE, computer.netSent() / 1024); }
    int netKbReceived() { return computer == null ? 0 : (int) Math.min(Integer.MAX_VALUE, computer.netReceived() / 1024); }

    private void refreshNetwork(ServerLevel level, BlockPos pos) {
        if (age % 20 == 3 && !be.isModule()) refreshWifi(level, pos);
        var lan = com.strikerr212.minepc.lan.LanManager.get(level);
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        long wired = !bios().lan() ? 0
                : be.isModule() ? RackBlock.moduleBits(be)
                : state.getBlock() instanceof com.strikerr212.minepc.lan.LanDevice d ? d.lanBitsPerSecond(level, pos, state) : 0;
        boolean cable = wired > 0 && lan.linked(level, be.hostRef());
        Computer.WifiStatus ws = wifiStatus;
        boolean wifi = ws != null && ws.connected();
        long bits = Math.max(cable ? wired : 0, wifi ? ws.mbit() * 1_000_000L : 0);
        boolean linked = cable || wifi;
        List<Computer.Peer> list = new java.util.ArrayList<>();
        if (linked) {
            for (var r : lan.reach(level, be.hostRef())) {
                var other = level.isLoaded(r.pos()) ? Hosts.endpoint(level, r.host()) : null;
                if (other != null && other.netOnline()) {
                    list.add(new Computer.Peer(other.address(), other.label(), r.hops()));
                }
            }
            list.sort(java.util.Comparator.comparingInt(Computer.Peer::hops).thenComparing(Computer.Peer::name));
        }
        linkBits = bits;
        linkCode = (cable ? 1 : 0) | (wifi ? 2 | ws.signal() << 2 : 0);
        peers = List.copyOf(list);
    }

    // ======================= BIOS =======================

    /** Настройки BIOS из платы (или по умолчанию, если платы нет). */
    BiosSettings bios() {
        return BiosSettings.of(be.getStack(PcCaseBlockEntity.SLOT_MOTHERBOARD));
    }

    /** Ядра, порядок дисков — применятся при следующей загрузке (перезагрузка из программы тоже считается). */
    private volatile boolean reconfigure;

    @Override
    public void beforeBoot() {
        if (!reconfigure || !(be.getLevel() instanceof ServerLevel level)) return;
        reconfigure = false;
        SystemModel.Input in = be.collectInput();
        SystemModel.Report report = SystemModel.evaluate(in);
        if (report.bootable()) configure(level, in, report);
    }

    /** Сигнал красного камня: включиться по фронту, если это разрешено в BIOS. */
    private boolean lastSignal;

    void redstone(ServerLevel level, boolean powered) {
        boolean rising = powered && !lastSignal;
        lastSignal = powered;
        if (rising && bios().redstone() && (computer == null || !computer.isOn())) {
            if (powerOn(level) == null) be.stateChanged();
        }
    }

    @Override
    public Map<String, Object> biosSettings() {
        return bios().toLua();
    }

    @Override
    public String biosSave(Map<?, ?> changes) {
        return onServer(level -> {
            ItemStack board = be.getStack(PcCaseBlockEntity.SLOT_MOTHERBOARD);
            if (board.isEmpty()) return "Нет материнской платы";
            BiosSettings old = BiosSettings.of(board);
            BiosSettings next = old.with(changes);
            next.writeTo(board);
            be.setChanged();
            if (next.cores() != old.cores() || !next.bootOrder().equals(old.bootOrder()) || next.usb() != old.usb()) {
                reconfigure = true;
            }
            return null;
        }, "Сервер не ответил");
    }

    @Override
    public boolean biosCheckPassword(String kind, String password) {
        BiosSettings b = bios();
        String want = "boot".equals(kind) ? b.bootPassword() : b.biosPassword();
        return want.isEmpty() || Secrets.matches(want, password);
    }

    @Override
    public Map<String, Object> biosInfo() {
        Map<String, Object> m = new LinkedHashMap<>();
        var board = be.motherboard();
        int tier = board == null ? 1 : board.tier();
        ItemStack stack = be.getStack(PcCaseBlockEntity.SLOT_MOTHERBOARD);
        String label = stack.getItem() instanceof HardwareItem ? HardwareItem.label(stack) : "";
        boolean uefi = tier >= 4;
        m.put("vendor", uefi ? "MineCore UEFI" : "MineBIOS");
        m.put("version", uefi ? "5.2." + (10 + tier) : "4." + (50 + tier * 3) + "PG");
        m.put("board", label.isEmpty() ? "Материнская плата ур. " + tier : label);
        m.put("tier", tier);
        m.put("theme", uefi ? "uefi" : "classic");
        m.put("serial", address.substring(0, 8).toUpperCase(java.util.Locale.ROOT));
        if (be.getLevel() != null) {
            long day = be.getLevel().getDayTime();
            m.put("day", (int) (day / 24000) + 1);
            m.put("time", String.format(java.util.Locale.ROOT, "%02d:%02d", (int) ((day / 1000 + 6) % 24), (int) (day % 1000 * 60 / 1000)));
        }
        return m;
    }

    /** Что может быть устройством загрузки: внутренние диски, флешка, дискета (с путём, если подключено). */
    private final Map<String, String> bootPaths = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public List<Map<String, Object>> biosDevices() {
        List<Map<String, Object>> out = new ArrayList<>();
        String[][] names = {{"nvme", "M.2 NVMe SSD"}, {"sata", "SATA SSD"}, {"hdd", "Жёсткий диск"}, {"usb", "USB-флешка"}, {"floppy", "Дискета"}};
        for (String[] n : names) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n[0]);
            m.put("name", n[1]);
            String path = bootPaths.get(n[0]);
            m.put("present", path != null);
            if (path != null) m.put("path", path);
            out.add(m);
        }
        return out;
    }

    // ======================= Запросы к миру из потока программы =======================

    private final java.util.concurrent.ConcurrentLinkedQueue<Runnable> serverTasks = new java.util.concurrent.ConcurrentLinkedQueue<>();

    /** Выполнить в потоке сервера (там живёт мир) и дождаться ответа; не дождались — fallback. */
    private <T> T onServer(java.util.function.Function<ServerLevel, T> task, T fallback) {
        if (!(be.getLevel() instanceof ServerLevel level)) return fallback;
        if (level.getServer().isSameThread()) return task.apply(level);
        java.util.concurrent.CompletableFuture<T> f = new java.util.concurrent.CompletableFuture<>();
        serverTasks.add(() -> {
            try {
                f.complete(task.apply(level));
            } catch (RuntimeException e) {
                f.completeExceptionally(e);
            }
        });
        try {
            return f.get(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return fallback;
        }
    }

    private BlockPos switchById(ServerLevel level, String id) {
        for (BlockPos p : com.strikerr212.minepc.lan.LanManager.get(level).switches(level, be.hostRef())) {
            if (com.strikerr212.minepc.lan.SwitchPorts.id(p).equalsIgnoreCase(id)) return p;
        }
        return null;
    }

    @Override
    public List<Map<String, Object>> switchList() {
        return onServer(level -> {
            var data = com.strikerr212.minepc.lan.LanData.get(level);
            List<Map<String, Object>> out = new ArrayList<>();
            if (linkCode == 0) return out;
            for (BlockPos p : com.strikerr212.minepc.lan.LanManager.get(level).switches(level, be.hostRef())) {
                var d = data.device(p);
                if (d == null) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", com.strikerr212.minepc.lan.SwitchPorts.id(p));
                m.put("kind", com.strikerr212.minepc.lan.SwitchPorts.kind(d));
                m.put("ports", com.strikerr212.minepc.lan.LanManager.usedPorts(data, p, d).size());
                m.put("speed", (double) d.bits() / 1_000_000);
                out.add(m);
            }
            return out;
        }, List.of());
    }

    @Override
    public List<Map<String, Object>> switchPorts(String id) {
        return onServer(level -> {
            BlockPos p = linkCode == 0 ? null : switchById(level, id);
            if (p == null) return null;
            List<Map<String, Object>> out = new ArrayList<>();
            for (var port : com.strikerr212.minepc.lan.SwitchPorts.ports(level, p)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("port", port.port());
                m.put("side", switch (port.side()) {
                    case 'F' -> "front";
                    case 'B' -> "back";
                    case 'L' -> "left";
                    case 'R' -> "right";
                    case 'S' -> "bus";
                    case 'W' -> "wan";
                    default -> "cord";
                });
                m.put("n", port.number());
                m.put("link", port.link());
                String dev = port.device();
                m.put("device", dev.startsWith("@") ? dev.substring(1) : dev.equals("!") ? "слишком длинный кабель" : dev);
                m.put("enabled", port.enabled());
                m.put("vlan", com.strikerr212.minepc.lan.SwitchPorts.vlanName(port.vlan()));
                m.put("label", port.label());
                out.add(m);
            }
            return out;
        }, null);
    }

    @Override
    public String switchSet(String id, int port, boolean enabled, int vlan) {
        return onServer(level -> {
            BlockPos p = linkCode == 0 ? null : switchById(level, id);
            if (p == null) return "Нет такого коммутатора";
            var data = com.strikerr212.minepc.lan.LanData.get(level);
            int v = vlan;
            if (vlan == -2) {   // VLAN не меняем: берём нынешний из списка портов
                v = 0;
                for (var pp : com.strikerr212.minepc.lan.SwitchPorts.ports(level, p)) if (pp.port() == port) v = pp.vlan();
            }
            return com.strikerr212.minepc.lan.SwitchPorts.set(level, p, port, enabled, v) ? null : "Нет такого порта";
        }, "Сервер не ответил");
    }

    // ======================= Wi-Fi =======================

    /** Сеть, к которой компьютер подключается (хранится в корпусе). */
    private volatile String wifiSsid = "", wifiPassword = "";
    private volatile Computer.WifiStatus wifiStatus;
    private volatile List<Computer.WifiNet> wifiNets = List.of();

    String wifiSsid() { return wifiSsid; }
    String wifiPassword() { return wifiPassword; }

    void setWifi(String ssid, String password) {
        wifiSsid = ssid == null ? "" : ssid;
        wifiPassword = password == null ? "" : password;
    }

    int wifiMbit() {
        Computer.WifiStatus ws = wifiStatus;
        return ws != null && ws.connected() ? ws.mbit() : 0;
    }

    @Override
    public Computer.WifiStatus wifiStatus() {
        return wifiStatus;
    }

    @Override
    public List<Computer.WifiNet> wifiScan() {
        return wifiNets;
    }

    @Override
    public boolean wifiConnect(String ssid, String password) {
        if (adapter() == null) return false;
        setWifi(ssid, password);
        be.setChanged();
        return true;
    }

    @Override
    public void wifiDisconnect() {
        setWifi("", "");
        be.setChanged();
    }

    private com.strikerr212.minepc.lan.WifiAdapterItem adapter() {
        BiosSettings b = bios();
        if (!b.usb() || !b.wifi()) return null;
        for (int i = 0; i < PcCaseBlockEntity.USB_SLOTS; i++) {
            if (be.getStack(PcCaseBlockEntity.SLOT_USB + i).getItem() instanceof com.strikerr212.minepc.lan.WifiAdapterItem a) return a;
        }
        return null;
    }

    /** Сколько непрозрачных блоков на прямой между двумя блоками (сами они не считаются). */
    static int walls(ServerLevel level, BlockPos a, BlockPos b) {
        return com.strikerr212.minepc.lan.WifiSignal.walls(level, a, b);
    }

    /** Раз в секунду: какие сети слышно и подключиться к выбранной (самый сильный роутер с этим именем). */
    private void refreshWifi(ServerLevel level, BlockPos pos) {
        var data = com.strikerr212.minepc.lan.LanData.get(level);
        var adapter = adapter();
        if (adapter == null) {
            data.removeWifi(pos);
            wifiStatus = null;
            wifiNets = List.of();
            return;
        }
        String ssid = wifiSsid;
        if (computer == null || !computer.isOn()) {
            data.removeWifi(pos);
            wifiStatus = new Computer.WifiStatus(adapter.standard(), ssid, false, 0, 0, "");
            wifiNets = List.of();
            return;
        }
        Map<String, Computer.WifiNet> nets = new LinkedHashMap<>();
        BlockPos bestRouter = null;
        Computer.WifiNet best = null;
        String bestPassword = "";
        for (BlockPos r : data.routersNear(pos, adapter.range() + 1)) {
            if (!level.isLoaded(r) || !(level.getBlockEntity(r) instanceof com.strikerr212.minepc.lan.RouterBlockEntity router)) continue;
            double dist = Math.sqrt(pos.distSqr(r));
            int signal = com.strikerr212.minepc.lan.WifiSignal.signal(dist, Math.min(adapter.range(), router.radius()),
                    walls(level, pos, r), com.strikerr212.minepc.lan.WifiSignal.wallCost(level.getBlockState(r)));
            if (signal <= 0) continue;
            int mbit = com.strikerr212.minepc.lan.WifiSignal.mbit(signal, adapter.mbit());
            Computer.WifiNet net = new Computer.WifiNet(router.ssid(), signal, router.secured(), mbit);
            Computer.WifiNet old = nets.get(net.ssid());
            if (old == null || old.signal() < signal) nets.put(net.ssid(), net);
            if (net.ssid().equals(ssid) && (best == null || best.signal() < signal)) {
                best = net;
                bestRouter = r.immutable();
                bestPassword = router.password();
            }
        }
        List<Computer.WifiNet> list = new ArrayList<>(nets.values());
        list.sort((x, y) -> Integer.compare(y.signal(), x.signal()));
        wifiNets = List.copyOf(list);
        String error = "";
        if (ssid.isEmpty()) {
            best = null;
        } else if (best == null) {
            error = "сеть не найдена";
        } else if (!bestPassword.isEmpty() && !bestPassword.equals(wifiPassword)) {
            error = "неверный пароль";
            best = null;
        }
        if (best != null) {
            data.putWifi(new com.strikerr212.minepc.lan.LanData.WifiLink(pos.immutable(), bestRouter, best.mbit(), best.signal()));
            wifiStatus = new Computer.WifiStatus(adapter.standard(), ssid, true, best.signal(), best.mbit(), "");
        } else {
            data.removeWifi(pos);
            wifiStatus = new Computer.WifiStatus(adapter.standard(), ssid, false, 0, 0, error);
        }
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
        reconfigure = false;
        configure(level, in, report);
        computer.setLabel(label);
        computer.turnOn();
        wantOn = true;
        be.setChanged();
        startSounds(level, in, report);
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
        if (be.getLevel() instanceof ServerLevel level && !be.isModule()) com.strikerr212.minepc.lan.LanData.get(level).removeWifi(be.getBlockPos());
    }

    /** Сколько блоков в ширину и высоту у подключённого монитора (по нему считается размер экрана). */
    private int wallCols = 1, wallRows = 1;
    private int gpuTier;

    /** Настоящие лимиты компьютера по деталям и настройкам сервера. */
    public static Computer.Limits limitsFor(SystemModel.Input in, SystemModel.Report report, double speed) {
        // Одна программа — одно ядро на своей частоте: одна команда Lua — один такт (3,5 МГц = 3,5 млн команд/с).
        // Буст и сброс частоты от перегрева — вживую, по температуре (Computer.clockFactor)
        int opsPerSecond = in.cpu() == null ? 0
                : (int) Math.min(500_000_000L, Math.round(in.cpu().clockKhz() * 1000.0 * report.cpuMemory() * speed));
        int cores = in.cpu() == null ? 1 : Math.max(1, Math.min(in.cpu().cores(), com.strikerr212.minepc.MineConfig.maxCores()));
        // Видеоядро забирает часть оперативной памяти под экран
        long memory = Math.min((long) com.strikerr212.minepc.MineConfig.maxMemoryMb() << 20,
                Math.round(Perf.gameMemoryKb(Math.max(0, report.ramMb() - in.sharedVramMb())) * 1024L * com.strikerr212.minepc.MineConfig.memory()));
        GpuData gpu = in.graphics();
        // Скорость отрисовки — по настоящей мощности видеокарты (или видеоядра) с охлаждением и памятью
        int cells = (int) Math.max(100, Perf.gpuCellsPerSecond(gpu == null ? 0 : report.gpuLevel())
                * com.strikerr212.minepc.MineConfig.gpu() / Computer.TICKS_PER_SECOND);
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
        BiosSettings bios = bios();
        if (bios.cores() > 0 && bios.cores() < limits.cores()) {
            limits = new Computer.Limits(limits.opsPerTick(), bios.cores(), limits.memoryBytes(), limits.gpuCellsPerTick(),
                    limits.vramCells(), limits.bootTicks(), limits.maxProcesses());
        }
        computer.setLimits(limits);
        int opsPerSecond = limits.opsPerTick() * Computer.TICKS_PER_SECOND;

        // Экран: без видеокарты и видеоядра — монохромный текст, дальше больше и в цвете (самый большой — на стене мониторов)
        GpuData gpu = in.graphics();
        gpuTier = gpu == null ? 0 : gpu.tier();
        int[] size = maxScreen();
        int w = size[0], h = size[1];
        computer.terminal().setMaxDepth(Perf.colorDepth(gpuTier));
        computer.terminal().setMaxSize(w, h);

        CpuData cpu = in.cpu();
        ItemStack cpuStack = be.getStack(PcCaseBlockEntity.SLOT_CPU);
        String cpuLabel = cpuStack.getItem() instanceof HardwareItem ? HardwareItem.label(cpuStack) : "";
        String mhz = String.format(java.util.Locale.ROOT, cpu.clockKhz() % 1000 == 0 ? "%.0f" : "%.1f", cpu.clockKhz() / 1000.0);
        String cpuName = (be.getStack(PcCaseBlockEntity.SLOT_CPU2).isEmpty() ? "" : "2 × ")
                + (cpuLabel.isEmpty() ? "Процессор ур. " + cpu.tier() : cpuLabel)
                + ", ядер: " + cpu.cores() + ", " + mhz + " МГц";
        String gpuName = gpu == null ? "" : (in.gpu() == null ? "Видеоядро процессора" : "Видеокарта ур. " + gpu.tier())
                + ", до " + Perf.screenSize(gpuTier)[0] + "×" + Perf.screenSize(gpuTier)[1];
        computer.setInfo(new Computer.Info(cpuName, cpu.cores(), cpu.clockKhz(), "Lua",
                (int) (limits.memoryBytes() / 1024), opsPerSecond, gpuName, report.score()));
        // Тепло и питание: температура в диспетчере задач и в BIOS считается по этим деталям
        int cpuCooling = in.cooler() != null ? in.cooler().coolingW() : Perf.PASSIVE_COOLING;
        // (тепло видеоядра уже входит в тепло процессора)
        GpuData card = in.gpu();
        int gpuCooling = card == null ? 0 : card.hasCooler() ? card.coolingW() : Perf.PASSIVE_COOLING;
        computer.setThermal(new Computer.Thermal(cpu.tdp(), cpuCooling, card == null ? 0 : card.tdp(), gpuCooling,
                card != null, in.powerDraw(), in.psu() == null ? 0 : in.psu().wattage(), in.cooler() != null,
                cpu.boostPct() / 100.0));

        // Накопители: системный — самый быстрый (M.2, потом SATA SSD, потом жёсткий диск)
        var fs = computer.fileSystem();
        for (String m : fs.mounts().keySet()) fs.unmount(m);
        removable.clear();
        soundMounts.clear();
        fs.mount("rom", ComputerRom.mount());
        // Системный диск — первый по порядку загрузки в BIOS (по умолчанию: M.2, SATA SSD, жёсткий диск).
        // Вид диска — по самому диску (у сервера отсеки универсальные). Массив RAID встаёт на место своего первого диска.
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < PcCaseBlockEntity.M2_SLOTS; i++) slots.add(PcCaseBlockEntity.SLOT_M2 + i);
        for (int i = 0; i < be.bayCount(); i++) slots.add(PcCaseBlockEntity.baySlot(i));
        List<String> bootOrder = bios.bootOrder();
        slots.sort(java.util.Comparator.comparingInt(sl -> {
            int k = bootOrder.indexOf(driveKind(be.getStack(sl)));
            return k < 0 ? 99 : k;
        }));
        bootPaths.keySet().removeAll(List.of("nvme", "sata", "hdd"));
        bays.clear();
        rebuildSlot = -1;
        raidConfig = be.motherboard() != null && be.motherboard().server() ? RaidConfig.of(be.getStack(PcCaseBlockEntity.SLOT_MOTHERBOARD)) : null;
        raidPath = null;
        int missing = raidConfig == null ? 0 : raidConfig.members() - raidMembers().size();
        raidState = raidConfig == null ? "" : missing == 0 ? "ok" : missing <= raidConfig.tolerance() ? "degraded" : "failed";
        if ("failed".equals(raidState)) be.bmc().log("RAID: не хватает дисков (" + missing + ") — массив не собран");
        boolean root = false;
        for (int slot : slots) {
            ItemStack stack = be.getStack(slot);
            if (bootOrder.indexOf(driveKind(stack)) < 0) continue;
            if (raidConfig != null && isMember(stack)) {
                if (DiskHealth.of(stack).failed()) continue;
                bays.put(slot, RAID);
                if (raidPath != null || "failed".equals(raidState)) continue;
                Mount array = raidMount(level);
                raidPath = root ? freeDataPath() : "";
                fs.mount(raidPath, array);
                bootPaths.putIfAbsent(driveKind(stack), "/" + raidPath);
                root = true;
                continue;
            }
            Mount mount = internalMount(level, stack);
            if (mount == null) continue;
            String at = root ? freeDataPath() : "";
            fs.mount(at, mount);
            bays.put(slot, at);
            bootPaths.putIfAbsent(driveKind(stack), "/" + at);
            root = true;
        }
        // Массив без резерва, а в отсеке чистый диск — восстановить на него сразу
        if ("degraded".equals(raidState)) {
            for (int i = 0; i < be.bayCount(); i++) {
                int slot = PcCaseBlockEntity.baySlot(i);
                ItemStack d = be.getStack(slot);
                if (!d.isEmpty() && !bays.containsKey(slot) && !DiskHealth.of(d).failed() && com.strikerr212.minepc.item.DriveItem.id(d).isEmpty()) {
                    startRebuild(slot);
                    break;
                }
            }
        }
        be.setChanged();   // накопители могли получить номера
        updateRemovable(level, false);
    }

    /** Какой накопитель где смонтирован: слот корпуса → путь ("" — системный диск). */
    private final Map<Integer, String> bays = new HashMap<>();

    /** Первое свободное имя для диска данных: data, data2, data3… */
    private String freeDataPath() {
        for (int n = 1; ; n++) {
            String at = n == 1 ? "data" : "data" + n;
            if (!bays.containsValue(at)) return at;
        }
    }

    /** Файловая система внутреннего накопителя (жёсткий диск или SSD) или null. */
    private Mount internalMount(ServerLevel level, ItemStack stack) {
        if (DiskHealth.of(stack).failed()) return null;   // отказавший диск не читается
        long capacity, speed;
        String name;
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
            return null;
        }
        Mount mount = DriveStorage.mount(level.getServer(), stack, capacity, name)
                .withSpeed(Math.round(speed * com.strikerr212.minepc.MineConfig.disk()), opBytes);
        if (stack.getItem() instanceof HddItem) soundMounts.put(mount, be.getBlockPos().immutable());   // жёсткий диск трещит
        return mount;
    }

    /**
     * Сервер: диск в отсеке или блок питания поменяли на ходу. Диск данных отключается и подключается с событиями
     * disk_eject / disk; вынули системный — сервер падает. Диск массива RAID: массив работает дальше без резерва
     * (или разваливается, если резерва не было); новый диск в массив без резерва — восстановление на него.
     * Блок питания: пока оставшийся тянет нагрузку, сервер работает (событие power_supply), иначе выключается.
     */
    void hotSwap(ServerLevel level, int slot) {
        boolean psuSlot = slot == PcCaseBlockEntity.SLOT_PSU || slot == PcCaseBlockEntity.SLOT_PSU2;
        boolean empty = be.getStack(slot).isEmpty();
        // Журнал BMC пишется всегда, даже у выключенного сервера (BMC работает от дежурного питания)
        if (psuSlot) be.bmc().log("блок питания " + (slot == PcCaseBlockEntity.SLOT_PSU ? 1 : 2) + (empty ? " вынут" : " вставлен"));
        else be.bmc().log("диск в отсеке " + (PcCaseBlockEntity.bayIndex(slot) + 1) + (empty ? " извлечён" : " вставлен"));
        be.markUpdated();
        if (computer == null || !computer.isOn()) return;
        if (psuSlot) {
            var psu = be.activePsu();
            if (psu == null || psu.wattage() < be.collectInput().powerDraw()) {
                be.bmc().log("питания не хватило — сервер выключился");
                partsChanged();
                return;
            }
            computer.queueEvent("power_supply", be.psuCount());
            return;
        }
        if (!detach(slot)) return;          // сервер упал
        attach(level, slot);
    }

    /** Диск в слоте больше не работает (вынули или отказал). false — сервер упал. */
    private boolean detach(int slot) {
        var fs = computer.fileSystem();
        String was = bays.remove(slot);
        if (was == null) return true;
        int bay = PcCaseBlockEntity.bayIndex(slot) + 1;
        if (was.equals(REBUILD)) {
            rebuildSlot = -1;
            raidState = "degraded";
            be.bmc().log("RAID: восстановление на отсек " + bay + " прервано");
            computer.queueEvent("raid", raidState);
            return true;
        }
        if (was.equals(RAID)) {
            int missing = raidConfig.members() - countRaid();
            if (missing > raidConfig.tolerance()) {
                raidState = "failed";
                be.bmc().log("RAID: из массива выпал диск " + bay + " — массив развалился");
                if (raidPath != null && raidPath.isEmpty()) {
                    partsChanged();
                    return false;
                }
                if (raidPath != null) {
                    fs.unmount(raidPath);
                    computer.queueEvent("disk_eject", "/" + raidPath);
                }
                raidPath = null;
            } else {
                raidState = "degraded";
                be.bmc().log("RAID: из массива выпал диск " + bay + " — работает без резерва");
            }
            computer.queueEvent("raid", raidState);
            return true;
        }
        if (was.isEmpty()) {          // системный диск — работать не на чем
            be.bmc().log("системный диск недоступен — сервер упал");
            partsChanged();
            return false;
        }
        fs.unmount(was);
        computer.queueEvent("disk_eject", "/" + was);
        return true;
    }

    /** В слот вставили диск: свой диск массива — обратно в массив, чужой в массив без резерва — восстановление, иначе — отдельный диск. */
    private void attach(ServerLevel level, int slot) {
        ItemStack now = be.getStack(slot);
        if (now.isEmpty()) return;
        DiskHealth h = DiskHealth.of(now);
        int bay = PcCaseBlockEntity.bayIndex(slot) + 1;
        if (h.failed()) {
            be.bmc().log("в отсек " + bay + " вставлен неисправный диск");
            return;
        }
        if (raidConfig != null && isMember(now)) {
            bays.put(slot, RAID);
            if ("failed".equals(raidState)) {
                be.bmc().log("RAID: диск " + bay + " вернулся — перезагрузите сервер, чтобы собрать массив");
            } else {
                raidState = raidConfig.members() - countRaid() == 0 && rebuildSlot < 0 ? "ok" : raidState;
                be.bmc().log("RAID: диск " + bay + " вернулся в массив");
                computer.queueEvent("raid", raidState);
            }
            return;
        }
        if (raidConfig != null && "degraded".equals(raidState) && rebuildSlot < 0 && PcCaseBlockEntity.isDriveBay(slot)) {
            startRebuild(slot);
            return;
        }
        Mount mount = internalMount(level, now);
        if (mount != null) {
            String at = freeDataPath();
            computer.fileSystem().mount(at, mount);
            bays.put(slot, at);
            computer.queueEvent("disk", "/" + at);
            be.setChanged();   // новый диск мог получить номер
        }
    }

    // ======================= RAID =======================

    private static final String RAID = "@raid", REBUILD = "@rebuild";
    private RaidConfig raidConfig;
    /** Где смонтирован массив (null — не смонтирован). */
    private String raidPath;
    /** ok, degraded (без резерва), rebuilding, failed; "" — массива нет. */
    private volatile String raidState = "";
    private int rebuildSlot = -1;
    private long rebuildTicks, rebuildTotal;

    private boolean isMember(ItemStack stack) {
        return isMember(raidConfig, stack);
    }

    private static boolean isMember(RaidConfig cfg, ItemStack stack) {
        return cfg != null && !stack.isEmpty() && cfg.id().equals(DiskHealth.of(stack).raid());
    }

    /** Исправные диски массива в отсеках. */
    private List<Integer> raidMembers() {
        return raidMembers(raidConfig);
    }

    private List<Integer> raidMembers(RaidConfig cfg) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < be.bayCount(); i++) {
            int slot = PcCaseBlockEntity.baySlot(i);
            ItemStack d = be.getStack(slot);
            if (isMember(cfg, d) && !DiskHealth.of(d).failed()) out.add(slot);
        }
        return out;
    }

    private int countRaid() {
        int n = 0;
        for (String v : bays.values()) if (RAID.equals(v)) n++;
        return n;
    }

    /** Файловая система массива: своя папка, объём и скорость — по самому маленькому и медленному диску. */
    private Mount raidMount(ServerLevel level) {
        long minCap = Long.MAX_VALUE, minSpeed = Long.MAX_VALUE;
        boolean hdd = false;
        for (int slot : raidMembers()) {
            ItemStack d = be.getStack(slot);
            minCap = Math.min(minCap, diskCapacity(d));
            minSpeed = Math.min(minSpeed, diskSpeed(d));
            if (d.getItem() instanceof HddItem) hdd = true;
        }
        if (minCap == Long.MAX_VALUE) minCap = 0;
        if (minSpeed == Long.MAX_VALUE) minSpeed = 1;
        Mount m = new com.strikerr212.minepc.computer.fs.DirMount(DriveStorage.folder(level.getServer(), raidConfig.folderId()),
                raidConfig.capacity(minCap), raidConfig.levelName())
                .withSpeed(Math.round(raidConfig.speed(minSpeed) * com.strikerr212.minepc.MineConfig.disk()), hdd ? 512 : 32);
        if (hdd) soundMounts.put(m, be.getBlockPos().immutable());
        return m;
    }

    private static long diskCapacity(ItemStack d) {
        if (d.getItem() instanceof SsdItem) return Perf.gameStorageBytes(SsdItem.data(d).capacityMb());
        if (d.getItem() instanceof HddItem) return Perf.gameStorageBytes(HddItem.data(d).capacityMb());
        return 0;
    }

    private static long diskSpeed(ItemStack d) {
        if (d.getItem() instanceof SsdItem) return Perf.gameDiskBytesPerSecond(SsdItem.data(d).speedMbs());
        if (d.getItem() instanceof HddItem) return Perf.gameDiskBytesPerSecond(HddItem.data(d).speedMbs());
        return 1;
    }

    static String driveKind(ItemStack stack) {
        if (stack.getItem() instanceof SsdItem) return SsdItem.data(stack).isNvme() ? "nvme" : "sata";
        if (stack.getItem() instanceof HddItem) return "hdd";
        return "";
    }

    /** Восстановить массив на диск в слоте: копирование занимает время (по объёму и скорости диска, 10 с – 1 мин). */
    private void startRebuild(int slot) {
        ItemStack d = be.getStack(slot);
        rebuildSlot = slot;
        rebuildTicks = 0;
        long seconds = diskCapacity(d) / Math.max(1, diskSpeed(d));
        rebuildTotal = Math.max(200, Math.min(1200, seconds * 20));   // от 10 секунд до минуты
        raidState = "rebuilding";
        bays.put(slot, REBUILD);
        be.bmc().log("RAID: восстановление на диск в отсеке " + (PcCaseBlockEntity.bayIndex(slot) + 1));
        computer.queueEvent("raid", raidState);
        be.markUpdated();
    }

    private void tickRebuild() {
        if (rebuildSlot < 0 || computer == null || !computer.isOn()) return;
        if (++rebuildTicks < rebuildTotal) return;
        ItemStack d = be.getStack(rebuildSlot);
        // Какого номера в массиве не хватает — его и занимает новый диск
        java.util.Set<Integer> have = new java.util.HashSet<>();
        for (int slot : raidMembers()) have.add(DiskHealth.of(be.getStack(slot)).member());
        int index = 0;
        while (have.contains(index)) index++;
        DiskHealth.of(d).withRaid(raidConfig.id(), index).writeTo(d);
        bays.put(rebuildSlot, RAID);
        be.bmc().log("RAID: восстановление на отсек " + (PcCaseBlockEntity.bayIndex(rebuildSlot) + 1) + " завершено");
        rebuildSlot = -1;
        raidState = raidConfig.members() - countRaid() == 0 ? "ok" : "degraded";
        computer.queueEvent("raid", raidState);
        be.markUpdated();
    }

    /** Какой отсек сейчас восстанавливается (-1 — никакой) — для лампы на модуле. */
    int rebuildBay() {
        return rebuildSlot < 0 ? -1 : PcCaseBlockEntity.bayIndex(rebuildSlot);
    }

    /** Состояние массива для BMC и программ. */
    Map<String, Object> raidStatus() {
        boolean on = computer != null && computer.isOn();
        RaidConfig cfg = on && raidConfig != null ? raidConfig : RaidConfig.of(be.getStack(PcCaseBlockEntity.SLOT_MOTHERBOARD));
        if (cfg == null || be.motherboard() == null || !be.motherboard().server()) return null;
        int present = raidMembers(cfg).size();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("level", cfg.level());
        m.put("name", cfg.levelName());
        m.put("members", cfg.members());
        m.put("present", present);
        m.put("state", !on || raidState.isEmpty() ? "off" : raidState);
        if (rebuildSlot >= 0) m.put("rebuild", (int) (rebuildTicks * 100 / Math.max(1, rebuildTotal)));
        if (raidPath != null) m.put("path", "/" + raidPath);
        return m;
    }

    // ======================= Износ дисков =======================

    /** Раз в 10 секунд: работающие диски изнашиваются (по настройке diskWear); 90% — просят замены, 100% — отказ. */
    private void tickWear() {
        int mode = com.strikerr212.minepc.MineConfig.diskWear();
        if (mode == 0 || mode == 1 && !be.serverChassis()) return;
        double life = com.strikerr212.minepc.MineConfig.diskLifeHours() * 3600;
        for (int slot : new ArrayList<>(bays.keySet())) {
            ItemStack d = be.getStack(slot);
            if (d.isEmpty()) continue;
            wearDisk(slot, d, 10 / (life * (d.getItem() instanceof SsdItem ? 1.5 : 1)));
            if (computer == null || !computer.isOn()) return;
        }
    }

    /** Добавить износ диску в слоте (и обработать «просит замены» и отказ). */
    void wearDisk(int slot, ItemStack d, double add) {
        DiskHealth h = DiskHealth.of(d);
        if (h.failed()) return;
        DiskHealth n = h.withWear(h.wear() + add);
        n.writeTo(d);
        be.setChanged();
        String where = PcCaseBlockEntity.isDriveBay(slot) ? "в отсеке " + (PcCaseBlockEntity.bayIndex(slot) + 1) : "M.2 " + (slot - PcCaseBlockEntity.SLOT_M2 + 1);
        if (n.failed()) {
            be.bmc().log("диск " + where + " отказал");
            computer.queueEvent("disk_failed", where);
            be.markUpdated();
            if (detach(slot)) {
                // диск остался в слоте, но больше не работает
            }
        } else if (n.warning() && !h.warning()) {
            be.bmc().log("диск " + where + " просит замены (износ " + n.percent() + "%)");
            computer.queueEvent("disk_warning", where);
            be.markUpdated();
        }
    }

    // ======================= RAID для программ =======================

    @Override
    public Map<String, Object> raidInfo() {
        return onServer(level -> {
            MotherboardData board = be.motherboard();
            if (board == null || !board.server()) return null;
            Map<String, Object> m = new LinkedHashMap<>();
            Map<String, Object> st = raidStatus();
            if (st != null) m.put("array", st);
            Map<Object, Object> disks = new LinkedHashMap<>();
            for (int i = 0; i < be.bayCount(); i++) {
                ItemStack d = be.getStack(PcCaseBlockEntity.baySlot(i));
                Map<String, Object> e = new LinkedHashMap<>();
                if (d.isEmpty()) {
                    e.put("empty", true);
                } else {
                    DiskHealth h = DiskHealth.of(d);
                    e.put("kind", driveKind(d));
                    e.put("size", (double) diskCapacity(d));
                    e.put("wear", h.percent());
                    e.put("failed", h.failed());
                    e.put("member", isMember(RaidConfig.of(be.getStack(PcCaseBlockEntity.SLOT_MOTHERBOARD)), d));
                    e.put("rebuilding", PcCaseBlockEntity.baySlot(i) == rebuildSlot);
                }
                disks.put(i + 1, e);
            }
            m.put("disks", disks);
            return m;
        }, null);
    }

    @Override
    public String raidCreate(String level) {
        int lv = RaidConfig.parseLevel(level);
        if (lv < 0) return "Уровень: stripe (RAID 0), mirror (RAID 1) или raid5";
        return onServer(srv -> {
            MotherboardData board = be.motherboard();
            if (board == null || !board.server()) return "Нет RAID-контроллера: нужна серверная плата";
            ItemStack boardStack = be.getStack(PcCaseBlockEntity.SLOT_MOTHERBOARD);
            if (RaidConfig.of(boardStack) != null) return "Массив уже есть — сначала raid delete";
            List<Integer> disks = new ArrayList<>();
            for (int i = 0; i < be.bayCount(); i++) {
                int slot = PcCaseBlockEntity.baySlot(i);
                ItemStack d = be.getStack(slot);
                if (d.isEmpty()) continue;
                if (DiskHealth.of(d).failed()) return "В отсеке " + (i + 1) + " неисправный диск — замените его";
                disks.add(slot);
            }
            if (disks.size() < RaidConfig.minMembers(lv)) return "Нужно хотя бы " + RaidConfig.minMembers(lv) + " диска в отсеках";
            RaidConfig cfg = new RaidConfig(java.util.UUID.randomUUID().toString().substring(0, 8), lv, disks.size());
            cfg.writeTo(boardStack);
            for (int k = 0; k < disks.size(); k++) {
                ItemStack d = be.getStack(disks.get(k));
                d.remove(com.strikerr212.minepc.registry.ModDataComponents.DRIVE_ID.get());   // данные дисков стираются
                DiskHealth.of(d).withRaid(cfg.id(), k).writeTo(d);
            }
            be.bmc().log("RAID: создан " + cfg.levelName() + " из " + disks.size() + " дисков");
            be.markUpdated();
            reconfigure = true;
            return null;
        }, "Сервер не ответил");
    }

    @Override
    public String raidDelete() {
        return onServer(srv -> {
            ItemStack boardStack = be.getStack(PcCaseBlockEntity.SLOT_MOTHERBOARD);
            RaidConfig cfg = RaidConfig.of(boardStack);
            if (cfg == null) return "Массива нет";
            RaidConfig.clear(boardStack);
            for (int i = 0; i < be.bayCount(); i++) {
                ItemStack d = be.getStack(PcCaseBlockEntity.baySlot(i));
                if (!d.isEmpty() && cfg.id().equals(DiskHealth.of(d).raid())) {
                    d.remove(com.strikerr212.minepc.registry.ModDataComponents.DRIVE_ID.get());
                    DiskHealth.of(d).withoutRaid().writeTo(d);
                }
            }
            be.bmc().log("RAID: массив " + cfg.levelName() + " разобран");
            be.markUpdated();
            reconfigure = true;
            return null;
        }, "Сервер не ответил");
    }

    private long lastOverheatLog = -100000;

    // ======================= BMC =======================

    private String myName() {
        return label.isEmpty() ? be.address().substring(0, Math.min(8, be.address().length())) : label;
    }

    @Override
    public Object[] bmc(String target, String password, String command, String arg) {
        return onServer(level -> {
            if ((linkCode & 1) == 0) return null;   // BMC отвечает только по проводу
            String t = target.toLowerCase(java.util.Locale.ROOT);
            PcCaseBlockEntity found = null;
            for (var r : com.strikerr212.minepc.lan.LanManager.get(level).reach(level, be.hostRef())) {
                PcCaseBlockEntity o = level.isLoaded(r.pos()) ? Hosts.get(level, r.pos(), r.slot()) : null;
                if (o == null) continue;
                if (o.label().equalsIgnoreCase(target) || !t.isEmpty() && (o.address().startsWith(t) || o.bmc().address().startsWith(t))) {
                    found = o;
                    break;
                }
            }
            if (found == null) return new Object[]{false, "«" + target + "» в сети не найден", null};
            MotherboardData board = found.motherboard();
            if (board == null || !board.server()) return new Object[]{false, "У «" + target + "» нет BMC (обычная плата)", null};
            if (!found.bmc().present()) return new Object[]{false, "Нет ответа: у сервера нет питания", null};
            if (!found.bmc().checkPassword(password)) {
                found.bmc().log("неверный пароль BMC (" + myName() + ")");
                return new Object[]{false, "Неверный пароль BMC", null};
            }
            return found.bmc().command(level, command, arg, myName());
        }, new Object[]{false, "Нет ответа", null});
    }

    @Override
    public Map<String, Object> bmcLocal() {
        return onServer(level -> {
            if (!be.bmc().present()) return null;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("address", be.bmc().address());
            m.put("server", be.address());
            m.put("defaultPassword", be.bmc().defaultPassword());
            m.put("log", be.bmc().entries().size());
            return m;
        }, null);
    }

    @Override
    public boolean bmcSetPassword(String password) {
        if (password == null || password.length() < 4 || password.length() > 32) return false;
        return onServer(level -> {
            if (!be.bmc().present()) return false;
            be.bmc().setPassword(password);
            be.bmc().log("пароль BMC изменён (с самого сервера)");
            return true;
        }, false);
    }

    // ======================= Каждый тик =======================

    void tick(ServerLevel level, BlockPos pos) {
        age++;
        Runnable task;
        while ((task = serverTasks.poll()) != null) task.run();
        tickRebuild();
        if (age % 200 == 11 && computer != null && computer.isOn()) tickWear();
        // BMC следит за температурой: перегрев — в журнал (не чаще раза в минуту)
        if (age % 100 == 7 && computer != null && computer.isOn() && computer.cpuTemp() >= 90 && age - lastOverheatLog > 1200) {
            lastOverheatLog = age;
            be.bmc().log("перегрев процессора: " + Math.round(computer.cpuTemp()) + " °C");
        }
        if (age == 1 && (computer == null || !computer.isOn())) {
            // Мир загрузился («питание вернулось»): по настройке BIOS — как было, включиться или остаться выключенным
            int mode = bios().powerLoss();
            boolean start = mode == 1 || (mode == 0 && wantOn);
            if (start && be.motherboard() != null) {
                if (powerOn(level) != null) wantOn = false;
            } else if (mode == 2) {
                wantOn = false;
            }
        }
        if (age % SCAN_INTERVAL == 1) scanDevices(level, pos);
        if (age % 10 == 3) refreshNetwork(level, pos);
        if (labelDirty) {
            labelDirty = false;
            be.markUpdated();   // имя видно в окне корпуса и другим компьютерам сети
        }

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
        tickSounds(level, pos);
    }

    long lastTickNanos() { return lastTickNanos; }

    private void scanDevices(ServerLevel level, BlockPos pos) {
        if (be.isModule()) {
            // Сервер в стойке: экрана рядом нет, клавиатура — консоль в той же стойке
            hasKeyboard = RackBlock.hasConsole(level, pos);
            keyboardPos = null;
            if (computer != null && computer.isOn()) updateRemovable(level, true);
            return;
        }
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
        hasKeyboard = be.isModule() ? RackBlock.hasConsole(level, pos)
                : DeviceBlock.hasKeyboard(level, pos) || level.getBlockState(pos).getBlock() instanceof PcCaseBlock b && b.hasKeyboard();
        keyboardPos = DeviceBlock.keyboardPos(level, pos);
        if (computer != null && computer.isOn()) updateRemovable(level, true);
    }

    /** Дискеты в дисководах рядом и флешки в USB-портах: подключить новые, отключить вынутые. */
    void updateRemovable(ServerLevel level, boolean notify) {
        if (computer == null || !computer.isOn() && notify) return;
        Map<String, ItemStack> wanted = new LinkedHashMap<>();
        Map<String, BlockPos> drivePos = new HashMap<>();
        int disks = 0;
        for (Direction d : Direction.values()) {
            BlockPos n = be.getBlockPos().relative(d);
            if (level.getBlockEntity(n) instanceof DiskDriveBlockEntity drive && !drive.disk().isEmpty()) {
                drivePos.put(disks == 0 ? "disk" : "disk" + (disks + 1), n.immutable());
                wanted.put(disks == 0 ? "disk" : "disk" + (disks + 1), drive.disk());
                disks++;
            }
        }
        boolean usbOn = bios().usb();
        for (int i = 0; usbOn && i < PcCaseBlockEntity.USB_SLOTS; i++) {
            ItemStack stack = be.getStack(PcCaseBlockEntity.SLOT_USB + i);
            if (stack.getItem() instanceof DriveItem) wanted.put(i == 0 ? "usb" : "usb" + (i + 1), stack);
        }
        // Для BIOS: где флешка и дискета (первые найденные)
        bootPaths.remove("usb");
        bootPaths.remove("floppy");
        for (Map.Entry<String, ItemStack> e : wanted.entrySet()) {
            boolean fl = e.getValue().getItem() instanceof DriveItem d && d.kind() == DriveItem.Kind.FLOPPY;
            bootPaths.putIfAbsent(fl ? "floppy" : "usb", "/" + e.getKey());
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
            Mount mount = DriveStorage.mount(level.getServer(), stack, drive.capacity(), label).withSpeed(speed, floppy ? 256 : 32);
            fs.mount(e.getKey(), mount);
            if (floppy && drivePos.containsKey(e.getKey())) soundMounts.put(mount, drivePos.get(e.getKey()));   // дисковод скрежещет
            removable.put(e.getKey(), DriveItem.id(stack));
            if (notify) computer.queueEvent("disk", "/" + e.getKey());
        }
    }

    // ======================= Звук =======================

    /** Накопители, которые слышно: жёсткий диск (звучит из корпуса) и дискеты (из своего дисковода). */
    private final Map<Mount, BlockPos> soundMounts = new java.util.concurrent.ConcurrentHashMap<>();
    /** Где недавно работали с диском (заполняют потоки программ, разбирает тик). */
    private final java.util.Set<BlockPos> diskBusy = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<BlockPos, Long> lastDiskSound = new HashMap<>();
    private int lastHum = -1;
    private boolean humStart;
    private long lastHumTick;
    private boolean hasHdd;
    private volatile BlockPos keyboardPos;
    private long lastKeyClickNanos;

    /** Звуковой чип платы: каналов больше на платах дороже. */
    @Override
    public int soundChannels() {
        var board = be.motherboard();
        if (board == null) return 0;
        return switch (board.tier()) {
            case 1 -> 2;
            case 2 -> 4;
            case 3 -> 6;
            default -> 8;
        };
    }

    /** Команды звука — игрокам рядом (из потока программы: список зрителей обновляет тик). */
    @Override
    public void audio(long startMs, long nowMs, byte[] commands) {
        BlockPos p = be.getBlockPos();
        var payload = new com.strikerr212.minepc.network.SoundPayload(be.hostRef(), startMs, nowMs, commands);
        for (net.minecraft.server.level.ServerPlayer player : viewers) {
            if (player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) < 48 * 48) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    @Override
    public void diskActivity(Mount mount, boolean write) {
        BlockPos p = soundMounts.get(mount);
        if (p != null) diskBusy.add(p);
    }

    /** Кнопка питания, раскрутка диска и писк самотеста. */
    private void startSounds(ServerLevel level, SystemModel.Input in, SystemModel.Report report) {
        hasHdd = false;
        for (int i = 0; i < PcCaseBlockEntity.MAX_BAYS; i++) {
            if (be.getStack(PcCaseBlockEntity.baySlot(i)).getItem() instanceof HddItem) hasHdd = true;
        }
        if (hasHdd) level.playSound(null, be.getBlockPos(), com.strikerr212.minepc.registry.ModSounds.HDD_SPINUP.get(),
                net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1f);
        // Самотест прошёл — один короткий писк; без видеокарты — длинный и два коротких, как у настоящих плат
        boolean noGpu = report.notes().stream().anyMatch(n -> n.issue() == SystemModel.Issue.NO_GPU);
        double[] f = noGpu ? new double[]{900, 900, 900} : new double[]{1000};
        double[] d = noGpu ? new double[]{0.6, 0.15, 0.15} : new double[]{0.12};
        audio(700, 0, com.strikerr212.minepc.computer.audio.Audio.beepCommands(f, d, 0.12));
        lastHum = -1;
        humStart = true;
    }

    /** Компьютер не включился: коды ошибок писком (нет памяти — три длинных, нет диска — один короткий). */
    void errorBeeps(List<SystemModel.Note> reasons) {
        boolean ram = false, storage = false;
        for (SystemModel.Note n : reasons) {
            if (n.issue() == SystemModel.Issue.NO_RAM) ram = true;
            if (n.issue() == SystemModel.Issue.NO_STORAGE) storage = true;
        }
        // Без платы, процессора или питания пищать нечему
        for (SystemModel.Note n : reasons) {
            switch (n.issue()) {
                case NO_BOARD, NO_CPU, NO_PSU, PSU_OVERLOAD -> {
                    return;
                }
                default -> {
                }
            }
        }
        if (ram) audio(-1, 0, com.strikerr212.minepc.computer.audio.Audio.beepCommands(
                new double[]{700, 700, 700}, new double[]{0.5, 0.5, 0.5}, 0.25));
        else if (storage) audio(-1, 0, com.strikerr212.minepc.computer.audio.Audio.beepCommands(
                new double[]{1000}, new double[]{0.12}, 0));
    }

    /** Игрок нажал клавишу: стук слышат остальные рядом (сам он уже слышал его у себя). */
    void keyClick(net.minecraft.world.entity.player.Player player) {
        long now = System.nanoTime();
        if (now - lastKeyClickNanos < 30_000_000L || !(be.getLevel() instanceof ServerLevel level)) return;
        lastKeyClickNanos = now;
        BlockPos at = keyboardPos != null ? keyboardPos : be.getBlockPos();
        level.playSound(player, at, com.strikerr212.minepc.registry.ModSounds.KEY.get(), net.minecraft.sounds.SoundSource.BLOCKS,
                0.4f, 0.9f + level.random.nextFloat() * 0.2f);
    }

    /** Каждый тик: треск дисков (не чаще, чем успевает головка) и гул вентиляторов для игроков рядом. */
    private void tickSounds(ServerLevel level, BlockPos pos) {
        if (!diskBusy.isEmpty()) {
            for (BlockPos p : new ArrayList<>(diskBusy)) {
                diskBusy.remove(p);
                boolean floppy = !p.equals(pos);
                long gap = floppy ? 12 : 3 + level.random.nextInt(5);
                Long last = lastDiskSound.get(p);
                if (last != null && age - last < gap) continue;
                lastDiskSound.put(p, age);
                level.playSound(null, p, (floppy ? com.strikerr212.minepc.registry.ModSounds.FLOPPY
                                : com.strikerr212.minepc.registry.ModSounds.HDD_SEEK).get(), net.minecraft.sounds.SoundSource.BLOCKS,
                        floppy ? 0.7f : 0.4f, 0.92f + level.random.nextFloat() * 0.16f);
            }
        }
        int fan = 0;
        if (computer != null && computer.isOn()) {
            int rpm = computer.fanRpm();
            // без кулера на процессоре всё равно крутится вентилятор блока питания
            fan = rpm <= 0 ? 15 : (int) Math.round(15 + 85 * Math.max(0, Math.min(1, (rpm - 600) / 1600.0)));
        }
        boolean changed = lastHum < 0 || Math.abs(fan - lastHum) >= 8 || (fan == 0) != (lastHum == 0);
        if (changed || (fan > 0 && age - lastHumTick >= 40)) {
            lastHum = fan;
            lastHumTick = age;
            var payload = new com.strikerr212.minepc.network.HumPayload(be.hostRef(), fan, fan > 0 && hasHdd, humStart);
            humStart = false;
            for (net.minecraft.server.level.ServerPlayer player : viewers) {
                if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 32 * 32) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
        }
    }

    // ======================= Экран для игроков =======================

    /** Игрок открыл экран — пусть получит весь кадр, а не только изменения. */
    void requestFullFrame() {
        forceFull = true;
    }

    /** Главный поток (каждый тик): кто смотрит на экран; новым зрителям — весь кадр. Сама отправка — в {@link ScreenSender}. */
    private void syncScreen(ServerLevel level, BlockPos pos) {
        lastTickNanos = System.nanoTime();
        List<net.minecraft.server.level.ServerPlayer> now = level.getChunkSource().chunkMap.getPlayers(new ChunkPos(pos), false);
        List<net.minecraft.server.level.ServerPlayer> before = viewers;
        if (!before.containsAll(now)) forceFull = true;
        viewers = List.copyOf(now);
        var server = level.getServer();
        unlimited = server == null || (server.isSingleplayer() && !server.isPublished());
        ScreenSender.track(this);
        flushScreen(true);
    }

    /**
     * Отправить кадр, если экран изменился (из потока {@link ScreenSender} или из тика).
     * Не чаще screenFps раз в секунду и, на сервере, не больше screenKbPerSecond на зрителя.
     */
    void flushScreen(boolean fromTick) {
        synchronized (screenLock) {
            long now = System.nanoTime();
            Computer c = computer;
            boolean on = c != null && c.state() != Computer.State.OFF;
            int version = on ? c.terminal().version() : -1;
            boolean changed = version != lastVersion;
            boolean keepalive = now - lastFullNanos >= KEEPALIVE_NANOS;
            boolean full = keepalive || forceFull;
            // Запас трафика копится не больше чем на 1/4 секунды
            long rate = (long) com.strikerr212.minepc.MineConfig.screenKbPerSecond() * 1024;
            if (lastSentNanos != 0) screenTokens = Math.min(rate / 4.0, screenTokens + rate * ((now - lastSentNanos) / 1e9));
            if (!changed && !full) return;
            if (!full) {
                long minGap = 1_000_000_000L / Math.max(10, com.strikerr212.minepc.MineConfig.screenFps());
                if (now - lastSentNanos < minGap) return;
                // Программа ещё рисует кадр (картинка уже готова, а строка состояния — нет): подождать,
                // пока все программы не уйдут ждать событие, но не дольше 1/30 секунды. Так кадр приходит целиком.
                if (on && now - lastSentNanos < BUSY_GAP_NANOS && !c.idle()) return;
                if (!unlimited && screenTokens < 0) return;
            }
            byte[] data;
            if (on) {
                byte[] frame = c.terminal().snapshot();
                data = TerminalCodec.encode(full ? null : lastFrame, frame);
                lastFrame = frame;
            } else {
                data = new byte[0];
                lastFrame = null;
            }
            TerminalPayload payload = new TerminalPayload(be.hostRef(), data, hasKeyboard);
            for (net.minecraft.server.level.ServerPlayer p : viewers) {
                PacketDistributor.sendToPlayer(p, payload);
            }
            if (!viewers.isEmpty()) FRAMES_SENT.incrementAndGet();
            screenTokens -= data.length;
            lastSentNanos = now;
            if (full) lastFullNanos = now;
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
