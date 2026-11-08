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

        // Экран компьютера: сервер → игроки рядом
        registrar.playToClient(TerminalPayload.TYPE, TerminalPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientAccess.onTerminal(payload)));

        // Клавиатура: игрок → сервер. Проверяем, что игрок рядом и клавиатура подключена
        registrar.playToServer(TerminalInputPayload.TYPE, TerminalInputPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    Player player = context.player();
                    BlockPos pos = payload.casePos();
                    if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 12 * 12) return;
                    if (!(player.level().getBlockEntity(pos) instanceof PcCaseBlockEntity pc)) return;
                    if (payload.kind() == TerminalInputPayload.POWER) {
                        var msg = pc.togglePower();
                        if (msg != null) player.displayClientMessage(msg, true);
                        return;
                    }
                    if (!pc.hasKeyboard()) return;
                    pc.input(payload.kind(), payload.text(), (payload.flags() & 1) != 0, (payload.flags() & 2) != 0);
                }));
    }
}
