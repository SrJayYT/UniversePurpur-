package org.purpurmc.purpur.task;

import org.bukkit.Bukkit;
import org.purpurmc.purpur.UniversePurpurConfig;

/**
 * Dynamic MSPT Governor and Lag Mitigation Engine for UniverseSpigot.
 * Continuously computes real-time MSPT/TPS metrics and applies multi-tier
 * dynamic throttling for massive server populations (+10,000 players).
 *
 * Author: DevPapo
 */
public class UniverseGovernorTask implements Runnable {
    private static UniverseGovernorTask instance;

    // Throttle Levels:
    // 0: NORMAL - MSPT < 35.0ms (Optimal, full fidelity)
    // 1: SOFT   - MSPT 35.0ms - 42.0ms (Soft throttling, DAB brain ticks slowed)
    // 2: HARD   - MSPT 42.0ms - 48.0ms (Hard throttling, tracking distance capped)
    // 3: CRITICAL / EMERGENCY - MSPT >= 48.0ms (Emergency lag protection active)
    private int throttleLevel = 0;
    private double currentMspt = 0.0D;
    private double currentTps = 20.0D;
    private Boolean manualEmergencyOverride = null;

    private UniverseGovernorTask() {}

    public static UniverseGovernorTask instance() {
        if (instance == null) {
            instance = new UniverseGovernorTask();
        }
        return instance;
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
            this.throttleLevel = manualEmergencyOverride ? 3 : 0;
            return;
        }

        if (!UniversePurpurConfig.dynamicThrottle) {
            this.throttleLevel = 0;
            return;
        }

        double target = UniversePurpurConfig.targetMspt;
        double hardLimit = UniversePurpurConfig.hardLimitMspt;

        if (currentMspt >= hardLimit) {
            this.throttleLevel = 3; // Emergency
        } else if (currentMspt >= target) {
            this.throttleLevel = 2; // Hard throttle
        } else if (currentMspt >= (target * 0.85D)) {
            this.throttleLevel = 1; // Soft throttle
        } else {
            this.throttleLevel = 0; // Normal
        }
    }

    public int getThrottleLevel() {
        return throttleLevel;
    }

    public String getThrottleLevelName() {
        return switch (throttleLevel) {
            case 0 -> "OPTIMAL (Level 0)";
            case 1 -> "SOFT THROTTLE (Level 1)";
            case 2 -> "HARD THROTTLE (Level 2)";
            case 3 -> "EMERGENCY PROTECTION (Level 3)";
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
        return throttleLevel >= 3;
    }

    public boolean shouldThrottleDAB() {
        return throttleLevel >= 1;
    }

    public boolean shouldCapTracking() {
        return throttleLevel >= 2;
    }

    public void setManualEmergencyOverride(Boolean override) {
        this.manualEmergencyOverride = override;
    }

    public Boolean getManualEmergencyOverride() {
        return manualEmergencyOverride;
    }
}
