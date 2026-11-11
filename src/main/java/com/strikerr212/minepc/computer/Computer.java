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

        // ----- BIOS -----

        /** Перед каждой загрузкой (и при перезагрузке из программы): применить новые настройки BIOS. */
        default void beforeBoot() { }

        /** Настройки BIOS для программ (без самих паролей); null — хранить в памяти компьютера (тесты). */
        default java.util.Map<String, Object> biosSettings() { return null; }

        /** Сохранить изменения настроек; null — получилось, иначе причина. */
        default String biosSave(java.util.Map<?, ?> changes) { return "Настройки не сохраняются"; }

        /** Проверить пароль: kind = "bios" или "boot". */
        default boolean biosCheckPassword(String kind, String password) { return true; }

        /** Сведения о плате и BIOS: vendor, version, board, tier, theme ("classic" или "uefi"). */
        default java.util.Map<String, Object> biosInfo() {
            return java.util.Map.of("vendor", "MineBIOS", "version", "2.3", "board", "Тестовая плата", "tier", 1, "theme", "classic");
        }

        /** Устройства загрузки: {id, name, path, present}. */
        default java.util.List<java.util.Map<String, Object>> biosDevices() { return java.util.List.of(); }

        // ----- Управляемые коммутаторы -----

        /** Коммутаторы (и роутеры, радиомодемы) на пути из этого компьютера: {id, kind, ports}. */
        default java.util.List<java.util.Map<String, Object>> switchList() { return java.util.List.of(); }

        /** Порты коммутатора: {port, side, n, link, device, enabled, vlan}; null — нет такого. */
        default java.util.List<java.util.Map<String, Object>> switchPorts(String id) { return null; }

        /** Включить/выключить порт, задать VLAN (0 — общий, 1..16 — цвет). null — получилось, иначе причина. */
        default String switchSet(String id, int port, boolean enabled, int vlan) { return "Нет такого коммутатора"; }
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

    /**
     * Время компьютера (нс): идёт по настоящим часам, пока игра не на паузе и сервер не завис.
     * Таймеры, os.sleep и computer.uptime() считаются по нему с точностью до миллисекунды,
     * а не тиками по 50 мс. Если тики идут быстрее часов (сервер догоняет после лага, тесты),
     * время подтягивается до числа тиков — так ничто не «отстаёт» от игры.
     */
    private long clockNanos;
    private long tickCount;
    private long lastPumpNanos = System.nanoTime();
    private volatile long lastTickNanos = System.nanoTime();
    /** Сервер не тикал дольше этого — игра на паузе или зависла: время компьютера стоит. */
    static final long PAUSE_NANOS = 300_000_000L;
    static final long TICK_NANOS = 1_000_000_000L / TICKS_PER_SECOND;
    /** Дробные остатки пополнения (команд, клеток, байт), чтобы маленькие шаги по 1 мс не терялись. */
    private double cpuCarry, gpuCarry, netCarry;
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
            return clockNanos / 1e9;
        }
    }

    long clockMillis() {
        synchronized (timers) {
            return clockNanos / 1_000_000L;
        }
    }

    // ======================= Включение и выключение =======================

    public synchronized void turnOn() {
        if (state == State.RUNNING) return;
        host.beforeBoot();
        machines.clear();
        main = null;
        mainEvents.clear();
        terminateWait = -1;
        synchronized (timers) {
            timers.clear();
            clockNanos = 0;
            tickCount = 0;
            lastPumpNanos = System.nanoTime();
            lastTickNanos = lastPumpNanos;
            cpuCarry = gpuCarry = netCarry = 0;
        }
        synchronized (ioBudget) {
            ioBudget.clear();
            ioWait = false;
        }
        gpu.reset();
        gpuBudget.set(limits.gpuCellsPerTick());
        openPorts.clear();
        netBudget.set(0);
        synchronized (diskStats) {
            diskStats.clear();
        }
        cpuLoad = gpuLoad = 0;
        coreLoads = new double[Math.max(1, limits.cores())];
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
        // Загрузку системы (время по скорости диска) теперь показывает BIOS после самотеста
        bootRemaining = 0;
        state = State.RUNNING;
        ComputerClock.register(this);
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
        ComputerClock.unregister(this);
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
            long delay = (long) (Math.max(0, Math.min(1e7, seconds)) * 1e9);
            timers.put(id, new Timer(clockNanos + Math.max(0, delay), target));
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
        boolean second;
        synchronized (timers) {
            tickCount++;
            lastTickNanos = System.nanoTime();
            second = tickCount % TICKS_PER_SECOND == 0;
            if (second) opsLastSecond = opsThisSecond.getAndSet(0);
        }
        if (second) sampleStats();
        if (bootRemaining > 0) {
            bootRemaining--;
            if (bootRemaining > 0) {
                if (bootRemaining % 2 == 0) drawBoot();
                return;
            }
            terminal.clear();
            terminal.setCursor(0, 0);
        }
        if (main == null) {
            synchronized (this) {
                if (main == null && state == State.RUNNING) {
                    main = new LuaMachine(this, nextPid++, 0, mainEvents, null, null);
                    machines.add(0, main);
                }
            }
        }
        advance();

        if (terminateWait >= 0 && ++terminateWait > TERMINATE_GRACE_TICKS) {
            terminateWait = -1;
            // Событие так и не прочитали — убираем его и прерываем программу ошибкой
            mainEvents.removeNamed("terminate");
            LuaMachine m = main;
            if (m != null) m.abort();
        }
    }

    /**
     * Шаг часов компьютера (раз в 1 мс из {@link ComputerClock} и каждый тик): сработавшие таймеры,
     * пополнение процессора, видеокарты, дисков и сети по прошедшему времени, запуск ждущих процессов.
     */
    void advance() {
        if (state != State.RUNNING || stopRequested) return;
        long dt;
        List<Object[]> fired = null;
        List<EventQueue> targets = null;
        synchronized (timers) {
            long now = System.nanoTime();
            long real = now - lastPumpNanos;
            lastPumpNanos = now;
            boolean active = now - lastTickNanos < PAUSE_NANOS;
            long next = clockNanos + (active ? Math.min(Math.max(0, real), 100_000_000L) : 0);
            long floor = tickCount * TICK_NANOS;
            if (next < floor) next = floor;
            dt = next - clockNanos;
            clockNanos = next;
            for (Iterator<Map.Entry<Integer, Timer>> it = timers.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, Timer> e = it.next();
                if (e.getValue().due() <= clockNanos) {
                    it.remove();
                    if (fired == null) { fired = new ArrayList<>(); targets = new ArrayList<>(); }
                    fired.add(event("timer", e.getKey()));
                    targets.add(e.getValue().target());
                }
            }
            if (dt > 0) refill(dt);
        }
        if (fired != null) for (int i = 0; i < fired.size(); i++) targets.get(i).offer(fired.get(i));
        for (LuaMachine m : machines) {
            if (m.needsWork() && m.scheduled.compareAndSet(false, true)) ComputerExecutor.submit(m);
        }
    }

    /** Пополнить запасы за dt нс (под замком часов). Копить можно не больше, чем на 2 тика. */
    private void refill(long dt) {
        Limits l = limits;
        double sec = dt / 1e9;
        // Процессор: каждое ядро — один процесс на полной скорости; процессов больше, чем ядер, — делят
        int alive = Math.max(1, machines.size());
        double share = Math.min(1.0, l.cores() / (double) alive);
        double perSecond = (double) l.opsPerTick() * TICKS_PER_SECOND * share;
        cpuCarry += perSecond * sec;
        long add = (long) cpuCarry;
        cpuCarry -= add;
        long cap = Math.max(100, Math.round(l.opsPerTick() * share)) * BUDGET_BANK_TICKS;
        if (add > 0) for (LuaMachine m : machines) m.refill(add, cap);
        gpuCarry += (double) l.gpuCellsPerTick() * TICKS_PER_SECOND * sec;
        long g = (long) gpuCarry;
        gpuCarry -= g;
        long gcap = (long) l.gpuCellsPerTick() * BUDGET_BANK_TICKS;
        if (g > 0) gpuBudget.updateAndGet(b -> Math.min(b + g, gcap));
        refillIo(sec);
        long nps = host.netBytesPerSecond();
        netCarry += nps * sec;
        long n = (long) netCarry;
        netCarry -= n;
        long ncap = nps / TICKS_PER_SECOND * BUDGET_BANK_TICKS;
        if (n > 0) netBudget.updateAndGet(b -> Math.min(b + n, ncap));
    }

    void addOps(long ops) {
        opsThisSecond.addAndGet(ops);
    }

    /** Все программы ждут событий — значит, кадр на экране дорисован целиком. */
    public boolean idle() {
        for (LuaMachine m : machines) if (!m.waitingForEvent()) return false;
        return true;
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

    private long nowMillis() {
        return clockMillis();
    }

    /**
     * Эхо-запрос: уходит по сети как обычное сообщение, адресат (если включён) сразу отвечает,
     * а сюда приходит событие net_pong(адрес, номер, мс туда и обратно, переходов). Возвращает номер или -1.
     */
    public int netPing(String target) {
        int id = nextPing.getAndIncrement();
        if (!netSend(target, PING_PORT, new Object[]{"ping", id, (double) nowMillis()}, PING_BYTES)) return -1;
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
                long rtt = Math.max(0, nowMillis() - sent.longValue());
                queueEvent("net_pong", from, payload[1], (int) rtt, hops);
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

    // ======================= Нагрузка (диспетчер задач, BIOS) =======================

    /**
     * Тепло и питание по деталям: TDP процессора и видеокарты, сколько отводят их кулеры, сколько потребляет
     * система на полной нагрузке и мощность блока питания. Есть ли кулер с вентилятором (для оборотов).
     */
    public record Thermal(int cpuTdp, int cpuCooling, int gpuTdp, int gpuCooling, boolean hasGpu,
                          int powerDraw, int psuWatts, boolean fan) {
        public static final Thermal NONE = new Thermal(65, 95, 0, 0, false, 120, 300, true);
    }

    private volatile Thermal thermal = Thermal.NONE;
    private final AtomicLong gpuThisSecond = new AtomicLong();
    private final Map<Mount, long[]> diskStats = new IdentityHashMap<>();   // чтение, запись за секунду; прошлой; всего
    private volatile double cpuLoad, gpuLoad, cpuTemp = 30, gpuTemp = 30;
    private volatile double[] coreLoads = new double[1];
    private volatile long netUp, netDown, lastSent, lastReceived;

    public void setThermal(Thermal t) {
        thermal = t == null ? Thermal.NONE : t;
    }

    public Thermal thermal() { return thermal; }
    public double cpuLoad() { return cpuLoad; }
    public double gpuLoad() { return gpuLoad; }
    public double cpuTemp() { return cpuTemp; }
    public double gpuTemp() { return gpuTemp; }
    public double[] coreLoads() { return coreLoads.clone(); }
    public long netUpPerSecond() { return netUp; }
    public long netDownPerSecond() { return netDown; }

    /** Обороты вентилятора процессора (0 — пассивное охлаждение). */
    public int fanRpm() {
        if (!thermal.fan()) return 0;
        double k = Math.max(0, Math.min(1, (cpuTemp - 35) / 40));
        return (int) Math.round(600 + 1600 * k);
    }

    /** Сколько ватт потребляет система сейчас. */
    public int powerNow() {
        Thermal t = thermal;
        return (int) Math.round(t.powerDraw() * (0.35 + 0.45 * cpuLoad + (t.hasGpu() ? 0.2 * gpuLoad : 0)));
    }

    /** Диск: чтение и запись за последнюю секунду, всего. */
    public long[] diskStats(Mount m) {
        synchronized (diskStats) {
            long[] d = diskStats.get(m);
            return d == null ? new long[6] : d.clone();
        }
    }

    private static double approach(double cur, double target) {
        return cur + (target - cur) * 0.2;
    }

    /** Раз в секунду: нагрузка по ядрам и процессам, видеокарта, диски, сеть, температура. */
    private void sampleStats() {
        Limits l = limits;
        double perCore = Math.max(1, (double) l.opsPerTick() * TICKS_PER_SECOND);
        int cores = Math.max(1, l.cores());
        double[] loads = new double[cores];
        List<Long> ops = new ArrayList<>();
        for (LuaMachine m : machines) {
            m.sampleOps();
            ops.add(m.opsLastSecond);
        }
        ops.sort(java.util.Comparator.reverseOrder());
        double total = 0;
        for (long o : ops) {
            // процесс работает на одном ядре — кладём его на самое свободное
            int best = 0;
            for (int i = 1; i < cores; i++) if (loads[i] < loads[best]) best = i;
            double f = Math.min(1, o / perCore);
            loads[best] = Math.min(1, loads[best] + f);
            total += f;
        }
        coreLoads = loads;
        cpuLoad = Math.min(1, total / cores);
        double gpuCap = Math.max(1, (double) l.gpuCellsPerTick() * TICKS_PER_SECOND);
        gpuLoad = Math.min(1, gpuThisSecond.getAndSet(0) / gpuCap);
        synchronized (diskStats) {
            for (long[] d : diskStats.values()) {
                d[2] = d[0];
                d[3] = d[1];
                d[0] = d[1] = 0;
            }
        }
        long sent = netSent.get(), received = netReceived.get();
        netUp = Math.max(0, sent - lastSent);
        netDown = Math.max(0, received - lastReceived);
        lastSent = sent;
        lastReceived = received;
        Thermal t = thermal;
        double cpuRatio = t.cpuTdp() / (double) Math.max(1, t.cpuCooling());
        cpuTemp = approach(cpuTemp, Math.max(28, Math.min(105, 28 + (0.22 + 0.78 * cpuLoad) * cpuRatio * 50)));
        if (t.hasGpu()) {
            double gpuRatio = t.gpuTdp() / (double) Math.max(1, t.gpuCooling());
            gpuTemp = approach(gpuTemp, Math.max(28, Math.min(105, 28 + (0.2 + 0.8 * gpuLoad) * gpuRatio * 45)));
        }
    }

    // ======================= Видеокарта =======================

    /** Списать работу видеокарты (клетки экрана). */
    void chargeGpu(long cells) {
        if (cells > 0) {
            gpuBudget.addAndGet(-cells);
            gpuThisSecond.addAndGet(cells);
        }
    }

    // ======================= Накопители =======================

    private void chargeIo(Mount mount, long bytes, boolean write) {
        synchronized (diskStats) {
            long[] d = diskStats.computeIfAbsent(mount, k -> new long[6]);
            d[write ? 1 : 0] += bytes;
            d[write ? 5 : 4] += bytes;
        }
        long speed = mount.bytesPerSecond();
        if (speed <= 0 || bytes <= 0) return;
        synchronized (ioBudget) {
            long[] b = ioBudget.computeIfAbsent(mount, k -> new long[]{speed / TICKS_PER_SECOND});
            b[0] -= bytes;
            if (b[0] < 0) ioWait = true;
        }
    }

    private void refillIo(double sec) {
        synchronized (ioBudget) {
            boolean wait = false;
            for (Map.Entry<Mount, long[]> e : ioBudget.entrySet()) {
                long speed = e.getKey().bytesPerSecond();
                long[] b = e.getValue();
                b[0] = Math.min(b[0] + Math.max(1, Math.round(speed * sec)), speed / TICKS_PER_SECOND * BUDGET_BANK_TICKS);
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
