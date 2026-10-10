package org.purpurmc.purpur.util;

import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.purpurmc.purpur.UniversePurpurConfig;

/**
 * Startup Banner and System Telemetry Diagnostic Printer for UniverseSpigot.
 *
 * Author: DevPapo
 */
public final class UniverseBanner {

    public static void print() {
        Logger logger = Bukkit.getLogger();
        int cores = Runtime.getRuntime().availableProcessors();
        long maxRamMb = Runtime.getRuntime().maxMemory() / 1024L / 1024L;
        String javaVer = System.getProperty("java.version", "Unknown");
        String osName = System.getProperty("os.name", "Unknown");
        String throttle = UniversePurpurConfig.dynamicThrottle ? "ENABLED" : "DISABLED";

        String banner = String.format("""
                \u001B[36m
                 _    _       _                                _____       _             _   
                | |  | |     (_)                             / ____|     (_)           | |  
                | |  | |_ __  _ _   _____ _ __ ___  ___     | (___  _ __  _  __ _  ___ | |_ 
                | |  | | '_ \\| | \\ / / _ \\ '__/ __|/ _ \\     \\___ \\| '_ \\| |/ _` |/ _ \\| __|
                | |__| | | | | |\\ V /  __/ |  \\__ \\  __/     ____) | |_) | | (_| | (_) | |_ 
                 \\____/|_| |_|_| \\_/ \\___|_|  |___/\\___|    |_____/| .__/|_|\\__, |\\___/ \\__|
                                                                   | |       __/ |          
                                                                   |_|      |___/           
                \u001B[33m  TITAN HYPER-SCALE EDITION v5.0 | Target: +15,000 Concurrent Players
                \u001B[32m  Author: DevPapo | Architecture: 5-Tier Governor, Spatial Grid & CAS Token Bucket
                \u001B[37m  System: %d Cores | %d MB RAM | Java %s (%s)
                \u001B[35m  Active Profile: %s | Dynamic Throttle: %s\u001B[0m
                """, cores, maxRamMb, javaVer, osName, UniversePurpurConfig.profile, throttle);

        logger.info(banner);
    }
}
