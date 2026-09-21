package com.batteryguard;

import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public final class BatteryMessages {
    public enum CancellationReason {
        AC_POWER_RESTORED,
        BATTERY_RECOVERED,
        NO_BATTERY,
        BATTERY_INFORMATION_UNAVAILABLE,
        CONFIGURATION_DISABLED
    }

    private BatteryMessages() {
    }

    public static String usage(boolean admin) {
        return admin
            ? "/batteryguard <add|remove|list|status|reload|check|cancel|shutdown> ..."
            : "/batteryguard <list|status|check>";
    }

    public static String permissionDenied(BatteryConfig.Language language) {
        return language == BatteryConfig.Language.PL
            ? "Nie masz uprawnień do użycia tej komendy Battery Guarda."
            : "You do not have permission to use this Battery Guard command.";
    }

    public static String monitoringInactive(BatteryConfig.Language language) {
        return language == BatteryConfig.Language.PL
            ? "Monitorowanie Battery Guard nie jest aktywne."
            : "Battery Guard monitoring is not active.";
    }

    public static String checkScheduled(BatteryConfig.Language language) {
        return language == BatteryConfig.Language.PL
            ? "Battery Guard: zlecono natychmiastowe sprawdzenie baterii systemu Windows."
            : "Battery Guard: immediate Windows battery check scheduled.";
    }

    public static String noShutdownActive(BatteryConfig.Language language) {
        return language == BatteryConfig.Language.PL
            ? "Battery Guard: nie ma aktywnego odliczania do awaryjnego wyłączenia serwera."
            : "Battery Guard: no emergency shutdown countdown is active.";
    }

    public static String shutdownCancelRequested(BatteryConfig.Language language) {
        return language == BatteryConfig.Language.PL
            ? "Battery Guard: zlecono anulowanie awaryjnego wyłączenia serwera."
            : "Battery Guard: emergency shutdown cancellation requested.";
    }

    public static String shutdownAlreadyActive(BatteryConfig.Language language) {
        return language == BatteryConfig.Language.PL
            ? "Odliczanie do awaryjnego wyłączenia serwera jest już aktywne."
            : "An emergency shutdown countdown is already active.";
    }

    public static String shutdownInitiated(BatteryConfig.Language language) {
        return language == BatteryConfig.Language.PL
            ? "Battery Guard: rozpoczęto wyłączenie serwera."
            : "Battery Guard: server shutdown initiated.";
    }

    public static String reloadComplete(BatteryConfig.Language language) {
        return language == BatteryConfig.Language.PL
            ? "Konfiguracja Battery Guard została przeładowana."
            : "Battery Guard configuration reloaded.";
    }

    public static String playerProfileMissing(BatteryConfig.Language language, String username) {
        return language == BatteryConfig.Language.PL
            ? "Nie można znaleźć profilu gracza: " + username
            : "Player profile could not be resolved: " + username;
    }

    public static String configuredPlayerMissing(BatteryConfig.Language language, String username) {
        return language == BatteryConfig.Language.PL
            ? "Nie można znaleźć skonfigurowanego gracza: " + username
            : "Configured player could not be resolved: " + username;
    }

    public static String playerAdded(BatteryConfig.Language language, String name, String uuid) {
        return language == BatteryConfig.Language.PL
            ? "Battery Guard: dodano gracza " + name + " (" + uuid + ")."
            : "Battery Guard: added " + name + " (" + uuid + ").";
    }

    public static String playerAlreadyAdded(BatteryConfig.Language language, String name) {
        return language == BatteryConfig.Language.PL
            ? "Battery Guard: gracz " + name + " jest już na liście dozwolonych."
            : "Battery Guard: " + name + " is already permitted.";
    }

    public static String playerRemoved(BatteryConfig.Language language, String name, String uuid) {
        return language == BatteryConfig.Language.PL
            ? "Battery Guard: usunięto gracza " + name + " (" + uuid + ")."
            : "Battery Guard: removed " + name + " (" + uuid + ").";
    }

    public static String playerNotAdded(BatteryConfig.Language language, String name) {
        return language == BatteryConfig.Language.PL
            ? "Battery Guard: gracza " + name + " nie ma na liście dozwolonych."
            : "Battery Guard: " + name + " is not permitted.";
    }

    public static String permittedPlayers(BatteryConfig.Language language, int count) {
        return language == BatteryConfig.Language.PL
            ? "Dozwoleni gracze Battery Guard: " + count
            : "Battery Guard permitted players: " + count;
    }

    public static String listEntry(BatteryConfig.Language language, String display) {
        return " - " + display;
    }

    public static TextComponentString batteryMessage(
        BatterySnapshot snapshot,
        BatteryConfig.Language language
    ) {
        if (!snapshot.isBatteryInfoValid()) {
            if (snapshot.getPowerSource() == BatterySnapshot.PowerSource.NO_BATTERY) {
                return colored(
                    language == BatteryConfig.Language.PL
                        ? "Bateria: N/D (nie wykryto baterii)"
                        : "Battery: N/A (no battery detected)",
                    TextFormatting.GRAY
                );
            }

            return colored(
                language == BatteryConfig.Language.PL
                    ? "Bateria: niedostępna"
                    : "Battery: unavailable",
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
            (language == BatteryConfig.Language.PL ? "Bateria: " : "Battery: ")
                + snapshot.getPercent() + "%",
            color
        );
    }

    public static String batteryLine(
        BatterySnapshot snapshot,
        BatteryConfig.Language language
    ) {
        if (!snapshot.isBatteryInfoValid()) {
            if (snapshot.getPowerSource() == BatterySnapshot.PowerSource.NO_BATTERY) {
                return language == BatteryConfig.Language.PL
                    ? "Bateria: N/D (nie wykryto baterii)"
                    : "Battery: N/A (no battery detected)";
            }
            return language == BatteryConfig.Language.PL
                ? "Bateria: niedostępna"
                : "Battery: unavailable";
        }
        return (language == BatteryConfig.Language.PL ? "Bateria: " : "Battery: ")
            + snapshot.getPercent() + "%";
    }

    public static TextComponentString snapshotMessage(
        BatterySnapshot snapshot,
        BatteryConfig.Language language
    ) {
        TextComponentString result = new TextComponentString("");
        result.appendSibling(batteryMessage(snapshot, language));
        result.appendText(
            language == BatteryConfig.Language.PL
                ? " | Źródło zasilania: " + powerSourceText(snapshot, language)
                : " | Power source: " + powerSourceText(snapshot, language)
        );
        return result;
    }

    public static String powerSourceText(
        BatterySnapshot snapshot,
        BatteryConfig.Language language
    ) {
        if (language == BatteryConfig.Language.PL) {
            switch (snapshot.getPowerSource()) {
                case AC:
                    return "SIEĆ";
                case BATTERY:
                    return "BATERIA";
                case NO_BATTERY:
                    return "BRAK BATERII";
                default:
                    return "NIEZNANE";
            }
        }

        switch (snapshot.getPowerSource()) {
            case AC:
                return "AC";
            case BATTERY:
                return "BATTERY";
            case NO_BATTERY:
                return "NO_BATTERY";
            default:
                return "UNKNOWN";
        }
    }

    public static TextComponentString warning(int percent, BatteryConfig.Language language) {
        return colored(
            language == BatteryConfig.Language.PL
                ? "⚠ Bateria komputera hosta ma " + percent
                    + "%. Podłącz komputer hosta do zasilania sieciowego."
                : "⚠ Host computer battery is at " + percent
                    + "%. Please connect the host computer to AC power.",
            TextFormatting.YELLOW
        );
    }

    public static TextComponentString criticalWarning(
        int percent,
        int shutdownPercent,
        BatteryConfig.Language language
    ) {
        return colored(
            language == BatteryConfig.Language.PL
                ? "⚠ KRYTYCZNE: bateria komputera hosta ma " + percent
                    + "%. Serwer zostanie automatycznie wyłączony, jeśli bateria spadnie do "
                    + shutdownPercent + "%."
                : "⚠ CRITICAL: Host computer battery is at " + percent
                    + "%. The server will automatically shut down if the battery reaches "
                    + shutdownPercent + "%.",
            TextFormatting.RED
        );
    }

    public static TextComponentString emergencyShutdown(
        int batteryPercent,
        int seconds,
        BatteryConfig.Language language
    ) {
        return colored(
            (language == BatteryConfig.Language.PL
                ? "⚠ AWARYJNE WYŁĄCZENIE SERWERA\n"
                    + "Bateria komputera hosta ma krytycznie niski poziom: " + batteryPercent + "%.\n"
                    + "Serwer zostanie wyłączony za " + seconds + " s.\n"
                    + "Natychmiast podłącz komputer hosta do zasilania sieciowego."
                : "⚠ EMERGENCY SERVER SHUTDOWN\n"
                    + "The host computer battery is critically low at " + batteryPercent + "%.\n"
                    + "The server will shut down in " + seconds + " seconds.\n"
                    + "Connect the host computer to AC power immediately."),
            TextFormatting.RED
        );
    }

    public static TextComponentString manualShutdown(
        int seconds,
        BatteryConfig.Language language
    ) {
        return colored(
            language == BatteryConfig.Language.PL
                ? "⚠ AWARYJNE WYŁĄCZENIE SERWERA: administrator rozpoczął wyłączenie serwera. Serwer zostanie wyłączony za "
                    + seconds + " s."
                : "⚠ EMERGENCY SERVER SHUTDOWN: An administrator initiated a graceful server shutdown. The server will shut down in "
                    + seconds + " seconds.",
            TextFormatting.RED
        );
    }

    public static TextComponentString countdown(long seconds, BatteryConfig.Language language) {
        if (language == BatteryConfig.Language.PL) {
            return colored(
                "⚠ Wyłączenie serwera za " + seconds + " " + polishSeconds(seconds) + ".",
                TextFormatting.RED
            );
        }

        return colored(
            "⚠ server shutdown in " + seconds
                + " second" + (seconds == 1 ? "" : "s") + ".",
            TextFormatting.RED
        );
    }

    public static TextComponentString cancellation(
        CancellationReason reason,
        BatteryConfig.Language language
    ) {
        String text;
        if (language == BatteryConfig.Language.PL) {
            switch (reason) {
                case AC_POWER_RESTORED:
                    text = "✓ Przywrócono zasilanie sieciowe. Awaryjne wyłączenie serwera zostało anulowane.";
                    break;
                case BATTERY_RECOVERED:
                    text = "✓ Poziom baterii wzrósł powyżej progu wyłączenia. Awaryjne wyłączenie serwera zostało anulowane.";
                    break;
                case NO_BATTERY:
                    text = "✓ Nie wykryto baterii systemowej. Automatyczne wyłączenie serwera zostało anulowane.";
                    break;
                case BATTERY_INFORMATION_UNAVAILABLE:
                    text = "✓ Informacje o baterii stały się niedostępne. Automatyczne wyłączenie serwera zostało anulowane dla bezpieczeństwa.";
                    break;
                case CONFIGURATION_DISABLED:
                    text = "✓ Automatyczna ochrona baterii została wyłączona w konfiguracji.";
                    break;
                default:
                    text = "✓ Awaryjne wyłączenie serwera zostało anulowane.";
                    break;
            }
        } else {
            switch (reason) {
                case AC_POWER_RESTORED:
                    text = "✓ AC power restored. Emergency server shutdown cancelled.";
                    break;
                case BATTERY_RECOVERED:
                    text = "✓ Battery rose above the shutdown threshold. Emergency server shutdown cancelled.";
                    break;
                case NO_BATTERY:
                    text = "✓ No system battery detected. Automatic server shutdown cancelled.";
                    break;
                case BATTERY_INFORMATION_UNAVAILABLE:
                    text = "✓ Battery information became unavailable. Automatic server shutdown cancelled for safety.";
                    break;
                case CONFIGURATION_DISABLED:
                    text = "✓ Automatic battery protection disabled by configuration.";
                    break;
                default:
                    text = "✓ Emergency server shutdown cancelled.";
                    break;
            }
        }
        return colored(text, TextFormatting.GREEN);
    }

    public static TextComponentString administratorCancellation(BatteryConfig.Language language) {
        return colored(
            language == BatteryConfig.Language.PL
                ? "✓ Administrator anulował awaryjne wyłączenie serwera."
                : "✓ Emergency server shutdown cancelled by administrator.",
            TextFormatting.YELLOW
        );
    }

    private static TextComponentString colored(String message, TextFormatting color) {
        TextComponentString result = new TextComponentString(message);
        result.setStyle(new Style().setColor(color));
        return result;
    }

    private static String polishSeconds(long seconds) {
        if (seconds == 1) {
            return "sekundę";
        }
        if (seconds % 10 >= 2 && seconds % 10 <= 4
            && (seconds % 100 < 10 || seconds % 100 >= 20)) {
            return "sekundy";
        }
        return "sekund";
    }
}
