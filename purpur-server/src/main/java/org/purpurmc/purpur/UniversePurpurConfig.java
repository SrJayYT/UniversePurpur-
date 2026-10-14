package org.purpurmc.purpur;

import com.google.common.base.Throwables;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * UniversePurpur / UniverseSpigot runtime configuration engine.
 * Hyper-scale performance profile for massive Minecraft server networks (+15,000 players online).
 *
 * Author: DevPapo
 */
public final class UniversePurpurConfig {
    private static final String HEADER = """
            =============================================================================
             UniversePurpur / UniverseSpigot Hyper-Scale Configuration (+15,000 Players)
             Author: DevPapo
             Target: Mega-Networks, Massive Hubs & Ultra-Scale Clusters (+15k players)
             Engine: 5-Tier MSPT Governor | Player Culling | Netty Token Bucket | Async I/O
            =============================================================================
            """;

    public static File SERVER_ROOT_DIR;
    public static File CONFIG_FILE;
    public static YamlConfiguration config;

    // --- Global Runtime Flags ---
    public static boolean enabled = true;
    public static String profile = "ultra-low-ram-cpu";

    // --- Dynamic MSPT & Tick Throttling (5-Tier Hyper-Scale Governor) ---
    public static boolean dynamicThrottle = true;
    public static double targetMspt = 38.0D;
    public static double hardLimitMspt = 46.0D;
    public static boolean emergencyLagProtection = true;
    public static boolean skipNonCriticalEntityTicks = true;
    public static boolean skipDistantPlayerUpdates = true;

    // --- Mob & Entity Controls ---
    public static boolean globalMobSpawningEnabled = true;
    public static boolean globalMobAiEnabled = true;
    public static boolean disablePassiveMobs = false;
    public static boolean disableHostileMobs = false;
    public static boolean disableAmbientMobs = true;
    public static boolean disableWaterMobs = false;
    public static boolean disableBossMobs = false;

    // Entity Optimization & Collisions (O(N^2) -> O(1))
    public static int maxCollisionsPerEntity = 1;
    public static boolean playerCollisionsEnabled = false;
    public static int inactiveEntityTickRate = 20;
    public static boolean dabEnabled = true; // Dynamic Activation of Brain
    public static int dabMaxTickFreq = 20;
    public static int dabActivationDistance = 12;
    public static double itemMergeRadius = 4.0D;
    public static boolean disableInactiveItemTicks = true;

    // --- Network & Netty Throughput (+15,000 Concurrent Connections) ---
    public static boolean asyncCompression = true;
    public static int compressionThreshold = 512;
    public static boolean tcpNoDelay = true;
    public static boolean flushConsolidation = true;
    public static int nettyEventLoopThreads = 0; // 0 = Auto-detect CPU cores * 2
    public static boolean asyncTabListUpdates = true;
    public static boolean chatAsyncDispatch = true;

    // Adaptive Packet Rate Limiting (Anti-DoS / Packet Flooding)
    public static boolean packetRateLimitEnabled = true;
    public static int maxPacketsPerSecond = 2500;
    public static int packetBurstLimit = 500;
    public static boolean adaptiveRateLimiting = true;

    // Anti-Payload Spam
    public static boolean antiPayloadSpamEnabled = true;
    public static int maxPayloadSizeKb = 16;
    public static int maxChannels = 64;

    // Entity Tracking Ranges & Visible Limits (Crucial for +15k Players)
    public static int maxTrackedEntitiesPerPlayer = 40;
    public static int maxVisiblePlayersPerClient = 32; // Hypixel-style player culling in mega hubs
    public static int trackingRangePlayer = 28;
    public static int trackingRangeAnimals = 16;
    public static int trackingRangeMonsters = 24;
    public static int trackingRangeMisc = 12;

    // --- Chunks, Autosave & World Simulation ---
    public static int simulationDistanceCap = 3;
    public static int viewDistanceCap = 6;
    public static boolean asyncChunkIo = true;
    public static boolean dirtySaveOnly = true;
    public static boolean autoSaveEnabled = true;
    public static int autoSaveIntervalSeconds = 600;
    public static int maxAutoSaveChunksPerTick = 6;

    // --- Hoppers & Redstone Optimization ---
    public static boolean hopperCacheInventoryLookups = true;
    public static int hopperThrottleEmptyTicks = 8;
    public static int redstoneMaxUpdatesPerTick = 5000;

    // --- Titan Hyper-Scale Modules (+15,000 Players) ---
    public static boolean spatialGridEnabled = true;
    public static boolean memoryAutoTrimEnabled = true;
    public static double memoryTrimThresholdPercent = 65.0D;
    public static boolean aggressiveMemorySaver = true;
    public static boolean antiCrashShieldEnabled = true;
    public static boolean idleHibernationEnabled = true;
    public static int idleHibernationTimeoutSeconds = 10;

    // --- Web Admin Panel & Port Infrastructure (2 Default Ports: 25565 Java, 25566 Web; 3 if Geyser: 19132) ---
    public static boolean webPanelEnabled = true;
    public static int webPanelPort = 25566;
    public static String webPanelBindAddress = "0.0.0.0";
    public static String webPanelAuthToken = "admin15k";
    public static boolean webPanelAllowCommands = true;
    public static int geyserPort = 19132;

    // --- Web Branding & Customization ---
    public static String webServerTitle = "UniversePurpur Network";
    public static String webServerDescription = "The Ultimate +15k Minecraft Server Network";
    public static String webServerLogoUrl = "/assets/universepurpur_banner.png";
    public static String webServerFaviconUrl = "https://purpurmc.org/favicon.ico";
    public static String webServerIpAddress = "panel.scon.host";
    public static String webDiscordInviteUrl = "https://discord.gg/universepurpur";

    // --- MultiVersion Support (1.21.x -> 1.26.x Full Protocol Range) ---
    public static boolean multiVersionEnabled = true;
    public static boolean multiVersionAutoDeploy = true;
    public static String multiVersionRange = "1.21.x - 1.26.x (Full Range)";

    private UniversePurpurConfig() {}

    public static void init(File serverRootDir) {
        if (serverRootDir == null) {
            serverRootDir = (SERVER_ROOT_DIR != null) ? SERVER_ROOT_DIR : new File(".");
        }
        SERVER_ROOT_DIR = serverRootDir;
        CONFIG_FILE = new File(serverRootDir, "universe-purpur.yml");
        config = new YamlConfiguration();
        try {
            if (CONFIG_FILE.exists()) {
                config.load(CONFIG_FILE);
            }
        } catch (IOException ignore) {
        } catch (InvalidConfigurationException ex) {
            Bukkit.getLogger().log(Level.SEVERE, "Could not load universe-purpur.yml, please correct syntax errors", ex);
            throw Throwables.propagate(ex);
        }

        config.options().header(HEADER);
        config.options().copyDefaults(true);

        readDefaultsAndValues();
        setProfile(profile);
        save();
        optimizeServerProperties(serverRootDir);
        registerWorldListener();
        try {
            org.purpurmc.purpur.multiversion.UniverseMultiVersionManager.init(serverRootDir);
            org.purpurmc.purpur.multiversion.UniverseGeyserManager.init(serverRootDir);
            org.purpurmc.purpur.multiversion.UniversePlugManManager.init(serverRootDir);
            org.purpurmc.purpur.task.UniverseGovernorTask.instance().start();
            org.purpurmc.purpur.task.UniverseMemoryGovernor.instance().start();
            org.purpurmc.purpur.web.UniverseWebPanel.instance().start();
            org.purpurmc.purpur.util.UniverseBanner.print();
            registerEnhancedCommands();
            try {
                org.bukkit.Bukkit.getScheduler().runTask(new org.purpurmc.purpur.util.MinecraftInternalPlugin(), UniversePurpurConfig::registerEnhancedCommands);
            } catch (Throwable ignore) {
            }
        } catch (Throwable ignore) {
        }
    }

    public static void registerWorldListener() {
        try {
            org.bukkit.plugin.PluginManager pm = Bukkit.getPluginManager();
            pm.registerEvents(new org.bukkit.event.Listener() {
                @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
                public void onWorldInit(org.bukkit.event.world.WorldInitEvent event) {
                    applyWorldOptimizations(event.getWorld());
                }

                @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
                public void onWorldLoad(org.bukkit.event.world.WorldLoadEvent event) {
                    applyWorldOptimizations(event.getWorld());
                }
            }, new org.purpurmc.purpur.util.MinecraftInternalPlugin());

            for (World w : Bukkit.getWorlds()) {
                applyWorldOptimizations(w);
            }
        } catch (Throwable ignore) {
        }
    }

    public static void applyWorldOptimizations(World world) {
        if (world == null) return;
        try {
            world.setKeepSpawnInMemory(false);
            int vDist = getWorldViewDistance(world.getName());
            int sDist = getWorldSimulationDistance(world.getName());
            world.setViewDistance(vDist);
            world.setSimulationDistance(sDist);

            try {
                world.setGameRule(org.bukkit.GameRule.SPAWN_RADIUS, 1);
                world.setGameRule(org.bukkit.GameRule.SPAWN_CHUNK_RADIUS, 0);
                world.setGameRule(org.bukkit.GameRule.RANDOM_TICK_SPEED, getWorldRandomTickSpeed(world.getName()));
            } catch (Throwable ignore) {}
        } catch (Throwable ignore) {}
    }

    public static void optimizeServerProperties(File serverRootDir) {
        try {
            File propFile = new File(serverRootDir, "server.properties");
            if (propFile.exists()) {
                java.util.Properties props = new java.util.Properties();
                try (java.io.FileInputStream in = new java.io.FileInputStream(propFile)) {
                    props.load(in);
                }
                boolean modified = false;
                if (!"0".equals(props.getProperty("spawn-chunk-radius"))) {
                    props.setProperty("spawn-chunk-radius", "0");
                    modified = true;
                }
                if (!"false".equals(props.getProperty("sync-chunk-writes"))) {
                    props.setProperty("sync-chunk-writes", "false");
                    modified = true;
                }
                if (!"false".equals(props.getProperty("enforce-secure-profile"))) {
                    props.setProperty("enforce-secure-profile", "false");
                    modified = true;
                }

                if ("ultra-low-ram-cpu".equalsIgnoreCase(profile)) {
                    if (!"4".equals(props.getProperty("view-distance"))) {
                        props.setProperty("view-distance", "4");
                        modified = true;
                    }
                    if (!"2".equals(props.getProperty("simulation-distance"))) {
                        props.setProperty("simulation-distance", "2");
                        modified = true;
                    }
                    if (!"256".equals(props.getProperty("network-compression-threshold"))) {
                        props.setProperty("network-compression-threshold", "256");
                        modified = true;
                    }
                    if (!"50".equals(props.getProperty("entity-broadcast-range-percentage"))) {
                        props.setProperty("entity-broadcast-range-percentage", "50");
                        modified = true;
                    }
                }

                if (modified) {
                    try (java.io.FileOutputStream out = new java.io.FileOutputStream(propFile)) {
                        props.store(out, "UniversePurpur Ultra-Low RAM & CPU Configuration");
                    }
                    Bukkit.getLogger().info("[UniverseTitan] server.properties optimizado: view-distance=4, simulation-distance=2, spawn-chunk-radius=0, sync-chunk-writes=false.");
                }
            }
        } catch (Throwable ignore) {
        }
    }

    public static void registerEnhancedCommands() {
        try {
            org.bukkit.command.CommandMap map = org.bukkit.Bukkit.getCommandMap();
            if (map instanceof org.bukkit.command.SimpleCommandMap simpleMap) {
                java.util.Map<String, org.bukkit.command.Command> known = simpleMap.getKnownCommands();
                org.purpurmc.purpur.command.UniverseVersionCommand verCmd = new org.purpurmc.purpur.command.UniverseVersionCommand("version");
                org.purpurmc.purpur.command.UniversePluginsCommand plCmd = new org.purpurmc.purpur.command.UniversePluginsCommand("plugins");
                org.purpurmc.purpur.command.UniverseHelpCommand helpCmd = new org.purpurmc.purpur.command.UniverseHelpCommand("help");
                org.purpurmc.purpur.command.UniverseTpsCommand tpsCmd = new org.purpurmc.purpur.command.UniverseTpsCommand("tps");

                known.put("version", verCmd);
                known.put("ver", verCmd);
                known.put("about", verCmd);
                known.put("bukkit:version", verCmd);
                known.put("bukkit:ver", verCmd);
                known.put("bukkit:about", verCmd);

                known.put("plugins", plCmd);
                known.put("pl", plCmd);
                known.put("bukkit:plugins", plCmd);
                known.put("bukkit:pl", plCmd);

                known.put("help", helpCmd);
                known.put("?", helpCmd);
                known.put("bukkit:help", helpCmd);
                known.put("bukkit:?", helpCmd);

                known.put("tps", tpsCmd);
                known.put("lag", tpsCmd);
                known.put("mspt", tpsCmd);
                known.put("bukkit:tps", tpsCmd);

                org.purpurmc.purpur.command.UniversePurpurCommand upCmd = new org.purpurmc.purpur.command.UniversePurpurCommand("universepurpur");
                known.put("universepurpur", upCmd);
                known.put("up", upCmd);
                known.put("universe", upCmd);
                known.put("upurpur", upCmd);

                // Quick Gamemode Commands (/gmc, /gms, /gma, /gmsp, /gm)
                org.purpurmc.purpur.command.UniverseGamemodeCommand gm = new org.purpurmc.purpur.command.UniverseGamemodeCommand("gm", java.util.List.of());
                org.purpurmc.purpur.command.UniverseGamemodeCommand gmc = new org.purpurmc.purpur.command.UniverseGamemodeCommand("gmc", org.bukkit.GameMode.CREATIVE, "Creativo", java.util.List.of("gm1", "creative"));
                org.purpurmc.purpur.command.UniverseGamemodeCommand gms = new org.purpurmc.purpur.command.UniverseGamemodeCommand("gms", org.bukkit.GameMode.SURVIVAL, "Supervivencia", java.util.List.of("gm0", "survival"));
                org.purpurmc.purpur.command.UniverseGamemodeCommand gma = new org.purpurmc.purpur.command.UniverseGamemodeCommand("gma", org.bukkit.GameMode.ADVENTURE, "Aventura", java.util.List.of("gm2", "adventure"));
                org.purpurmc.purpur.command.UniverseGamemodeCommand gmsp = new org.purpurmc.purpur.command.UniverseGamemodeCommand("gmsp", org.bukkit.GameMode.SPECTATOR, "Espectador", java.util.List.of("gm3", "spectator"));

                known.put("gm", gm);
                known.put("gmc", gmc);
                known.put("gm1", gmc);
                known.put("creative", gmc);

                known.put("gms", gms);
                known.put("gm0", gms);
                known.put("survival", gms);

                known.put("gma", gma);
                known.put("gm2", gma);
                known.put("adventure", gma);

                known.put("gmsp", gmsp);
                known.put("gm3", gmsp);
                known.put("spectator", gmsp);
            }
        } catch (Throwable ignore) {
        }
    }

    public static void reload() {
        init(SERVER_ROOT_DIR);
    }

    public static void reload(File serverRootDir) {
        init(serverRootDir != null ? serverRootDir : SERVER_ROOT_DIR);
    }

    public static void setProfile(String newProfile) {
        profile = newProfile;
        if (newProfile != null && newProfile.equalsIgnoreCase("ultra-low-ram-cpu")) {
            simulationDistanceCap = 2;
            viewDistanceCap = 4;
            maxVisiblePlayersPerClient = 16;
            maxCollisionsPerEntity = 0;
            playerCollisionsEnabled = false;
            hopperThrottleEmptyTicks = 16;
            dabActivationDistance = 8;
            dabMaxTickFreq = 40;
            memoryTrimThresholdPercent = 80.0D;
            maxAutoSaveChunksPerTick = 2;
            itemMergeRadius = 5.0D;
            aggressiveMemorySaver = false;
            disableAmbientMobs = true;
        } else if (newProfile != null && newProfile.equalsIgnoreCase("15k-hyper-hub")) {
            simulationDistanceCap = 2;
            viewDistanceCap = 6;
            maxVisiblePlayersPerClient = 24;
            maxCollisionsPerEntity = 0;
            playerCollisionsEnabled = false;
            memoryTrimThresholdPercent = 80.0D;
            aggressiveMemorySaver = false;
        } else if (newProfile != null && newProfile.equalsIgnoreCase("15k-scale-smp")) {
            simulationDistanceCap = 4;
            viewDistanceCap = 6;
            maxVisiblePlayersPerClient = 32;
            maxCollisionsPerEntity = 1;
            playerCollisionsEnabled = false;
            memoryTrimThresholdPercent = 80.0D;
            aggressiveMemorySaver = false;
        }
        if (config != null) {
            config.set("profile", newProfile);
            save();
        }
        try {
            for (World w : Bukkit.getWorlds()) {
                applyWorldOptimizations(w);
            }
        } catch (Throwable ignore) {
        }
    }

    private static void readDefaultsAndValues() {
        enabled = getBoolean("global.enabled", enabled);
        profile = getString("profile", profile);


        // Dynamic Tick Governor
        dynamicThrottle = getBoolean("global.tick.dynamic-throttle", dynamicThrottle);
        targetMspt = getDouble("global.tick.target-mspt", targetMspt);
        hardLimitMspt = getDouble("global.tick.hard-limit-mspt", hardLimitMspt);
        emergencyLagProtection = getBoolean("global.tick.emergency-lag-protection", emergencyLagProtection);
        skipNonCriticalEntityTicks = getBoolean("global.tick.skip-non-critical-entity-ticks", skipNonCriticalEntityTicks);
        skipDistantPlayerUpdates = getBoolean("global.tick.skip-distant-player-updates", skipDistantPlayerUpdates);

        // Mob & AI Controls
        globalMobSpawningEnabled = getBoolean("global.mobs.global-spawning-enabled", globalMobSpawningEnabled);
        globalMobAiEnabled = getBoolean("global.mobs.global-ai-enabled", globalMobAiEnabled);
        disablePassiveMobs = getBoolean("global.mobs.disable-passive-mobs", disablePassiveMobs);
        disableHostileMobs = getBoolean("global.mobs.disable-hostile-mobs", disableHostileMobs);
        disableAmbientMobs = getBoolean("global.mobs.disable-ambient-mobs", disableAmbientMobs);
        disableWaterMobs = getBoolean("global.mobs.disable-water-mobs", disableWaterMobs);
        disableBossMobs = getBoolean("global.mobs.disable-boss-mobs", disableBossMobs);

        // Density / Collision Optimization
        maxCollisionsPerEntity = getInt("global.mobs.max-collisions-per-entity", maxCollisionsPerEntity);
        playerCollisionsEnabled = getBoolean("global.mobs.player-collisions-enabled", playerCollisionsEnabled);
        inactiveEntityTickRate = getInt("global.mobs.inactive-entity-tick-rate", inactiveEntityTickRate);
        dabEnabled = getBoolean("global.mobs.dab.enabled", dabEnabled);
        dabMaxTickFreq = getInt("global.mobs.dab.max-tick-freq", dabMaxTickFreq);
        dabActivationDistance = getInt("global.mobs.dab.activation-distance", dabActivationDistance);
        itemMergeRadius = getDouble("global.mobs.item-merge-radius", itemMergeRadius);
        disableInactiveItemTicks = getBoolean("global.mobs.disable-inactive-item-ticks", disableInactiveItemTicks);

        // Network Layer
        asyncCompression = getBoolean("global.network.async-compression", asyncCompression);
        compressionThreshold = getInt("global.network.compression-threshold", compressionThreshold);
        tcpNoDelay = getBoolean("global.network.tcp-nodelay", tcpNoDelay);
        flushConsolidation = getBoolean("global.network.flush-consolidation", flushConsolidation);
        nettyEventLoopThreads = getInt("global.network.netty-event-loop-threads", nettyEventLoopThreads);
        asyncTabListUpdates = getBoolean("global.network.async-tablist-updates", asyncTabListUpdates);
        chatAsyncDispatch = getBoolean("global.network.chat-async-dispatch", chatAsyncDispatch);

        packetRateLimitEnabled = getBoolean("global.network.packet-rate-limit.enabled", packetRateLimitEnabled);
        maxPacketsPerSecond = getInt("global.network.packet-rate-limit.max-packets-per-second", maxPacketsPerSecond);
        packetBurstLimit = getInt("global.network.packet-rate-limit.burst", packetBurstLimit);
        adaptiveRateLimiting = getBoolean("global.network.packet-rate-limit.adaptive", adaptiveRateLimiting);

        antiPayloadSpamEnabled = getBoolean("global.network.anti-payload-spam.enabled", antiPayloadSpamEnabled);
        maxPayloadSizeKb = getInt("global.network.anti-payload-spam.max-custom-payload-size-kb", maxPayloadSizeKb);
        maxChannels = getInt("global.network.anti-payload-spam.max-channels", maxChannels);

        // Tracking Ranges & Player Culling
        maxTrackedEntitiesPerPlayer = getInt("global.network.tracking.max-tracked-entities-per-player", maxTrackedEntitiesPerPlayer);
        maxVisiblePlayersPerClient = getInt("global.network.tracking.max-visible-players-per-client", maxVisiblePlayersPerClient);
        trackingRangePlayer = getInt("global.network.tracking.range-player", trackingRangePlayer);
        trackingRangeAnimals = getInt("global.network.tracking.range-animals", trackingRangeAnimals);
        trackingRangeMonsters = getInt("global.network.tracking.range-monsters", trackingRangeMonsters);
        trackingRangeMisc = getInt("global.network.tracking.range-misc", trackingRangeMisc);

        // Chunks & Storage
        simulationDistanceCap = getInt("global.chunks.simulation-distance-cap", simulationDistanceCap);
        viewDistanceCap = getInt("global.chunks.view-distance-cap", viewDistanceCap);
        asyncChunkIo = getBoolean("global.chunks.async-chunk-io", asyncChunkIo);
        dirtySaveOnly = getBoolean("global.chunks.dirty-save-only", getBoolean("global.chunks.dirtySaveOnly", dirtySaveOnly));
        autoSaveEnabled = getBoolean("global.data.autosave.enabled", autoSaveEnabled);
        autoSaveIntervalSeconds = getInt("global.data.autosave.interval-seconds", autoSaveIntervalSeconds);
        maxAutoSaveChunksPerTick = getInt("global.data.autosave.max-chunks-per-tick", maxAutoSaveChunksPerTick);

        // Hoppers & Redstone
        hopperCacheInventoryLookups = getBoolean("global.hoppers.cache-inventory-lookups", hopperCacheInventoryLookups);
        hopperThrottleEmptyTicks = getInt("global.hoppers.throttle-empty-ticks", hopperThrottleEmptyTicks);
        redstoneMaxUpdatesPerTick = getInt("global.redstone.max-updates-per-tick", redstoneMaxUpdatesPerTick);

        // Titan Enterprise Modules
        spatialGridEnabled = getBoolean("global.performance.spatial-grid-enabled", spatialGridEnabled);
        memoryAutoTrimEnabled = getBoolean("global.performance.memory-auto-trim.enabled", memoryAutoTrimEnabled);
        memoryTrimThresholdPercent = getDouble("global.performance.memory-auto-trim.threshold-percent", memoryTrimThresholdPercent);
        aggressiveMemorySaver = getBoolean("global.performance.aggressive-memory-saver", aggressiveMemorySaver);
        idleHibernationEnabled = getBoolean("global.performance.idle-hibernation.enabled", idleHibernationEnabled);
        idleHibernationTimeoutSeconds = getInt("global.performance.idle-hibernation.timeout-seconds", idleHibernationTimeoutSeconds);
        antiCrashShieldEnabled = getBoolean("global.security.anti-crash-shield.enabled", antiCrashShieldEnabled);


        // Web Admin Panel & Port Infrastructure (2 Ports Default, 3 if Geyser)
        webPanelEnabled = getBoolean("global.web-panel.enabled", webPanelEnabled);
        webPanelPort = getInt("global.web-panel.port", webPanelPort);
        webPanelBindAddress = getString("global.web-panel.bind-address", webPanelBindAddress);
        webPanelAuthToken = getString("global.web-panel.auth-token", webPanelAuthToken);
        webPanelAllowCommands = getBoolean("global.web-panel.allow-remote-commands", webPanelAllowCommands);
        geyserPort = getInt("global.geyser.bedrock-port", geyserPort);

        // Environment Variable & System Property Overrides (Critical for Pterodactyl & Cloud Hosting)
        String envWebPort = System.getenv("WEB_PORT");
        if (envWebPort == null || envWebPort.isEmpty()) envWebPort = System.getProperty("universe.web-panel.port");
        if (envWebPort != null && !envWebPort.isEmpty()) {
            try {
                webPanelPort = Integer.parseInt(envWebPort.trim());
                if (config != null) config.set("global.web-panel.port", webPanelPort);
            } catch (NumberFormatException ignored) {}
        }

        String envGeyserPort = System.getenv("GEYSER_PORT");
        if (envGeyserPort == null || envGeyserPort.isEmpty()) envGeyserPort = System.getProperty("universe.geyser.port");
        if (envGeyserPort != null && !envGeyserPort.isEmpty()) {
            try {
                geyserPort = Integer.parseInt(envGeyserPort.trim());
                if (config != null) config.set("global.geyser.bedrock-port", geyserPort);
            } catch (NumberFormatException ignored) {}
        }

        String envAuthToken = System.getenv("WEB_AUTH_TOKEN");
        if (envAuthToken == null || envAuthToken.isEmpty()) envAuthToken = System.getProperty("universe.web-panel.auth-token");
        if (envAuthToken != null && !envAuthToken.isEmpty()) {
            webPanelAuthToken = envAuthToken.trim();
            if (config != null) config.set("global.web-panel.auth-token", webPanelAuthToken);
        }

        String envProfile = System.getenv("UNIVERSE_PROFILE");
        if (envProfile == null || envProfile.isEmpty()) envProfile = System.getProperty("universe.profile");
        if (envProfile != null && !envProfile.isEmpty()) {
            profile = envProfile.trim();
            if (config != null) config.set("performance.profile", profile);
        }

        String envServerIp = System.getenv("SERVER_IP");
        if (envServerIp != null && !envServerIp.isEmpty() && !envServerIp.equals("0.0.0.0")) {
            webServerIpAddress = envServerIp.trim();
        }

        // Web Branding Defaults
        webServerTitle = getString("global.web-panel.branding.title", webServerTitle);
        webServerDescription = getString("global.web-panel.branding.description", webServerDescription);
        webServerLogoUrl = getString("global.web-panel.branding.logo-url", webServerLogoUrl);
        webServerFaviconUrl = getString("global.web-panel.branding.favicon-url", webServerFaviconUrl);
        webServerIpAddress = getString("global.web-panel.branding.server-ip", webServerIpAddress);
        webDiscordInviteUrl = getString("global.web-panel.branding.discord-invite-url", webDiscordInviteUrl);

        // MultiVersion Protocol Engine (1.21.x -> 1.26.x)
        multiVersionEnabled = getBoolean("global.multiversion.enabled", multiVersionEnabled);
        multiVersionAutoDeploy = getBoolean("global.multiversion.auto-deploy", multiVersionAutoDeploy);
        multiVersionRange = getString("global.multiversion.range", multiVersionRange);
    }

    public static void updateBranding(String title, String desc, String logo, String favicon, String ip, String discord) {
        if (title != null && !title.isEmpty()) webServerTitle = title;
        if (desc != null && !desc.isEmpty()) webServerDescription = desc;
        if (logo != null && !logo.isEmpty()) webServerLogoUrl = logo;
        if (favicon != null && !favicon.isEmpty()) webServerFaviconUrl = favicon;
        if (ip != null && !ip.isEmpty()) webServerIpAddress = ip;
        if (discord != null && !discord.isEmpty()) webDiscordInviteUrl = discord;

        if (config != null) {
            config.set("global.web-panel.branding.title", webServerTitle);
            config.set("global.web-panel.branding.description", webServerDescription);
            config.set("global.web-panel.branding.logo-url", webServerLogoUrl);
            config.set("global.web-panel.branding.favicon-url", webServerFaviconUrl);
            config.set("global.web-panel.branding.server-ip", webServerIpAddress);
            config.set("global.web-panel.branding.discord-invite-url", webDiscordInviteUrl);
            save();
        }
    }

    // --- Dynamic Governor Query Helpers ---
    public static boolean isLagging(double currentMspt) {
        return dynamicThrottle && (currentMspt >= targetMspt);
    }

    public static boolean isEmergencyLag(double currentMspt) {
        Boolean override = org.purpurmc.purpur.task.UniverseGovernorTask.instance().getManualEmergencyOverride();
        if (override != null) {
            return override;
        }
        return emergencyLagProtection && (currentMspt >= hardLimitMspt);
    }

    public static boolean shouldThrottleBrain(double currentMspt) {
        return dabEnabled && (currentMspt >= (targetMspt * 0.85D));
    }

    // --- Per-World & Preset Query Helpers ---
    public static String getWorldPreset(String worldName) {
        String worldPath = "world-rules." + worldName + ".preset";
        if (config != null && config.contains(worldPath)) {
            return config.getString(worldPath, "15k-hyper-hub");
        }
        return "15k-hyper-hub";
    }

    public static boolean isWorldEnabled(String worldName) {
        return getBoolean("world-rules." + worldName + ".enabled", true);
    }

    public static boolean isWorldTimeLocked(String worldName) {
        return getBoolean("world-rules." + worldName + ".time.lock", false);
    }

    public static long getWorldFixedTime(String worldName) {
        return getInt("world-rules." + worldName + ".time.value", 6000);
    }

    public static boolean isWorldTimeCycle(String worldName) {
        return getBoolean("world-rules." + worldName + ".time.cycle", true);
    }

    public static boolean isWorldMobSpawningEnabled(String worldName) {
        String worldPath = "world-rules." + worldName + ".mobs.global-spawning-enabled";
        if (config != null && config.contains(worldPath)) {
            return config.getBoolean(worldPath, globalMobSpawningEnabled);
        }
        String preset = getWorldPreset(worldName);
        return getBoolean("presets." + preset + ".mobs.global-spawning-enabled", globalMobSpawningEnabled);
    }

    public static boolean isWorldMobAiEnabled(String worldName) {
        String worldPath = "world-rules." + worldName + ".mobs.global-ai-enabled";
        if (config != null && config.contains(worldPath)) {
            return config.getBoolean(worldPath, globalMobAiEnabled);
        }
        String preset = getWorldPreset(worldName);
        return getBoolean("presets." + preset + ".mobs.global-ai-enabled", globalMobAiEnabled);
    }

    public static int getWorldMaxCollisions(String worldName) {
        String worldPath = "world-rules." + worldName + ".mobs.max-collisions-per-entity";
        if (config != null && config.contains(worldPath)) {
            return config.getInt(worldPath, maxCollisionsPerEntity);
        }
        String preset = getWorldPreset(worldName);
        return getInt("presets." + preset + ".mobs.max-collisions-per-entity", maxCollisionsPerEntity);
    }

    public static boolean isWorldPlayerCollisionsEnabled(String worldName) {
        String worldPath = "world-rules." + worldName + ".mobs.player-collisions-enabled";
        if (config != null && config.contains(worldPath)) {
            return config.getBoolean(worldPath, playerCollisionsEnabled);
        }
        String preset = getWorldPreset(worldName);
        return getBoolean("presets." + preset + ".mobs.player-collisions-enabled", playerCollisionsEnabled);
    }

    public static int getWorldSimulationDistance(String worldName) {
        String worldPath = "world-rules." + worldName + ".chunks.simulation-distance";
        String preset = getWorldPreset(worldName);
        int def = getInt("presets." + preset + ".chunks.simulation-distance", simulationDistanceCap);
        int val = (config != null && config.contains(worldPath)) ? config.getInt(worldPath, def) : def;
        return Math.max(2, Math.min(val, simulationDistanceCap));
    }

    public static int getWorldViewDistance(String worldName) {
        String worldPath = "world-rules." + worldName + ".chunks.view-distance";
        String preset = getWorldPreset(worldName);
        int def = getInt("presets." + preset + ".chunks.view-distance", viewDistanceCap);
        int val = (config != null && config.contains(worldPath)) ? config.getInt(worldPath, def) : def;
        return Math.max(2, Math.min(val, viewDistanceCap));
    }

    public static int getWorldMaxVisiblePlayers(String worldName) {
        String worldPath = "world-rules." + worldName + ".tracking.max-visible-players-per-client";
        String preset = getWorldPreset(worldName);
        int def = getInt("presets." + preset + ".tracking.max-visible-players-per-client", maxVisiblePlayersPerClient);
        int val = (config != null && config.contains(worldPath)) ? config.getInt(worldPath, def) : def;
        return Math.max(4, val);
    }

    public static int getWorldRandomTickSpeed(String worldName) {
        String worldPath = "world-rules." + worldName + ".ticks.random-tick-speed";
        if (config != null && config.contains(worldPath)) {
            return config.getInt(worldPath, 1);
        }
        String preset = getWorldPreset(worldName);
        return getInt("presets." + preset + ".ticks.random-tick-speed", 1);
    }

    public static int getWorldMaxBlockUpdates(String worldName) {
        String worldPath = "world-rules." + worldName + ".ticks.max-block-updates-per-tick";
        if (config != null && config.contains(worldPath)) {
            return config.getInt(worldPath, 5000);
        }
        String preset = getWorldPreset(worldName);
        return getInt("presets." + preset + ".ticks.max-block-updates-per-tick", 5000);
    }

    public static int getWorldAutoSaveInterval(String worldName) {
        return getInt("world-rules." + worldName + ".data.autosave-interval-seconds", autoSaveIntervalSeconds);
    }

    // --- Configuration Getters / Defaults ---
    private static boolean getBoolean(String path, boolean def) {
        if (config == null) return def;
        config.addDefault(path, def);
        return config.getBoolean(path, def);
    }

    private static double getDouble(String path, double def) {
        if (config == null) return def;
        config.addDefault(path, def);
        return config.getDouble(path, def);
    }

    private static int getInt(String path, int def) {
        if (config == null) return def;
        config.addDefault(path, def);
        return config.getInt(path, def);
    }

    private static String getString(String path, String def) {
        if (config == null) return def;
        config.addDefault(path, def);
        return config.getString(path, def);
    }

    private static void save() {
        if (CONFIG_FILE == null || config == null) return;
        try {
            config.save(CONFIG_FILE);
        } catch (IOException ex) {
            Bukkit.getLogger().log(Level.SEVERE, "Could not save " + CONFIG_FILE, ex);
        }
    }
}
