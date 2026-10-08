# BatteryGuard

Monitors the host's battery and stops the server before power runs out

When the battery gets low, it warns everyone on the server.
By default, at 10% it starts 30-second countdown and then safely stops it.

It is fully server-side. There is no need to install batteryguard on the client for it to work.

## Commands

/batteryguard - Show the battery level.

/batteryguard status - Show battery status.

/batteryguard check - Check the battery now.

/batteryguard add <player> - Allow player to use the read-only commands.

/batteryguard remove <player> - Remove player from the allowed list.

/batteryguard list - Show allowed players.

/batteryguard reload - Reload the configuration.

/batteryguard cancel - Cancel shutdown countdown.

/batteryguard shutdown - Start server shutdown countdown.

# Configuration

The language can be changed in the config.

For example, language=pl sets the language to Polish.

warnings=false turns off batteryGuard's automatic warnings.

## Language

Supported languages:

English = en [default]

Polish = pl

## Compatibility

BatteryGuard was made and tested on my own windows 11 laptop. It may not work on every windows 11 build or on windows 10 or below.
