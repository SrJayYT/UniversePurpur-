package org.purpurmc.purpur.util;

import java.io.File;
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

        int javaPort = Bukkit.getPort();
        if (javaPort <= 0) {
            String envServerPort = System.getenv("SERVER_PORT");
            if (envServerPort != null && !envServerPort.isEmpty()) {
                try {
                    javaPort = Integer.parseInt(envServerPort.trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        if (javaPort <= 0) {
            try {
                File propFile = new File(UniversePurpurConfig.SERVER_ROOT_DIR != null ? UniversePurpurConfig.SERVER_ROOT_DIR : new File("."), "server.properties");
                if (propFile.exists()) {
                    java.util.Properties p = new java.util.Properties();
                    try (java.io.FileInputStream in = new java.io.FileInputStream(propFile)) {
                        p.load(in);
                        String sp = p.getProperty("server-port");
                        if (sp != null) javaPort = Integer.parseInt(sp.trim());
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (javaPort <= 0) {
            javaPort = 25565;
        }

        int webPort = UniversePurpurConfig.webPanelPort;
        int geyserPort = UniversePurpurConfig.geyserPort;
        String tokenStr = UniversePurpurConfig.webPanelAuthToken.isEmpty() ? "None (Open)" : UniversePurpurConfig.webPanelAuthToken;

        String publicHost = UniversePurpurConfig.webServerIpAddress;
        if (publicHost == null || publicHost.isEmpty() || publicHost.equals("play.yournetwork.net")) {
            String envHost = System.getenv("SERVER_IP");
            if (envHost != null && !envHost.isEmpty() && !envHost.equals("0.0.0.0")) {
                publicHost = envHost;
            } else {
                publicHost = "127.0.0.1";
            }
        }

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
                \u001B[36m  Network Ports: Java: %d | Web Admin: %d | Geyser Bedrock: %d
                \u001B[32m  Public Portal:   http://%s:%d
                \u001B[32m  Web Admin Panel: http://%s:%d/admin (Token: %s)\u001B[0m
                """, cores, maxRamMb, javaVer, osName, UniversePurpurConfig.profile, throttle, javaPort, webPort, geyserPort, publicHost, webPort, publicHost, webPort, tokenStr);

        logger.info(banner);
    }
}
