package org.purpurmc.purpur;

import com.google.common.base.Throwables;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import org.bukkit.Bukkit;
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

    private static File CONFIG_FILE;
    public static YamlConfiguration config;

    // --- Global Runtime Flags ---
    public static boolean enabled = true;
    public static String profile = "15k-hyper-scale";

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
    public static int maxPacketsPerSecond = 1200;
    public static int packetBurstLimit = 250;
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

    private UniversePurpurConfig() {}

    public static void init(File serverRootDir) {
        if (serverRootDir == null) {
            serverRootDir = new File(".");
        }
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
        save();
        try {
            org.purpurmc.purpur.task.UniverseGovernorTask.instance().start();
        } catch (Throwable ignore) {
        }
    }

    public static void reload(File serverRootDir) {
        init(serverRootDir);
    }

    public static void setProfile(String newProfile) {
        profile = newProfile;
        if (config != null) {
            config.set("profile", newProfile);
            save();
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
    }

    // --- Dynamic Governor Query Helpers ---
    public static boolean isLagging(double currentMspt) {
        return dynamicThrottle && (currentMspt >= targetMspt);
    }

    public static boolean isEmergencyLag(double currentMspt) {
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
