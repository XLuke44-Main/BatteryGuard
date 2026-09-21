package com.batteryguard;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.ArrayList;
import java.util.List;

public final class BatteryCommand extends CommandBase {
    private final BatteryGuard mod;

    public BatteryCommand(BatteryGuard mod) {
        this.mod = mod;
    }

    @Override
    public String getName() {
        return "bat";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/bat";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        throws CommandException {

        if (!hasReadAccess(sender)) {
            throw new CommandException(
                BatteryMessages.permissionDenied(mod.getConfig().getLanguage())
            );
        }

        BatteryMonitor monitor = mod.getMonitor();
        if (monitor == null) {
            sender.sendMessage(new net.minecraft.util.text.TextComponentString(
                BatteryMessages.monitoringInactive(mod.getConfig().getLanguage())
            ));
            return;
        }

        sender.sendMessage(
            BatteryMessages.batteryMessage(
                monitor.getLatestSnapshot(),
                mod.getConfig().getLanguage()
            )
        );
    }

    private boolean hasReadAccess(ICommandSender sender) {
        if (sender.canUseCommand(2, getName())) {
            return true;
        }
        if (!(sender instanceof EntityPlayerMP)) {
            return false;
        }
        return mod.getConfig().isPlayerPermitted(
            ((EntityPlayerMP) sender).getGameProfile().getId()
        );
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return hasReadAccess(sender);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public List<String> getAliases() {
        return new ArrayList<String>();
    }
}
