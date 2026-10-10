package org.purpurmc.purpur.task;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.purpurmc.purpur.UniversePurpurConfig;
import org.purpurmc.purpur.util.MinecraftInternalPlugin;

/**
 * 5-Tier Hyper-Scale Dynamic MSPT Governor and Lag Mitigation Engine for UniverseSpigot (+15,000 Players).
 * Continuously computes real-time MSPT/TPS metrics and enforces multi-level autonomous throttling.
 *
 * Thread-safe, volatile metrics with safe restartable BukkitTask scheduling.
 * Author: DevPapo
 */
public class UniverseGovernorTask implements Runnable {
    private static UniverseGovernorTask instance;
    private BukkitTask scheduledTask = null;

    // 5-Tier Hyper-Scale Levels (volatile for cross-thread Netty / Server Thread visibility):
    // 0: OPTIMAL              - MSPT < 30.0ms (Pristine, 20.0 TPS full fidelity)
    // 1: SOFT THROTTLE        - MSPT 30.0ms - 38.0ms (Soft DAB brain throttle for distant entities)
    // 2: MODERATE THROTTLE    - MSPT 38.0ms - 44.0ms (Freeze inactive item ticks, defer block physics)
    // 3: AGGRESSIVE CULLING   - MSPT 44.0ms - 46.0ms (Cap player tracking distance to 20 blocks, tighten packet rates)
    // 4: HYPER-SCALE LOCKDOWN - MSPT >= 46.0ms (Lockdown: suppress cosmetic packets, skip non-player ticks)
    private volatile int throttleLevel = 0;
    private volatile double currentMspt = 0.0D;
    private volatile double currentTps = 20.0D;
    private volatile Boolean manualEmergencyOverride = null;

    private UniverseGovernorTask() {}

    public static UniverseGovernorTask instance() {
        if (instance == null) {
            instance = new UniverseGovernorTask();
        }
        return instance;
    }

    public synchronized void start() {
        stop();
        this.scheduledTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                new MinecraftInternalPlugin(),
                this,
                20L,
                20L
        );
    }

    public synchronized void stop() {
        if (this.scheduledTask != null) {
            this.scheduledTask.cancel();
            this.scheduledTask = null;
        }
    }

    public synchronized boolean isRunning() {
        return this.scheduledTask != null && !this.scheduledTask.isCancelled();
    }

    @Override
    public void run() {
        if (!UniversePurpurConfig.enabled) {
            this.throttleLevel = 0;
            return;
        }

        try {
            this.currentMspt = Bukkit.getAverageTickTime();
            double[] tpsArr = Bukkit.getTPS();
            this.currentTps = (tpsArr != null && tpsArr.length > 0) ? Math.min(20.0D, Math.max(0.0D, tpsArr[0])) : 20.0D;
        } catch (Throwable ignore) {
            this.currentMspt = 0.0D;
            this.currentTps = 20.0D;
        }

        if (manualEmergencyOverride != null) {
            this.throttleLevel = manualEmergencyOverride ? 4 : 0;
            return;
        }

        if (!UniversePurpurConfig.dynamicThrottle) {
            this.throttleLevel = 0;
            return;
        }

        double target = UniversePurpurConfig.targetMspt;
        double hardLimit = UniversePurpurConfig.hardLimitMspt;

        if (currentMspt >= hardLimit) {
            this.throttleLevel = 4; // Hyper-Scale Lockdown
        } else if (currentMspt >= (hardLimit - 2.0D)) {
            this.throttleLevel = 3; // Aggressive Culling
        } else if (currentMspt >= target) {
            this.throttleLevel = 2; // Moderate Throttle
        } else if (currentMspt >= (target * 0.80D)) {
            this.throttleLevel = 1; // Soft Throttle
        } else {
            this.throttleLevel = 0; // Optimal
        }
    }

    public int getThrottleLevel() {
        return throttleLevel;
    }

    public String getThrottleLevelName() {
        return switch (throttleLevel) {
            case 0 -> "OPTIMAL (Level 0)";
            case 1 -> "SOFT THROTTLE (Level 1)";
            case 2 -> "MODERATE THROTTLE (Level 2)";
            case 3 -> "AGGRESSIVE CULLING (Level 3)";
            case 4 -> "HYPER-SCALE LOCKDOWN (Level 4)";
            default -> "UNKNOWN";
        };
    }

    public double getCurrentMspt() {
        return currentMspt;
    }

    public double getCurrentTps() {
        return currentTps;
    }

    public boolean isEmergencyActive() {
        return throttleLevel >= 4;
    }

    public boolean shouldCullDistantTracking() {
        return throttleLevel >= 3;
    }

    public boolean shouldThrottleDAB() {
        return throttleLevel >= 1;
    }

    public boolean shouldCapBlockPhysics() {
        return throttleLevel >= 2;
    }

    public void setManualEmergencyOverride(Boolean override) {
        this.manualEmergencyOverride = override;
    }

    public Boolean getManualEmergencyOverride() {
        return manualEmergencyOverride;
    }
}
