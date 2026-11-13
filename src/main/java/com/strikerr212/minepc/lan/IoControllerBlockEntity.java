package com.strikerr212.minepc.lan;

import com.strikerr212.minepc.block.HostRef;
import com.strikerr212.minepc.computer.Computer;
import com.strikerr212.minepc.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Контроллер ввода-вывода: адрес в сети, шесть сторон с редстоуном (каждая — вход или выход, уровень 0–15) и
 * встроенные датчики — заполненность контейнера рядом (как у компаратора), игроки рядом, освещённость.
 *
 * Компьютеры управляют им по служебному порту (как ping): net.io(кому, команда, ...). Ответ — событие
 * io_state(адрес, номер, данные) или io_error(адрес, номер, текст). Подписчики (команда watch) получают
 * io_change(адрес, сторона, уровень, было) при изменении входа и io_player(адрес, игрок, пришёл).
 */
public class IoControllerBlockEntity extends BlockEntity implements NetEndpoint, CordList.Holder {

    /** Порядок сторон: как Direction (низ, верх, север, юг, запад, восток). */
    public static final String[] SIDE_NAMES = {"down", "up", "north", "south", "west", "east"};
    private static final String[][] SIDE_ALIASES = {
            {"down", "bottom", "низ", "вниз"}, {"up", "top", "верх", "вверх"}, {"north", "север"}, {"south", "юг"},
            {"west", "запад"}, {"east", "восток"}};
    public static final int MAX_WATCHERS = 8, PLAYER_RADIUS = 6, LOG_LINES = 6;
    private static final String[] SIDE_RU = {"низ", "верх", "север", "юг", "запад", "восток"};

    private String address = "";
    private String name = "";
    /** true — сторона выход. */
    private final boolean[] output = new boolean[6];
    private final int[] outLevel = new int[6];
    private final int[] inLevel = new int[6];
    private final int[] pulseLeft = new int[6];
    private final int[] fill = {-1, -1, -1, -1, -1, -1};
    private final List<String> watchers = new ArrayList<>();
    private final List<String> players = new ArrayList<>();
    private final List<String> log = new ArrayList<>();
    private int light;
    private int age;
    private final CordList cords = new CordList();

    public IoControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IO_CONTROLLER.get(), pos, state);
    }

    @Override
    public CordList cords() {
        return cords;
    }

    // ======================= Адрес и имя =======================

    @Override
    public String address() {
        if (address.isEmpty()) {
            address = String.format(Locale.ROOT, "%08x", new java.util.Random().nextInt());
            setChanged();
        }
        return address;
    }

    @Override
    public String label() {
        return name;
    }

    public void setLabel(String label) {
        name = label.length() > LanData.MAX_LABEL ? label.substring(0, LanData.MAX_LABEL) : label;
        setChanged();
        sync();
    }

    @Override
    public boolean netOnline() {
        return true;
    }

    public HostRef hostRef() {
        return HostRef.of(worldPosition);
    }

    // ======================= Редстоун =======================

    public static int sideIndex(String s) {
        String t = s.toLowerCase(Locale.ROOT).trim();
        for (int i = 0; i < 6; i++) for (String a : SIDE_ALIASES[i]) if (a.equals(t)) return i;
        return -1;
    }

    public boolean isOutput(int side) {
        return output[side];
    }

    /** Что контроллер подаёт на сторону side (0 — сторона вход). */
    public int signalTo(Direction side) {
        int i = side.get3DDataValue();
        return output[i] ? outLevel[i] : 0;
    }

    public int input(int side) {
        return inLevel[side];
    }

    public int outLevel(int side) {
        return outLevel[side];
    }

    public int fill(int side) {
        return fill[side];
    }

    public List<String> players() {
        return players;
    }

    public int light() {
        return light;
    }

    public List<String> log() {
        return log;
    }

    public boolean anyOutput() {
        for (int i = 0; i < 6; i++) if (output[i] && outLevel[i] > 0) return true;
        return false;
    }

    public void setMode(int side, boolean out) {
        if (output[side] == out) return;
        output[side] = out;
        if (!out) outLevel[side] = 0;
        changed(true);
        note(SIDE_RU[side] + (out ? " → выход" : " → вход"));
        readInputs(false);
    }

    public void setOutput(int side, int level) {
        level = Math.max(0, Math.min(15, level));
        boolean modeChange = !output[side];
        output[side] = true;
        pulseLeft[side] = 0;
        if (outLevel[side] == level && !modeChange) return;
        note(SIDE_RU[side] + " " + outLevel[side] + " → " + level);
        outLevel[side] = level;
        changed(true);
    }

    /** Импульс: уровень на ticks тиков, потом 0 (открыть дверь на секунду). */
    public void pulse(int side, int level, int ticks) {
        setOutput(side, level);
        pulseLeft[side] = Math.max(1, Math.min(200, ticks));
    }

    private void changed(boolean outputs) {
        setChanged();
        if (level == null || level.isClientSide) return;
        if (outputs) {
            BlockState s = getBlockState();
            boolean lit = anyOutput();
            if (s.hasProperty(IoControllerBlock.LIT) && s.getValue(IoControllerBlock.LIT) != lit) {
                level.setBlock(worldPosition, s.setValue(IoControllerBlock.LIT, lit), 3);
            }
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            for (Direction d : Direction.values()) {
                level.updateNeighborsAtExceptFromFacing(worldPosition.relative(d), getBlockState().getBlock(), d.getOpposite());
            }
        }
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    /** Соседи изменились (или прошло время): перечитать входы, у подписчиков — события. */
    public void readInputs(boolean notify) {
        if (level == null || level.isClientSide) return;
        boolean any = false;
        for (Direction d : Direction.values()) {
            int i = d.get3DDataValue();
            int v = output[i] ? 0 : level.getSignal(worldPosition.relative(d), d);
            if (v != inLevel[i]) {
                int old = inLevel[i];
                inLevel[i] = v;
                any = true;
                if (notify) {
                    note(SIDE_RU[i] + " вход " + old + " → " + v);
                    broadcast(new Object[]{"io_change", SIDE_NAMES[i], v, old});
                }
            }
        }
        if (any) {
            setChanged();
            sync();
        }
    }

    // ======================= Датчики =======================

    public static void serverTick(Level level, BlockPos pos, BlockState state, IoControllerBlockEntity be) {
        be.age++;
        for (int i = 0; i < 6; i++) {
            if (be.pulseLeft[i] > 0 && --be.pulseLeft[i] == 0) {
                be.outLevel[i] = 0;
                be.note(SIDE_RU[i] + ": импульс закончился");
                be.changed(true);
            }
        }
        if (be.age % 10 == 0) be.sensors();
    }

    private void sensors() {
        if (!(level instanceof ServerLevel server)) return;
        readInputs(true);
        boolean dirty = false;
        for (Direction d : Direction.values()) {
            int i = d.get3DDataValue();
            BlockPos n = worldPosition.relative(d);
            BlockState s = level.getBlockState(n);
            int f = s.hasAnalogOutputSignal() ? s.getAnalogOutputSignal(level, n) : -1;
            if (f != fill[i]) {
                fill[i] = f;
                dirty = true;
            }
        }
        int l = level.getMaxLocalRawBrightness(worldPosition.above());
        if (l != light) {
            light = l;
            dirty = true;
        }
        List<String> now = new ArrayList<>();
        for (Player p : server.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(PLAYER_RADIUS))) {
            if (!p.isSpectator()) now.add(p.getGameProfile().getName());
        }
        now.sort(String::compareTo);
        if (!now.equals(players)) {
            for (String p : now) if (!players.contains(p)) {
                note("пришёл " + p);
                broadcast(new Object[]{"io_player", p, true});
            }
            for (String p : players) if (!now.contains(p)) {
                note("ушёл " + p);
                broadcast(new Object[]{"io_player", p, false});
            }
            players.clear();
            players.addAll(now);
            dirty = true;
        }
        if (dirty) {
            setChanged();
            sync();
        }
    }

    private void note(String line) {
        String time = level == null ? "" : clock(level.getDayTime());
        log.add(0, time + " " + line);
        while (log.size() > LOG_LINES) log.remove(log.size() - 1);
    }

    private static String clock(long dayTime) {
        long t = (dayTime + 6000) % 24000;
        return String.format(Locale.ROOT, "%02d:%02d", t / 1000, (t % 1000) * 60 / 1000);
    }

    // ======================= Сеть =======================

    private void post(String target, Object[] payload) {
        if (level instanceof ServerLevel server) {
            LanManager.get(server).post(hostRef(), target, Computer.PING_PORT, payload, 96);
        }
    }

    private void broadcast(Object[] payload) {
        for (String w : watchers) post(w, payload);
    }

    private static String text(Object o) {
        if (o instanceof byte[] b) return new String(b, java.nio.charset.StandardCharsets.UTF_8);
        return o == null ? "" : String.valueOf(o);
    }

    private static int num(Object o, int def) {
        if (o instanceof Number n) return (int) Math.round(n.doubleValue());
        try {
            return Integer.parseInt(text(o).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public void netReceive(String from, int port, int hops, Object[] p, int bytes) {
        if (port != Computer.PING_PORT || p.length < 3 || !"io".equals(text(p[0]))) return;
        Object id = p[1];
        String cmd = text(p[2]).toLowerCase(Locale.ROOT);
        String err = null;
        switch (cmd) {
            case "get", "state" -> {
            }
            case "set", "pulse" -> {
                int side = p.length > 3 ? sideIndex(text(p[3])) : -1;
                int lvl = p.length > 4 ? num(p[4], -1) : 15;
                if (side < 0) err = "Нет такой стороны (north, south, east, west, up, down)";
                else if (lvl < 0 || lvl > 15) err = "Уровень — от 0 до 15";
                else if (cmd.equals("pulse")) pulse(side, lvl, p.length > 5 ? num(p[5], 10) : 10);
                else setOutput(side, lvl);
            }
            case "mode" -> {
                int side = p.length > 3 ? sideIndex(text(p[3])) : -1;
                String m = p.length > 4 ? text(p[4]).toLowerCase(Locale.ROOT) : "";
                boolean out = m.equals("out") || m.equals("выход");
                if (side < 0) err = "Нет такой стороны (north, south, east, west, up, down)";
                else if (!out && !m.equals("in") && !m.equals("вход")) err = "Режим — in или out";
                else setMode(side, out);
            }
            case "watch" -> {
                if (!watchers.contains(from)) {
                    if (watchers.size() >= MAX_WATCHERS) watchers.remove(0);
                    watchers.add(from);
                }
            }
            case "unwatch" -> watchers.remove(from);
            case "name" -> {
                String n = p.length > 3 ? text(p[3]).trim() : "";
                if (n.length() > LanData.MAX_LABEL) err = "Имя — не длиннее " + LanData.MAX_LABEL + " знаков";
                else setLabel(n);
            }
            default -> err = "Неизвестная команда: " + cmd;
        }
        if (err != null) post(from, new Object[]{"io_error", id, err});
        else post(from, new Object[]{"io_state", id, state()});
    }

    /** Всё о контроллере — ответ на команды (таблица для Lua). */
    public Map<Object, Object> state() {
        Map<Object, Object> m = new LinkedHashMap<>();
        m.put("address", address());
        m.put("name", name);
        Map<Object, Object> sides = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            Map<Object, Object> s = new LinkedHashMap<>();
            s.put("mode", output[i] ? "out" : "in");
            s.put("level", output[i] ? outLevel[i] : inLevel[i]);
            if (fill[i] >= 0) s.put("fill", fill[i]);
            if (level != null) {
                BlockState n = level.getBlockState(worldPosition.relative(Direction.from3DDataValue(i)));
                if (!n.isAir()) s.put("block", BuiltInRegistries.BLOCK.getKey(n.getBlock()).toString());
            }
            sides.put(SIDE_NAMES[i], s);
        }
        m.put("sides", sides);
        Map<Object, Object> pl = new LinkedHashMap<>();
        for (int i = 0; i < players.size(); i++) pl.put(i + 1, players.get(i));
        m.put("players", pl);
        m.put("light", light);
        m.put("time", level == null ? "" : clock(level.getDayTime()));
        m.put("watchers", watchers.size());
        return m;
    }

    // ======================= Сохранение =======================

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) LanManager.updateDevice(level, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Address", address());
        tag.putString("Name", name);
        int[] modes = new int[6];
        for (int i = 0; i < 6; i++) modes[i] = output[i] ? 1 : 0;
        tag.putIntArray("Output", modes);
        tag.putIntArray("OutLevel", outLevel);
        tag.putIntArray("InLevel", inLevel);
        tag.putIntArray("Fill", fill);
        tag.putInt("Light", light);
        ListTag pl = new ListTag();
        for (String p : players) pl.add(StringTag.valueOf(p));
        tag.put("Players", pl);
        ListTag lg = new ListTag();
        for (String l : log) lg.add(StringTag.valueOf(l));
        tag.put("Log", lg);
        cords.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        address = tag.getString("Address");
        name = tag.getString("Name");
        int[] modes = tag.getIntArray("Output");
        int[] lv = tag.getIntArray("OutLevel");
        int[] in = tag.getIntArray("InLevel");
        int[] fl = tag.getIntArray("Fill");
        for (int i = 0; i < 6; i++) {
            output[i] = i < modes.length && modes[i] == 1;
            outLevel[i] = i < lv.length ? lv[i] : 0;
            inLevel[i] = i < in.length ? in[i] : 0;
            fill[i] = i < fl.length ? fl[i] : -1;
        }
        light = tag.getInt("Light");
        players.clear();
        for (var t : tag.getList("Players", 8)) players.add(t.getAsString());
        log.clear();
        for (var t : tag.getList("Log", 8)) log.add(t.getAsString());
        cords.load(tag);
    }

    /** Имя переходит в предмет при разборке (и обратно — при установке). */
    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!name.isEmpty()) components.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(name));
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        var n = input.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (n != null) name = n.getString();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
