package org.purpurmc.purpur.network;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.purpurmc.purpur.UniversePurpurConfig;
import org.purpurmc.purpur.task.UniverseGovernorTask;

/**
 * Ultra-high-performance lock-free Packet Rate Limiter for massive server connections (+15,000 players).
 * Employs a CAS loop (Compare-And-Set) atomic token bucket algorithm to suppress crasher floods and packet storms
 * with zero lock contention, precise micro-token refill, and zero negative token underflow.
 *
 * Author: DevPapo
 */
public final class PacketRateLimiter {
    private final AtomicInteger tokens;
    private final AtomicLong lastRefillTimestamp;
    private final AtomicLong totalDroppedPackets = new AtomicLong(0);

    public PacketRateLimiter() {
        int initialBurst = Math.max(10, UniversePurpurConfig.packetBurstLimit);
        this.tokens = new AtomicInteger(initialBurst);
        this.lastRefillTimestamp = new AtomicLong(System.currentTimeMillis());
    }

    /**
     * Checks if an incoming network packet is allowed through the pipeline.
     * Uses a lock-free CAS loop to avoid race conditions or negative token underflows.
     *
     * @return true if packet is accepted; false if it exceeded packet rate threshold.
     */
    public boolean checkPacket() {
        if (!UniversePurpurConfig.packetRateLimitEnabled) {
            return true;
        }

        refillTokens();

        int current;
        do {
            current = tokens.get();
            if (current <= 0) {
                totalDroppedPackets.incrementAndGet();
                return false;
            }
        } while (!tokens.compareAndSet(current, current - 1));

        return true;
    }

    private void refillTokens() {
        long now = System.currentTimeMillis();
        long last = lastRefillTimestamp.get();
        long elapsed = now - last;
        if (elapsed <= 0) {
            return;
        }

        int baseRate = Math.max(10, UniversePurpurConfig.maxPacketsPerSecond);
        int baseBurst = Math.max(10, UniversePurpurConfig.packetBurstLimit);

        // Adaptive Rate Limiting: If Governor is under high load (Level 3 or 4), tighten burst window
        if (UniversePurpurConfig.adaptiveRateLimiting) {
            int level = UniverseGovernorTask.instance().getThrottleLevel();
            if (level >= 4) {
                baseRate = Math.max(10, (int) (baseRate * 0.60D));
                baseBurst = Math.max(10, (int) (baseBurst * 0.50D));
            } else if (level >= 3) {
                baseRate = Math.max(10, (int) (baseRate * 0.80D));
                baseBurst = Math.max(10, (int) (baseBurst * 0.75D));
            }
        }

        // Proportional token refill: add tokens based on elapsed time without starving legitimate players
        long tokensToAdd = (elapsed * (long) baseRate) / 1000L;
        if (tokensToAdd > 0) {
            long timeConsumed = (tokensToAdd * 1000L) / baseRate;
            // Advance timestamp by consumed time to prevent fractional token loss, or advance to now if capped
            long newTime = (tokens.get() >= baseBurst) ? now : (last + timeConsumed);
            if (lastRefillTimestamp.compareAndSet(last, newTime)) {
                final int maxAllowed = baseBurst;
                tokens.updateAndGet(current -> (int) Math.min(maxAllowed, (long) current + tokensToAdd));
            }
        }
    }

    public long getDroppedPackets() {
        return totalDroppedPackets.get();
    }

    public void reset() {
        tokens.set(Math.max(10, UniversePurpurConfig.packetBurstLimit));
        lastRefillTimestamp.set(System.currentTimeMillis());
        totalDroppedPackets.set(0);
    }
}
