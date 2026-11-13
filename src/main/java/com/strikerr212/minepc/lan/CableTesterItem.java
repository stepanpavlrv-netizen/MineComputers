package com.strikerr212.minepc.lan;

import com.strikerr212.minepc.block.PcCaseBlockEntity;
import com.strikerr212.minepc.network.TesterPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Кабельный тестер: ПКМ по гнезду устройства или по проводу — прозвонка. Весь кабель подсвечивается на 10 секунд
 * (видно сквозь стены), устройство на другом конце мигает, обрывы — красным. На экранчике тестера — длина,
 * скорость, концы, бирка и есть ли обрыв (держи тестер в руке — экранчик виден).
 */
public class CableTesterItem extends Item {

    public CableTesterItem(Properties properties) {
        super(properties);
    }

    /** По устройству — раньше самого устройства (иначе оно откроет своё окно). */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LanDevice dev)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        Player player = ctx.getPlayer();
        int port = socketAt(level, pos, state, dev, ctx.getClickLocation(), ctx.getClickedFace());
        if (port < 0) return say(player, "message.minepc.tester.no_socket");
        return testPort(server, player, stack, pos, port);
    }

    /** Прозвонить порт port устройства pos (и из CI). */
    public static InteractionResult testPort(ServerLevel server, Player player, ItemStack stack, BlockPos pos, int port) {
        LanManager.updateDevice(server, pos);
        LanData data = LanData.get(server);
        LanData.Device d = data.device(pos);
        if (d == null) return say(player, "message.minepc.tester.no_socket");
        // Гнездо пустое, а в устройстве есть патч-корды — прозваниваем корды
        if (!LanManager.usedPorts(data, pos, d).contains(port)) {
            int cord = -1;
            for (int p = LanData.CABLE_PORTS; p < LanData.PORTS && cord < 0; p++) if (data.cordAt(pos, p)) cord = p;
            if (cord < 0) return say(player, "message.minepc.tester.empty", port + 1);
            port = cord;
        }
        LanManager.Trace t = LanManager.trace(data, pos.immutable(), port, null, 0);
        report(server, player, stack, t, pos, port, data.labelName(LanManager.portLabel(data, pos, port)));
        return InteractionResult.CONSUME;
    }

    /** По проводу. */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof CableBlockEntity be) || be.wires().isEmpty()) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        Vec3 at = ctx.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()).scale(16);
        Wire w = be.nearest(at.x, at.y, at.z);
        if (w == null) return InteractionResult.PASS;
        LanData data = LanData.get(server);
        data.putCable(pos, be.packed());
        LanManager.Trace t = LanManager.trace(data, null, 0, pos.immutable(), be.wires().indexOf(w));
        report(server, ctx.getPlayer(), ctx.getItemInHand(), t, null, 0, be.label(w));
        return InteractionResult.CONSUME;
    }

    /** Гнездо под курсором (номер порта) или -1. */
    private static int socketAt(Level level, BlockPos pos, BlockState state, LanDevice dev, Vec3 hit, Direction face) {
        List<LanDevice.Socket> sockets = dev.sockets(level, pos, state);
        Vec3 at = hit.subtract(pos.getX(), pos.getY(), pos.getZ()).scale(16);
        int found = -1;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < sockets.size(); i++) {
            LanDevice.Socket s = sockets.get(i);
            if (s.face() != face || !s.copper() && !s.fiber()) continue;
            Direction.Axis[] ax = LanDevice.faceAxes(s.face());
            double d = Math.max(Math.abs(get(at, ax[0]) - (s.along() + 0.5)), Math.abs(get(at, ax[1]) - (s.height() + 0.5)));
            if (d < best) {
                best = d;
                found = i;
            }
        }
        return found;
    }

    private static double get(Vec3 v, Direction.Axis a) {
        return a == Direction.Axis.X ? v.x : a == Direction.Axis.Y ? v.y : v.z;
    }

    private static InteractionResult say(Player player, String key, Object... args) {
        if (player != null) player.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED), true);
        return InteractionResult.FAIL;
    }

    /** Ключ названия устройства для экранчика: по блоку, если он загружен, иначе по виду в схеме. */
    private static String deviceKey(ServerLevel level, BlockPos pos, LanData.Device d) {
        if (level.isLoaded(pos)) return level.getBlockState(pos).getBlock().getDescriptionId();
        return switch (d.type()) {
            case LanData.COMPUTER -> "block.minepc.pc_case";
            case LanData.DESK_SWITCH -> "block.minepc.network_switch";
            case LanData.RACK_SWITCH -> "block.minepc.rack_switch";
            case LanData.ROUTER -> "block.minepc.wifi_router";
            case LanData.RADIO -> "block.minepc.radio_modem";
            case LanData.RACK -> "block.minepc.server_rack";
            default -> "block.minepc.network_switch";
        };
    }

    /** Скорость порта устройства, бит/с. */
    private static long bitsOf(LanData.Device d, int port) {
        return d.computer() ? d.bitsFor(LanManager.hostSlot(d, port)) : d.bits();
    }

    private static void report(ServerLevel level, Player player, ItemStack stack, LanManager.Trace t, BlockPos startDev, int startPort,
                               String label) {
        LanData data = LanData.get(level);
        int limit = t.copper() ? LanManager.MAX_SEGMENT : LanManager.MAX_FIBER;
        // Скорость: самое медленное из устройств на концах (и того, по которому щёлкнули)
        long bits = Long.MAX_VALUE;
        int ends = 0;
        if (startDev != null && data.device(startDev) != null) bits = bitsOf(data.device(startDev), startPort);
        StringBuilder endText = new StringBuilder();
        for (LanData.Plug p : t.ends()) {
            LanData.Device d = data.device(p.pos());
            if (d == null) continue;
            ends++;
            bits = Math.min(bits, bitsOf(d, p.port()));
            if (ends > 4) continue;
            String name = "";
            if (d.computer() && level.isLoaded(p.pos())) {
                PcCaseBlockEntity pc = com.strikerr212.minepc.block.Hosts.get(level, p.pos(), LanManager.hostSlot(d, p.port()));
                if (pc != null) name = pc.label().isEmpty() ? pc.address().substring(0, 8) : pc.label();
            }
            if (endText.length() > 0) endText.append('\u001e');
            endText.append(deviceKey(level, p.pos(), d)).append('\u001f').append(p.port() + 1).append('\u001f').append(name.replace('\t', ' '));
        }
        int needEnds = startDev != null ? 1 : 2;
        if (ends < needEnds || bits == Long.MAX_VALUE) bits = 0;
        boolean ok = t.length() <= limit;
        long kbit = bits / 1000;
        String info = t.length() + "\t" + limit + "\t" + (t.copper() ? 0 : 1) + "\t" + kbit + "\t" + label.replace('\t', ' ')
                + "\t" + endText + "\t" + t.open().size() + "\t" + ends;

        if (player instanceof ServerPlayer sp) {
            long[] wires = new long[t.wires().size() * 2];
            for (int i = 0; i < t.wires().size(); i++) {
                wires[2 * i] = t.wires().get(i)[0];
                wires[2 * i + 1] = t.wires().get(i)[1];
            }
            long[] cords = new long[t.cords().size() * 2];
            for (int i = 0; i < t.cords().size(); i++) {
                cords[2 * i] = t.cords().get(i).a().asLong();
                cords[2 * i + 1] = t.cords().get(i).b().asLong();
            }
            long[] endPos = new long[t.ends().size()];
            for (int i = 0; i < endPos.length; i++) endPos[i] = t.ends().get(i).pos().asLong();
            PacketDistributor.sendToPlayer(sp, new TesterPayload(wires, t.trays().stream().mapToLong(Long::longValue).toArray(), cords,
                    t.passives().stream().mapToLong(BlockPos::asLong).toArray(), endPos,
                    t.open().stream().mapToLong(BlockPos::asLong).toArray(), info));
        }
        // Экранчик тестера: последний результат хранится в самом тестере
        CompoundTag tag = new CompoundTag();
        tag.putInt("len", t.length());
        tag.putInt("limit", limit);
        tag.putBoolean("fiber", !t.copper());
        tag.putLong("kbit", kbit);
        tag.putString("label", label);
        tag.putInt("ends", ends);
        tag.putInt("open", t.open().size());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        boolean broken = !t.open().isEmpty() || !ok || ends == 0;
        if (player != null) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.6f, broken ? 0.6f : 1.6f);
            Component msg = Component.translatable("message.minepc.tester.result", t.length(), speedText(kbit), ends)
                    .withStyle(broken ? ChatFormatting.GOLD : ChatFormatting.GREEN);
            if (!t.open().isEmpty()) msg = msg.copy().append(Component.translatable("message.minepc.tester.open").withStyle(ChatFormatting.RED));
            if (!ok) msg = msg.copy().append(Component.translatable("message.minepc.tester.long", limit).withStyle(ChatFormatting.RED));
            player.displayClientMessage(msg, true);
        }
    }

    /** «1 Гбит/с», «300 Мбит/с» или «—». */
    public static Component speedText(long kbit) {
        if (kbit <= 0) return Component.literal("—");
        if (kbit >= 1_000_000) {
            double g = kbit / 1_000_000.0;
            return Component.translatable("unit.minepc.gbit", g == Math.floor(g) ? String.valueOf((long) g) : String.format(Locale.ROOT, "%.1f", g));
        }
        return Component.translatable("unit.minepc.mbit", kbit / 1000);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.minepc.tester").withStyle(ChatFormatting.GRAY));
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;
        CompoundTag t = data.copyTag();
        if (!t.contains("len")) return;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("tooltip.minepc.tester.len", t.getInt("len"), t.getInt("limit"),
                Component.translatable(t.getBoolean("fiber") ? "tooltip.minepc.tester.fiber" : "tooltip.minepc.tester.copper")));
        lines.add(Component.translatable("tooltip.minepc.tester.speed", speedText(t.getLong("kbit")), t.getInt("ends")));
        if (t.getInt("open") > 0) lines.add(Component.translatable("tooltip.minepc.tester.open").withStyle(ChatFormatting.RED));
        if (!t.getString("label").isEmpty()) lines.add(Component.translatable("hud.minepc.port_label", t.getString("label")));
        for (Component c : lines) tooltip.add(c.copy().withStyle(s -> s.withColor(0x9CF59C)));
    }
}
