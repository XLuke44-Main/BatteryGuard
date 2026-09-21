package com.batteryguard;

import org.apache.logging.log4j.Logger;

import net.minecraft.server.MinecraftServer;

public final class ServerShutdownManager {
    private final MinecraftServer server;
    private final Logger logger;
    private final BatteryWarningManager warnings;

    private boolean active;
    private boolean automatic;
    private long deadlineMillis;
    private long lastAnnouncedSecond = Long.MAX_VALUE;

    public ServerShutdownManager(
        MinecraftServer server,
        Logger logger,
        BatteryWarningManager warnings
    ) {
        this.server = server;
        this.logger = logger;
        this.warnings = warnings;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isAutomatic() {
        return automatic;
    }

    public void startAutomatic(int batteryPercent, BatteryConfig config) {
        if (active) {
            return;
        }

        active = true;
        automatic = true;
        deadlineMillis = System.currentTimeMillis()
            + (config.getShutdownCountdownSeconds() * 1000L);
        lastAnnouncedSecond = config.getShutdownCountdownSeconds();

        warnings.broadcast(
            BatteryMessages.emergencyShutdown(
                batteryPercent,
                config.getShutdownCountdownSeconds(),
                config.getLanguage()
            )
        );
        logger.warn(
            "[BatteryGuard] Automatic emergency shutdown started at {}%. Countdown: {} seconds.",
            batteryPercent,
            config.getShutdownCountdownSeconds()
        );

        if (config.getShutdownCountdownSeconds() <= 0) {
            initiateMinecraftShutdown();
        }
    }

    public void startManual(BatteryConfig config) {
        if (active) {
            return;
        }

        active = true;
        automatic = false;
        deadlineMillis = System.currentTimeMillis()
            + (config.getShutdownCountdownSeconds() * 1000L);
        lastAnnouncedSecond = config.getShutdownCountdownSeconds();

        warnings.broadcast(
            BatteryMessages.manualShutdown(
                config.getShutdownCountdownSeconds(),
                config.getLanguage()
            )
        );
        logger.warn(
            "[BatteryGuard] Manual graceful Minecraft server shutdown started. Countdown: {} seconds.",
            config.getShutdownCountdownSeconds()
        );

        if (config.getShutdownCountdownSeconds() <= 0) {
            initiateMinecraftShutdown();
        }
    }

    public void tick(BatteryConfig config) {
        if (!active) {
            return;
        }

        long remainingMillis = deadlineMillis - System.currentTimeMillis();
        long remainingSeconds = Math.max(0L, (remainingMillis + 999L) / 1000L);

        if (remainingSeconds <= 0L) {
            initiateMinecraftShutdown();
            return;
        }

        int[] announcementSeconds = new int[] {30, 20, 10, 5, 4, 3, 2, 1};
        for (int seconds : announcementSeconds) {
            if (remainingSeconds <= seconds && lastAnnouncedSecond > seconds) {
                lastAnnouncedSecond = seconds;
                warnings.broadcast(BatteryMessages.countdown(seconds, config.getLanguage()));
                return;
            }
        }
    }

    public void cancelAutomatic(
        BatteryMessages.CancellationReason reason,
        BatteryConfig config
    ) {
        if (!active || !automatic) {
            return;
        }

        active = false;
        automatic = false;
        deadlineMillis = 0L;
        lastAnnouncedSecond = Long.MAX_VALUE;

        warnings.broadcast(BatteryMessages.cancellation(reason, config.getLanguage()));
        logger.info("[BatteryGuard] Automatic emergency shutdown cancelled: {}", reason);
    }

    public void cancelAny(BatteryConfig config) {
        if (!active) {
            return;
        }

        active = false;
        automatic = false;
        deadlineMillis = 0L;
        lastAnnouncedSecond = Long.MAX_VALUE;

        warnings.broadcast(BatteryMessages.administratorCancellation(config.getLanguage()));
        logger.info("[BatteryGuard] Emergency server shutdown cancelled by administrator.");
    }

    private void initiateMinecraftShutdown() {
        if (!active) {
            return;
        }

        active = false;
        automatic = false;
        deadlineMillis = 0L;
        lastAnnouncedSecond = Long.MAX_VALUE;

        logger.warn("[BatteryGuard] Initiating normal Minecraft server shutdown.");
        server.initiateShutdown();
    }
}
