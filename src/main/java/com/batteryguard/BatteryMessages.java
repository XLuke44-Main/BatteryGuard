package com.batteryguard;

import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class BatteryMessages {
    private static final String LANGUAGE_RESOURCE_PREFIX = "lang/";
    private static final String LANGUAGE_RESOURCE_SUFFIX = ".lang";
    private static final String DEFAULT_LANGUAGE = "en";
    private static final Map<String, Properties> LANGUAGES = new HashMap<String, Properties>();

    public enum CancellationReason {
        AC_POWER_RESTORED,
        BATTERY_RECOVERED,
        NO_BATTERY,
        BATTERY_INFORMATION_UNAVAILABLE,
        CONFIGURATION_DISABLED
    }

    private BatteryMessages() {
    }

    public static boolean isLanguageAvailable(String languageCode) {
        String normalized = normalize(languageCode);
        if (normalized.isEmpty() || !normalized.matches("[a-z0-9_-]+")) {
            return false;
        }

        try {
            return loadLanguage(normalized) != null;
        } catch (IllegalStateException ex) {
            return false;
        }
    }

    public static String usage(boolean admin, String language) {
        return message(
            language,
            admin
                ? "command.usage.admin"
                : "command.usage.player"
        );
    }

    public static String permissionDenied(String language) {
        return message(language, "permission.denied");
    }

    public static String monitoringInactive(String language) {
        return message(language, "monitoring.inactive");
    }

    public static String checkScheduled(String language) {
        return message(language, "check.scheduled");
    }

    public static String noShutdownActive(String language) {
        return message(language, "shutdown.none.active");
    }

    public static String shutdownCancelRequested(String language) {
        return message(language, "shutdown.cancel.requested");
    }

    public static String shutdownAlreadyActive(String language) {
        return message(language, "shutdown.already.active");
    }

    public static String shutdownInitiated(String language) {
        return message(language, "shutdown.initiated");
    }

    public static String reloadComplete(String language) {
        return message(language, "reload.complete");
    }

    public static String playerProfileMissing(String language, String username) {
        return message(language, "player.profile.missing", username);
    }

    public static String configuredPlayerMissing(String language, String username) {
        return message(language, "player.configured.missing", username);
    }

    public static String playerAdded(String language, String name, String uuid) {
        return message(language, "player.added", name, uuid);
    }

    public static String playerAlreadyAdded(String language, String name) {
        return message(language, "player.already.added", name);
    }

    public static String playerRemoved(String language, String name, String uuid) {
        return message(language, "player.removed", name, uuid);
    }

    public static String playerNotAdded(String language, String name) {
        return message(language, "player.not.added", name);
    }

    public static String permittedPlayers(String language, int count) {
        return message(language, "player.permitted.count", count);
    }

    public static String listEntry(String language, String display) {
        return message(language, "player.list.entry", display);
    }

    public static TextComponentString batteryMessage(
        BatterySnapshot snapshot,
        String language
    ) {
        if (!snapshot.isBatteryInfoValid()) {
            if (snapshot.getPowerSource() == BatterySnapshot.PowerSource.NO_BATTERY) {
                return colored(
                    message(language, "battery.no.battery"),
                    TextFormatting.GRAY
                );
            }

            return colored(
                message(language, "battery.unavailable"),
                TextFormatting.RED
            );
        }

        TextFormatting color;
        if (snapshot.getPercent() > 50) {
            color = TextFormatting.GREEN;
        } else if (snapshot.getPercent() >= 21) {
            color = TextFormatting.YELLOW;
        } else {
            color = TextFormatting.RED;
        }

        return colored(
            message(language, "battery.percent", snapshot.getPercent()),
            color
        );
    }

    public static String batteryLine(
        BatterySnapshot snapshot,
        String language
    ) {
        if (!snapshot.isBatteryInfoValid()) {
            if (snapshot.getPowerSource() == BatterySnapshot.PowerSource.NO_BATTERY) {
                return message(language, "battery.no.battery");
            }
            return message(language, "battery.unavailable");
        }
        return message(language, "battery.percent", snapshot.getPercent());
    }

    public static TextComponentString snapshotMessage(
        BatterySnapshot snapshot,
        String language
    ) {
        TextComponentString result = new TextComponentString("");
        result.appendSibling(batteryMessage(snapshot, language));
        result.appendText(
            message(language, "snapshot.power.source", powerSourceText(snapshot, language))
        );
        return result;
    }

    public static String powerSourceText(
        BatterySnapshot snapshot,
        String language
    ) {
        switch (snapshot.getPowerSource()) {
            case AC:
                return message(language, "power.source.ac");
            case BATTERY:
                return message(language, "power.source.battery");
            case NO_BATTERY:
                return message(language, "power.source.no.battery");
            default:
                return message(language, "power.source.unknown");
        }
    }

    public static TextComponentString warning(int percent, String language) {
        return colored(
            message(language, "warning", percent),
            TextFormatting.YELLOW
        );
    }

    public static TextComponentString criticalWarning(
        int percent,
        int shutdownPercent,
        String language
    ) {
        return colored(
            message(language, "critical.warning", percent, shutdownPercent),
            TextFormatting.RED
        );
    }

    public static TextComponentString emergencyShutdown(
        int batteryPercent,
        int seconds,
        String language
    ) {
        return colored(
            message(language, "emergency.shutdown", batteryPercent, seconds),
            TextFormatting.RED
        );
    }

    public static TextComponentString manualShutdown(
        int seconds,
        String language
    ) {
        return colored(
            message(language, "manual.shutdown", seconds),
            TextFormatting.RED
        );
    }

    public static TextComponentString countdown(long seconds, String language) {
        String key;
        if (seconds == 1) {
            key = "countdown.single";
        } else if (seconds % 10 >= 2 && seconds % 10 <= 4
            && (seconds % 100 < 10 || seconds % 100 >= 20)) {
            key = "countdown.few";
        } else {
            key = "countdown.other";
        }

        return colored(
            message(language, key, seconds),
            TextFormatting.RED
        );
    }

    public static TextComponentString cancellation(
        CancellationReason reason,
        String language
    ) {
        return colored(
            message(language, "cancellation." + reason.name().toLowerCase(Locale.ENGLISH)),
            TextFormatting.GREEN
        );
    }

    public static TextComponentString administratorCancellation(String language) {
        return colored(
            message(language, "administrator.cancellation"),
            TextFormatting.YELLOW
        );
    }

    public static String statusAvailable(String language) {
        return message(language, "status.available");
    }

    public static String statusUnavailable(String language) {
        return message(language, "status.unavailable");
    }

    public static String statusActive(String language) {
        return message(language, "status.active");
    }

    public static String statusInactive(String language) {
        return message(language, "status.inactive");
    }

    public static String statusPowerSource(String language) {
        return message(language, "status.power.source");
    }

    public static String statusBatteryInformation(String language) {
        return message(language, "status.battery.information");
    }

    public static String statusWarningThreshold(String language) {
        return message(language, "status.warning.threshold");
    }

    public static String statusCriticalThreshold(String language) {
        return message(language, "status.critical.threshold");
    }

    public static String statusShutdownThreshold(String language) {
        return message(language, "status.shutdown.threshold");
    }

    public static String statusEmergencyShutdown(String language) {
        return message(language, "status.emergency.shutdown");
    }

    private static String message(String language, String key, Object... arguments) {
        Properties properties = loadLanguage(normalize(language));
        if (properties == null && !DEFAULT_LANGUAGE.equals(normalize(language))) {
            properties = loadLanguage(DEFAULT_LANGUAGE);
        }
        if (properties == null) {
            throw new IllegalStateException("BatteryGuard language resource '" + language + "' could not be loaded.");
        }

        String value = properties.getProperty(key);
        if (value == null) {
            if (!DEFAULT_LANGUAGE.equals(normalize(language))) {
                Properties defaults = loadLanguage(DEFAULT_LANGUAGE);
                value = defaults == null ? null : defaults.getProperty(key);
            }
        }
        if (value == null) {
            throw new IllegalStateException(
                "BatteryGuard translation key '" + key + "' is missing from language '" + language + "'."
            );
        }

        return arguments.length == 0
            ? value
            : String.format(Locale.ENGLISH, value, arguments);
    }

    private static synchronized Properties loadLanguage(String languageCode) {
        String normalized = normalize(languageCode);
        if (LANGUAGES.containsKey(normalized)) {
            return LANGUAGES.get(normalized);
        }

        String resourceName = LANGUAGE_RESOURCE_PREFIX + normalized + LANGUAGE_RESOURCE_SUFFIX;
        InputStream stream = BatteryMessages.class.getClassLoader().getResourceAsStream(resourceName);
        if (stream == null) {
            return null;
        }

        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException ex) {
            throw new IllegalStateException(
                "BatteryGuard language resource '" + resourceName + "' could not be read.",
                ex
            );
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                "BatteryGuard language resource '" + resourceName + "' contains invalid properties.",
                ex
            );
        }

        LANGUAGES.put(normalized, properties);
        return properties;
    }

    private static String normalize(String languageCode) {
        String normalized = languageCode == null
            ? ""
            : languageCode.trim().toLowerCase(Locale.ENGLISH);
        return normalized.matches("[a-z0-9_-]+") ? normalized : "";
    }

    private static TextComponentString colored(String message, TextFormatting color) {
        TextComponentString result = new TextComponentString(message);
        result.setStyle(new Style().setColor(color));
        return result;
    }
}
