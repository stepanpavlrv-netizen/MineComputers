package com.strikerr212.minepc.network;

import com.strikerr212.minepc.MineComputers;
import com.strikerr212.minepc.menu.AssemblyMenu;
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
    }
}
