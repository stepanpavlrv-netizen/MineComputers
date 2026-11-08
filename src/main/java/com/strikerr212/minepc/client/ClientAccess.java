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

    public static void onTerminal(TerminalPayload payload) {
        ClientTerminals.update(payload.casePos(), payload.data(), payload.keyboard());
    }
}
