package com.batteryguard;

import org.apache.logging.log4j.Logger;

import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class BatteryConfig {
    private static final String CATEGORY_GENERAL = "general";
    private static final String CATEGORY_PLAYERS = "players";

    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_WARNING_PERCENT = 30;
    public static final int DEFAULT_CRITICAL_WARNING_PERCENT = 20;
    public static final int DEFAULT_SHUTDOWN_PERCENT = 10;
    public static final int DEFAULT_SHUTDOWN_COUNTDOWN_SECONDS = 30;
    public static final int DEFAULT_BATTERY_CHECK_INTERVAL_SECONDS = 5;
    public static final boolean DEFAULT_CANCEL_SHUTDOWN_WHEN_CHARGING = true;
    public static final String DEFAULT_LANGUAGE = "en";

    public enum Language {
        EN("en"),
        PL("pl");

        private final String code;

        Language(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }

        public static Language from(String value) {
            return "pl".equals(value) ? PL : EN;
        }

        public static boolean isSupported(String value) {
            return "en".equals(value) || "pl".equals(value);
        }
    }

    private final File file;
    private final Language language;
    private final boolean enabled;
    private final int warningPercent;
    private final int criticalWarningPercent;
    private final int shutdownPercent;
    private final int shutdownCountdownSeconds;
    private final int batteryCheckIntervalSeconds;
    private final boolean cancelShutdownWhenCharging;
    private final Set<UUID> playerUuids;

    private BatteryConfig(
        File file,
        Language language,
        boolean enabled,
        int warningPercent,
        int criticalWarningPercent,
        int shutdownPercent,
        int shutdownCountdownSeconds,
        int batteryCheckIntervalSeconds,
        boolean cancelShutdownWhenCharging,
        Set<UUID> playerUuids
    ) {
        this.file = file;
        this.language = language;
        this.enabled = enabled;
        this.warningPercent = warningPercent;
        this.criticalWarningPercent = criticalWarningPercent;
        this.shutdownPercent = shutdownPercent;
        this.shutdownCountdownSeconds = shutdownCountdownSeconds;
        this.batteryCheckIntervalSeconds = batteryCheckIntervalSeconds;
        this.cancelShutdownWhenCharging = cancelShutdownWhenCharging;
        this.playerUuids = new HashSet<UUID>(playerUuids);
    }

    public static BatteryConfig load(File file, Logger logger) {
        Configuration configuration = new Configuration(file);
        configuration.load();

        String configuredLanguage = configuration.getString(
            "language", CATEGORY_GENERAL, DEFAULT_LANGUAGE,
            "Global server language for Battery Guard messages. Accepted values: en, pl."
        );
        Language language;
        if (Language.isSupported(configuredLanguage)) {
            language = Language.from(configuredLanguage);
        } else {
            language = Language.EN;
            logger.warn(
                "[BatteryGuard] Invalid language '{}'; falling back to English.",
                configuredLanguage
            );
            configuration.get(CATEGORY_GENERAL, "language", DEFAULT_LANGUAGE,
                "Global server language for Battery Guard messages. Accepted values: en, pl.")
                .set(DEFAULT_LANGUAGE);
        }

        boolean enabled = configuration.getBoolean(
            "enabled", CATEGORY_GENERAL, DEFAULT_ENABLED,
            "Enable Battery Guard automatic battery monitoring."
        );

        int warning = configuration.getInt(
            "warningPercent", CATEGORY_GENERAL, DEFAULT_WARNING_PERCENT, 0, 100,
            "Broadcast the warning to all online players at or below this percentage."
        );

        int critical = configuration.getInt(
            "criticalWarningPercent", CATEGORY_GENERAL, DEFAULT_CRITICAL_WARNING_PERCENT, 0, 100,
            "Broadcast the critical warning to all online players at or below this percentage."
        );

        int shutdown = configuration.getInt(
            "shutdownPercent", CATEGORY_GENERAL, DEFAULT_SHUTDOWN_PERCENT, 0, 100,
            "Start the automatic Minecraft server shutdown countdown at or below this percentage while on battery."
        );

        int countdown = configuration.getInt(
            "shutdownCountdownSeconds", CATEGORY_GENERAL, DEFAULT_SHUTDOWN_COUNTDOWN_SECONDS, 0, Integer.MAX_VALUE,
            "Seconds before an automatic or administrator-requested graceful Minecraft server shutdown."
        );

        int interval = configuration.getInt(
            "batteryCheckIntervalSeconds", CATEGORY_GENERAL, DEFAULT_BATTERY_CHECK_INTERVAL_SECONDS, 1, Integer.MAX_VALUE,
            "Seconds between Windows battery checks."
        );

        boolean cancelWhenCharging = configuration.getBoolean(
            "cancelShutdownWhenCharging", CATEGORY_GENERAL, DEFAULT_CANCEL_SHUTDOWN_WHEN_CHARGING,
            "Cancel an automatic shutdown countdown when AC power returns or the battery recovers above the shutdown threshold."
        );

        int safeWarning = clamp(warning, 0, 100);
        int safeCritical = clamp(critical, 0, 100);
        int safeShutdown = clamp(shutdown, 0, 100);
        int safeCountdown = Math.max(0, countdown);
        int safeInterval = Math.max(1, interval);

        if (safeCritical > safeWarning) {
            logger.warn(
                "[BatteryGuard] criticalWarningPercent ({}) was above warningPercent ({}); correcting criticalWarningPercent to {}.",
                safeCritical, safeWarning, safeWarning
            );
            safeCritical = safeWarning;
        }

        if (safeShutdown > safeCritical) {
            logger.warn(
                "[BatteryGuard] shutdownPercent ({}) was above criticalWarningPercent ({}); correcting shutdownPercent to {}.",
                safeShutdown, safeCritical, safeCritical
            );
            safeShutdown = safeCritical;
        }

        String[] configuredPlayers = configuration.getStringList(
            "playerUuids", CATEGORY_PLAYERS, new String[0],
            "UUIDs of non-OP players permitted to use Battery Guard read-only commands."
        );

        Set<UUID> playerUuids = new HashSet<UUID>();
        for (String value : configuredPlayers) {
            try {
                playerUuids.add(UUID.fromString(value));
            } catch (IllegalArgumentException ex) {
                logger.warn("[BatteryGuard] Ignoring invalid player UUID in configuration: {}", value);
            }
        }

        configuration.save();

        return new BatteryConfig(
            file,
            language,
            enabled,
            safeWarning,
            safeCritical,
            safeShutdown,
            safeCountdown,
            safeInterval,
            cancelWhenCharging,
            playerUuids
        );
    }

    public synchronized void save() {
        Configuration configuration = new Configuration(file);
        configuration.load();

        configuration.getString(
            "language", CATEGORY_GENERAL, language.getCode(),
            "Global server language for Battery Guard messages. Accepted values: en, pl."
        );
        configuration.getBoolean(
            "enabled", CATEGORY_GENERAL, enabled,
            "Enable Battery Guard automatic battery monitoring."
        );
        configuration.getInt(
            "warningPercent", CATEGORY_GENERAL, warningPercent, 0, 100,
            "Broadcast the warning to all online players at or below this percentage."
        );
        configuration.getInt(
            "criticalWarningPercent", CATEGORY_GENERAL, criticalWarningPercent, 0, 100,
            "Broadcast the critical warning to all online players at or below this percentage."
        );
        configuration.getInt(
            "shutdownPercent", CATEGORY_GENERAL, shutdownPercent, 0, 100,
            "Start the automatic Minecraft server shutdown countdown at or below this percentage while on battery."
        );
        configuration.getInt(
            "shutdownCountdownSeconds", CATEGORY_GENERAL, shutdownCountdownSeconds, 0, Integer.MAX_VALUE,
            "Seconds before an automatic or administrator-requested graceful Minecraft server shutdown."
        );
        configuration.getInt(
            "batteryCheckIntervalSeconds", CATEGORY_GENERAL, batteryCheckIntervalSeconds, 1, Integer.MAX_VALUE,
            "Seconds between Windows battery checks."
        );
        configuration.getBoolean(
            "cancelShutdownWhenCharging", CATEGORY_GENERAL, cancelShutdownWhenCharging,
            "Cancel an automatic shutdown countdown when AC power returns or the battery recovers above the shutdown threshold."
        );

        String[] uuids = new String[playerUuids.size()];
        int i = 0;
        for (UUID uuid : playerUuids) {
            uuids[i++] = uuid.toString();
        }
        configuration.get(
            CATEGORY_PLAYERS,
            "playerUuids",
            uuids,
            "UUIDs of non-OP players permitted to use Battery Guard read-only commands."
        ).set(uuids);

        configuration.save();
    }

    public synchronized boolean addPlayer(UUID uuid) {
        return playerUuids.add(uuid);
    }

    public synchronized boolean removePlayer(UUID uuid) {
        return playerUuids.remove(uuid);
    }

    public synchronized boolean isPlayerPermitted(UUID uuid) {
        return playerUuids.contains(uuid);
    }

    public synchronized Set<UUID> getPlayerUuids() {
        return Collections.unmodifiableSet(new HashSet<UUID>(playerUuids));
    }

    public Language getLanguage() {
        return language;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getWarningPercent() {
        return warningPercent;
    }

    public int getCriticalWarningPercent() {
        return criticalWarningPercent;
    }

    public int getShutdownPercent() {
        return shutdownPercent;
    }

    public int getShutdownCountdownSeconds() {
        return shutdownCountdownSeconds;
    }

    public int getBatteryCheckIntervalSeconds() {
        return batteryCheckIntervalSeconds;
    }

    public boolean isCancelShutdownWhenCharging() {
        return cancelShutdownWhenCharging;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
