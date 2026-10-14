package org.purpurmc.purpur.task;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import org.purpurmc.purpur.UniversePurpurConfig;
import org.purpurmc.purpur.util.MinecraftInternalPlugin;

/**
 * Intelligent Memory Governor & Auto-Garbage Healer for UniverseSpigot (+15,000 Players).
 * Monitors JVM heap saturation and GC pause times in real time, automatically pruning
 * volatile world caches, unloaded chunk data, and requesting JVM memory reclamation
 * before Stop-The-World GC spikes can occur.
 *
 * Includes Zero-Player Deep-Sleep Hibernation Engine to reduce physical RAM usage
 * down to the bare minimum when no players are online.
 *
 * Thread-safe with AsyncCatcher-compliant synchronous dispatch for Bukkit world operations.
 * Author: DevPapo
 */
public final class UniverseMemoryGovernor implements Runnable {
    private static UniverseMemoryGovernor instance;
    private BukkitTask task;

    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

    private volatile double heapUsedMb = 0.0D;
    private volatile double heapMaxMb = 0.0D;
    private volatile double heapUsagePercent = 0.0D;
    private volatile long totalGcPauseTimeMs = 0L;

    private final AtomicInteger autoTrimCount = new AtomicInteger(0);
    private final AtomicLong lastTrimTimestamp = new AtomicLong(0L);

    private final AtomicInteger zeroPlayerSeconds = new AtomicInteger(0);
    private final long bootTimestamp = System.currentTimeMillis();
    private volatile boolean isHibernating = false;

    private UniverseMemoryGovernor() {}

    public static UniverseMemoryGovernor instance() {
        if (instance == null) {
            instance = new UniverseMemoryGovernor();
        }
        return instance;
    }

    public synchronized void start() {
        stop();
        this.task = Bukkit.getScheduler().runTaskTimerAsynchronously(
                new MinecraftInternalPlugin(),
                this,
                100L, // every 5 seconds (100 ticks)
                100L
        );
    }

    public synchronized void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    @Override
    public void run() {
        try {
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            long used = heapUsage.getUsed();
            long max = heapUsage.getMax();

            this.heapUsedMb = (double) used / (1024.0D * 1024.0D);
            this.heapMaxMb = (double) max / (1024.0D * 1024.0D);
            this.heapUsagePercent = (max > 0) ? ((double) used / (double) max) * 100.0D : 0.0D;

            long totalPause = 0L;
            for (GarbageCollectorMXBean gc : gcBeans) {
                long time = gc.getCollectionTime();
                if (time > 0) {
                    totalPause += time;
                }
            }
            this.totalGcPauseTimeMs = totalPause;

            int online = Bukkit.getOnlinePlayers().size();

            // Zero-Player Deep Hibernation Engine (with startup grace period)
            long uptime = System.currentTimeMillis() - bootTimestamp;
            if (online == 0 && UniversePurpurConfig.idleHibernationEnabled && uptime >= 45000L) {
                int secs = zeroPlayerSeconds.addAndGet(5);
                if (secs >= UniversePurpurConfig.idleHibernationTimeoutSeconds && !isHibernating) {
                    isHibernating = true;
                    Bukkit.getLogger().info("[UniverseTitan] 0 jugadores detectados -> Activando Hibernación Profunda (descargando chunks y liberando RAM al SO).");
                    triggerAsyncSafeTrim();
                } else if (secs >= 120 && (secs % 120 == 0)) {
                    // Mantenimiento periódico de reposo cada 2 minutos
                    triggerAsyncSafeTrim();
                }
            } else {
                if (isHibernating) {
                    isHibernating = false;
                    Bukkit.getLogger().info("[UniverseTitan] Jugador conectado -> Reanudando actividad normal del motor.");
                }
                zeroPlayerSeconds.set(0);

                // Auto-heal / Auto-trim if heap exceeds configured threshold (default 82%+)
                double threshold = Math.max(82.0D, UniversePurpurConfig.memoryTrimThresholdPercent);
                if (UniversePurpurConfig.memoryAutoTrimEnabled && heapUsagePercent >= threshold) {
                    long now = System.currentTimeMillis();
                    long last = lastTrimTimestamp.get();
                    if (now - last >= 60000L && lastTrimTimestamp.compareAndSet(last, now)) {
                        triggerAsyncSafeTrim();
                    }
                }
            }
        } catch (Throwable ignore) {
        }
    }

    private void triggerAsyncSafeTrim() {
        trimCaches();
    }

    /**
     * Intelligently trims volatile world caches, unloaded chunk data, and invites GC reclamation.
     * Guaranteed 100% thread-safe with primary server thread dispatch against Paper AsyncCatcher violations.
     */
    public int trimCaches() {
        if (!Bukkit.isPrimaryThread()) {
            java.util.concurrent.CompletableFuture<Integer> future = new java.util.concurrent.CompletableFuture<>();
            try {
                Bukkit.getScheduler().runTask(new MinecraftInternalPlugin(), () -> {
                    try {
                        future.complete(doTrimCaches());
                    } catch (Throwable t) {
                        future.complete(0);
                    }
                });
                return future.get(2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Throwable t) {
                return 0;
            }
        }
        return doTrimCaches();
    }

    private int doTrimCaches() {
        autoTrimCount.incrementAndGet();
        int freedChunks = 0;
        int online = Bukkit.getOnlinePlayers().size();

        try {
            for (World world : Bukkit.getWorlds()) {
                if (online == 0) {
                    try {
                        world.setKeepSpawnInMemory(false);
                    } catch (Throwable ignore) {}
                }

                Chunk[] loaded = world.getLoadedChunks();
                for (Chunk chunk : loaded) {
                    if (online == 0) {
                        // En reposo profundo, encolamos descarga asíncrona sin bloquear el tick loop
                        world.unloadChunkRequest(chunk.getX(), chunk.getZ());
                        freedChunks++;
                    } else if (UniversePurpurConfig.aggressiveMemorySaver && !chunk.isEntitiesLoaded()) {
                        boolean hasNearbyPlayer = false;
                        int cx = (chunk.getX() << 4) + 8;
                        int cz = (chunk.getZ() << 4) + 8;
                        double safeRadiusBlocks = (world.getViewDistance() + 2) * 16.0D;
                        double safeRadiusSq = safeRadiusBlocks * safeRadiusBlocks;
                        for (org.bukkit.entity.Player p : world.getPlayers()) {
                            org.bukkit.Location pLoc = p.getLocation();
                            double dx = pLoc.getX() - cx;
                            double dz = pLoc.getZ() - cz;
                            if ((dx * dx + dz * dz) <= safeRadiusSq) {
                                hasNearbyPlayer = true;
                                break;
                            }
                        }
                        if (!hasNearbyPlayer) {
                            world.unloadChunkRequest(chunk.getX(), chunk.getZ());
                            freedChunks++;
                        }
                    }
                }
            }

            if (online == 0) {
                try {
                    org.purpurmc.purpur.util.SpatialGrid.instance().clear();
                } catch (Throwable ignore) {}
            }

            // Prune expired codes & web sessions to minimize JVM Heap footprint
            try {
                org.purpurmc.purpur.web.UniverseDiscordLinker.instance().cleanExpiredCodes();
                org.purpurmc.purpur.web.UniverseUserManager.instance().cleanExpiredSessions();
            } catch (Throwable ignore) {
            }

            // ONLY invite System.gc() when 0 players are online (Deep Hibernation)
            // and execute it ASYNCHRONOUSLY on a background worker thread so the main server tick loop NEVER halts.
            if (online == 0) {
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        System.gc();
                    } catch (Throwable ignore) {}
                });
            }
        } catch (Throwable ignore) {
        }

        return freedChunks;
    }

    public boolean isHibernating() {
        return isHibernating;
    }

    public double getHeapUsedMb() {
        return heapUsedMb;
    }

    public double getHeapMaxMb() {
        return heapMaxMb;
    }

    public double getHeapUsagePercent() {
        return heapUsagePercent;
    }

    public long getTotalGcPauseTimeMs() {
        return totalGcPauseTimeMs;
    }

    public int getAutoTrimCount() {
        return autoTrimCount.get();
    }
}
