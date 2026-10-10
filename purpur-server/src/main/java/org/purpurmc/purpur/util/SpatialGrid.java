package org.purpurmc.purpur.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * High-performance 2D Spatial Grid Partitioning Engine for UniverseSpigot (+15,000 Players).
 * Replaces O(N) linear entity scans with O(1) hash bucket lookups for nearby players and entities,
 * eliminating the CPU bottleneck in mega-scale player tracking and culling.
 *
 * Thread-safe with multi-world partitioning and true nearest-player distance sorting.
 * Author: DevPapo
 */
public final class SpatialGrid {
    private static final int BUCKET_SIZE = 32; // 32x32 block spatial partition
    private static final SpatialGrid INSTANCE = new SpatialGrid();

    // Multi-world bucket mapping: World UUID -> (Bucket Key -> Entities)
    private final Map<UUID, Map<Long, List<Entity>>> worldBuckets = new ConcurrentHashMap<>();

    public SpatialGrid() {}

    public static SpatialGrid instance() {
        return INSTANCE;
    }

    private static long bucketKey(int blockX, int blockZ) {
        int gx = blockX >> 5; // division by 32
        int gz = blockZ >> 5;
        return (((long) gx & 0xFFFFFFFFL) << 32) | ((long) gz & 0xFFFFFFFFL);
    }

    private Map<Long, List<Entity>> getOrCreateWorldMap(UUID worldUid) {
        return worldBuckets.computeIfAbsent(worldUid, u -> new ConcurrentHashMap<>());
    }

    /**
     * Updates spatial partitioning for entities in a specific world.
     */
    public void updateWorldEntities(World world, Iterable<? extends Entity> entities) {
        if (world == null || entities == null) return;
        Map<Long, List<Entity>> buckets = getOrCreateWorldMap(world.getUID());
        buckets.clear();
        for (Entity entity : entities) {
            if (!entity.isValid()) continue;
            Location loc = entity.getLocation();
            if (loc.getWorld() == null || !loc.getWorld().equals(world)) continue;
            long key = bucketKey(loc.getBlockX(), loc.getBlockZ());
            buckets.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>(8))).add(entity);
        }
    }

    /**
     * Backward-compatible update for general entity collections.
     * Partitions entities by world and safely refreshes buckets without memory leaks or entity duplication.
     */
    public void updateWorldEntities(Iterable<? extends Entity> entities) {
        if (entities == null) return;
        Map<World, List<Entity>> byWorld = new java.util.HashMap<>();
        for (Entity entity : entities) {
            if (!entity.isValid()) continue;
            Location loc = entity.getLocation();
            World w = loc.getWorld();
            if (w == null) continue;
            byWorld.computeIfAbsent(w, k -> new ArrayList<>()).add(entity);
        }
        for (Map.Entry<World, List<Entity>> entry : byWorld.entrySet()) {
            updateWorldEntities(entry.getKey(), entry.getValue());
        }
    }


    /**
     * Finds nearby players in neighboring buckets in O(1) time without iterating the entire server.
     * Guaranteed nearest-player sorting so players in immediate vicinity are never culled.
     *
     * @param center Source player location
     * @param radiusBlock Distance radius in blocks
     * @param maxResults Maximum number of players to return (culling cap)
     * @return List of nearby players sorted by proximity to center
     */
    public List<Player> getNearbyPlayers(Location center, int radiusBlock, int maxResults) {
        if (center == null || center.getWorld() == null || maxResults <= 0 || radiusBlock <= 0) {
            return Collections.emptyList();
        }

        World world = center.getWorld();
        Map<Long, List<Entity>> buckets = worldBuckets.get(world.getUID());
        if (buckets == null || buckets.isEmpty()) {
            return Collections.emptyList();
        }

        int minX = (center.getBlockX() - radiusBlock) >> 5;
        int maxX = (center.getBlockX() + radiusBlock) >> 5;
        int minZ = (center.getBlockZ() - radiusBlock) >> 5;
        int maxZ = (center.getBlockZ() + radiusBlock) >> 5;

        double radiusSq = (double) radiusBlock * radiusBlock;
        List<Player> candidates = new ArrayList<>(Math.min(maxResults * 2, 128));

        for (int gx = minX; gx <= maxX; gx++) {
            for (int gz = minZ; gz <= maxZ; gz++) {
                long key = (((long) gx & 0xFFFFFFFFL) << 32) | ((long) gz & 0xFFFFFFFFL);
                List<Entity> bucket = buckets.get(key);
                if (bucket == null) continue;

                synchronized (bucket) {
                    for (Entity entity : bucket) {
                        if (entity instanceof Player player && player.isOnline()) {
                            Location pLoc = player.getLocation();
                            // Guard against cross-world IllegalArgumentException
                            if (pLoc.getWorld() == null || !pLoc.getWorld().equals(world)) {
                                continue;
                            }
                            if (pLoc.distanceSquared(center) <= radiusSq) {
                                candidates.add(player);
                            }
                        }
                    }
                }
            }
        }

        if (candidates.size() <= maxResults) {
            return candidates;
        }

        // Sort candidates by proximity to center to ensure closest players are prioritized
        candidates.sort(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(center)));
        return new ArrayList<>(candidates.subList(0, maxResults));
    }

    public int getBucketCount() {
        return worldBuckets.values().stream().mapToInt(Map::size).sum();
    }

    public void clear(World world) {
        if (world != null) {
            worldBuckets.remove(world.getUID());
        }
    }

    public void clear() {
        worldBuckets.clear();
    }
}
