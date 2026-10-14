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

        int javaPort = Bukkit.getPort() > 0 ? Bukkit.getPort() : 25565;
        int webPort = UniversePurpurConfig.webPanelPort;
        int geyserPort = UniversePurpurConfig.geyserPort;
        String tokenStr = UniversePurpurConfig.webPanelAuthToken.isEmpty() ? "None (Open)" : UniversePurpurConfig.webPanelAuthToken;

        String banner = String.format("""
                \u001B[35m
                 _    _       _                                _____                                
                | |  | |     (_)                             |  __ \\                               
                | |  | |_ __  _ _   _____ _ __ ___  ___      | |__) |   _ _ __ _ __  _   _ _ __    
                | |  | | '_ \\| | \\ / / _ \\ '__/ __|/ _ \\     |  ___/ | | | '__| '_ \\| | | | '__|   
                | |__| | | | | |\\ V /  __/ |  \\__ \\  __/     | |   | |_| | |  | |_) | |_| | |      
                 \\____/|_| |_|_| \\_/ \\___|_|  |___/\\___|     |_|    \\__,_|_|  | .__/ \\__,_|_|      
                                                                               | |                  
                                                                               |_|                  
                \u001B[33m  TITAN HYPER-SCALE ENGINE | Target: +15,000 Concurrent Players
                \u001B[32m  Author: DevPapo (admin@scon.host) | High-Performance Server Software
                \u001B[37m  System: %d Cores | %d MB RAM | Java %s (%s)
                \u001B[35m  Active Profile: %s | Dynamic Throttle: %s
                \u001B[36m  Network Ports: Java: %d | Web Admin: %d | Geyser: %d
                \u001B[32m  Web Admin Panel: http://127.0.0.1:%d (Token: %s)\u001B[0m
                """, cores, maxRamMb, javaVer, osName, UniversePurpurConfig.profile, throttle, javaPort, webPort, geyserPort, webPort, tokenStr);

        logger.info(banner);
    }
}
