package com.batteryguard;

import org.apache.logging.log4j.Logger;

import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;

import java.util.concurrent.TimeUnit;

public final class ServerShutdownManager {
    private final MinecraftServer server;
    private final Logger logger;
    private final BatteryWarningManager warnings;

    private boolean active;
    private boolean automatic;
    private ICommandSender manualInitiator;
    private long deadlineNanos;
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
        manualInitiator = null;
        deadlineNanos = System.nanoTime()
            + TimeUnit.SECONDS.toNanos(config.getShutdownCountdownSeconds());
        lastAnnouncedSecond = config.getShutdownCountdownSeconds();

        warnings.broadcast(
            BatteryMessages.emergencyShutdown(
                batteryPercent,
                config.getShutdownCountdownSeconds(),
                config.getLanguage()
            ),
            config
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

    public void startManual(ICommandSender initiator, BatteryConfig config) {
        if (active) {
            return;
        }

        active = true;
        automatic = false;
        manualInitiator = initiator;
        deadlineNanos = System.nanoTime()
            + TimeUnit.SECONDS.toNanos(config.getShutdownCountdownSeconds());
        lastAnnouncedSecond = config.getShutdownCountdownSeconds();

        ITextComponent manualShutdownMessage = BatteryMessages.manualShutdown(
            config.getShutdownCountdownSeconds(),
            config.getLanguage()
        );
        if (config.areWarningsEnabled()) {
            warnings.broadcast(manualShutdownMessage, config);
        } else if (initiator != null) {
            initiator.sendMessage(manualShutdownMessage);
        }
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

        long remainingNanos = deadlineNanos - System.nanoTime();
        long remainingSeconds = remainingNanos <= 0L
            ? 0L
            : TimeUnit.NANOSECONDS.toSeconds(remainingNanos + 999_999_999L);

        if (remainingSeconds <= 0L) {
            initiateMinecraftShutdown();
            return;
        }

        int[] announcementSeconds = new int[] {30, 20, 10, 5, 4, 3, 2, 1};
        for (int seconds : announcementSeconds) {
            if (remainingSeconds == seconds && lastAnnouncedSecond > seconds) {
                lastAnnouncedSecond = seconds;
                ITextComponent countdownMessage = BatteryMessages.countdown(seconds, config.getLanguage());
                if (automatic || config.areWarningsEnabled()) {
                    warnings.broadcast(countdownMessage, config);
                } else if (manualInitiator != null) {
                    manualInitiator.sendMessage(countdownMessage);
                }
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
        manualInitiator = null;
        deadlineNanos = 0L;
        lastAnnouncedSecond = Long.MAX_VALUE;

        warnings.broadcast(BatteryMessages.cancellation(reason, config.getLanguage()), config);
        logger.info("[BatteryGuard] Automatic emergency shutdown cancelled: {}", reason);
    }

    public void cancelAny(BatteryConfig config) {
        if (!active) {
            return;
        }

        ICommandSender initiator = manualInitiator;
        boolean wasManual = !automatic;

        active = false;
        automatic = false;
        manualInitiator = null;
        deadlineNanos = 0L;
        lastAnnouncedSecond = Long.MAX_VALUE;

        ITextComponent cancellationMessage = BatteryMessages.administratorCancellation(config.getLanguage());
        if (wasManual && !config.areWarningsEnabled() && initiator != null) {
            initiator.sendMessage(cancellationMessage);
        } else {
            warnings.broadcast(cancellationMessage, config);
        }
        logger.info("[BatteryGuard] Emergency server shutdown cancelled by administrator.");
    }

    private void initiateMinecraftShutdown() {
        if (!active) {
            return;
        }

        active = false;
        automatic = false;
        manualInitiator = null;
        deadlineNanos = 0L;
        lastAnnouncedSecond = Long.MAX_VALUE;

        logger.warn("[BatteryGuard] Initiating normal Minecraft server shutdown.");
        server.initiateShutdown();
    }
}
