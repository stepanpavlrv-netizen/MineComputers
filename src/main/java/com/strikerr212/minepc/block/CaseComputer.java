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
    private static final long SETTLE_NANOS = 1_500_000L;
    private int seenVersion = -2;
    private long seenChangeNanos;

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
        com.strikerr212.minepc.lan.LanManager.get(level).post(be.getBlockPos(), target, port, payload, bytes);
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
        if (age % 20 == 3) refreshWifi(level, pos);
        var lan = com.strikerr212.minepc.lan.LanManager.get(level);
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        long wired = !bios().lan() ? 0
                : state.getBlock() instanceof com.strikerr212.minepc.lan.LanDevice d ? d.lanBitsPerSecond(level, pos, state) : 0;
        boolean cable = wired > 0 && lan.linked(level, pos);
        Computer.WifiStatus ws = wifiStatus;
        boolean wifi = ws != null && ws.connected();
        long bits = Math.max(cable ? wired : 0, wifi ? ws.mbit() * 1_000_000L : 0);
        boolean linked = cable || wifi;
        List<Computer.Peer> list = new java.util.ArrayList<>();
        if (linked) {
            for (var r : lan.reach(level, pos)) {
                if (level.isLoaded(r.pos()) && level.getBlockEntity(r.pos()) instanceof PcCaseBlockEntity other && other.computerState() == 1) {
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
        return want.isEmpty() || want.equals(password);
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
        for (BlockPos p : com.strikerr212.minepc.lan.LanManager.get(level).switches(level, be.getBlockPos())) {
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
            for (BlockPos p : com.strikerr212.minepc.lan.LanManager.get(level).switches(level, be.getBlockPos())) {
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
                    default -> "cord";
                });
                m.put("n", port.number());
                m.put("link", port.link());
                String dev = port.device();
                m.put("device", dev.startsWith("@") ? dev.substring(1) : dev.equals("!") ? "слишком длинный кабель" : dev);
                m.put("enabled", port.enabled());
                m.put("vlan", com.strikerr212.minepc.lan.SwitchPorts.vlanName(port.vlan()));
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
            if (port < 0 || port >= com.strikerr212.minepc.lan.LanData.PORTS) return "Нет такого порта";
            var data = com.strikerr212.minepc.lan.LanData.get(level);
            int v = vlan == -2 ? com.strikerr212.minepc.lan.LanData.portVlan(data.portConfig(p, port)) : vlan;
            data.setPort(p, port, enabled, v);
            return null;
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
        net.minecraft.world.phys.Vec3 from = net.minecraft.world.phys.Vec3.atCenterOf(a), to = net.minecraft.world.phys.Vec3.atCenterOf(b);
        double len = from.distanceTo(to);
        int steps = (int) Math.ceil(len * 4);
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        int count = 0;
        for (int i = 1; i < steps; i++) {
            BlockPos p = BlockPos.containing(from.lerp(to, i / (double) steps));
            if (p.equals(a) || p.equals(b) || !seen.add(p) || !level.isLoaded(p)) continue;
            if (level.getBlockState(p).isSolidRender(level, p)) count++;
        }
        return count;
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
            int effective = Math.min(adapter.range(), router.radius()) - 3 * walls(level, pos, r);
            double dist = Math.sqrt(pos.distSqr(r));
            if (effective <= 0 || dist > effective) continue;
            int signal = (int) Math.round(10 + 90 * (1 - dist / effective));
            int factor = signal >= 60 ? 4 : signal >= 35 ? 2 : 1;
            int mbit = Math.max(1, Math.min(adapter.mbit(), com.strikerr212.minepc.lan.RouterBlockEntity.RADIO_MBIT) * factor / 4);
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
        if (be.getLevel() instanceof ServerLevel level) com.strikerr212.minepc.lan.LanData.get(level).removeWifi(be.getBlockPos());
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
        BiosSettings bios = bios();
        if (bios.cores() > 0 && bios.cores() < limits.cores()) {
            limits = new Computer.Limits(limits.opsPerTick(), bios.cores(), limits.memoryBytes(), limits.gpuCellsPerTick(),
                    limits.vramCells(), limits.bootTicks(), limits.maxProcesses());
        }
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
        // Тепло и питание: температура в диспетчере задач и в BIOS считается по этим деталям
        int cpuCooling = in.cooler() != null ? in.cooler().coolingW() : Perf.PASSIVE_COOLING;
        int gpuCooling = gpu == null ? 0 : gpu.hasCooler() ? gpu.coolingW() : Perf.PASSIVE_COOLING;
        computer.setThermal(new Computer.Thermal(cpu.tdp(), cpuCooling, gpu == null ? 0 : gpu.tdp(), gpuCooling,
                gpu != null, in.powerDraw(), in.psu() == null ? 0 : in.psu().wattage(), in.cooler() != null));

        // Накопители: системный — самый быстрый (M.2, потом SATA SSD, потом жёсткий диск)
        var fs = computer.fileSystem();
        for (String m : fs.mounts().keySet()) fs.unmount(m);
        removable.clear();
        fs.mount("rom", ComputerRom.mount());
        // Системный диск — первый по порядку загрузки в BIOS (по умолчанию: M.2, SATA SSD, жёсткий диск)
        List<int[]> kinds = new ArrayList<>();
        for (String id : bios.bootOrder()) {
            switch (id) {
                case "nvme" -> kinds.add(new int[]{PcCaseBlockEntity.SLOT_M2, PcCaseBlockEntity.SLOT_M2 + 1});
                case "sata" -> kinds.add(new int[]{PcCaseBlockEntity.SLOT_SATA_SSD, PcCaseBlockEntity.SLOT_SATA_SSD + 1});
                case "hdd" -> kinds.add(new int[]{PcCaseBlockEntity.SLOT_HDD, PcCaseBlockEntity.SLOT_HDD + 1});
                default -> { }
            }
        }
        int[] order = kinds.stream().flatMapToInt(java.util.Arrays::stream).toArray();
        bootPaths.keySet().removeAll(List.of("nvme", "sata", "hdd"));
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
            String kind = stack.getItem() instanceof SsdItem ? (SsdItem.data(stack).isNvme() ? "nvme" : "sata") : "hdd";
            if (!root) {
                fs.mount("", mount);
                root = true;
                bootPaths.putIfAbsent(kind, "/");
            } else {
                String at = data == 0 ? "data" : "data" + (data + 1);
                fs.mount(at, mount);
                bootPaths.putIfAbsent(kind, "/" + at);
                data++;
            }
        }
        be.setChanged();   // накопители могли получить номера
        updateRemovable(level, false);
    }

    // ======================= Каждый тик =======================

    void tick(ServerLevel level, BlockPos pos) {
        age++;
        Runnable task;
        while ((task = serverTasks.poll()) != null) task.run();
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
    }

    long lastTickNanos() { return lastTickNanos; }

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
        hasKeyboard = DeviceBlock.hasKeyboard(level, pos)
                || level.getBlockState(pos).getBlock() instanceof PcCaseBlock b && b.hasKeyboard();
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
            if (version != seenVersion) {
                seenVersion = version;
                seenChangeNanos = now;
            }
            if (!full) {
                long minGap = 1_000_000_000L / Math.max(10, com.strikerr212.minepc.MineConfig.screenFps());
                if (now - lastSentNanos < minGap) return;
                // Программа ещё рисует кадр (например, картинка уже готова, а строка состояния — нет):
                // подождать, пока экран 1.5 мс не меняется, но не дольше 1/20 секунды. Так кадр приходит целиком.
                if (now - seenChangeNanos < SETTLE_NANOS && now - lastSentNanos < 50_000_000L) return;
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
            TerminalPayload payload = new TerminalPayload(be.getBlockPos(), data, hasKeyboard);
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
