package com.batteryguard;

import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class BatteryConfig {
    private static final String DEFAULT_CONFIG_RESOURCE = "/batteryguard.cfg";
    private static final String CATEGORY_GENERAL = "general";
    private static final String CATEGORY_PLAYERS = "players";

    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_WARNINGS = "warnings";
    private static final String KEY_WARNING_PERCENT = "warningPercent";
    private static final String KEY_CRITICAL_WARNING_PERCENT = "criticalWarningPercent";
    private static final String KEY_SHUTDOWN_PERCENT = "shutdownPercent";
    private static final String KEY_SHUTDOWN_COUNTDOWN_SECONDS = "shutdownCountdownSeconds";
    private static final String KEY_BATTERY_CHECK_INTERVAL_SECONDS = "batteryCheckIntervalSeconds";
    private static final String KEY_CANCEL_SHUTDOWN_WHEN_CHARGING = "cancelShutdownWhenCharging";
    private static final String KEY_PLAYER_UUIDS = "playerUuids";

    public static final boolean DEFAULT_ENABLED = true;
    public static final boolean DEFAULT_WARNINGS = true;
    public static final int DEFAULT_WARNING_PERCENT = 30;
    public static final int DEFAULT_CRITICAL_WARNING_PERCENT = 20;
    public static final int DEFAULT_SHUTDOWN_PERCENT = 10;
    public static final int DEFAULT_SHUTDOWN_COUNTDOWN_SECONDS = 30;
    public static final int DEFAULT_BATTERY_CHECK_INTERVAL_SECONDS = 5;
    public static final boolean DEFAULT_CANCEL_SHUTDOWN_WHEN_CHARGING = true;
    public static final String DEFAULT_LANGUAGE = "en";

    private final File file;
    private final Logger logger;
    private final String language;
    private final boolean enabled;
    private final boolean warnings;
    private final int warningPercent;
    private final int criticalWarningPercent;
    private final int shutdownPercent;
    private final int shutdownCountdownSeconds;
    private final int batteryCheckIntervalSeconds;
    private final boolean cancelShutdownWhenCharging;
    private final Set<UUID> playerUuids;

    private BatteryConfig(
        File file,
        Logger logger,
        String language,
        boolean enabled,
        boolean warnings,
        int warningPercent,
        int criticalWarningPercent,
        int shutdownPercent,
        int shutdownCountdownSeconds,
        int batteryCheckIntervalSeconds,
        boolean cancelShutdownWhenCharging,
        Set<UUID> playerUuids
    ) {
        this.file = file;
        this.logger = logger;
        this.language = language;
        this.enabled = enabled;
        this.warnings = warnings;
        this.warningPercent = warningPercent;
        this.criticalWarningPercent = criticalWarningPercent;
        this.shutdownPercent = shutdownPercent;
        this.shutdownCountdownSeconds = shutdownCountdownSeconds;
        this.batteryCheckIntervalSeconds = batteryCheckIntervalSeconds;
        this.cancelShutdownWhenCharging = cancelShutdownWhenCharging;
        this.playerUuids = new HashSet<UUID>(playerUuids);
    }

    public static BatteryConfig load(File file, Logger logger) {
        ensureDefaultConfig(file, logger);

        ConfigValues values;
        try {
            values = parse(file);
        } catch (ConfigFormatException ex) {
            logger.error(
                "[BatteryGuard] Invalid configuration file: {}. Replacing it with the bundled default configuration. Reason: {}",
                file,
                ex.getMessage()
            );
            copyBundledDefaultConfig(file, logger);
            try {
                values = parse(file);
            } catch (ConfigFormatException recoveryFailure) {
                throw new IllegalStateException(
                    "The bundled BatteryGuard configuration is invalid.",
                    recoveryFailure
                );
            } catch (IOException recoveryFailure) {
                throw new IllegalStateException(
                    "Unable to read the bundled BatteryGuard configuration.",
                    recoveryFailure
                );
            }
        } catch (IOException ex) {
            throw new IllegalStateException(
                "Unable to read the BatteryGuard configuration file: " + file,
                ex
            );
        }

        String language = normalizeLanguage(values.language);
        if (!BatteryMessages.isLanguageAvailable(language)) {
            logger.warn(
                "[BatteryGuard] Language '{}' is unavailable; falling back to English.",
                values.language
            );
            language = DEFAULT_LANGUAGE;
            values.language = DEFAULT_LANGUAGE;
            saveValues(file, values);
        }

        return new BatteryConfig(
            file,
            logger,
            language,
            values.enabled,
            values.warnings,
            values.warningPercent,
            values.criticalWarningPercent,
            values.shutdownPercent,
            values.shutdownCountdownSeconds,
            values.batteryCheckIntervalSeconds,
            values.cancelShutdownWhenCharging,
            values.playerUuids
        );
    }

    public synchronized void save() {
        ConfigValues values = new ConfigValues();
        values.language = language;
        values.enabled = enabled;
        values.warnings = warnings;
        values.warningPercent = warningPercent;
        values.criticalWarningPercent = criticalWarningPercent;
        values.shutdownPercent = shutdownPercent;
        values.shutdownCountdownSeconds = shutdownCountdownSeconds;
        values.batteryCheckIntervalSeconds = batteryCheckIntervalSeconds;
        values.cancelShutdownWhenCharging = cancelShutdownWhenCharging;
        values.playerUuids = new HashSet<UUID>(playerUuids);

        try {
            writeUsingBundledTemplate(file, values);
        } catch (IOException ex) {
            throw new IllegalStateException(
                "Unable to save the BatteryGuard configuration file: " + file,
                ex
            );
        }
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

    public String getLanguage() {
        return language;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean areWarningsEnabled() {
        return warnings;
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

    private static void ensureDefaultConfig(File file, Logger logger) {
        if (file.exists()) {
            return;
        }
        copyBundledDefaultConfig(file, logger);
    }

    private static void copyBundledDefaultConfig(File file, Logger logger) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IllegalStateException(
                "Unable to create the BatteryGuard configuration directory: " + parent
            );
        }

        try (InputStream input = BatteryConfig.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException(
                    "Bundled BatteryGuard default configuration resource is missing: "
                        + DEFAULT_CONFIG_RESOURCE
                );
            }

            replaceFileFromStream(input, file);
            logger.info("[BatteryGuard] Installed bundled default configuration: {}", file);
        } catch (IOException ex) {
            throw new IllegalStateException(
                "Unable to install the BatteryGuard configuration file: " + file,
                ex
            );
        }
    }

    private static ConfigValues parse(File file) throws IOException, ConfigFormatException {
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        ConfigValues values = new ConfigValues();
        Set<String> generalKeys = new HashSet<String>();
        boolean generalSectionClosed = false;
        boolean playersSectionClosed = false;
        boolean sawGeneralSection = false;
        boolean sawPlayersSection = false;
        boolean sawPlayerList = false;
        boolean readingPlayerList = false;
        boolean readingGeneral = false;
        boolean readingPlayers = false;

        for (int lineNumber = 0; lineNumber < lines.size(); lineNumber++) {
            String line = lines.get(lineNumber);
            if (lineNumber == 0 && line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }
            String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            if (readingPlayerList) {
                if (">".equals(trimmed)) {
                    readingPlayerList = false;
                    continue;
                }
                if (trimmed.startsWith("#")) {
                    continue;
                }
                try {
                    values.playerUuids.add(UUID.fromString(unquote(trimmed)));
                } catch (IllegalArgumentException ex) {
                    throw invalid(lineNumber + 1, "invalid player UUID '" + trimmed + "'");
                }
                continue;
            }

            if (readingGeneral) {
                if ("}".equals(trimmed)) {
                    readingGeneral = false;
                    generalSectionClosed = true;
                    continue;
                }
                if (trimmed.endsWith("{")) {
                    throw invalid(lineNumber + 1, "nested sections are not supported");
                }

                ParsedProperty property = parseProperty(trimmed, lineNumber + 1);
                if (!isKnownGeneralKey(property.name)) {
                    throw invalid(lineNumber + 1, "unknown general setting '" + property.name + "'");
                }
                if (!generalKeys.add(property.name)) {
                    throw invalid(lineNumber + 1, "duplicate general setting '" + property.name + "'");
                }
                readGeneralProperty(values, property, lineNumber + 1);
                continue;
            }

            if (readingPlayers) {
                if ("}".equals(trimmed)) {
                    readingPlayers = false;
                    playersSectionClosed = true;
                    continue;
                }
                if (("S:" + KEY_PLAYER_UUIDS + " <").equals(trimmed)) {
                    if (sawPlayerList) {
                        throw invalid(lineNumber + 1, "duplicate playerUuids list");
                    }
                    sawPlayerList = true;
                    readingPlayerList = true;
                    continue;
                }
                throw invalid(lineNumber + 1, "expected the playerUuids list or the end of the players section");
            }

            if ("general {".equals(trimmed)) {
                if (sawGeneralSection || generalSectionClosed) {
                    throw invalid(lineNumber + 1, "duplicate general section");
                }
                sawGeneralSection = true;
                readingGeneral = true;
                continue;
            }

            if ("players {".equals(trimmed)) {
                if (sawPlayersSection || playersSectionClosed) {
                    throw invalid(lineNumber + 1, "duplicate players section");
                }
                sawPlayersSection = true;
                readingPlayers = true;
                continue;
            }

            throw invalid(lineNumber + 1, "unexpected content '" + trimmed + "'");
        }

        if (readingPlayerList) {
            throw invalid(lines.size(), "unterminated playerUuids list");
        }
        if (readingGeneral || !generalSectionClosed || !sawGeneralSection) {
            throw invalid(lines.size(), "missing or unterminated general section");
        }
        if (readingPlayers || !playersSectionClosed || !sawPlayersSection) {
            throw invalid(lines.size(), "missing or unterminated players section");
        }
        if (!sawPlayerList) {
            throw invalid(lines.size(), "missing playerUuids list");
        }

        validateRequiredGeneralKeys(generalKeys);
        validateSemanticValues(values);
        return values;
    }

    private static void readGeneralProperty(ConfigValues values, ParsedProperty property, int lineNumber)
        throws ConfigFormatException {
        String value = property.value.trim();
        if (value.isEmpty()) {
            throw invalid(lineNumber, "setting '" + property.name + "' has no value");
        }

        if (KEY_LANGUAGE.equals(property.name)) {
            requireType(property, 'S', lineNumber);
            values.language = unquote(value);
            if (values.language.isEmpty()) {
                throw invalid(lineNumber, "language cannot be empty");
            }
        } else if (KEY_ENABLED.equals(property.name)) {
            requireType(property, 'B', lineNumber);
            values.enabled = parseBoolean(value, lineNumber, property.name);
        } else if (KEY_WARNINGS.equals(property.name)) {
            requireType(property, 'B', lineNumber);
            values.warnings = parseBoolean(value, lineNumber, property.name);
        } else if (KEY_WARNING_PERCENT.equals(property.name)) {
            requireType(property, 'I', lineNumber);
            values.warningPercent = parseInteger(value, lineNumber, property.name);
        } else if (KEY_CRITICAL_WARNING_PERCENT.equals(property.name)) {
            requireType(property, 'I', lineNumber);
            values.criticalWarningPercent = parseInteger(value, lineNumber, property.name);
        } else if (KEY_SHUTDOWN_PERCENT.equals(property.name)) {
            requireType(property, 'I', lineNumber);
            values.shutdownPercent = parseInteger(value, lineNumber, property.name);
        } else if (KEY_SHUTDOWN_COUNTDOWN_SECONDS.equals(property.name)) {
            requireType(property, 'I', lineNumber);
            values.shutdownCountdownSeconds = parseInteger(value, lineNumber, property.name);
        } else if (KEY_BATTERY_CHECK_INTERVAL_SECONDS.equals(property.name)) {
            requireType(property, 'I', lineNumber);
            values.batteryCheckIntervalSeconds = parseInteger(value, lineNumber, property.name);
        } else if (KEY_CANCEL_SHUTDOWN_WHEN_CHARGING.equals(property.name)) {
            requireType(property, 'B', lineNumber);
            values.cancelShutdownWhenCharging = parseBoolean(value, lineNumber, property.name);
        }
    }

    private static void requireType(ParsedProperty property, char expectedType, int lineNumber)
        throws ConfigFormatException {
        if (property.type != expectedType) {
            throw invalid(
                lineNumber,
                "setting '" + property.name + "' uses type '" + property.type + "' but requires '" + expectedType + "'"
            );
        }
    }

    private static boolean parseBoolean(String value, int lineNumber, String name) throws ConfigFormatException {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw invalid(lineNumber, "setting '" + name + "' must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    private static int parseInteger(String value, int lineNumber, String name) throws ConfigFormatException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw invalid(lineNumber, "setting '" + name + "' must be an integer");
        }
    }

    private static ParsedProperty parseProperty(String line, int lineNumber) throws ConfigFormatException {
        if (line.length() < 3 || line.charAt(1) != ':') {
            throw invalid(lineNumber, "invalid setting syntax");
        }

        char type = line.charAt(0);
        int equals = line.indexOf('=', 2);
        if (equals < 3) {
            throw invalid(lineNumber, "invalid setting syntax");
        }

        String name = line.substring(2, equals).trim();
        if (name.isEmpty()) {
            throw invalid(lineNumber, "setting name cannot be empty");
        }

        return new ParsedProperty(type, name, line.substring(equals + 1));
    }

    private static boolean isKnownGeneralKey(String name) {
        return KEY_LANGUAGE.equals(name)
            || KEY_ENABLED.equals(name)
            || KEY_WARNINGS.equals(name)
            || KEY_WARNING_PERCENT.equals(name)
            || KEY_CRITICAL_WARNING_PERCENT.equals(name)
            || KEY_SHUTDOWN_PERCENT.equals(name)
            || KEY_SHUTDOWN_COUNTDOWN_SECONDS.equals(name)
            || KEY_BATTERY_CHECK_INTERVAL_SECONDS.equals(name)
            || KEY_CANCEL_SHUTDOWN_WHEN_CHARGING.equals(name);
    }

    private static void validateRequiredGeneralKeys(Set<String> keys) throws ConfigFormatException {
        String[] required = {
            KEY_LANGUAGE,
            KEY_ENABLED,
            KEY_WARNINGS,
            KEY_WARNING_PERCENT,
            KEY_CRITICAL_WARNING_PERCENT,
            KEY_SHUTDOWN_PERCENT,
            KEY_SHUTDOWN_COUNTDOWN_SECONDS,
            KEY_BATTERY_CHECK_INTERVAL_SECONDS,
            KEY_CANCEL_SHUTDOWN_WHEN_CHARGING
        };
        for (String key : required) {
            if (!keys.contains(key)) {
                throw new ConfigFormatException("missing general setting '" + key + "'");
            }
        }
    }

    private static void validateSemanticValues(ConfigValues values) throws ConfigFormatException {
        if (values.warningPercent < 0 || values.warningPercent > 100) {
            throw new ConfigFormatException("warningPercent must be between 0 and 100");
        }
        if (values.criticalWarningPercent < 0 || values.criticalWarningPercent > 100) {
            throw new ConfigFormatException("criticalWarningPercent must be between 0 and 100");
        }
        if (values.shutdownPercent < 0 || values.shutdownPercent > 100) {
            throw new ConfigFormatException("shutdownPercent must be between 0 and 100");
        }
        if (values.shutdownCountdownSeconds < 0) {
            throw new ConfigFormatException("shutdownCountdownSeconds cannot be negative");
        }
        if (values.batteryCheckIntervalSeconds < 1) {
            throw new ConfigFormatException("batteryCheckIntervalSeconds must be at least 1");
        }
        if (values.criticalWarningPercent > values.warningPercent) {
            throw new ConfigFormatException("criticalWarningPercent cannot be above warningPercent");
        }
        if (values.shutdownPercent > values.criticalWarningPercent) {
            throw new ConfigFormatException("shutdownPercent cannot be above criticalWarningPercent");
        }
        if (!values.playerUuids.isEmpty()) {
            for (UUID uuid : values.playerUuids) {
                if (uuid == null) {
                    throw new ConfigFormatException("playerUuids contains an empty value");
                }
            }
        }
    }

    private static void saveValues(File file, ConfigValues values) {
        try {
            writeUsingBundledTemplate(file, values);
        } catch (IOException ex) {
            throw new IllegalStateException(
                "Unable to save the BatteryGuard configuration file: " + file,
                ex
            );
        }
    }

    private static void writeUsingBundledTemplate(File file, ConfigValues values) throws IOException {
        byte[] templateBytes;
        try (InputStream input = BatteryConfig.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
            if (input == null) {
                throw new IOException("Bundled BatteryGuard default configuration resource is missing.");
            }
            templateBytes = readAllBytes(input);
        }

        String template = new String(templateBytes, StandardCharsets.UTF_8);
        List<String> templateLines = splitLines(template);
        List<String> output = new ArrayList<String>();
        boolean inGeneral = false;
        boolean inPlayers = false;
        boolean inPlayerList = false;

        for (String originalLine : templateLines) {
            String trimmed = originalLine.trim();

            if ("general {".equals(trimmed)) {
                inGeneral = true;
            } else if ("players {".equals(trimmed)) {
                inPlayers = true;
            }

            if (inPlayerList) {
                if (">".equals(trimmed)) {
                    inPlayerList = false;
                    output.add(originalLine);
                }
                continue;
            }

            if (inGeneral && trimmed.startsWith("S:" + KEY_LANGUAGE + "=")) {
                output.add(prefix(originalLine) + "S:" + KEY_LANGUAGE + "=" + values.language);
                continue;
            }
            if (inGeneral && trimmed.startsWith("B:" + KEY_ENABLED + "=")) {
                output.add(prefix(originalLine) + "B:" + KEY_ENABLED + "=" + Boolean.toString(values.enabled));
                continue;
            }
            if (inGeneral && trimmed.startsWith("B:" + KEY_WARNINGS + "=")) {
                output.add(prefix(originalLine) + "B:" + KEY_WARNINGS + "=" + Boolean.toString(values.warnings));
                continue;
            }
            if (inGeneral && trimmed.startsWith("I:" + KEY_WARNING_PERCENT + "=")) {
                output.add(prefix(originalLine) + "I:" + KEY_WARNING_PERCENT + "=" + values.warningPercent);
                continue;
            }
            if (inGeneral && trimmed.startsWith("I:" + KEY_CRITICAL_WARNING_PERCENT + "=")) {
                output.add(prefix(originalLine) + "I:" + KEY_CRITICAL_WARNING_PERCENT + "=" + values.criticalWarningPercent);
                continue;
            }
            if (inGeneral && trimmed.startsWith("I:" + KEY_SHUTDOWN_PERCENT + "=")) {
                output.add(prefix(originalLine) + "I:" + KEY_SHUTDOWN_PERCENT + "=" + values.shutdownPercent);
                continue;
            }
            if (inGeneral && trimmed.startsWith("I:" + KEY_SHUTDOWN_COUNTDOWN_SECONDS + "=")) {
                output.add(prefix(originalLine) + "I:" + KEY_SHUTDOWN_COUNTDOWN_SECONDS + "=" + values.shutdownCountdownSeconds);
                continue;
            }
            if (inGeneral && trimmed.startsWith("I:" + KEY_BATTERY_CHECK_INTERVAL_SECONDS + "=")) {
                output.add(prefix(originalLine) + "I:" + KEY_BATTERY_CHECK_INTERVAL_SECONDS + "=" + values.batteryCheckIntervalSeconds);
                continue;
            }
            if (inGeneral && trimmed.startsWith("B:" + KEY_CANCEL_SHUTDOWN_WHEN_CHARGING + "=")) {
                output.add(prefix(originalLine) + "B:" + KEY_CANCEL_SHUTDOWN_WHEN_CHARGING + "=" + Boolean.toString(values.cancelShutdownWhenCharging));
                continue;
            }

            if (inPlayers && ("S:" + KEY_PLAYER_UUIDS + " <").equals(trimmed)) {
                output.add(originalLine);
                for (UUID uuid : sortedUuids(values.playerUuids)) {
                    output.add(prefix(originalLine) + uuid.toString());
                }
                inPlayerList = true;
                continue;
            }

            output.add(originalLine);

            if (inGeneral && "}".equals(trimmed)) {
                inGeneral = false;
            } else if (inPlayers && "}".equals(trimmed)) {
                inPlayers = false;
            }
        }

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IOException("Unable to create the configuration directory: " + parent);
        }

        byte[] bytes = joinLines(output).getBytes(StandardCharsets.UTF_8);
        writeAtomically(file, bytes);
    }

    private static void replaceFileFromStream(InputStream input, File file) throws IOException {
        File temp = temporaryFile(file);
        boolean moved = false;
        try {
            Files.copy(input, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            moveReplacing(temp, file);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp.toPath());
            }
        }
    }

    private static void writeAtomically(File file, byte[] bytes) throws IOException {
        File temp = temporaryFile(file);
        boolean moved = false;
        try {
            Files.write(temp.toPath(), bytes);
            moveReplacing(temp, file);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp.toPath());
            }
        }
    }

    private static void moveReplacing(File source, File target) throws IOException {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException ex) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private static File temporaryFile(File file) {
        File parent = file.getParentFile();
        String name = file.getName() + ".tmp";
        return parent == null ? new File(name) : new File(parent, name);
    }

    private static List<UUID> sortedUuids(Set<UUID> uuids) {
        List<UUID> sorted = new ArrayList<UUID>(uuids);
        Collections.sort(sorted, (left, right) -> left.toString().compareTo(right.toString()));
        return sorted;
    }

    private static String prefix(String line) {
        int first = 0;
        while (first < line.length() && Character.isWhitespace(line.charAt(first))) {
            first++;
        }
        return line.substring(0, first);
    }

    private static List<String> splitLines(String text) {
        String[] lines = text.split("\\r?\\n", -1);
        List<String> result = new ArrayList<String>(lines.length);
        Collections.addAll(result, lines);
        return result;
    }

    private static String joinLines(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            builder.append(lines.get(i));
            if (i + 1 < lines.size()) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String normalizeLanguage(String languageCode) {
        String normalized = languageCode == null
            ? ""
            : languageCode.trim().toLowerCase(Locale.ENGLISH);
        return normalized.matches("[a-z0-9_-]+") ? normalized : "";
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        byte[] buffer = new byte[4096];
        int read;
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static ConfigFormatException invalid(int lineNumber, String reason) {
        return new ConfigFormatException("line " + lineNumber + ": " + reason);
    }

    private static final class ParsedProperty {
        private final char type;
        private final String name;
        private final String value;

        private ParsedProperty(char type, String name, String value) {
            this.type = type;
            this.name = name;
            this.value = value;
        }
    }

    private static final class ConfigValues {
        private String language;
        private boolean enabled;
        private boolean warnings;
        private int warningPercent;
        private int criticalWarningPercent;
        private int shutdownPercent;
        private int shutdownCountdownSeconds;
        private int batteryCheckIntervalSeconds;
        private boolean cancelShutdownWhenCharging;
        private Set<UUID> playerUuids = new HashSet<UUID>();
    }

    private static final class ConfigFormatException extends Exception {
        private ConfigFormatException(String message) {
            super(message);
        }
    }
}
