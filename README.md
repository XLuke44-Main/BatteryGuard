# Battery Guard

Battery Guard watches the battery of the laptop running the minecraft server it's on.

When the battery gets low, it warns everyone on the server. 
By default, at 10% it starts a 30-second countdown and then safely stops it.

It is fully server-side. You do not need to install Battery Guard on the client for it to work.

## Commands

/bat - Show the battery level.
/batteryguard status - Show battery status.
/batteryguard check - Check the battery now.
/batteryguard add <player> - Allow a player to use the read-only commands.
/batteryguard remove <player> - Remove a player from the allowed list.
/batteryguard list - Show allowed players.
/batteryguard reload - Reload the configuration.
/batteryguard cancel - Cancel a shutdown countdown.
/batteryguard shutdown - Start a server shutdown countdown.

## Language

By default, the mod uses language=en and responds in english. To use polish instead, change it to language=pl in config.

## Compatibility

Battery Guard was made and tested on my own windows 11 laptop. It may not work on every windows 11 build or on windows 10 or below.
