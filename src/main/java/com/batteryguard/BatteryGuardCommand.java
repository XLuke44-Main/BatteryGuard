package com.batteryguard;

import com.mojang.authlib.GameProfile;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerProfileCache;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Locale;
import java.util.UUID;

public final class BatteryGuardCommand extends CommandBase {
    private final BatteryGuard mod;

    public BatteryGuardCommand(BatteryGuard mod) {
        this.mod = mod;
    }

    @Override
    public String getName() {
        return "batteryguard";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return BatteryMessages.usage(sender != null && sender.canUseCommand(2, getName()), mod.getConfig().getLanguage());
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        if (sender.canUseCommand(2, getName())) {
            return true;
        }

        return sender instanceof EntityPlayerMP
            && mod.getConfig().isPlayerPermitted(
                ((EntityPlayerMP) sender).getGameProfile().getId());
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        throws CommandException {

        requireReadAccess(sender);

        if (args.length == 0) {
            sendBatteryReading(sender);
            return;
        }

        String action = args[0].toLowerCase(Locale.ENGLISH);

        if ("status".equals(action)) {
            requireArgCount(args, 1);
            sendStatus(sender, server);
            return;
        }

        if ("list".equals(action)) {
            requireAdmin(sender);
            requireArgCount(args, 1);
            listPlayers(server, sender);
            return;
        }

        if ("check".equals(action)) {
            requireArgCount(args, 1);
            BatteryMonitor monitor = mod.getMonitor();
            if (monitor == null) {
                sender.sendMessage(new TextComponentString(
                    BatteryMessages.monitoringInactive(mod.getConfig().getLanguage())
                ));
                return;
            }
            monitor.requestImmediateCheck(sender);
            sender.sendMessage(new TextComponentString(
                BatteryMessages.checkScheduled(mod.getConfig().getLanguage())
            ));
            return;
        }

        requireAdmin(sender);

        if ("add".equals(action)) {
            requireArgCount(args, 2);
            addPlayer(server, sender, args[1]);
            return;
        }

        if ("remove".equals(action)) {
            requireArgCount(args, 2);
            removePlayer(server, sender, args[1]);
            return;
        }

        if ("reload".equals(action)) {
            requireArgCount(args, 1);
            mod.reloadConfig();
            sender.sendMessage(new TextComponentString(
                BatteryMessages.reloadComplete(mod.getConfig().getLanguage())
            ));
            return;
        }

        if ("cancel".equals(action)) {
            requireArgCount(args, 1);
            BatteryMonitor monitor = mod.getMonitor();
            if (monitor == null || !monitor.isEmergencyShutdownActive()) {
                sender.sendMessage(new TextComponentString(
                    BatteryMessages.noShutdownActive(mod.getConfig().getLanguage())
                ));
                return;
            }
            monitor.cancelShutdown();
            sender.sendMessage(new TextComponentString(
                BatteryMessages.shutdownCancelRequested(mod.getConfig().getLanguage())
            ));
            return;
        }

        if ("shutdown".equals(action)) {
            requireArgCount(args, 1);
            BatteryMonitor monitor = mod.getMonitor();
            if (monitor == null) {
                throw new CommandException(
                    BatteryMessages.monitoringInactive(mod.getConfig().getLanguage())
                );
            }
            if (monitor.isEmergencyShutdownActive()) {
                throw new CommandException(
                    BatteryMessages.shutdownAlreadyActive(mod.getConfig().getLanguage())
                );
            }
            monitor.manualShutdown(sender);
            sender.sendMessage(new TextComponentString(
                BatteryMessages.shutdownInitiated(mod.getConfig().getLanguage())
            ));
            return;
        }

        throw new WrongUsageException(BatteryMessages.usage(true, mod.getConfig().getLanguage()));
    }

    private void requireReadAccess(ICommandSender sender) throws CommandException {
        if (sender.canUseCommand(2, getName())) {
            return;
        }

        if (sender instanceof EntityPlayerMP
            && mod.getConfig().isPlayerPermitted(
                ((EntityPlayerMP) sender).getGameProfile().getId())) {
            return;
        }

        throw new CommandException(
            BatteryMessages.permissionDenied(mod.getConfig().getLanguage())
        );
    }

    private void requireAdmin(ICommandSender sender) throws CommandException {
        if (!sender.canUseCommand(2, getName())) {
            throw new CommandException(
                BatteryMessages.permissionDenied(mod.getConfig().getLanguage())
            );
        }
    }

    private void requireArgCount(String[] args, int count) throws CommandException {
        if (args.length != count) {
            throw new WrongUsageException(BatteryMessages.usage(true, mod.getConfig().getLanguage()));
        }
    }

    private void addPlayer(MinecraftServer server, ICommandSender sender, String username)
        throws CommandException {

        GameProfile profile = resolveProfile(server, username);
        if (profile == null || profile.getId() == null) {
            throw new CommandException(
                BatteryMessages.playerProfileMissing(mod.getConfig().getLanguage(), username)
            );
        }

        if (mod.getConfig().addPlayer(profile.getId())) {
            mod.getConfig().save();
            sender.sendMessage(new TextComponentString(
                BatteryMessages.playerAdded(
                    mod.getConfig().getLanguage(), displayName(profile, username), profile.getId().toString()
                )
            ));
        } else {
            sender.sendMessage(new TextComponentString(
                BatteryMessages.playerAlreadyAdded(mod.getConfig().getLanguage(), displayName(profile, username))
            ));
        }
    }

    private void removePlayer(MinecraftServer server, ICommandSender sender, String username)
        throws CommandException {

        UUID uuid = findPlayerUuid(server, username);
        if (uuid == null) {
            throw new CommandException(
                BatteryMessages.configuredPlayerMissing(mod.getConfig().getLanguage(), username)
            );
        }

        if (mod.getConfig().removePlayer(uuid)) {
            mod.getConfig().save();
            sender.sendMessage(new TextComponentString(
                BatteryMessages.playerRemoved(
                    mod.getConfig().getLanguage(), username, uuid.toString()
                )
            ));
        } else {
            sender.sendMessage(new TextComponentString(
                BatteryMessages.playerNotAdded(mod.getConfig().getLanguage(), username)
            ));
        }
    }

    private void sendBatteryReading(ICommandSender sender) {
        BatteryMonitor monitor = mod.getMonitor();
        String language = mod.getConfig().getLanguage();

        if (monitor == null) {
            sender.sendMessage(new TextComponentString(
                BatteryMessages.monitoringInactive(language)
            ));
            return;
        }

        sender.sendMessage(
            BatteryMessages.batteryMessage(
                monitor.getLatestSnapshot(),
                language
            )
        );
    }

    private String displayName(GameProfile profile, String fallback) {
        String name = profile == null ? null : profile.getName();
        return name == null || name.isEmpty() ? fallback : name;
    }

    private GameProfile resolveProfile(MinecraftServer server, String username) {
        EntityPlayerMP online = server.getPlayerList().getPlayerByUsername(username);
        if (online != null) {
            return online.getGameProfile();
        }

        PlayerProfileCache cache = server.getPlayerProfileCache();
        return cache == null ? null : cache.getGameProfileForUsername(username);
    }

    private UUID findPlayerUuid(MinecraftServer server, String username) {
        GameProfile profile = resolveProfile(server, username);
        return profile == null ? null : profile.getId();
    }

    private void listPlayers(MinecraftServer server, ICommandSender sender) {
        String language = mod.getConfig().getLanguage();
        Set<UUID> uuids = mod.getConfig().getPlayerUuids();

        sender.sendMessage(new TextComponentString(
            BatteryMessages.permittedPlayers(language, uuids.size())
        ));

        PlayerProfileCache cache = server.getPlayerProfileCache();
        List<UUID> sortedUuids = new ArrayList<UUID>(uuids);
        java.util.Collections.sort(sortedUuids, new java.util.Comparator<UUID>() {
            @Override
            public int compare(UUID left, UUID right) {
                return left.toString().compareTo(right.toString());
            }
        });

        for (UUID uuid : sortedUuids) {
            String display = uuid.toString();

            if (cache != null) {
                GameProfile profile = cache.getProfileByUUID(uuid);
                if (profile != null && profile.getName() != null) {
                    display = profile.getName() + " (" + uuid + ")";
                }
            }

            sender.sendMessage(new TextComponentString(
                BatteryMessages.listEntry(language, display)
            ));
        }
    }

    private void sendStatus(ICommandSender sender, MinecraftServer server) {
        String language = mod.getConfig().getLanguage();
        BatteryMonitor monitor = mod.getMonitor();
        BatterySnapshot snapshot = monitor == null
            ? BatterySnapshot.unavailable("Monitoring is not active.")
            : monitor.getLatestSnapshot();

        sender.sendMessage(new TextComponentString(
            BatteryMessages.batteryLine(snapshot, language)
        ));
        sender.sendMessage(new TextComponentString(
            BatteryMessages.statusPowerSource(language)
                + BatteryMessages.powerSourceText(snapshot, language)
        ));
        sender.sendMessage(new TextComponentString(
            BatteryMessages.statusBatteryInformation(language)
                + (snapshot.isBatteryInfoValid()
                    ? BatteryMessages.statusAvailable(language)
                    : BatteryMessages.statusUnavailable(language))
        ));
        sender.sendMessage(new TextComponentString(
            BatteryMessages.statusWarningThreshold(language)
                + mod.getConfig().getWarningPercent() + "%"
        ));
        sender.sendMessage(new TextComponentString(
            BatteryMessages.statusCriticalThreshold(language)
                + mod.getConfig().getCriticalWarningPercent() + "%"
        ));
        sender.sendMessage(new TextComponentString(
            BatteryMessages.statusShutdownThreshold(language)
                + mod.getConfig().getShutdownPercent() + "%"
        ));
        sender.sendMessage(new TextComponentString(
            BatteryMessages.statusEmergencyShutdown(language)
                + (monitor != null && monitor.isEmergencyShutdownActive()
                    ? BatteryMessages.statusActive(language)
                    : BatteryMessages.statusInactive(language))
        ));
    }

    @Override
    public List<String> getTabCompletions(
        MinecraftServer server,
        ICommandSender sender,
        String[] args,
        net.minecraft.util.math.BlockPos targetPos
    ) {
        if (args.length == 1) {
            List<String> options = new ArrayList<String>();
            if (sender.canUseCommand(2, getName())) {
                options.add("add");
                options.add("remove");
                options.add("list");
                options.add("status");
                options.add("reload");
                options.add("check");
                options.add("cancel");
                options.add("shutdown");
            } else if (sender instanceof EntityPlayerMP
                && mod.getConfig().isPlayerPermitted(
                    ((EntityPlayerMP) sender).getGameProfile().getId())) {
                options.add("status");
                options.add("check");
            }
            return getListOfStringsMatchingLastWord(args, options);
        }

        if (args.length == 2 && sender.canUseCommand(2, getName())) {
            String action = args[0].toLowerCase(Locale.ENGLISH);
            if ("add".equals(action)) {
                List<String> onlinePlayers = new ArrayList<String>();
                Set<String> seen = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
                for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                    String playerName = player.getName();
                    if (playerName != null && !playerName.isEmpty() && seen.add(playerName)) {
                        onlinePlayers.add(playerName);
                    }
                }
                return getListOfStringsMatchingLastWord(args, onlinePlayers);
            }

            if ("remove".equals(action)) {
                List<String> configuredPlayers = new ArrayList<String>();
                Set<String> seen = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
                PlayerProfileCache cache = server.getPlayerProfileCache();
                for (UUID uuid : mod.getConfig().getPlayerUuids()) {
                    String playerName = null;
                    if (cache != null) {
                        GameProfile profile = cache.getProfileByUUID(uuid);
                        if (profile != null) {
                            playerName = profile.getName();
                        }
                    }
                    if (playerName != null && !playerName.isEmpty() && seen.add(playerName)) {
                        configuredPlayers.add(playerName);
                    }
                }
                return getListOfStringsMatchingLastWord(args, configuredPlayers);
            }
        }

        return new ArrayList<String>();
    }
}
