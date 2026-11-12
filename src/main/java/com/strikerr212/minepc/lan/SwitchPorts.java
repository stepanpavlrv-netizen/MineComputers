package com.strikerr212.minepc.lan;

import com.strikerr212.minepc.block.PcCaseBlockEntity;
import com.strikerr212.minepc.network.SwitchOpenPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Порты коммутатора: что в них воткнуто, включены ли, VLAN. Для окна коммутатора и программы switch. */
public final class SwitchPorts {

    private SwitchPorts() {
    }

    /** Порт: номер, сторона (F/B/L/R спереди/сзади/слева/справа, C — патч-корд), номер на стороне, есть связь, кто там, вкл, VLAN. */
    public record Port(int port, char side, int number, boolean link, String device, boolean enabled, int vlan, int length) {
    }

    /** Короткий постоянный номер коммутатора для программ (по координатам). */
    public static String id(BlockPos pos) {
        long v = pos.asLong();
        return String.format(Locale.ROOT, "%08x", (int) (v ^ (v >>> 32)) * 0x9E3779B1);
    }

    public static String kind(LanData.Device d) {
        return switch (d.type()) {
            case LanData.DESK_SWITCH -> "desk";
            case LanData.RACK_SWITCH -> "rack";
            case LanData.ROUTER -> "router";
            case LanData.RADIO -> "radio";
            default -> "other";
        };
    }

    /** Сторона относительно лица блока. */
    private static char side(BlockState state, Direction face) {
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) return 'F';
        Direction f = state.getValue(HorizontalDirectionalBlock.FACING);
        if (face == f) return 'F';
        if (face == f.getOpposite()) return 'B';
        return face == f.getClockWise() ? 'L' : 'R';
    }

    /** Все порты устройства, которые есть или заняты. */
    public static List<Port> ports(ServerLevel level, BlockPos pos) {
        LanData data = LanData.get(level);
        LanData.Device d = data.device(pos);
        List<Port> out = new ArrayList<>();
        if (d == null) return out;
        BlockState state = level.getBlockState(pos);
        Map<Integer, LanManager.Segment> used = new HashMap<>();
        for (int port : LanManager.usedPorts(data, pos, d)) used.put(port, LanManager.explore(data, pos, port));
        List<LanDevice.Socket> socks = state.getBlock() instanceof LanDevice dev ? dev.sockets(state) : List.of();
        for (int port = 0; port < LanData.PORTS; port++) {
            boolean exists = port < LanData.CABLE_PORTS && port < socks.size();
            LanManager.Segment seg = used.get(port);
            if (!exists && seg == null) continue;
            // У стоечного коммутатора 12 гнёзд: показываем только занятые, чтобы окно не было бесконечным
            if (d.type() == LanData.RACK_SWITCH && seg == null) continue;
            char side = 'C';
            int number = port - LanData.CABLE_PORTS + 1;
            if (port < LanData.CABLE_PORTS) {
                Direction face = port < socks.size() ? socks.get(port).face() : Direction.NORTH;
                side = side(state, face);
                number = 1;
                for (int j = 0; j < port && j < socks.size(); j++) if (socks.get(j).face() == face) number++;
            }
            String device = "";
            boolean link = seg != null && seg.ok() && !seg.plugs().isEmpty();
            if (seg != null && !seg.ok()) device = "!";
            else if (link) device = describe(level, data, seg);
            int cfg = data.portConfig(pos, port);
            out.add(new Port(port, side, number, link, device, LanData.portEnabled(cfg), LanData.portVlan(cfg),
                    seg == null ? 0 : seg.length()));
        }
        return out;
    }

    /** Кто на другом конце: имя компьютера или вид устройства (+ сколько ещё). */
    private static String describe(ServerLevel level, LanData data, LanManager.Segment seg) {
        String first = null;
        for (LanData.Plug p : seg.plugs()) {
            LanData.Device d = data.device(p.pos());
            if (d == null) continue;
            String name;
            if (d.computer()) {
                name = "pc";
                PcCaseBlockEntity pc = level.isLoaded(p.pos())
                        ? com.strikerr212.minepc.block.Hosts.get(level, p.pos(), LanManager.hostSlot(d, p.port())) : null;
                if (pc != null) {
                    name = pc.label().isEmpty() ? pc.address().substring(0, 8) : pc.label();
                }
                name = "@" + name;
            } else {
                name = kind(d);
            }
            if (first == null || (name.startsWith("@") && !first.startsWith("@"))) first = name;
        }
        if (first == null) return "";
        return seg.plugs().size() > 1 ? first + " +" + (seg.plugs().size() - 1) : first;
    }

    /** Порты для подсказки при взгляде на гнездо: те же поля, что в окне коммутатора, и длина кабеля. */
    public static com.strikerr212.minepc.network.PortsInfoPayload infoPayload(ServerLevel level, BlockPos pos) {
        StringBuilder b = new StringBuilder();
        for (Port p : ports(level, pos)) {
            if (b.length() > 0) b.append('\n');
            b.append(p.port()).append('\t').append(p.link() ? 1 : 0).append('\t')
                    .append(p.device().replace('\t', ' ').replace('\n', ' ')).append('\t')
                    .append(p.enabled() ? 1 : 0).append('\t').append(p.vlan()).append('\t').append(p.length());
        }
        LanData.Device d = LanData.get(level).device(pos);
        return new com.strikerr212.minepc.network.PortsInfoPayload(pos, d == null ? 0 : d.kbit(), b.toString());
    }

    public static SwitchOpenPayload openPayload(ServerLevel level, BlockPos pos) {
        StringBuilder b = new StringBuilder();
        for (Port p : ports(level, pos)) {
            if (b.length() > 0) b.append('\n');
            b.append(p.port()).append('\t').append(p.side()).append('\t').append(p.number()).append('\t')
                    .append(p.link() ? 1 : 0).append('\t').append(p.device().replace('\t', ' ').replace('\n', ' ')).append('\t')
                    .append(p.enabled() ? 1 : 0).append('\t').append(p.vlan());
        }
        LanData.Device d = LanData.get(level).device(pos);
        return new SwitchOpenPayload(pos, d == null ? "" : kind(d), id(pos), b.toString());
    }

    /** VLAN из Lua: число 0..16 или имя цвета ("red"); 0 — общий. */
    public static int parseVlan(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return Math.max(0, Math.min(16, n.intValue()));
        String s = v.toString().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.equals("none") || s.equals("0")) return 0;
        DyeColor c = DyeColor.byName(s, null);
        return c == null ? -1 : c.getId() + 1;
    }

    public static String vlanName(int vlan) {
        return vlan <= 0 ? "" : DyeColor.byId(vlan - 1).getName();
    }
}
