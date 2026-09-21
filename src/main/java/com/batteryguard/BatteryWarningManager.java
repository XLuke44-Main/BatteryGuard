package com.batteryguard;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;

public final class BatteryWarningManager {
    private final MinecraftServer server;

    public BatteryWarningManager(MinecraftServer server) {
        this.server = server;
    }

    public void broadcast(ITextComponent message) {
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            player.sendMessage(message);
        }
    }
}
