package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.block.PcCaseBlockEntity;
import com.strikerr212.minepc.client.ClientAccess;
import com.strikerr212.minepc.menu.AssemblyMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Регистрация сетевых пакетов мода. */
@EventBusSubscriber(modid = MineComputers.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModNetwork {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(LabelPayload.TYPE, LabelPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof AssemblyMenu menu) {
                        menu.setLabel(payload.text());
                    }
                }));
        registrar.playToServer(AssemblyValuesPayload.TYPE, AssemblyValuesPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player().containerMenu instanceof AssemblyMenu menu) {
                        menu.applyValues(payload.tab(), payload.values());
                    }
                }));

        // Подсказка у гнезда: игрок смотрит на устройство — сервер присылает, что в его портах
        registrar.playToServer(PortsRequestPayload.TYPE, PortsRequestPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp
                            && sp.blockPosition().distSqr(payload.pos()) < 16 * 16 && sp.serverLevel().isLoaded(payload.pos())) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                                com.strikerr212.minepc.lan.SwitchPorts.infoPayload(sp.serverLevel(), payload.pos()));
                    }
                }));
        registrar.playToClient(PortsInfoPayload.TYPE, PortsInfoPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientAccess.onPortsInfo(payload)));
        registrar.playToClient(LanActivityPayload.TYPE, LanActivityPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientAccess.onLanActivity(payload)));

        // Экран компьютера: сервер → игроки рядом
        registrar.playToClient(TerminalPayload.TYPE, TerminalPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientAccess.onTerminal(payload)));

        // Звук компьютера (пищалка, звуковой чип) и гул вентиляторов: сервер → игроки рядом
        // Звук — прямо в сетевом потоке, не дожидаясь следующего кадра игры (синтезатор потокобезопасен)
        registrar.executesOn(net.neoforged.neoforge.network.registration.HandlerThread.NETWORK)
                .playToClient(SoundPayload.TYPE, SoundPayload.STREAM_CODEC, (payload, context) -> ClientAccess.onSound(payload));
        registrar.playToClient(HumPayload.TYPE, HumPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientAccess.onHum(payload)));

        // Клавиатура: игрок → сервер. Проверяем, что игрок рядом и клавиатура подключена
        registrar.playToServer(TerminalInputPayload.TYPE, TerminalInputPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    Player player = context.player();
                    BlockPos pos = payload.host().pos();
                    if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 24 * 24) return;
                    PcCaseBlockEntity pc = com.strikerr212.minepc.block.Hosts.get(player.level(), payload.host());
                    if (pc == null) return;
                    if (payload.kind() == TerminalInputPayload.REFRESH) {
                        pc.requestFullFrame();
                        return;
                    }
                    if (payload.kind() == TerminalInputPayload.POWER) {
                        var msg = pc.togglePower();
                        if (msg != null) player.displayClientMessage(msg, true);
                        return;
                    }
                    if (payload.kind() >= TerminalInputPayload.MOUSE_DOWN && payload.kind() <= TerminalInputPayload.MOUSE_SCROLL) {
                        pc.mouse(payload.kind(), payload.flags(), payload.x(), payload.y());   // мышь работает и без клавиатуры
                        return;
                    }
                    if (!pc.hasKeyboard()) return;
                    // Стук клавиши слышат другие игроки рядом (сам игрок уже услышал его у себя)
                    if ((payload.flags() & TerminalInputPayload.FLAG_CLICK) != 0) pc.keyClick(player);
                    pc.input(payload.kind(), payload.text(), (payload.flags() & 1) != 0, (payload.flags() & 2) != 0);
                }));

        // Файл с компьютера игрока, перетащенный в окно экрана
        registrar.playToServer(FileUploadPayload.TYPE, FileUploadPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    Player player = context.player();
                    BlockPos pos = payload.host().pos();
                    if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 24 * 24) return;
                    PcCaseBlockEntity pc = com.strikerr212.minepc.block.Hosts.get(player.level(), payload.host());
                    if (pc == null) return;
                    var msg = pc.upload(payload.name(), payload.offset(), payload.total(), payload.data());
                    if (msg != null) player.displayClientMessage(msg, false);
                }));

        // Роутер: окно настроек
        registrar.playToClient(RouterOpenPayload.TYPE, RouterOpenPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> com.strikerr212.minepc.client.ClientAccess.openRouter(payload)));
        registrar.playToServer(RouterSavePayload.TYPE, RouterSavePayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer player)) return;
                    BlockPos pos = payload.pos();
                    if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 8 * 8) return;
                    if (!(player.level().getBlockEntity(pos) instanceof com.strikerr212.minepc.lan.RouterBlockEntity router)) return;
                    if (!payload.refresh()) {
                        String error = com.strikerr212.minepc.lan.RouterBlockEntity.validate(payload.ssid(), payload.password(), payload.radius());
                        if (error != null) {
                            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(error), true);
                            return;
                        }
                        router.apply(payload.ssid(), payload.password(), payload.radius());
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.minepc.router.saved"), true);
                    }
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                            com.strikerr212.minepc.lan.RouterBlock.openPayload(player.serverLevel(), pos, router));
                }));

        // Коммутатор: окно портов
        registrar.playToClient(SwitchOpenPayload.TYPE, SwitchOpenPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> com.strikerr212.minepc.client.ClientAccess.openSwitch(payload)));
        registrar.playToServer(SwitchPortPayload.TYPE, SwitchPortPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer player)) return;
                    BlockPos pos = payload.pos();
                    if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 8 * 8) return;
                    var blk = player.level().getBlockState(pos).getBlock();
                    if (!(blk instanceof com.strikerr212.minepc.lan.SwitchBlock) && !(blk instanceof com.strikerr212.minepc.block.RackBlock)) return;
                    var level = player.serverLevel();
                    if (payload.port() >= 0) {
                        com.strikerr212.minepc.lan.SwitchPorts.set(level, pos, payload.port(), payload.enabled(), payload.vlan());
                    }
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                            com.strikerr212.minepc.lan.SwitchPorts.openPayload(level, pos));
                }));
    }
}
