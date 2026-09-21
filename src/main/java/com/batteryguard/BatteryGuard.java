package com.batteryguard;

import org.apache.logging.log4j.Logger;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;

import java.io.File;

@Mod(
    modid = BatteryGuard.MODID,
    name = BatteryGuard.NAME,
    version = BatteryGuard.VERSION,
    acceptedMinecraftVersions = "[1.12.2]",
    serverSideOnly = true,
    acceptableRemoteVersions = "*",
    dependencies = "required-after:forge@14.23.5.2859"
)
public class BatteryGuard {
    public static final String MODID = "batteryguard";
    public static final String NAME = "Battery Guard";
    public static final String VERSION = "1.0.1";

    private Logger logger;
    private BatteryConfig config;
    private File configFile;
    private BatteryMonitor monitor;
    private MinecraftServer server;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        configFile = event.getSuggestedConfigurationFile();
        config = BatteryConfig.load(configFile, logger);

        logger.info(
            "Battery Guard {} initialized for Minecraft 1.12.2 / Forge 14.23.5.2859.",
            VERSION
        );
        logger.info("Configuration file: {}", configFile.getAbsolutePath());
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // No registries or client-side setup are required.
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new BatteryCommand(this));
        event.registerServerCommand(new BatteryGuardCommand(this));
        server = event.getServer();
        startMonitor(server);
    }

    @EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        stopMonitor();
    }

    private void startMonitor(MinecraftServer server) {
        stopMonitor();

        if (!config.isEnabled()) {
            logger.info("Battery monitoring is disabled by configuration.");
            return;
        }

        monitor = new BatteryMonitor(server, config, logger);
        monitor.start();
    }

    private void stopMonitor() {
        if (monitor != null) {
            monitor.stop();
            monitor = null;
        }
    }

    public BatteryConfig getConfig() {
        return config;
    }

    public BatteryMonitor getMonitor() {
        return monitor;
    }

    public synchronized void reloadConfig() {
        BatteryConfig newConfig = BatteryConfig.load(configFile, logger);
        config = newConfig;

        if (monitor != null) {
            monitor.applyConfig(newConfig);
            if (!newConfig.isEnabled()) {
                monitor.stopAutomaticProtection();
            }
        } else if (newConfig.isEnabled() && server != null) {
            monitor = new BatteryMonitor(server, newConfig, logger);
            monitor.start();
        }
    }
}
