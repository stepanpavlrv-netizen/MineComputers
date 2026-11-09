package com.strikerr212.minepc.computer;

import com.strikerr212.minepc.computer.fs.FileSystem;
import com.strikerr212.minepc.computer.fs.Mount;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Один работающий компьютер: экран, файлы, видеокарта и процессы на Lua.
 *
 * Как устроено время. Каждый тик сервер вызывает {@link #tick()}: каждый процесс получает порцию команд
 * (скорость ядра процессора) и, если ему есть что делать, встаёт в очередь {@link ComputerExecutor}.
 * Там программа выполняется, пока не начнёт ждать событие или пока не кончится порция — тогда она
 * замирает посреди работы и продолжит в следующем тике.
 *
 * Характеристики железа — настоящие лимиты ({@link Limits}):
 * <ul>
 *   <li>процессор — команд за тик на ядро; ядер столько, сколько процессов работает одновременно;</li>
 *   <li>память — сколько данных могут держать программы (иначе «Недостаточно памяти»);</li>
 *   <li>видеокарта — размер экрана, цвета, скорость отрисовки и видеопамять для буферов;</li>
 *   <li>накопители — объём и скорость чтения/записи; от накопителя зависит и время загрузки.</li>
 * </ul>
 *
 * Здесь нет ничего из Minecraft: системный блок передаёт сюда только числа и накопители.
 */
public final class Computer {

    public enum State { OFF, RUNNING, CRASHED }

    /** Что компьютеру нужно знать о мире. Реализует системный блок. */
    public interface Host {
        /** Время суток в часах 0..24. */
        default double timeOfDay() { return 12; }

        default int day() { return 1; }

        default void onLabelChanged(String label) { }

        /** Постоянный адрес компьютера (UUID, как в OpenComputers). */
        default String address() { return "00000000-0000-4000-8000-000000000000"; }

        // ----- Сеть -----

        /**
         * Отправить сообщение по сети (вызывается из потока программы — только поставить в очередь).
         * target — адрес, его начало или имя компьютера; null — всем в сети. false — сети нет.
         */
        default boolean netSend(String target, int port, Object[] payload, int bytes) { return false; }

        /** Скорость сетевого порта (байт/с в игре); 0 — кабеля нет или порт не подключён. */
        default long netBytesPerSecond() { return 0; }

        /** Кто ещё в сети: {адрес, имя, переходов}. */
        default java.util.List<Peer> netPeers() { return java.util.List.of(); }

        // ----- Wi-Fi -----

        /** Wi-Fi адаптер и подключение; null — адаптера нет. */
        default WifiStatus wifiStatus() { return null; }

        /** Сети вокруг (обновляется раз в секунду), сильные первыми. */
        default java.util.List<WifiNet> wifiScan() { return java.util.List.of(); }

        /** Запомнить сеть и подключаться к ней (сам компьютер подключится в течение секунды). false — нет адаптера. */
        default boolean wifiConnect(String ssid, String password) { return false; }

        default void wifiDisconnect() { }
    }

    /** Сеть Wi-Fi в эфире: имя, сигнал %, есть ли пароль, скорость с этим адаптером (Мбит/с). */
    public record WifiNet(String ssid, int signal, boolean secured, int mbit) {
    }

    /**
     * Wi-Fi адаптер: его стандарт, выбранная сеть (или ""), подключён ли, сигнал %, скорость (Мбит/с)
     * и почему не подключён ("" — всё хорошо или сеть не выбрана).
     */
    public record WifiStatus(String adapter, String ssid, boolean connected, int signal, int mbit, String error) {
    }

    /** Компьютер в сети: адрес, имя (может быть пустым) и сколько коммутаторов до него. */
    public record Peer(String address, String name, int hops) {
    }

    /** Сведения о железе для программ (computer.info()). */
    public record Info(String cpu, int cores, int clockMhz, String architecture, int ramKb, int opsPerSecond,
                       String gpu, int score) {
    }

    /**
     * Лимиты железа.
     *
     * @param opsPerTick      команд Lua за тик на одно ядро
     * @param cores           сколько процессов работает одновременно на полной скорости
     * @param memoryBytes     память для программ (все процессы вместе)
     * @param gpuCellsPerTick сколько клеток экрана видеокарта меняет за тик
     * @param vramCells       видеопамять для буферов, клеток
     * @param bootTicks       сколько длится загрузка системы
     * @param maxProcesses    сколько процессов можно запустить всего
     */
    public record Limits(int opsPerTick, int cores, long memoryBytes, int gpuCellsPerTick, long vramCells,
                         int bootTicks, int maxProcesses) {
        public static Limits basic(int opsPerTick) {
            return new Limits(opsPerTick, 1, 64L << 20, 1_000_000, 200_000, 0, 16);
        }

        public Limits withOps(int ops) {
            return new Limits(Math.max(100, ops), cores, memoryBytes, gpuCellsPerTick, vramCells, bootTicks, maxProcesses);
        }
    }

    public static final int TICKS_PER_SECOND = 20;
    /** Сколько порций можно накопить, пока программа ждёт (чтобы после ожидания сразу был запас). */
    static final int BUDGET_BANK_TICKS = 2;
    /** Размер экрана после включения (программа может увеличить его до максимума). */
    static final int BOOT_WIDTH = 160, BOOT_HEIGHT = 50;

    private final Host host;
    private final Terminal terminal = new Terminal(50, 16);
    private final FileSystem fs = new FileSystem();
    private final Gpu gpu = new Gpu(this);
    /** События от железа (клавиатура, мышь, диски) получает главный процесс. */
    private final EventQueue mainEvents = new EventQueue();
    private final CopyOnWriteArrayList<LuaMachine> machines = new CopyOnWriteArrayList<>();

    private volatile State state = State.OFF;
    private volatile Limits limits = Limits.basic(10_000);
    private volatile Info info = new Info("?", 1, 0, "Lua", 0, 0, "", 0);
    private volatile String label = "";
    private volatile String crashMessage = "";

    private volatile LuaMachine main;
    private int nextPid = 1;
    private volatile boolean stopRequested, rebootRequested;
    private volatile int bootRemaining;

    /** Сколько тиков назад нажали Ctrl+T, а программа так и не прочитала событие (-1 — не нажимали). */
    private volatile int terminateWait = -1;
    private static final int TERMINATE_GRACE_TICKS = 20;

    private long ticks;
    private record Timer(long due, EventQueue target) {
    }
    private final Map<Integer, Timer> timers = new HashMap<>();
    private int nextTimer = 1;

    /** Сколько команд выполнено за последнюю секунду (все процессы вместе). */
    private volatile long opsLastSecond;
    private final AtomicLong opsThisSecond = new AtomicLong();

    /** Запас видеокарты (клеток); меньше нуля — программы ждут, пока видеокарта дорисует. */
    private final AtomicLong gpuBudget = new AtomicLong();
    /** Запас каждого накопителя (байт); меньше нуля — программы ждут, пока диск дочитает/допишет. */
    private final Map<Mount, long[]> ioBudget = new IdentityHashMap<>();
    private volatile boolean ioWait;

    // ----- Сеть -----
    /** Самое большое сообщение, байт (как у сетевой карты OpenComputers). */
    public static final int NET_MAX_PACKET = 8192;
    /** Порты, которые слушают программы. */
    private final java.util.Set<Integer> openPorts = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Запас сетевого порта (байт); меньше нуля — программа ждёт, пока сообщение уйдёт. */
    private final AtomicLong netBudget = new AtomicLong();
    private final AtomicLong netSent = new AtomicLong(), netReceived = new AtomicLong();

    public Computer(Host host) {
        this.host = host;
        fs.setMeter(this::chargeIo);
    }

    // ======================= Состояние =======================

    public Terminal terminal() { return terminal; }
    public FileSystem fileSystem() { return fs; }
    public State state() { return state; }
    public boolean isOn() { return state == State.RUNNING; }
    public String crashMessage() { return crashMessage; }
    public Info info() { return info; }
    public Limits limits() { return limits; }
    public long opsLastSecond() { return opsLastSecond; }
    public int opsPerTick() { return limits.opsPerTick(); }
    public String label() { return label; }
    public String address() { return host.address(); }
    Gpu gpu() { return gpu; }
    /** Идёт загрузка системы (программы ещё не запущены). */
    public boolean booting() { return state == State.RUNNING && bootRemaining > 0; }

    public void setInfo(Info info) { this.info = info; }

    public void setLimits(Limits limits) { this.limits = limits; }

    /** Лимит процессора: команд за тик (на ядро). */
    public void setOpsPerTick(int ops) { limits = limits.withOps(ops); }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
    }

    double uptimeSeconds() {
        synchronized (timers) {
            return ticks / (double) TICKS_PER_SECOND;
        }
    }

    // ======================= Включение и выключение =======================

    public synchronized void turnOn() {
        if (state == State.RUNNING) return;
        machines.clear();
        main = null;
        mainEvents.clear();
        terminateWait = -1;
        synchronized (timers) {
            timers.clear();
            ticks = 0;
        }
        synchronized (ioBudget) {
            ioBudget.clear();
            ioWait = false;
        }
        gpu.reset();
        gpuBudget.set(limits.gpuCellsPerTick());
        openPorts.clear();
        netBudget.set(0);
        stopRequested = rebootRequested = false;
        crashMessage = "";
        nextPid = 1;
        for (int i = 0; i < 16; i++) terminal.setPaletteColor(i, Terminal.DEFAULT_PALETTE[i]);
        terminal.setDepth(terminal.maxDepth());
        terminal.setTextIndex(Terminal.WHITE);
        terminal.setBackIndex(Terminal.BLACK);
        terminal.resize(Math.min(BOOT_WIDTH, terminal.maxWidth()), Math.min(BOOT_HEIGHT, terminal.maxHeight()));
        terminal.setCursor(0, 0);
        terminal.setCursorBlink(false);
        bootRemaining = limits.bootTicks();
        state = State.RUNNING;
        if (bootRemaining > 0) drawBoot();
    }

    /**
     * Выключить. Безопасно вызывать с любого потока: работающие программы остановятся.
     * Замки процессов берутся вне замка компьютера — иначе упавший процесс и сервер могли бы ждать друг друга.
     */
    public void shutdown() {
        List<LuaMachine> list;
        synchronized (this) {
            list = new ArrayList<>(machines);
            machines.clear();
            main = null;
            state = State.OFF;
            stopRequested = rebootRequested = false;
            bootRemaining = 0;
        }
        for (LuaMachine m : list) m.kill();
        for (LuaMachine m : list) m.closeLocked();
        gpu.reset();
        terminal.clear();
        terminal.setCursor(0, 0);
    }

    public void reboot() {
        shutdown();
        turnOn();
    }

    void requestShutdown() { stopRequested = true; }

    void requestReboot() { rebootRequested = true; stopRequested = true; }

    private void drawBoot() {
        int total = Math.max(1, limits.bootTicks());
        int done = total - bootRemaining;
        int pct = done * 100 / total;
        int w = terminal.width();
        terminal.setTextIndex(Terminal.WHITE);
        terminal.setBackIndex(Terminal.BLACK);
        terminal.clear();
        terminal.setCursor(1, 1);
        terminal.write("MineComputers");
        terminal.setCursor(1, 2);
        terminal.write("Загрузка системы… " + pct + "%");
        int bar = Math.max(4, Math.min(40, w - 2));
        int fill = bar * done / total;
        terminal.setCursor(1, 3);
        terminal.write("█".repeat(fill) + "░".repeat(bar - fill));
    }

    // ======================= События =======================

    /** Добавить событие главному процессу: имя и значения (строки, числа, логические, null). */
    public void queueEvent(String name, Object... args) {
        if (state != State.RUNNING) return;
        mainEvents.offer(event(name, args));
    }

    static Object[] event(String name, Object... args) {
        Object[] ev = new Object[args.length + 1];
        ev[0] = name;
        System.arraycopy(args, 0, ev, 1, args.length);
        return ev;
    }

    /** Ctrl+T: попросить программу завершиться. Если она занята и не читает события — прервать через секунду. */
    public void terminate() {
        if (state != State.RUNNING) return;
        queueEvent("terminate");
        terminateWait = 0;
    }

    void terminateDelivered() {
        terminateWait = -1;
    }

    int startTimer(double seconds, EventQueue target) {
        synchronized (timers) {
            int id = nextTimer++;
            long dueTicks = ticks + Math.max(1, Math.round(seconds * TICKS_PER_SECOND));
            timers.put(id, new Timer(dueTicks, target));
            return id;
        }
    }

    void cancelTimer(int id) {
        synchronized (timers) {
            timers.remove(id);
        }
    }

    // ======================= Процессы =======================

    List<LuaMachine> machines() { return machines; }

    LuaMachine machine(int pid) {
        for (LuaMachine m : machines) if (m.pid == pid) return m;
        return null;
    }

    /** Запустить программу отдельным процессом (на своём ядре). Возвращает номер процесса. */
    synchronized int spawn(LuaMachine parent, String path, Object[] args) throws LuaMachine.Crash {
        if (state != State.RUNNING) throw new LuaMachine.Crash("Компьютер выключается");
        if (machines.size() >= limits.maxProcesses()) {
            throw new LuaMachine.Crash("Слишком много процессов (не больше " + limits.maxProcesses() + ")");
        }
        int pid = nextPid++;
        machines.add(new LuaMachine(this, pid, parent == null ? 0 : parent.pid, new EventQueue(), path, args));
        return pid;
    }

    /** Процесс закончил работу (поток процесса). */
    void machineDone(LuaMachine m, boolean ok, String error) {
        if (m == main) {
            if (ok) stopRequested = true;       // BIOS завершился — выключаемся
            else crash(error);
            return;
        }
        machines.remove(m);
        LuaMachine parent = machine(m.parentPid);
        if (parent == null) parent = main;
        if (parent != null && state == State.RUNNING) {
            parent.events.offer(event("process_exit", m.pid, ok, error));
        }
    }

    // ======================= Работа =======================

    /** Вызывается сервером каждый тик (главный поток). */
    public void tick() {
        if (state != State.RUNNING) return;
        if (stopRequested) {
            boolean reboot = rebootRequested;
            shutdown();
            if (reboot) turnOn();
            return;
        }
        synchronized (timers) {
            ticks++;
            for (Iterator<Map.Entry<Integer, Timer>> it = timers.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, Timer> e = it.next();
                if (e.getValue().due() <= ticks) {
                    it.remove();
                    e.getValue().target().offer(event("timer", e.getKey()));
                }
            }
            if (ticks % TICKS_PER_SECOND == 0) opsLastSecond = opsThisSecond.getAndSet(0);
        }
        if (bootRemaining > 0) {
            bootRemaining--;
            if (bootRemaining > 0) {
                if (bootRemaining % 2 == 0) drawBoot();
                return;
            }
            terminal.clear();
            terminal.setCursor(0, 0);
        }
        Limits l = limits;
        if (main == null) {
            synchronized (this) {
                if (main == null && state == State.RUNNING) {
                    main = new LuaMachine(this, nextPid++, 0, mainEvents, null, null);
                    machines.add(0, main);
                }
            }
        }
        // Порции команд: каждое ядро — один процесс на полной скорости; процессов больше, чем ядер, — делят
        int alive = Math.max(1, machines.size());
        double share = Math.min(1.0, l.cores() / (double) alive);
        long per = Math.max(100, Math.round(l.opsPerTick() * share));
        for (LuaMachine m : machines) m.refill(per);
        gpuBudget.updateAndGet(b -> Math.min(b + l.gpuCellsPerTick(), (long) l.gpuCellsPerTick() * BUDGET_BANK_TICKS));
        refillIo();
        long net = host.netBytesPerSecond() / TICKS_PER_SECOND;
        netBudget.updateAndGet(b -> Math.min(b + net, net * BUDGET_BANK_TICKS));

        if (terminateWait >= 0 && ++terminateWait > TERMINATE_GRACE_TICKS) {
            terminateWait = -1;
            // Событие так и не прочитали — убираем его и прерываем программу ошибкой
            mainEvents.removeNamed("terminate");
            LuaMachine m = main;
            if (m != null) m.abort();
        }
        for (LuaMachine m : machines) {
            if (m.needsWork() && m.scheduled.compareAndSet(false, true)) ComputerExecutor.submit(m);
        }
    }

    void addOps(long ops) {
        opsThisSecond.addAndGet(ops);
    }

    /** Программы должны подождать: видеокарта или накопитель не успевают. */
    boolean mustWait() {
        return gpuBudget.get() < 0 || ioWait || netBudget.get() < 0;
    }

    // ======================= Сеть =======================


    public Host host() { return host; }

    public boolean openPort(int port) { return openPorts.add(port); }
    public boolean closePort(int port) { return openPorts.remove(port); }
    public boolean isPortOpen(int port) { return openPorts.contains(port); }
    public void closeAllPorts() { openPorts.clear(); }

    public long netSent() { return netSent.get(); }
    public long netReceived() { return netReceived.get(); }

    /** Отправка: сообщение уходит со скоростью порта (программа подождёт, если порт занят). */
    boolean netSend(String target, int port, Object[] payload, int bytes) {
        if (!host.netSend(target, port, payload, bytes)) return false;
        netBudget.addAndGet(-bytes);
        netSent.addAndGet(bytes);
        return true;
    }

    /** Служебный порт: эхо-запросы ping. Программы его открыть не могут (порты 1..65535). */
    public static final int PING_PORT = 0;
    private static final int PING_BYTES = 32;
    private final java.util.concurrent.atomic.AtomicInteger nextPing = new java.util.concurrent.atomic.AtomicInteger(1);

    private long nowTicks() {
        synchronized (timers) {
            return ticks;
        }
    }

    /**
     * Эхо-запрос: уходит по сети как обычное сообщение, адресат (если включён) сразу отвечает,
     * а сюда приходит событие net_pong(адрес, номер, мс туда и обратно, переходов). Возвращает номер или -1.
     */
    public int netPing(String target) {
        int id = nextPing.getAndIncrement();
        if (!netSend(target, PING_PORT, new Object[]{"ping", id, (double) nowTicks()}, PING_BYTES)) return -1;
        return id;
    }

    /** Пришло сообщение по сети: событие net_message, если программа слушает этот порт. */
    public void netReceive(String from, int port, int hops, Object[] payload, int bytes) {
        if (state != State.RUNNING) return;
        if (port == PING_PORT) {
            if (payload.length < 3 || !(payload[2] instanceof Number sent)) return;
            netReceived.addAndGet(bytes);
            if ("ping".equals(payload[0])) {
                // Отвечает сама сетевая карта — программа для этого не нужна
                if (host.netSend(from, PING_PORT, new Object[]{"pong", payload[1], sent}, PING_BYTES)) netSent.addAndGet(PING_BYTES);
            } else if ("pong".equals(payload[0])) {
                long rtt = Math.max(0, nowTicks() - sent.longValue());
                queueEvent("net_pong", from, payload[1], (int) (rtt * 1000 / TICKS_PER_SECOND), hops);
            }
            return;
        }
        if (!openPorts.contains(port)) return;
        netReceived.addAndGet(bytes);
        Object[] args = new Object[3 + payload.length];
        args[0] = from;
        args[1] = port;
        args[2] = hops;
        System.arraycopy(payload, 0, args, 3, payload.length);
        queueEvent("net_message", args);
    }

    // ======================= Видеокарта =======================

    /** Списать работу видеокарты (клетки экрана). */
    void chargeGpu(long cells) {
        if (cells > 0) gpuBudget.addAndGet(-cells);
    }

    // ======================= Накопители =======================

    private void chargeIo(Mount mount, long bytes) {
        long speed = mount.bytesPerSecond();
        if (speed <= 0 || bytes <= 0) return;
        synchronized (ioBudget) {
            long[] b = ioBudget.computeIfAbsent(mount, k -> new long[]{speed / TICKS_PER_SECOND});
            b[0] -= bytes;
            if (b[0] < 0) ioWait = true;
        }
    }

    private void refillIo() {
        synchronized (ioBudget) {
            boolean wait = false;
            for (Map.Entry<Mount, long[]> e : ioBudget.entrySet()) {
                long speed = e.getKey().bytesPerSecond();
                long[] b = e.getValue();
                b[0] = Math.min(b[0] + speed / TICKS_PER_SECOND, speed / TICKS_PER_SECOND * BUDGET_BANK_TICKS);
                if (b[0] < 0) wait = true;
            }
            ioWait = wait;
        }
    }

    // ======================= Память =======================

    long memoryLimit() {
        return limits.memoryBytes();
    }

    /** Сколько памяти занято всеми процессами (по последнему подсчёту). */
    public long memoryUsed() {
        long n = 0;
        for (LuaMachine m : machines) n += m.liveBytes();
        return n;
    }

    long memoryUsedExcept(LuaMachine self) {
        long n = 0;
        for (LuaMachine m : machines) if (m != self) n += m.liveBytes();
        return n;
    }

    // ======================= Ошибка =======================

    /** Главный процесс упал: остальные процессы останавливаются, на экране — сообщение. */
    void crash(String message) {
        synchronized (this) {
            state = State.CRASHED;
            crashMessage = message;
            for (LuaMachine m : machines) m.kill();
            machines.clear();
            main = null;
        }
        terminal.setBackIndex(Terminal.BLACK);
        terminal.setTextIndex(14);
        int y = Math.min(terminal.height() - 1, Math.max(0, terminal.cursorY() + 1));
        if (terminal.cursorX() == 0) y = Math.max(0, terminal.cursorY());
        for (String part : wrap("Ошибка: " + message, terminal.width())) {
            if (y >= terminal.height()) {
                terminal.scroll(1);
                y = terminal.height() - 1;
            }
            terminal.setCursor(0, y++);
            terminal.write(part);
        }
        terminal.setTextIndex(Terminal.WHITE);
        terminal.setCursorBlink(false);
    }

    static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\n")) {
            while (line.length() > width) {
                out.add(line.substring(0, width));
                line = line.substring(width);
            }
            out.add(line);
        }
        return out;
    }

    /** Выгрузить без следов (чанк выгружен, блок сломан). */
    public void unload() {
        shutdown();
    }
}
