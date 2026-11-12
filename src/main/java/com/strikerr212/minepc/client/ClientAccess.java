package com.strikerr212.minepc.client;

import com.strikerr212.minepc.network.TerminalPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Точка входа в клиентский код из общего (блоков и сетевых пакетов). Методы вызываются только у игрока,
 * поэтому на сервере этот класс даже не загружается.
 */
public final class ClientAccess {

    private ClientAccess() {
    }

    public static void openTerminal(BlockPos casePos) {
        Minecraft.getInstance().setScreen(new TerminalScreen(casePos));
    }

    public static void onSound(com.strikerr212.minepc.network.SoundPayload payload) {
        ComputerAudio.receive(payload.pos(), payload.startMs(), payload.nowMs(), payload.commands());
    }

    public static void onHum(com.strikerr212.minepc.network.HumPayload payload) {
        HumSounds.update(payload.pos(), payload.fan(), payload.hdd(), payload.start());
    }

    public static void onPortsInfo(com.strikerr212.minepc.network.PortsInfoPayload payload) {
        NetHud.onPortsInfo(payload);
    }

    public static void onLanActivity(com.strikerr212.minepc.network.LanActivityPayload payload) {
        NetHud.activity(payload.pos());
    }

    public static void onTerminal(TerminalPayload payload) {
        ClientTerminals.update(payload.casePos(), payload.data(), payload.keyboard());
    }

    /** Окно роутера: открыть или (если уже открыто) обновить список подключённых. */
    public static void openRouter(com.strikerr212.minepc.network.RouterOpenPayload payload) {
        var mc = Minecraft.getInstance();
        com.mojang.logging.LogUtils.getLogger().debug("Окно роутера {}", payload.pos());
        if (mc.screen instanceof RouterScreen s && s.pos().equals(payload.pos())) s.update(payload);
        else mc.setScreen(new RouterScreen(payload));
    }

    public static void openSwitch(com.strikerr212.minepc.network.SwitchOpenPayload payload) {
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof SwitchScreen s && s.pos().equals(payload.pos())) s.update(payload);
        else mc.setScreen(new SwitchScreen(payload));
    }
}
