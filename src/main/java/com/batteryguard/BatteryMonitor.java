package com.batteryguard;

import org.apache.logging.log4j.Logger;

import net.minecraft.server.MinecraftServer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class BatteryMonitor {
    private final MinecraftServer server;
    private final Logger logger;
    private final WindowsBatteryProvider provider;
    private final BatteryWarningManager warnings;
    private final ServerShutdownManager shutdownManager;

    private volatile BatteryConfig config;

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> pollingTask;
    private ScheduledFuture<?> countdownTask;
    private volatile BatterySnapshot latestSnapshot =
        BatterySnapshot.unavailable("No battery query has completed yet.");
    private boolean lastAvailabilityKnown;
    private boolean lastBatteryPresent;
    private boolean unavailableReported;
    private boolean previousAc;
    private boolean warningSent;
    private boolean criticalWarningSent;
    private int shutdownRecoveryPolls;
    private boolean startupReported;

    public BatteryMonitor(MinecraftServer server, BatteryConfig config, Logger logger) {
        this.server = server;
        this.config = config;
        this.logger = logger;
        this.provider = new WindowsBatteryProvider();
        this.warnings = new BatteryWarningManager(server);
        this.shutdownManager = new ServerShutdownManager(server, logger, warnings);
    }

    public synchronized void start() {
        if (executor != null) {
            return;
        }

        if (!config.isEnabled()) {
            logger.info("[BatteryGuard] Battery monitoring disabled by configuration.");
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "BatteryGuard-Monitor");
                thread.setDaemon(true);
                return thread;
            }
        });

        pollingTask = executor.scheduleWithFixedDelay(
            new Runnable() {
                @Override
                public void run() {
                    pollBattery();
                }
            },
            0L,
            config.getBatteryCheckIntervalSeconds(),
            TimeUnit.SECONDS
        );

        countdownTask = executor.scheduleWithFixedDelay(
            new Runnable() {
                @Override
                public void run() {
                    tickCountdown();
                }
            },
            1L,
            1L,
            TimeUnit.SECONDS
        );

        logger.info("[BatteryGuard] Battery monitoring enabled.");
    }

    public synchronized void stop() {
        if (pollingTask != null) {
            pollingTask.cancel(false);
            pollingTask = null;
        }

        if (countdownTask != null) {
            countdownTask.cancel(false);
            countdownTask = null;
        }

        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public synchronized void applyConfig(BatteryConfig newConfig) {
        config = newConfig;
        // Apply both enable/disable and polling-interval changes by rebuilding
        // the single lightweight scheduler rather than mutating an active task.
        boolean wasRunning = executor != null;
        if (wasRunning) {
            stop();
        }
        if (newConfig.isEnabled()) {
            start();
        }
    }

    public void stopAutomaticProtection() {
        if (server.isCallingFromMinecraftThread()) {
            shutdownManager.cancelAutomatic(BatteryMessages.CancellationReason.CONFIGURATION_DISABLED, config);
        } else {
            server.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    shutdownManager.cancelAutomatic(
                        BatteryMessages.CancellationReason.CONFIGURATION_DISABLED,
                        config
                    );
                }
            });
        }
    }

    public BatterySnapshot getLatestSnapshot() {
        return latestSnapshot;
    }

    public boolean isEmergencyShutdownActive() {
        return shutdownManager.isActive();
    }

    public synchronized void requestImmediateCheck(final net.minecraft.command.ICommandSender sender) {
        if (executor == null) {
            executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "BatteryGuard-Check");
                    thread.setDaemon(true);
                    return thread;
                }
            });
        }

        executor.submit(new Runnable() {
            @Override
            public void run() {
                final BatterySnapshot snapshot = provider.query();
                latestSnapshot = snapshot;

                server.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        sendStartupOrCheckLog(snapshot);
                        sender.sendMessage(BatteryMessages.snapshotMessage(snapshot, config.getLanguage()));
                    }
                });
            }
        });
    }

    public void manualShutdown() {
        server.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                shutdownManager.startManual(config);
            }
        });
    }

    public void cancelShutdown() {
        server.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                shutdownManager.cancelAny(config);
            }
        });
    }

    private void pollBattery() {
        final BatterySnapshot snapshot = provider.query();
        latestSnapshot = snapshot;

        server.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                processBatterySnapshot(snapshot);
            }
        });
    }

    private void tickCountdown() {
        server.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                shutdownManager.tick(config);
            }
        });
    }

    private void processBatterySnapshot(BatterySnapshot snapshot) {
        if (!config.isEnabled()) {
            return;
        }

        if (!startupReported) {
            sendStartupOrCheckLog(snapshot);
            startupReported = true;
            if (!snapshot.isBatteryInfoValid()
                && snapshot.getPowerSource() != BatterySnapshot.PowerSource.NO_BATTERY) {
                unavailableReported = true;
            }
        }

        if (!snapshot.isBatteryInfoValid()) {
            if (snapshot.getPowerSource() == BatterySnapshot.PowerSource.NO_BATTERY) {
                if (lastBatteryPresent) {
                    logger.info("[BatteryGuard] Windows reports that no system battery is present.");
                }
                lastAvailabilityKnown = false;
                lastBatteryPresent = false;
                if (shutdownManager.isAutomatic()) {
                    shutdownManager.cancelAutomatic(
                        BatteryMessages.CancellationReason.NO_BATTERY,
                        config
                    );
                }
                return;
            }

            if (!unavailableReported) {
                logger.warn(
                    "[BatteryGuard] WARNING: Unable to read Windows battery information."
                );
                logger.warn(
                    "[BatteryGuard] Automatic battery shutdown protection is disabled."
                );
                unavailableReported = true;
            }

            lastAvailabilityKnown = false;
            lastBatteryPresent = false;

            if (shutdownManager.isAutomatic()) {
                shutdownManager.cancelAutomatic(
                    BatteryMessages.CancellationReason.BATTERY_INFORMATION_UNAVAILABLE,
                    config
                );
            }
            return;
        }

        if (!lastAvailabilityKnown) {
            if (unavailableReported) {
                logger.info("[BatteryGuard] Battery information is available again.");
            }
            unavailableReported = false;
        }

        lastAvailabilityKnown = true;
        lastBatteryPresent = true;

        boolean acConnected = snapshot.isAcConnected();
        if (acConnected && !previousAc) {
            warningSent = false;
            criticalWarningSent = false;

            if (shutdownManager.isAutomatic()
                && config.isCancelShutdownWhenCharging()) {
                shutdownManager.cancelAutomatic(
                    BatteryMessages.CancellationReason.AC_POWER_RESTORED,
                    config
                );
            }
        }

        if (!acConnected && previousAc) {
            // New discharge cycle.
            warningSent = false;
            criticalWarningSent = false;
        }

        previousAc = acConnected;

        logger.debug(
            "[BatteryGuard] Battery query: {}%, power source {}.",
            snapshot.getPercent(),
            snapshot.getPowerSource()
        );

        if (!snapshot.isOnBattery()) {
            shutdownRecoveryPolls = 0;
            return;
        }

        int percent = snapshot.getPercent();

        // Require two consecutive polling results above the shutdown threshold
        // before cancelling on battery recovery. This simple hysteresis prevents
        // a single 10% -> 11% -> 10% fluctuation from cancelling and restarting
        // the same emergency countdown.
        if (shutdownManager.isAutomatic()
            && config.isCancelShutdownWhenCharging()
            && percent > config.getShutdownPercent()) {
            shutdownRecoveryPolls++;

            if (shutdownRecoveryPolls >= 2) {
                shutdownManager.cancelAutomatic(
                    BatteryMessages.CancellationReason.BATTERY_RECOVERED,
                    config
                );
                shutdownRecoveryPolls = 0;
            }
            return;
        }

        shutdownRecoveryPolls = 0;

        // Escalate once per discharge cycle. When a server starts already at a
        // critical/emergency level, only the most relevant warning is sent.
        if (percent <= config.getShutdownPercent()) {
            warningSent = true;
            criticalWarningSent = true;

            if (!shutdownManager.isActive()) {
                shutdownManager.startAutomatic(percent, config);
            }
            return;
        }

        if (percent <= config.getCriticalWarningPercent() && !criticalWarningSent) {
            warningSent = true;
            criticalWarningSent = true;
            warnings.broadcast(
                BatteryMessages.criticalWarning(
                    percent, config.getShutdownPercent(), config.getLanguage()
                )
            );
            return;
        }

        if (percent <= config.getWarningPercent() && !warningSent) {
            warningSent = true;
            warnings.broadcast(BatteryMessages.warning(percent, config.getLanguage()));
        }
    }

    private void sendStartupOrCheckLog(BatterySnapshot snapshot) {
        if (snapshot.isBatteryInfoValid()) {
            logger.info("[BatteryGuard] Battery: {}%", snapshot.getPercent());
            logger.info("[BatteryGuard] Power source: {}", snapshot.getPowerSource().name());
            logger.info("[BatteryGuard] Warning threshold: {}%", config.getWarningPercent());
            logger.info(
                "[BatteryGuard] Critical threshold: {}%",
                config.getCriticalWarningPercent()
            );
            logger.info("[BatteryGuard] Shutdown threshold: {}%", config.getShutdownPercent());
            logger.info(
                "[BatteryGuard] Poll interval: {} seconds",
                config.getBatteryCheckIntervalSeconds()
            );
        } else if (snapshot.getPowerSource() == BatterySnapshot.PowerSource.NO_BATTERY) {
            logger.info("[BatteryGuard] No system battery detected.");
        } else {
            logger.warn("[BatteryGuard] WARNING: Unable to read Windows battery information: {}", snapshot.getProblem());
            logger.warn("[BatteryGuard] Automatic battery shutdown protection is disabled.");
        }
    }
}
